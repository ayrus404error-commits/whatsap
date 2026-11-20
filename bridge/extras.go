package wa

import (
	"bytes"
	"context"
	"crypto/sha256"
	"encoding/json"
	"errors"
	"strings"
	"time"

	"go.mau.fi/whatsmeow/proto/waE2E"
	"go.mau.fi/whatsmeow/types"
	"go.mau.fi/whatsmeow/types/events"
	"google.golang.org/protobuf/proto"
)

// ---------------------------------------------------------------------------
// Media, links and docs of one chat (the contact-info gallery)

func mediaFilter(kind string) string {
	switch kind {
	case "media":
		return `m.type IN ('image','video','gif')`
	case "docs":
		return `m.type='document'`
	case "links":
		return `m.type='text' AND (m.text LIKE '%http://%' OR m.text LIKE '%https://%' OR m.text LIKE '%www.%')`
	}
	return ""
}

// GetChatMedia lists a chat's photos and videos ("media"), documents
// ("docs") or messages containing links ("links"), newest first, as a JSON
// array of messages. beforeTs (ms) pages backwards; 0 starts at the newest.
func (c *Client) GetChatMedia(chat, kind string, beforeTs int64, limit int) string {
	where := mediaFilter(kind)
	if where == "" {
		return "[]"
	}
	if beforeTs <= 0 {
		beforeTs = 1 << 62
	}
	if limit <= 0 || limit > 1000 {
		limit = 200
	}
	rows, err := c.db.Query(`SELECT `+msgCols+` FROM messages m WHERE m.chat=? AND m.revoked=0 AND m.ts<? AND `+where+
		` ORDER BY m.ts DESC, m.rowid DESC LIMIT ?`, chat, beforeTs, limit)
	if err != nil {
		c.log.Errorf("GetChatMedia: %v", err)
		return "[]"
	}
	var list []*msgRow
	for rows.Next() {
		if m, err := scanMsg(rows); err == nil {
			list = append(list, m)
		}
	}
	rows.Close()
	for _, m := range list {
		c.decorate(m)
	}
	if list == nil {
		return "[]"
	}
	b, _ := json.Marshal(list)
	return string(b)
}

// ChatMediaCounts returns {"media":n,"links":n,"docs":n} for a chat.
func (c *Client) ChatMediaCounts(chat string) string {
	out := map[string]int{}
	for _, k := range []string{"media", "links", "docs"} {
		var n int
		_ = c.db.QueryRow(`SELECT COUNT(*) FROM messages m WHERE m.chat=? AND m.revoked=0 AND `+mediaFilter(k), chat).Scan(&n)
		out[k] = n
	}
	b, _ := json.Marshal(out)
	return string(b)
}

// ---------------------------------------------------------------------------
// Sending locations, contacts and polls

// queueOutgoing stores an outgoing message, returns it as JSON and delivers
// it in the background.
func (c *Client) queueOutgoing(chat string, jid types.JID, msg *waE2E.Message, row *msgRow) (string, error) {
	row.Raw, _ = proto.Marshal(msg)
	ensureChat(c.db, chat, jid.Server == types.GroupServer, jid.String())
	if err := upsertMessage(c.db, row); err != nil {
		return "", err
	}
	_, _ = c.db.Exec(`UPDATE chats SET last_ts=MAX(last_ts,?), unread=0, marked_unread=0 WHERE jid=?`, row.Ts, chat)
	c.decorate(row)
	go c.deliver(jid, msg, row)
	b, _ := json.Marshal(row)
	return string(b), nil
}

func (c *Client) newOutgoing(chat string, jid types.JID, typ, text string, extra map[string]any, quotedID string) (*msgRow, *waE2E.ContextInfo) {
	row := &msgRow{
		Chat: chat, ID: c.cli.GenerateMessageID(), Sender: c.ownJID().String(), FromMe: true,
		Ts: time.Now().UnixMilli(), Type: typ, Text: text, Status: StatusPending,
		RawChat: jid.String(), RawSender: c.ownJID().String(),
	}
	if len(extra) > 0 {
		b, _ := json.Marshal(extra)
		row.Extra = string(b)
	}
	var ci *waE2E.ContextInfo
	if quotedID != "" {
		if q := c.loadQuoteSource(chat, quotedID); q != nil {
			row.QuotedID, row.QuotedSender, row.QuotedText, row.QuotedType = quotedID, q.sender, q.text, q.typ
			ci = q.ctx
		}
	}
	return row, c.withExpiration(chat, ci)
}

