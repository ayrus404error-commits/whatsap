// Package wa is the WhatsApp engine for Glass Chat. It is compiled with
// gomobile into an Android library; the Kotlin UI talks to it through Client
// and receives live updates through Listener.
package wa

import (
	"context"
	"database/sql"
	"encoding/json"
	"errors"
	"fmt"
	"net/http"
	"os"
	"path/filepath"
	"strings"
	"sync"
	"sync/atomic"
	"time"

	"go.mau.fi/whatsmeow"
	"go.mau.fi/whatsmeow/appstate"
	"go.mau.fi/whatsmeow/proto/waCompanionReg"
	"go.mau.fi/whatsmeow/proto/waE2E"
	"go.mau.fi/whatsmeow/store"
	"go.mau.fi/whatsmeow/store/sqlstore"
	"go.mau.fi/whatsmeow/types"
	"go.mau.fi/whatsmeow/types/events"
	"google.golang.org/protobuf/proto"
)

// Listener receives events from the engine. kind is a short event name and
// payload is JSON (or empty). Calls arrive on background threads.
type Listener interface {
	OnEvent(kind string, payload string)
}

// Client wraps one linked WhatsApp account.
type Client struct {
	keepDeleted atomic.Bool
	dataDir     string
	listener    Listener
	logs        *logSink
	log         *fileLogger
	container   *sqlstore.Container
	cli         *whatsmeow.Client
	db          *sql.DB
	names       *nameCache
	ctx         context.Context
	cancel      context.CancelFunc
	mu          sync.Mutex
	qrCancel    context.CancelFunc
	avatarSem   chan struct{}
	http        *http.Client
	groupMu     sync.Mutex
	groupBusy   map[string]bool
	active      bool
	dlLocks     keyedLocks
	retryMu     sync.Mutex
	retryWait   map[string]chan *events.MediaRetry
}

// Version of the engine, shown in Settings.
func Version() string { return "1.1.0" }

// NewClient opens (or creates) the account stored in dataDir.
func NewClient(dataDir string, listener Listener) (*Client, error) {
	if err := os.MkdirAll(filepath.Join(dataDir, "avatars"), 0o700); err != nil {
		return nil, err
	}
	sink := newLogSink(filepath.Join(dataDir, "engine.log"))
	openTrail(filepath.Join(dataDir, "status-trail.txt"))
	trailf("app started")
	logger := &fileLogger{mod: "wa", out: sink}
	logger.Infof("starting engine %s", Version())

	// How this linked device appears under "Linked devices" on the phone.
	store.SetOSInfo("Glass Chat", [3]uint32{1, 0, 0})
	store.DeviceProps.PlatformType = waCompanionReg.DeviceProps_CHROME.Enum()

	ctx, cancel := context.WithCancel(context.Background())
	container, err := sqlstore.New(ctx, "sqlite3",
		"file:"+filepath.Join(dataDir, "session.db")+"?_foreign_keys=on&_journal_mode=WAL&_busy_timeout=10000", logger.Sub("DB"))
	if err != nil {
		cancel()
		return nil, fmt.Errorf("open session store: %w", err)
	}
	db, err := openAppDB(filepath.Join(dataDir, "chats.db"))
	if err != nil {
		cancel()
		return nil, fmt.Errorf("open chat store: %w", err)
	}
	c := &Client{
		dataDir:   dataDir,
		listener:  listener,
		logs:      sink,
		log:       logger,
		container: container,
		db:        db,
		names:     &nameCache{m: map[string]resolvedName{}},
		ctx:       ctx,
		cancel:    cancel,
		avatarSem: make(chan struct{}, 2),
		http:      &http.Client{Timeout: 20 * time.Second},
		groupBusy: map[string]bool{},
		retryWait: map[string]chan *events.MediaRetry{},
	}
	if err = c.newWAClient(); err != nil {
		cancel()
		return nil, err
	}
	go c.statusPoller()
	return c, nil
}

func (c *Client) newWAClient() error {
	device, err := c.container.GetFirstDevice(c.ctx)
	if err != nil {
		return fmt.Errorf("load device: %w", err)
	}
	cli := whatsmeow.NewClient(device, c.log.Sub("Client"))
	cli.EmitAppStateEventsOnFullSync = true
	cli.AutomaticMessageRerequestFromPhone = true
	cli.AddEventHandler(c.handleEvent)
	c.cli = cli
	return nil
}

func (c *Client) emit(kind string, payload any) {
	if c.listener == nil {
		return
	}
	var s string
	switch v := payload.(type) {
	case nil:
	case string:
		s = v
	default:
		b, _ := json.Marshal(v)
		s = string(b)
	}
	defer func() {
		if r := recover(); r != nil {
			c.log.Errorf("listener panic: %v", r)
		}
	}()
	c.listener.OnEvent(kind, s)
}

// IsLoggedIn reports whether a WhatsApp account is linked.
func (c *Client) IsLoggedIn() bool {
	return c.cli.Store.ID != nil
}

// IsConnected reports whether the socket to WhatsApp is up.
func (c *Client) IsConnected() bool {
	return c.cli.IsConnected()
}

