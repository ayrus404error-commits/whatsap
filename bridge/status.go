package wa

import (
	"context"
	"encoding/json"
	"errors"
	"os"
	"sort"
	"strings"
	"time"

	"go.mau.fi/whatsmeow/proto/waE2E"
	"go.mau.fi/whatsmeow/proto/waWeb"
	"go.mau.fi/whatsmeow/types"
	"go.mau.fi/whatsmeow/types/events"
	"google.golang.org/protobuf/proto"
	"google.golang.org/protobuf/reflect/protoreflect"
)

// Statuses (stories) are kept in the messages table under the chat
// "status@broadcast"; read_sent doubles as "viewed". They never appear in
// the chat list because that chat has no row in the chats table.

const statusChat = "status@broadcast"

const statusLife = 24 * time.Hour

// onStatus stores a status update from a contact (or one you posted from
// the phone).
func (c *Client) onStatus(evt *events.Message, historical, viewed bool) {
	info := evt.Info
	msg := evt.Message
	if msg == nil {
		return
	}
	if pm := msg.GetProtocolMessage(); pm != nil {
		if pm.GetType() == waE2E.ProtocolMessage_REVOKE {
			c.dropStatus(pm.GetKey().GetID())
			if !historical {
				c.emit("status", map[string]string{"removed": pm.GetKey().GetID()})
			}
		}
		return
	}
	typ, text, extra := describe(msg)
	if typ == "" {
		// "Add yours", question stickers, mentions, spoilers, limited
		// sharing… arrive wrapped one or more levels deep.
		if inner := unwrapStatus(msg); inner != msg {
			msg = inner
			typ, text, extra = describe(msg)
		}
	}
	switch typ {
	case "text", "image", "video", "gif", "voice", "audio", "sticker":
	case "":
		if !hasStatusContent(msg) {
			return // key distribution, receipts and other plumbing
		}
		trailf("can't draw update %s from %s: %s", info.ID, info.Sender.ToNonAD(), fieldNames(msg))
		typ, text, extra = "other", "", ""
	default:
		// A kind of update this app can't draw (location, contact…):
		// still show that the person posted something.
		typ, extra = "other", ""
	}
	if e := msg.GetExtendedTextMessage(); e != nil {
		ex := map[string]any{}
		if extra != "" {
			_ = json.Unmarshal([]byte(extra), &ex)
		}
		ex["bg"], ex["fg"], ex["font"] = e.GetBackgroundArgb(), e.GetTextArgb(), int(e.GetFont())
		b, _ := json.Marshal(ex)
		extra = string(b)
	}
	sender := c.canonical(info.Sender)
	if info.IsFromMe {
		sender = c.ownJID()
	}
	row := &msgRow{
		Chat: statusChat, ID: info.ID, Sender: sender.String(),
		RawChat: statusChat, RawSender: info.Sender.ToNonAD().String(),
		FromMe: info.IsFromMe, Ts: info.Timestamp.UnixMilli(), Type: typ, Text: text, Extra: extra,
		ReadSent: viewed || info.IsFromMe,
	}
	if row.FromMe {
		row.Status = StatusSent
	}
	row.Raw, _ = proto.Marshal(msg)
	if err := upsertMessage(c.db, row); err != nil {
		c.log.Errorf("store status: %v", err)
		trailf("couldn't save %s: %v", info.ID, err)
		return
	}
	if !info.IsFromMe {
		src := "live"
		if historical {
			src = "history"
		}
		trailf("got %s %s from %s (%s)", typ, info.ID, row.Sender, src)
	}
	if !historical {
		c.emit("status", map[string]string{"sender": row.Sender, "id": row.ID})
	}
}

// unwrapStatus opens FutureProofMessage wrappers (StatusAddYours,
// StatusMentionMessage, QuestionMessage, SpoilerMessage, GroupStatusMessage…)
// until it reaches the real content.
func unwrapStatus(m *waE2E.Message) *waE2E.Message {
	fpName := (&waE2E.FutureProofMessage{}).ProtoReflect().Descriptor().FullName()
	for depth := 0; depth < 6 && m != nil; depth++ {
		var inner *waE2E.Message
		m.ProtoReflect().Range(func(fd protoreflect.FieldDescriptor, v protoreflect.Value) bool {
			if fd.Kind() == protoreflect.MessageKind && !fd.IsList() && !fd.IsMap() && fd.Message().FullName() == fpName {
				if fp, ok := v.Message().Interface().(*waE2E.FutureProofMessage); ok && fp.GetMessage() != nil {
					inner = fp.GetMessage()
					return false
				}
			}
			return true
		})
		if inner == nil {
			break
		}
		if inner.MessageContextInfo == nil {
			inner.MessageContextInfo = m.MessageContextInfo
		}
		m = inner
	}
	return m
}