// SendLocation shares a place (e.g. your current location).
func (c *Client) SendLocation(chat string, lat, lng float64, name, address, quotedID string) (string, error) {
	jid, err := parseChat(chat)
	if err != nil {
		return "", err
	}
	label := firstNonEmpty(name, address)
	row, ci := c.newOutgoing(chat, jid, "location", label, map[string]any{"lat": lat, "lng": lng}, quotedID)
	msg := &waE2E.Message{LocationMessage: &waE2E.LocationMessage{
		DegreesLatitude:  proto.Float64(lat),
		DegreesLongitude: proto.Float64(lng),
		Name:             optString(name),
		Address:          optString(address),
		ContextInfo:      ci,
	}}
	return c.queueOutgoing(chat, jid, msg, row)
}

// SendContact shares a contact card (vCard text).
func (c *Client) SendContact(chat, name, vcard, quotedID string) (string, error) {
	jid, err := parseChat(chat)
	if err != nil {
		return "", err
	}
	if strings.TrimSpace(vcard) == "" {
		return "", errors.New("empty contact")
	}
	ex := map[string]any{}
	if len(vcard) < 8192 {
		ex["vcard"] = vcard
	}
	row, ci := c.newOutgoing(chat, jid, "contact", name, ex, quotedID)
	msg := &waE2E.Message{ContactMessage: &waE2E.ContactMessage{
		DisplayName: proto.String(name),
		Vcard:       proto.String(vcard),
		ContextInfo: ci,
	}}
	return c.queueOutgoing(chat, jid, msg, row)
}

// SendPoll creates a poll. options is a JSON array of 2–12 strings; multi
// allows choosing more than one answer.
func (c *Client) SendPoll(chat, question, optionsJSON string, multi bool) (string, error) {
	jid, err := parseChat(chat)
	if err != nil {
		return "", err
	}
	question = strings.TrimSpace(question)
	var raw []string
	if err = json.Unmarshal([]byte(optionsJSON), &raw); err != nil {
		return "", err
	}
	var options []string
	seen := map[string]bool{}
	for _, o := range raw {
		o = strings.TrimSpace(o)
		if o != "" && !seen[o] {
			seen[o] = true
			options = append(options, o)
		}
	}
	if question == "" || len(options) < 2 {
		return "", errors.New("a poll needs a question and at least two options")
	}
	if len(options) > 12 {
		options = options[:12]
	}
	selectable := 1
	if multi {
		selectable = 0
	}
	msg := c.cli.BuildPollCreation(question, options, selectable)
	row, _ := c.newOutgoing(chat, jid, "poll", question, map[string]any{"options": options, "selectable": selectable}, "")
	return c.queueOutgoing(chat, jid, msg, row)
}

// ---------------------------------------------------------------------------
// Poll votes

type pollOptionOut struct {
	Name  string `json:"name"`
	Votes int    `json:"votes"`
	Mine  bool   `json:"mine"`
}

type pollOut struct {
	Options []pollOptionOut `json:"options"`
	Voters  int             `json:"voters"`
	Multi   bool            `json:"multi"`
}

func pollOptionsOf(extra string) (opts []string, multi bool) {
	var ex struct {
		Options    []string `json:"options"`
		Selectable *int     `json:"selectable"`
	}
	if json.Unmarshal([]byte(extra), &ex) != nil {
		return nil, false
	}
	return ex.Options, ex.Selectable != nil && *ex.Selectable != 1
}

// optionNames maps the hashed choices in a vote back to option names.
func optionNames(hashes [][]byte, options []string) []string {
	names := []string{}
	for _, h := range hashes {
		for _, o := range options {
			sum := sha256.Sum256([]byte(o))
			if bytes.Equal(sum[:], h) {
				names = append(names, o)
				break
			}
		}
	}
	return names
}