// Connect opens the connection. When no account is linked yet, it starts the
// pairing flow and emits "qr" events with codes to show.
func (c *Client) Connect() error {
	c.mu.Lock()
	defer c.mu.Unlock()
	if c.cli.IsConnected() {
		return nil
	}
	if c.cli.Store.ID == nil {
		if c.qrCancel != nil {
			c.qrCancel()
		}
		qctx, qcancel := context.WithCancel(c.ctx)
		c.qrCancel = qcancel
		ch, err := c.cli.GetQRChannel(qctx)
		if err != nil {
			return err
		}
		go func() {
			for item := range ch {
				switch item.Event {
				case whatsmeow.QRChannelEventCode:
					c.emit("qr", map[string]any{"code": item.Code, "timeout": int(item.Timeout.Seconds())})
				case whatsmeow.QRChannelSuccess.Event:
					c.emit("pair_success", nil)
				case whatsmeow.QRChannelTimeout.Event:
					c.emit("qr_timeout", nil)
				case whatsmeow.QRChannelEventError:
					c.emit("pair_error", errString(item.Error))
				case whatsmeow.QRChannelClientOutdated.Event:
					c.emit("outdated", nil)
				default:
					c.emit("pair_error", item.Event)
				}
			}
		}()
	}
	return c.cli.Connect()
}

// PairWithPhone asks WhatsApp for an 8-character code to type on the phone
// (Linked devices → Link with phone number instead). Call after Connect,
// once the first "qr" event has arrived.
func (c *Client) PairWithPhone(phone string) (string, error) {
	ctx, cancel := context.WithTimeout(c.ctx, 30*time.Second)
	defer cancel()
	return c.cli.PairPhone(ctx, phone, true, whatsmeow.PairClientChrome, "Chrome (Linux)")
}

// Disconnect closes the socket but keeps the account linked.
func (c *Client) Disconnect() {
	c.mu.Lock()
	defer c.mu.Unlock()
	if c.qrCancel != nil {
		c.qrCancel()
		c.qrCancel = nil
	}
	c.cli.Disconnect()
}

// Logout unlinks this device from the WhatsApp account and wipes local data.
func (c *Client) Logout() error {
	ctx, cancel := context.WithTimeout(c.ctx, 20*time.Second)
	defer cancel()
	err := c.cli.Logout(ctx)
	if err != nil {
		c.log.Warnf("logout: %v", err)
		// Remove the local session even if the server call failed.
		_ = c.cli.Store.Delete(ctx)
		c.cli.Disconnect()
	}
	c.wipeLocal()
	return c.newWAClient()
}

func (c *Client) wipeLocal() {
	for _, t := range []string{"messages", "chats", "reactions", "receipts", "stickers", "poll_votes", "group_info", "blocked", "pinned_msgs"} {
		_, _ = c.db.Exec("DELETE FROM " + t)
	}
	_ = os.RemoveAll(filepath.Join(c.dataDir, "avatars"))
	_ = os.MkdirAll(filepath.Join(c.dataDir, "avatars"), 0o700)
	_ = os.RemoveAll(c.mediaDir())
	_ = os.RemoveAll(c.tmpDir())
	c.invalidateNames()
}

// KeepAlive checks that the connection really works and revives it when it
// doesn't. While the phone sleeps a socket can die without anyone noticing
// (Wi-Fi switching off, a router dropping idle connections), and whatsmeow's
// own keepalive timers stop with the CPU. The app calls this from a periodic
// alarm, on network changes and when it comes back to the foreground.
// timeoutSec bounds the round trip (the app uses a short one when it comes
// back to the foreground, so a dead connection is replaced quickly).
// Returns "connected", "reconnecting", "connecting" or "" (not linked).
func (c *Client) KeepAlive(timeoutSec int) string {
	if c.cli.Store.ID == nil {
		return ""
	}
	if !c.cli.IsConnected() {
		go func() {
			if err := c.Connect(); err != nil {
				c.log.Warnf("keepalive connect: %v", err)
			}
		}()
		return "connecting"
	}
	if timeoutSec <= 0 {
		timeoutSec = 12
	}
	ctx, cancel := context.WithTimeout(c.ctx, time.Duration(timeoutSec)*time.Second)
	defer cancel()
	// The same ping whatsmeow sends every 20–30 s while awake.
	if ok, _ := c.cli.DangerousInternals().SendKeepAlive(ctx); !ok {
		if c.ctx.Err() != nil {
			return ""
		}
		c.log.Warnf("keepalive: no answer within %ds, reconnecting", timeoutSec)
		c.emit("connecting", nil)
		c.cli.ResetConnection()
		return "reconnecting"
	}
	return "connected"
}

// CachedAvatar returns a profile photo already on this device (however old),
// or "". It never touches the network, so notifications don't wait on it.
func (c *Client) CachedAvatar(jid string) string {
	p := c.avatarPath(jid)
	if _, err := os.Stat(p); err == nil {
		return p
	}
	return ""
}

// SetActive marks you online while the app is open, and offline when it goes
// to the background so your phone keeps getting its own notifications.
func (c *Client) SetActive(active bool) {
	c.active = active
	if !c.cli.IsLoggedIn() {
		return
	}
	go func() {
		ctx, cancel := context.WithTimeout(c.ctx, 15*time.Second)
		defer cancel()
		state := types.PresenceUnavailable
		if active {
			state = types.PresenceAvailable
		}
		if err := c.cli.SendPresence(ctx, state); err != nil {
			c.log.Warnf("presence: %v", err)
		}
	}()
}