// statusPlumbing are message fields that travel on status@broadcast but
// are not an update anyone posted.
var statusPlumbing = map[protoreflect.Name]bool{
	"senderKeyDistributionMessage": true, "fastRatchetKeySenderKeyDistributionMessage": true,
	"messageContextInfo": true, "protocolMessage": true, "reactionMessage": true,
	"encReactionMessage": true, "pollUpdateMessage": true, "keepInChatMessage": true,
	"pinInChatMessage": true, "statusNotificationMessage": true, "statusStickerInteractionMessage": true,
	"call": true, "secretEncryptedMessage": true, "encCommentMessage": true, "encEventResponseMessage": true,
}

// hasStatusContent reports whether m carries something a person posted.
func hasStatusContent(m *waE2E.Message) bool {
	found := false
	m.ProtoReflect().Range(func(fd protoreflect.FieldDescriptor, _ protoreflect.Value) bool {
		if !statusPlumbing[fd.Name()] {
			found = true
			return false
		}
		return true
	})
	return found
}

func (c *Client) dropStatus(id string) {
	var local string
	_ = c.db.QueryRow(`SELECT local FROM messages WHERE chat=? AND id=?`, statusChat, id).Scan(&local)
	if local != "" {
		_ = os.Remove(local)
	}
	_, _ = c.db.Exec(`DELETE FROM messages WHERE chat=? AND id=?`, statusChat, id)
}

// onHistoryStatuses stores the statuses that came with the history sync.
func (c *Client) onHistoryStatuses(list []*waWeb.WebMessageInfo) {
	cutoff := time.Now().Add(-statusLife)
	for _, web := range list {
		evt, err := c.cli.ParseWebMessage(types.StatusBroadcastJID, web)
		if err != nil || evt.Message == nil || evt.Info.Timestamp.Before(cutoff) {
			continue
		}
		st := web.GetStatus()
		viewed := st == waWeb.WebMessageInfo_READ || st == waWeb.WebMessageInfo_PLAYED
		c.onStatus(evt, true, viewed)
	}
}

type statusItemOut struct {
	ID     string `json:"id"`
	Type   string `json:"type"`
	Text   string `json:"text"`
	Extra  string `json:"extra"`
	Ts     int64  `json:"ts"`
	Viewed bool   `json:"viewed"`
	Local  string `json:"local"`
	Status int    `json:"status"`
	Views  int    `json:"views"`
}

type statusGroupOut struct {
	Sender   string          `json:"sender"`
	Name     string          `json:"name"`
	Mine     bool            `json:"mine"`
	LastTs   int64           `json:"lastTs"`
	Unviewed int             `json:"unviewed"`
	Items    []statusItemOut `json:"items"`
}

// GetStatuses returns the last 24 hours of status updates, grouped by
// person, as JSON: your own first (if any), then people with unviewed
// updates (newest first), then everyone already seen.
func (c *Client) GetStatuses() string {
	now := time.Now()
	c.pruneStatuses(now)
	rows, err := c.db.Query(`SELECT id, sender, ts, type, text, extra, read_sent, from_me, local, status
		FROM messages WHERE chat=? AND revoked=0 AND ts>? ORDER BY ts`, statusChat, now.Add(-statusLife).UnixMilli())
	if err != nil {
		return "[]"
	}
	groups := map[string]*statusGroupOut{}
	var order []string
	var fix [][2]string
	for rows.Next() {
		var it statusItemOut
		var sender string
		var viewed, fromMe int
		if rows.Scan(&it.ID, &sender, &it.Ts, &it.Type, &it.Text, &it.Extra, &viewed, &fromMe, &it.Local, &it.Status) != nil {
			continue
		}
		it.Viewed = viewed == 1 || fromMe == 1
		if it.Local != "" && !fileOK(it.Local) {
			it.Local = ""
		}
		if fromMe == 0 {
			// Saved under a LID before WhatsApp told us the number: file it
			// under the number now, so it matches the chat and the contact.
			if j, err := types.ParseJID(sender); err == nil && j.Server == types.HiddenUserServer {
				if pn := c.resolveLID(j); pn.String() != sender {
					fix = append(fix, [2]string{it.ID, pn.String()})
					sender = pn.String()
				}
			}
		}
		g := groups[sender]
		if g == nil {
			g = &statusGroupOut{Sender: sender, Mine: fromMe == 1}
			groups[sender] = g
			order = append(order, sender)
		}
		g.Items = append(g.Items, it)
		g.LastTs = max(g.LastTs, it.Ts)
		if !it.Viewed {
			g.Unviewed++
		}
	}
	rows.Close()
	for _, f := range fix {
		_, _ = c.db.Exec(`UPDATE messages SET sender=? WHERE chat=? AND id=?`, f[1], statusChat, f[0])
	}
	out := make([]*statusGroupOut, 0, len(order))
	for _, s := range order {
		g := groups[s]
		if g.Mine {
			g.Name = "My status"
			for i := range g.Items {
				_ = c.db.QueryRow(`SELECT COUNT(*) FROM receipts WHERE chat=? AND msg_id=?`, statusChat, g.Items[i].ID).Scan(&g.Items[i].Views)
			}
		} else if j, err := types.ParseJID(s); err == nil {
			g.Name = strings.TrimPrefix(c.resolveName(j).Title, "~ ")
		}
		out = append(out, g)
	}
	sort.SliceStable(out, func(a, b int) bool {
		x, y := out[a], out[b]
		if x.Mine != y.Mine {
			return x.Mine
		}
		if (x.Unviewed > 0) != (y.Unviewed > 0) {
			return x.Unviewed > 0
		}
		return x.LastTs > y.LastTs
	})
	b, _ := json.Marshal(out)
	return string(b)
}