func recordPollVote(db execer, chat, pollID, voter string, names []string, ts int64) {
	b, _ := json.Marshal(names)
	_, _ = db.Exec(`INSERT INTO poll_votes (chat, poll_id, voter, options, ts) VALUES (?,?,?,?,?)
		ON CONFLICT(chat, poll_id, voter) DO UPDATE SET options=excluded.options, ts=excluded.ts WHERE excluded.ts>=poll_votes.ts`,
		chat, pollID, voter, string(b), ts)
}

func (c *Client) pollState(m *msgRow) *pollOut {
	opts, multi := pollOptionsOf(m.Extra)
	if len(opts) == 0 {
		return nil
	}
	out := &pollOut{Multi: multi}
	counts := map[string]int{}
	mine := map[string]bool{}
	me := c.ownJID().String()
	rows, err := c.db.Query(`SELECT voter, options FROM poll_votes WHERE chat=? AND poll_id=?`, m.Chat, m.ID)
	if err == nil {
		for rows.Next() {
			var voter, raw string
			if rows.Scan(&voter, &raw) != nil {
				continue
			}
			var names []string
			_ = json.Unmarshal([]byte(raw), &names)
			if len(names) > 0 {
				out.Voters++
			}
			for _, n := range names {
				counts[n]++
				if voter == me {
					mine[n] = true
				}
			}
		}
		rows.Close()
	}
	for _, o := range opts {
		out.Options = append(out.Options, pollOptionOut{Name: o, Votes: counts[o], Mine: mine[o]})
	}
	return out
}

// onPollVote decrypts a vote on a poll and records it.
func (c *Client) onPollVote(evt *events.Message, chat string, historical bool) {
	pu := evt.Message.GetPollUpdateMessage()
	pollID := pu.GetPollCreationMessageKey().GetID()
	ctx, cancel := context.WithTimeout(c.ctx, 10*time.Second)
	defer cancel()
	vote, err := c.cli.DecryptPollVote(ctx, evt)
	if err != nil {
		c.log.Warnf("poll vote %s: %v", pollID, err)
		return
	}
	var extra string
	if c.db.QueryRow(`SELECT extra FROM messages WHERE chat=? AND id=?`, chat, pollID).Scan(&extra) != nil {
		return
	}
	opts, _ := pollOptionsOf(extra)
	voter := c.canonical(evt.Info.Sender).String()
	if evt.Info.IsFromMe {
		voter = c.ownJID().String()
	}
	recordPollVote(c.db, chat, pollID, voter, optionNames(vote.GetSelectedOptions(), opts), evt.Info.Timestamp.UnixMilli())
	if !historical {
		if m := c.loadMessage(chat, pollID); m != nil {
			c.emit("message_update", m)
		}
	}
}

// VotePoll casts (or changes) your vote. options is a JSON array of the
// chosen option names; an empty array removes your vote.
func (c *Client) VotePoll(chat, pollID, optionsJSON string) error {
	jid, err := parseChat(chat)
	if err != nil {
		return err
	}
	var names []string
	if err = json.Unmarshal([]byte(optionsJSON), &names); err != nil {
		return err
	}
	var rawChat, rawSender string
	var fromMe int
	if err = c.db.QueryRow(`SELECT raw_chat, raw_sender, from_me FROM messages WHERE chat=? AND id=?`, chat, pollID).
		Scan(&rawChat, &rawSender, &fromMe); err != nil {
		return errors.New("poll not found")
	}
	cj, _ := types.ParseJID(rawChat)
	sj, _ := types.ParseJID(rawSender)
	if fromMe == 1 {
		sj = c.ownJID()
	}
	info := &types.MessageInfo{
		MessageSource: types.MessageSource{Chat: cj, Sender: sj, IsFromMe: fromMe == 1, IsGroup: cj.Server == types.GroupServer},
		ID:            pollID,
	}
	ctx, cancel := context.WithTimeout(c.ctx, time.Minute)
	defer cancel()
	msg, err := c.cli.BuildPollVote(ctx, info, names)
	if err != nil {
		return err
	}
	if _, err = c.cli.SendMessage(ctx, jid, msg); err != nil {
		return err
	}
	recordPollVote(c.db, chat, pollID, c.ownJID().String(), names, time.Now().UnixMilli())
	if m := c.loadMessage(chat, pollID); m != nil {
		c.emit("message_update", m)
	}
	return nil
}