// Me returns {"jid","phone","name"} for the linked account.
func (c *Client) Me() string {
	out := map[string]string{}
	if id := c.cli.Store.ID; id != nil {
		out["jid"] = id.ToNonAD().String()
		out["phone"] = formatPhone(id.User)
		out["name"] = c.cli.Store.PushName
	}
	b, _ := json.Marshal(out)
	return string(b)
}

// LogTail returns the end of the engine log for the debug screen.
func (c *Client) LogTail() string {
	return c.logs.tail(48 * 1024)
}

func errString(err error) string {
	if err == nil {
		return ""
	}
	return err.Error()
}

// parseChat accepts a JID string from the UI.
func parseChat(s string) (types.JID, error) {
	j, err := types.ParseJID(s)
	if err != nil {
		return j, err
	}
	if j.User == "" {
		return j, errors.New("invalid chat")
	}
	return j, nil
}

// ownJID returns the account's own phone-number JID without device part.
func (c *Client) ownJID() types.JID {
	if c.cli.Store.ID == nil {
		return types.EmptyJID
	}
	return c.cli.Store.ID.ToNonAD()
}

// canonical maps a LID (WhatsApp's privacy address) to the phone-number JID
// when the mapping is known, so one person never shows up as two chats.
func (c *Client) canonical(j types.JID) types.JID {
	pn := c.resolveLID(j)
	if pn.Server == types.DefaultUserServer && j.Server == types.HiddenUserServer {
		c.mergeChat(j.ToNonAD().String(), pn.String())
	}
	return pn
}

// resolveLID is canonical without touching the chat database (safe to call
// inside an open transaction).
func (c *Client) resolveLID(j types.JID) types.JID {
	j = j.ToNonAD()
	if j.Server != types.HiddenUserServer || c.cli.Store.LIDs == nil {
		return j
	}
	if own := c.cli.Store.LID; !own.IsEmpty() && own.User == j.User {
		return c.ownJID()
	}
	pn, err := c.cli.Store.LIDs.GetPNForLID(context.Background(), j)
	if err != nil || pn.IsEmpty() {
		return j
	}
	return pn.ToNonAD()
}

// mergeChat moves rows stored under an old LID chat onto the phone-number chat.
func (c *Client) mergeChat(from, to string) {
	var n int
	if c.db.QueryRow(`SELECT COUNT(*) FROM chats WHERE jid=?`, from).Scan(&n) != nil || n == 0 {
		return
	}
	tx, err := c.db.Begin()
	if err != nil {
		return
	}
	defer tx.Rollback()
	_, _ = tx.Exec(`INSERT INTO chats (jid,name,is_group,last_ts,unread,pinned,archived,muted_until,raw_jid)
		SELECT ?,name,is_group,last_ts,unread,pinned,archived,muted_until,raw_jid FROM chats WHERE jid=?
		ON CONFLICT(jid) DO UPDATE SET last_ts=MAX(chats.last_ts,excluded.last_ts), unread=chats.unread+excluded.unread`, to, from)
	_, _ = tx.Exec(`UPDATE OR IGNORE messages SET chat=? WHERE chat=?`, to, from)
	_, _ = tx.Exec(`DELETE FROM messages WHERE chat=?`, from)
	_, _ = tx.Exec(`UPDATE OR IGNORE reactions SET chat=? WHERE chat=?`, to, from)
	_, _ = tx.Exec(`UPDATE OR IGNORE receipts SET chat=? WHERE chat=?`, to, from)
	_, _ = tx.Exec(`DELETE FROM chats WHERE jid=?`, from)
	if tx.Commit() == nil {
		c.emit("chats_changed", nil)
	}
}

// ---------------------------------------------------------------------------
// Queries for the UI

type chatOut struct {
	JID          string `json:"jid"`
	Name         string `json:"name"`
	Phone        string `json:"phone"`
	IsGroup      bool   `json:"isGroup"`
	Unread       int    `json:"unread"`
	MarkedUnread bool   `json:"markedUnread"`
	Pinned       bool   `json:"pinned"`
	Archived     bool   `json:"archived"`
	MutedUntil   int64  `json:"mutedUntil"`
	LastTs       int64  `json:"lastTs"`
	LastID       string `json:"lastId"`
	LastType     string `json:"lastType"`
	LastText     string `json:"lastText"`
	LastFromMe   bool   `json:"lastFromMe"`
	LastStatus   int    `json:"lastStatus"`
	LastSender   string `json:"lastSender"`
	LastRevoked  bool   `json:"lastRevoked"`
	Participants int    `json:"participants"`
	Blocked      bool   `json:"blocked"`
	Ephemeral    int    `json:"ephemeral"` // disappearing-messages timer, seconds (0 = off)
	Saved        bool   `json:"saved"`     // in your phone's contacts (people only)
}