// pruneStatuses deletes statuses (and their files) older than two days.
func (c *Client) pruneStatuses(now time.Time) {
	cutoff := now.Add(-2 * statusLife).UnixMilli()
	rows, err := c.db.Query(`SELECT local FROM messages WHERE chat=? AND ts<? AND local!=''`, statusChat, cutoff)
	if err == nil {
		var files []string
		for rows.Next() {
			var p string
			if rows.Scan(&p) == nil {
				files = append(files, p)
			}
		}
		rows.Close()
		for _, p := range files {
			_ = os.Remove(p)
		}
	}
	_, _ = c.db.Exec(`DELETE FROM messages WHERE chat=? AND ts<?`, statusChat, cutoff)
}

// MarkStatusViewed marks a status as seen and, if sendReceipt is true, lets
// the poster see that you viewed it.
func (c *Client) MarkStatusViewed(id string, sendReceipt bool) {
	var rawSender string
	var viewed, fromMe int
	if c.db.QueryRow(`SELECT raw_sender, read_sent, from_me FROM messages WHERE chat=? AND id=?`, statusChat, id).
		Scan(&rawSender, &viewed, &fromMe) != nil || fromMe == 1 || viewed == 1 {
		return
	}
	_, _ = c.db.Exec(`UPDATE messages SET read_sent=1 WHERE chat=? AND id=?`, statusChat, id)
	if !sendReceipt {
		return
	}
	sj, err := types.ParseJID(rawSender)
	if err != nil {
		return
	}
	go func() {
		ctx, cancel := context.WithTimeout(c.ctx, 30*time.Second)
		defer cancel()
		if err := c.cli.MarkRead(ctx, []types.MessageID{id}, time.Now(), types.StatusBroadcastJID, sj); err != nil {
			c.log.Warnf("status viewed receipt: %v", err)
		}
	}()
}

// PostTextStatus shares a text status with a coloured background
// (bg is 0xAARRGGBB; font 0-5 picks one of WhatsApp's status fonts).
func (c *Client) PostTextStatus(text string, bg int64, font int) (string, error) {
	text = strings.TrimSpace(text)
	if text == "" {
		return "", errors.New("empty status")
	}
	if font < 0 || font > 5 {
		font = 0
	}
	msg := &waE2E.Message{ExtendedTextMessage: &waE2E.ExtendedTextMessage{
		Text:           proto.String(text),
		BackgroundArgb: proto.Uint32(uint32(bg)),
		TextArgb:       proto.Uint32(0xFFFFFFFF),
		Font:           waE2E.ExtendedTextMessage_FontType(font).Enum(),
	}}
	ex, _ := json.Marshal(map[string]any{"bg": uint32(bg), "fg": uint32(0xFFFFFFFF), "font": font})
	row := &msgRow{
		Chat: statusChat, ID: c.cli.GenerateMessageID(), Sender: c.ownJID().String(), FromMe: true,
		Ts: time.Now().UnixMilli(), Type: "text", Text: text, Extra: string(ex), Status: StatusPending,
		RawChat: statusChat, RawSender: c.ownJID().String(),
	}
	row.Raw, _ = proto.Marshal(msg)
	if err := upsertMessage(c.db, row); err != nil {
		return "", err
	}
	c.decorate(row)
	go c.deliver(types.StatusBroadcastJID, msg, row)
	b, _ := json.Marshal(row)
	return string(b), nil
}

// ---------------------------------------------------------------------------
// Calls can't be answered on a linked device, but the app shows that one is
// ringing so you can pick it up on your phone.

func (c *Client) onCallOffer(meta types.BasicCallMeta, video, group bool) {
	from := meta.CallCreator
	if from.IsEmpty() {
		from = meta.From
	}
	who := c.canonical(from)
	name := c.resolveName(who).Title
	if !meta.GroupJID.IsEmpty() {
		group = true
	}
	c.emit("call", map[string]any{
		"id": meta.CallID, "from": who.String(), "name": name, "video": video, "group": group,
	})
}

func callIsVideo(evt *events.CallOffer) bool {
	if evt.Data == nil {
		return false
	}
	_, ok := evt.Data.GetOptionalChildByTag("video")
	return ok
}