// GetChats returns all chats (newest first, pinned on top) as a JSON array.
func (c *Client) GetChats() string {
	rows, err := c.db.Query(`
		SELECT c.jid, c.name, c.is_group, c.unread, c.marked_unread, c.pinned, c.archived, c.muted_until, c.participants,
			COALESCE(m.ts, c.last_ts), COALESCE(m.id,''), COALESCE(m.type,''), COALESCE(m.text,''), COALESCE(m.from_me,0),
			COALESCE(m.status,0), COALESCE(m.sender,''), COALESCE(m.revoked,0),
			c.ephemeral, EXISTS(SELECT 1 FROM blocked b WHERE b.jid=c.jid),
			CASE WHEN m.type='document' THEN COALESCE(m.extra,'') ELSE '' END,
			c.react_ts, c.react_text
		FROM chats c
		LEFT JOIN messages m ON m.rowid = (SELECT rowid FROM messages WHERE chat=c.jid ORDER BY ts DESC, rowid DESC LIMIT 1)
		WHERE COALESCE(m.ts, c.last_ts) > 0
		ORDER BY c.pinned DESC, MAX(COALESCE(m.ts, c.last_ts), c.react_ts) DESC`)
	if err != nil {
		c.log.Errorf("GetChats: %v", err)
		return "[]"
	}
	defer rows.Close()
	var out []chatOut
	var needGroup []string
	for rows.Next() {
		var ch chatOut
		var isGroup, marked, pinned, archived, fromMe, revoked, blocked int
		var sender, docExtra, reactText string
		var reactTs int64
		if err := rows.Scan(&ch.JID, &ch.Name, &isGroup, &ch.Unread, &marked, &pinned, &archived, &ch.MutedUntil, &ch.Participants,
			&ch.LastTs, &ch.LastID, &ch.LastType, &ch.LastText, &fromMe, &ch.LastStatus, &sender, &revoked,
			&ch.Ephemeral, &blocked, &docExtra, &reactTs, &reactText); err != nil {
			continue
		}
		// A document without a caption shows its file name, like WhatsApp.
		if ch.LastType == "document" && ch.LastText == "" && docExtra != "" {
			var ex struct {
				FileName string `json:"fileName"`
			}
			if json.Unmarshal([]byte(docExtra), &ex) == nil {
				ch.LastText = ex.FileName
			}
		}
		ch.Blocked = blocked == 1
		ch.IsGroup, ch.MarkedUnread, ch.Pinned, ch.Archived = isGroup == 1, marked == 1, pinned == 1, archived == 1
		ch.LastFromMe, ch.LastRevoked = fromMe == 1, revoked == 1
		// Someone reacted to your message after the last message: show it,
		// like WhatsApp ("Sam reacted 👍 to "📷 Photo"").
		if reactText != "" && reactTs > ch.LastTs {
			ch.LastType, ch.LastText, ch.LastTs = "reaction", reactText, reactTs
			ch.LastFromMe, ch.LastRevoked, sender = false, false, ""
		}
		jid, _ := types.ParseJID(ch.JID)
		if ch.IsGroup {
			if ch.Name == "" {
				needGroup = append(needGroup, ch.JID)
				ch.Name = "Group"
			}
			if sender != "" && !ch.LastFromMe {
				if sj, err := types.ParseJID(sender); err == nil {
					ch.LastSender = c.resolveName(sj).Sender
				}
			}
		} else {
			r := c.resolveName(jid)
			ch.Phone = r.Phone
			ch.Saved = r.Saved
			if r.Saved || ch.Name == "" {
				ch.Name = r.Title
			}
		}
		out = append(out, ch)
	}
	rows.Close()
	for _, g := range needGroup {
		c.fetchGroupAsync(g)
	}
	if out == nil {
		return "[]"
	}
	b, _ := json.Marshal(out)
	return string(b)
}

// GetChat returns one chat's header info as JSON.
func (c *Client) GetChat(chat string) string {
	var ch chatOut
	var isGroup, pinned, archived, blocked int
	err := c.db.QueryRow(`SELECT jid,name,is_group,unread,pinned,archived,muted_until,participants,ephemeral,
		EXISTS(SELECT 1 FROM blocked b WHERE b.jid=chats.jid) FROM chats WHERE jid=?`, chat).
		Scan(&ch.JID, &ch.Name, &isGroup, &ch.Unread, &pinned, &archived, &ch.MutedUntil, &ch.Participants, &ch.Ephemeral, &blocked)
	ch.Blocked = blocked == 1
	jid, perr := parseChat(chat)
	if perr != nil {
		return "{}"
	}
	if err != nil {
		ch.JID = chat
		isGroup = b2i(jid.Server == types.GroupServer)
	}
	ch.IsGroup, ch.Pinned, ch.Archived = isGroup == 1, pinned == 1, archived == 1
	if ch.IsGroup {
		if ch.Name == "" || ch.Participants == 0 {
			c.fetchGroupAsync(chat)
		}
	} else {
		r := c.resolveName(jid)
		ch.Phone = r.Phone
		ch.Saved = r.Saved
		if r.Saved || ch.Name == "" {
			ch.Name = r.Title
		}
	}
	b, _ := json.Marshal(ch)
	return string(b)
}

// GetMessages returns up to limit messages older than beforeTs (ms; 0 = newest),
// oldest first, as a JSON array.
func (c *Client) GetMessages(chat string, beforeTs int64, limit int) string {
	if beforeTs <= 0 {
		beforeTs = 1 << 62
	}
	if limit <= 0 || limit > 500 {
		limit = 60
	}
	rows, err := c.db.Query(`SELECT `+msgCols+` FROM messages m WHERE m.chat=? AND m.ts<? ORDER BY m.ts DESC, m.rowid DESC LIMIT ?`, chat, beforeTs, limit)
	if err != nil {
		c.log.Errorf("GetMessages: %v", err)
		return "[]"
	}
	var list []*msgRow
	for rows.Next() {
		m, err := scanMsg(rows)
		if err == nil {
			list = append(list, m)
		}
	}
	rows.Close()
	ids := make([]string, len(list))
	for i, m := range list {
		ids[i] = m.ID
	}
	reacts := loadReactions(c.db, chat, ids, c.ownJID().String())
	out := make([]*msgRow, 0, len(list))
	for i := len(list) - 1; i >= 0; i-- {
		m := list[i]
		c.decorate(m)
		m.Reactions = reacts[m.ID]
		out = append(out, m)
	}
	b, _ := json.Marshal(out)
	return string(b)
}

func (c *Client) decorate(m *msgRow) {
	if m.Local != "" && !fileOK(m.Local) {
		m.Local = ""
	}
	if m.Type == "poll" && !m.Revoked {
		m.Poll = c.pollState(m)
	}
	if m.FromMe {
		m.SenderName = "You"
	} else if m.Sender != "" {
		if sj, err := types.ParseJID(m.Sender); err == nil {
			m.SenderName = c.resolveName(sj).Sender
		}
	}
	if m.QuotedID != "" {
		if m.QuotedSender == "" || m.QuotedSender == c.ownJID().String() {
			m.QuotedName = "You"
		} else if qj, err := types.ParseJID(m.QuotedSender); err == nil {
			m.QuotedName = c.resolveName(c.canonical(qj)).Sender
		}
	}
}

func (c *Client) loadMessage(chat, id string) *msgRow {
	row := c.db.QueryRow(`SELECT `+msgCols+` FROM messages m WHERE m.chat=? AND m.id=?`, chat, id)
	m, err := scanMsg(row)
	if err != nil {
		return nil
	}
	c.decorate(m)
	m.Reactions = loadReactions(c.db, chat, []string{id}, c.ownJID().String())[id]
	return m
}

// GetContacts lists saved contacts (for starting a new chat) as JSON.
func (c *Client) GetContacts() string {
	type contactOut struct {
		JID   string `json:"jid"`
		Name  string `json:"name"`
		Phone string `json:"phone"`
	}
	out := []contactOut{}
	all, err := c.cli.Store.Contacts.GetAllContacts(context.Background())
	if err != nil {
		return "[]"
	}
	for jid, info := range all {
		if jid.Server != types.DefaultUserServer {
			continue
		}
		name := firstNonEmpty(info.FullName, info.FirstName, info.BusinessName)
		if name == "" {
			continue
		}
		out = append(out, contactOut{JID: jid.String(), Name: name, Phone: formatPhone(jid.User)})
	}
	b, _ := json.Marshal(out)
	return string(b)
}

// CheckNumber returns the chat JID for a phone number, or an error if the
// number isn't on WhatsApp.
func (c *Client) CheckNumber(phone string) (string, error) {
	digits := strings.Map(func(r rune) rune {
		if r >= '0' && r <= '9' {
			return r
		}
		return -1
	}, phone)
	if len(digits) == 10 {
		digits = "91" + digits
	}
	ctx, cancel := context.WithTimeout(c.ctx, 20*time.Second)
	defer cancel()
	res, err := c.cli.IsOnWhatsApp(ctx, []string{"+" + digits})
	if err != nil {
		return "", err
	}
	for _, r := range res {
		if r.IsIn {
			jid := r.JID.ToNonAD()
			ensureChat(c.db, jid.String(), false, jid.String())
			return jid.String(), nil
		}
	}
	return "", errors.New("this number isn't on WhatsApp")
}

// ---------------------------------------------------------------------------
// Sending

// SendText sends a text message (optionally replying to quotedID). It
// returns the new message as JSON straight away; delivery updates follow as
// "message_update" events.
func (c *Client) SendText(chat, text, quotedID string) (string, error) {
	return c.sendText(chat, text, quotedID, nil)
}

// SendTextMentions sends a message that @mentions people. text uses
// "@<number>" for each one (as WhatsApp does); mentionsJSON is a JSON array of
// their JIDs. They get a mention notification, and everyone sees the names.
func (c *Client) SendTextMentions(chat, text, quotedID, mentionsJSON string) (string, error) {
	var mentions []string
	_ = json.Unmarshal([]byte(mentionsJSON), &mentions)
	return c.sendText(chat, text, quotedID, mentions)
}

func (c *Client) sendText(chat, text, quotedID string, mentions []string) (string, error) {
	text = strings.TrimSpace(text)
	if text == "" {
		return "", errors.New("empty message")
	}
	jid, err := parseChat(chat)
	if err != nil {
		return "", err
	}
	msg := &waE2E.Message{}
	row := &msgRow{
		Chat: chat, ID: c.cli.GenerateMessageID(), Sender: c.ownJID().String(), FromMe: true,
		Ts: time.Now().UnixMilli(), Type: "text", Text: text, Status: StatusPending,
		RawChat: jid.String(), RawSender: c.ownJID().String(),
	}
	if quotedID != "" {
		if q := c.loadQuoteSource(chat, quotedID); q != nil {
			msg.ExtendedTextMessage = &waE2E.ExtendedTextMessage{
				Text:        proto.String(text),
				ContextInfo: q.ctx,
			}
			row.QuotedID, row.QuotedSender, row.QuotedText, row.QuotedType = quotedID, q.sender, q.text, q.typ
		}
	}
	if exp := c.expirationFor(chat); exp > 0 {
		// Disappearing messages are on: ours must disappear too.
		if msg.ExtendedTextMessage == nil {
			msg.ExtendedTextMessage = &waE2E.ExtendedTextMessage{Text: proto.String(text)}
		}
		if msg.ExtendedTextMessage.ContextInfo == nil {
			msg.ExtendedTextMessage.ContextInfo = &waE2E.ContextInfo{}
		}
		msg.ExtendedTextMessage.ContextInfo.Expiration = proto.Uint32(exp)
	}
	if len(mentions) > 0 {
		if msg.ExtendedTextMessage == nil {
			msg.ExtendedTextMessage = &waE2E.ExtendedTextMessage{Text: proto.String(text)}
		}
		if msg.ExtendedTextMessage.ContextInfo == nil {
			msg.ExtendedTextMessage.ContextInfo = &waE2E.ContextInfo{}
		}
		msg.ExtendedTextMessage.ContextInfo.MentionedJID = mentions
		// Here, show names instead of "@<number>".
		row.Text, row.Mentions = c.applyMentions(text, mentions)
	}
	if msg.ExtendedTextMessage == nil {
		msg.Conversation = proto.String(text)
	}
	row.Raw, _ = proto.Marshal(msg)
	ensureChat(c.db, chat, jid.Server == types.GroupServer, jid.String())
	if err = upsertMessage(c.db, row); err != nil {
		return "", err
	}
	_, _ = c.db.Exec(`UPDATE chats SET last_ts=MAX(last_ts,?), unread=0, marked_unread=0 WHERE jid=?`, row.Ts, chat)
	c.decorate(row)
	go func() {
		// Link preview (title, description, picture) before it goes out.
		c.addPreview(msg, row)
		c.deliver(jid, msg, row)
	}()
	b, _ := json.Marshal(row)
	return string(b), nil
}

func (c *Client) deliver(to types.JID, msg *waE2E.Message, row *msgRow) {
	ctx, cancel := context.WithTimeout(c.ctx, 2*time.Minute)
	defer cancel()
	if !c.cli.IsConnected() && !c.cli.WaitForConnection(30*time.Second) {
		// Not connected: mark it failed so it can be retried with a tap.
		c.log.Warnf("send %s: not connected", row.ID)
		_, _ = c.db.Exec(`UPDATE messages SET status=? WHERE chat=? AND id=? AND status<=0`, StatusFailed, row.Chat, row.ID)
		if m := c.loadMessage(row.Chat, row.ID); m != nil {
			c.emit("message_update", m)
		}
		c.emit("chats_changed", nil)
		return
	}
	resp, err := c.cli.SendMessage(ctx, to, msg, whatsmeow.SendRequestExtra{ID: row.ID})
	if err != nil {
		c.log.Errorf("send %s: %v", row.ID, err)
		_, _ = c.db.Exec(`UPDATE messages SET status=? WHERE chat=? AND id=? AND status<=0`, StatusFailed, row.Chat, row.ID)
	} else {
		_, _ = c.db.Exec(`UPDATE messages SET status=MAX(status,?), ts=? WHERE chat=? AND id=?`, StatusSent, resp.Timestamp.UnixMilli(), row.Chat, row.ID)
	}
	if m := c.loadMessage(row.Chat, row.ID); m != nil {
		c.emit("message_update", m)
	}
	c.emit("chats_changed", nil)
}

// RetrySend re-sends a failed text message.
func (c *Client) RetrySend(chat, id string) error {
	var raw []byte
	var status int
	if err := c.db.QueryRow(`SELECT raw, status FROM messages WHERE chat=? AND id=? AND from_me=1`, chat, id).Scan(&raw, &status); err != nil {
		return err
	}
	if status != StatusFailed {
		return nil
	}
	// Media whose upload never finished is uploaded again from the local copy.
	if handled, err := c.retryMedia(chat, id); handled {
		return err
	}
	msg := &waE2E.Message{}
	if err := proto.Unmarshal(raw, msg); err != nil {
		return err
	}
	jid, err := parseChat(chat)
	if err != nil {
		return err
	}
	_, _ = c.db.Exec(`UPDATE messages SET status=? WHERE chat=? AND id=?`, StatusPending, chat, id)
	row := c.loadMessage(chat, id)
	if row == nil {
		return errors.New("message not found")
	}
	c.emit("message_update", row)
	go c.deliver(jid, msg, row)
	return nil
}

type quoteSource struct {
	ctx               *waE2E.ContextInfo
	sender, text, typ string
}

func (c *Client) loadQuoteSource(chat, id string) *quoteSource {
	var raw []byte
	var rawSender, typ, text string
	var fromMe int
	if c.db.QueryRow(`SELECT raw, raw_sender, from_me, type, text FROM messages WHERE chat=? AND id=?`, chat, id).
		Scan(&raw, &rawSender, &fromMe, &typ, &text) != nil {
		return nil
	}
	quoted := &waE2E.Message{}
	if len(raw) == 0 || proto.Unmarshal(raw, quoted) != nil {
		quoted = &waE2E.Message{Conversation: proto.String(text)}
	}
	// Strip any nested quote so replies don't grow forever.
	if e := quoted.GetExtendedTextMessage(); e != nil {
		e.ContextInfo = nil
	}
	participant := rawSender
	if fromMe == 1 || participant == "" {
		participant = c.ownJID().String()
	}
	return &quoteSource{
		ctx: &waE2E.ContextInfo{
			StanzaID:      proto.String(id),
			Participant:   proto.String(participant),
			QuotedMessage: quoted,
		},
		sender: participant, text: text, typ: typ,
	}
}

// SendReaction reacts to a message; an empty emoji removes your reaction.
func (c *Client) SendReaction(chat, msgID, emoji string) error {
	jid, err := parseChat(chat)
	if err != nil {
		return err
	}
	var rawSender string
	var fromMe int
	if err := c.db.QueryRow(`SELECT raw_sender, from_me FROM messages WHERE chat=? AND id=?`, chat, msgID).Scan(&rawSender, &fromMe); err != nil {
		return err
	}
	sender := c.ownJID()
	if fromMe == 0 {
		if sj, err := types.ParseJID(rawSender); err == nil {
			sender = sj
		}
	}
	me := c.ownJID().String()
	_, _ = c.db.Exec(`INSERT INTO reactions (chat,msg_id,sender,emoji,ts) VALUES (?,?,?,?,?)
		ON CONFLICT(chat,msg_id,sender) DO UPDATE SET emoji=excluded.emoji, ts=excluded.ts`, chat, msgID, me, emoji, time.Now().UnixMilli())
	if m := c.loadMessage(chat, msgID); m != nil {
		c.emit("message_update", m)
	}
	go func() {
		ctx, cancel := context.WithTimeout(c.ctx, time.Minute)
		defer cancel()
		if _, err := c.cli.SendMessage(ctx, jid, c.cli.BuildReaction(jid, sender, msgID, emoji)); err != nil {
			c.log.Errorf("reaction: %v", err)
		}
	}()
	return nil
}

// DeleteForEveryone revokes one of your own messages.
func (c *Client) DeleteForEveryone(chat, msgID string) error {
	jid, err := parseChat(chat)
	if err != nil {
		return err
	}
	ctx, cancel := context.WithTimeout(c.ctx, time.Minute)
	defer cancel()
	if _, err = c.cli.SendMessage(ctx, jid, c.cli.BuildRevoke(jid, types.EmptyJID, msgID)); err != nil {
		return err
	}
	c.markRevoked(chat, msgID)
	return nil
}

// EditMessage changes the text of one of your own recent messages.
func (c *Client) EditMessage(chat, msgID, text string) error {
	jid, err := parseChat(chat)
	if err != nil {
		return err
	}
	ctx, cancel := context.WithTimeout(c.ctx, time.Minute)
	defer cancel()
	newContent := &waE2E.Message{Conversation: proto.String(text)}
	if _, err = c.cli.SendMessage(ctx, jid, c.cli.BuildEdit(jid, msgID, newContent)); err != nil {
		return err
	}
	_, _ = c.db.Exec(`UPDATE messages SET text=?, edited=1 WHERE chat=? AND id=?`, text, chat, msgID)
	if m := c.loadMessage(chat, msgID); m != nil {
		c.emit("message_update", m)
	}
	return nil
}

// DeleteForMe removes a message from this device only.
func (c *Client) DeleteForMe(chat, msgID string) {
	_, _ = c.db.Exec(`DELETE FROM messages WHERE chat=? AND id=?`, chat, msgID)
	c.emit("message_removed", map[string]string{"chat": chat, "id": msgID})
	c.emit("chats_changed", nil)
}

// MarkChatRead clears a chat's unread count and, if sendReceipts is true,
// sends blue ticks for its unread incoming messages.
func (c *Client) MarkChatRead(chat string, sendReceipts bool) {
	_, _ = c.db.Exec(`UPDATE chats SET unread=0, marked_unread=0 WHERE jid=?`, chat)
	rows, err := c.db.Query(`SELECT id, raw_chat, raw_sender, ts FROM messages WHERE chat=? AND from_me=0 AND read_sent=0 AND type!='waiting' ORDER BY ts`, chat)
	if err != nil {
		return
	}
	type batch struct {
		chat, sender types.JID
		ids          []string
		ts           int64
	}
	batches := map[string]*batch{}
	var all []string
	for rows.Next() {
		var id, rawChat, rawSender string
		var ts int64
		if rows.Scan(&id, &rawChat, &rawSender, &ts) != nil {
			continue
		}
		key := rawChat + "|" + rawSender
		b := batches[key]
		if b == nil {
			cj, err1 := types.ParseJID(rawChat)
			sj, err2 := types.ParseJID(rawSender)
			if err1 != nil || err2 != nil {
				continue
			}
			b = &batch{chat: cj, sender: sj}
			batches[key] = b
		}
		b.ids = append(b.ids, id)
		b.ts = ts
		all = append(all, id)
	}
	rows.Close()
	if len(all) == 0 {
		c.emit("chats_changed", nil)
		return
	}
	for _, id := range all {
		_, _ = c.db.Exec(`UPDATE messages SET read_sent=1 WHERE chat=? AND id=?`, chat, id)
	}
	c.emit("chats_changed", nil)
	if !sendReceipts {
		return
	}
	go func() {
		ctx, cancel := context.WithTimeout(c.ctx, time.Minute)
		defer cancel()
		for _, b := range batches {
			for start := 0; start < len(b.ids); start += 50 {
				end := min(start+50, len(b.ids))
				if err := c.cli.MarkRead(ctx, b.ids[start:end], time.Now(), b.chat, b.sender); err != nil {
					c.log.Warnf("mark read: %v", err)
				}
			}
		}
	}()
}

// SendTyping shows or clears "typing…" in the other person's chat.
func (c *Client) SendTyping(chat string, typing bool) {
	jid, err := parseChat(chat)
	if err != nil {
		return
	}
	state := types.ChatPresencePaused
	if typing {
		state = types.ChatPresenceComposing
	}
	go func() {
		ctx, cancel := context.WithTimeout(c.ctx, 10*time.Second)
		defer cancel()
		_ = c.cli.SendChatPresence(ctx, jid, state, types.ChatPresenceMediaText)
	}()
}

// SubscribePresence asks for online / last-seen updates for a person.
func (c *Client) SubscribePresence(chat string) {
	jid, err := parseChat(chat)
	if err != nil || jid.Server == types.GroupServer {
		return
	}
	go func() {
		ctx, cancel := context.WithTimeout(c.ctx, 10*time.Second)
		defer cancel()
		_ = c.cli.SubscribePresence(ctx, jid)
	}()
}

// appStateTarget is the address the phone uses for a chat in synced settings.
func (c *Client) appStateTarget(chat string) (types.JID, int64) {
	var raw string
	var lastTs int64
	_ = c.db.QueryRow(`SELECT raw_jid, last_ts FROM chats WHERE jid=?`, chat).Scan(&raw, &lastTs)
	if raw == "" {
		raw = chat
	}
	j, _ := types.ParseJID(raw)
	return j, lastTs
}

func (c *Client) syncAppState(patch appstate.PatchInfo) {
	if c.cli.Store == nil || c.cli.Store.AppState == nil || c.cli.Store.AppStateKeys == nil {
		return
	}
	go func() {
		// A panic in a background goroutine would close the whole app.
		defer func() {
			if r := recover(); r != nil {
				c.log.Errorf("app state panic: %v", r)
			}
		}()
		ctx, cancel := context.WithTimeout(c.ctx, time.Minute)
		defer cancel()
		if err := c.cli.SendAppState(ctx, patch); err != nil {
			c.log.Warnf("app state: %v", err)
		}
	}()
}

// SetPinned / SetArchived / SetMutedUntil change chat state here and on the phone.
func (c *Client) SetPinned(chat string, pinned bool) {
	_, _ = c.db.Exec(`UPDATE chats SET pinned=? WHERE jid=?`, b2i(pinned), chat)
	c.emit("chats_changed", nil)
	target, _ := c.appStateTarget(chat)
	c.syncAppState(appstate.BuildPin(target, pinned))
}

func (c *Client) SetArchived(chat string, archived bool) {
	_, _ = c.db.Exec(`UPDATE chats SET archived=?, pinned=CASE WHEN ? THEN 0 ELSE pinned END WHERE jid=?`, b2i(archived), b2i(archived), chat)
	c.emit("chats_changed", nil)
	target, lastTs := c.appStateTarget(chat)
	c.syncAppState(appstate.BuildArchive(target, archived, time.UnixMilli(lastTs), nil))
}

// SetMutedUntil mutes a chat until untilMs (-1 = always, 0 = unmute).
func (c *Client) SetMutedUntil(chat string, untilMs int64) {
	_, _ = c.db.Exec(`UPDATE chats SET muted_until=? WHERE jid=?`, untilMs, chat)
	c.emit("chats_changed", nil)
	target, _ := c.appStateTarget(chat)
	switch {
	case untilMs == 0:
		c.syncAppState(appstate.BuildMute(target, false, 0))
	case untilMs < 0:
		c.syncAppState(appstate.BuildMuteAbs(target, true, proto.Int64(-1)))
	default:
		c.syncAppState(appstate.BuildMuteAbs(target, true, proto.Int64(untilMs)))
	}
}

func (c *Client) MarkUnread(chat string) {
	_, _ = c.db.Exec(`UPDATE chats SET marked_unread=1 WHERE jid=?`, chat)
	c.emit("chats_changed", nil)
}

// Close shuts the engine down.
func (c *Client) Close() {
	c.Disconnect()
	c.cancel()
	_ = c.db.Close()
}
