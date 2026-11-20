package wa

import (
	"context"
	"encoding/json"
	"errors"
	"strings"
	"time"

	"go.mau.fi/whatsmeow/appstate"
	"go.mau.fi/whatsmeow/proto/waE2E"
	"go.mau.fi/whatsmeow/types"
	"go.mau.fi/whatsmeow/types/events"
	"google.golang.org/protobuf/proto"
)

// ---------------------------------------------------------------------------
// Disappearing messages

// expirationFor is the chat's disappearing-messages timer in seconds (0 = off).
func (c *Client) expirationFor(chat string) uint32 {
	var exp int64
	_ = c.db.QueryRow(`SELECT ephemeral FROM chats WHERE jid=?`, chat).Scan(&exp)
	if exp < 0 {
		return 0
	}
	return uint32(exp)
}

// withExpiration adds the chat's timer to an outgoing message's context, so
// what you send disappears for everyone like WhatsApp's own messages.
func (c *Client) withExpiration(chat string, ci *waE2E.ContextInfo) *waE2E.ContextInfo {
	exp := c.expirationFor(chat)
	if exp == 0 {
		return ci
	}
	if ci == nil {
		ci = &waE2E.ContextInfo{}
	}
	ci.Expiration = proto.Uint32(exp)
	return ci
}

func (c *Client) setEphemeral(chat string, seconds uint32) {
	res, err := c.db.Exec(`UPDATE chats SET ephemeral=? WHERE jid=? AND ephemeral!=?`, seconds, chat, seconds)
	if err == nil {
		if n, _ := res.RowsAffected(); n > 0 {
			c.emit("chats_changed", nil)
		}
	}
}

// ---------------------------------------------------------------------------
// Blocking and leaving

// SetBlocked blocks or unblocks a person (here and on all your devices).
func (c *Client) SetBlocked(chat string, block bool) error {
	jid, err := parseChat(chat)
	if err != nil {
		return err
	}
	if jid.Server == types.GroupServer {
		return errors.New("groups can't be blocked; exit the group instead")
	}
	action := events.BlocklistChangeActionUnblock
	if block {
		action = events.BlocklistChangeActionBlock
	}
	ctx, cancel := context.WithTimeout(c.ctx, 30*time.Second)
	defer cancel()
	list, err := c.cli.UpdateBlocklist(ctx, jid, action, "")
	if err != nil {
		return err
	}
	if list != nil && len(list.Items) > 0 {
		c.applyBlocklist(list)
	} else if block {
		_, _ = c.db.Exec(`INSERT OR IGNORE INTO blocked (jid) VALUES (?)`, chat)
	} else {
		_, _ = c.db.Exec(`DELETE FROM blocked WHERE jid=?`, chat)
	}
	c.emit("chats_changed", nil)
	return nil
}

// RefreshBlocklist reloads who you've blocked (after connecting).
func (c *Client) RefreshBlocklist() {
	if !c.cli.IsConnected() {
		return
	}
	ctx, cancel := context.WithTimeout(c.ctx, 30*time.Second)
	defer cancel()
	list, err := c.cli.GetBlocklist(ctx, "")
	if err != nil {
		c.log.Warnf("blocklist: %v", err)
		return
	}
	c.applyBlocklist(list)
	c.emit("chats_changed", nil)
}

func (c *Client) applyBlocklist(list *types.Blocklist) {
	if list == nil {
		return
	}
	tx, err := c.db.Begin()
	if err != nil {
		return
	}
	_, _ = tx.Exec(`DELETE FROM blocked`)
	for _, it := range list.Items {
		if !it.Active && (it.PN.IsEmpty() && it.LID.IsEmpty()) {
			continue
		}
		j := it.PN
		if j.IsEmpty() {
			j = c.resolveLID(it.LID)
		}
		if j.IsEmpty() {
			continue
		}
		_, _ = tx.Exec(`INSERT OR IGNORE INTO blocked (jid) VALUES (?)`, j.ToNonAD().String())
	}
	_ = tx.Commit()
}

// LeaveGroup exits a group.
func (c *Client) LeaveGroup(chat string) error {
	jid, err := parseChat(chat)
	if err != nil || jid.Server != types.GroupServer {
		return errors.New("not a group")
	}
	ctx, cancel := context.WithTimeout(c.ctx, 30*time.Second)
	defer cancel()
	return c.cli.LeaveGroup(ctx, jid)
}

// ---------------------------------------------------------------------------
// Clearing and deleting chats

func (c *Client) wipeChatMessages(chat string, keepStarred bool) {
	if keepStarred {
		_, _ = c.db.Exec(`DELETE FROM messages WHERE chat=? AND starred=0`, chat)
	} else {
		_, _ = c.db.Exec(`DELETE FROM messages WHERE chat=?`, chat)
	}
	for _, t := range []string{"reactions", "receipts", "poll_votes", "pinned_msgs"} {
		_, _ = c.db.Exec(`DELETE FROM `+t+` WHERE chat=?`, chat)
	}
}

// ClearChat removes a chat's messages from this device (starred ones stay).
func (c *Client) ClearChat(chat string) {
	c.wipeChatMessages(chat, true)
	_, _ = c.db.Exec(`UPDATE chats SET unread=0, marked_unread=0 WHERE jid=?`, chat)
	c.emit("chat_cleared", map[string]string{"chat": chat})
	c.emit("chats_changed", nil)
}

// DeleteChat deletes a chat here and on your phone.
func (c *Client) DeleteChat(chat string) {
	target, lastTs := c.appStateTarget(chat)
	c.wipeChatMessages(chat, false)
	_, _ = c.db.Exec(`DELETE FROM chats WHERE jid=?`, chat)
	c.emit("chat_cleared", map[string]string{"chat": chat})
	c.emit("chats_changed", nil)
	if !target.IsEmpty() {
		c.syncAppState(appstate.BuildDeleteChat(target, time.UnixMilli(lastTs), nil, false))
	}
}

// ---------------------------------------------------------------------------
// Forwarding

// forwardable copies a stored message for forwarding: marked as forwarded,
// without reply context, with the target chat's disappearing timer.
func forwardable(raw []byte, exp uint32) (*waE2E.Message, error) {
	src := &waE2E.Message{}
	if len(raw) == 0 || proto.Unmarshal(raw, src) != nil {
		return nil, errors.New("this message can't be forwarded")
	}
	out := &waE2E.Message{}
	ctxFor := func(old *waE2E.ContextInfo) *waE2E.ContextInfo {
		score := old.GetForwardingScore() + 1
		ci := &waE2E.ContextInfo{IsForwarded: proto.Bool(true), ForwardingScore: proto.Uint32(score)}
		if exp > 0 {
			ci.Expiration = proto.Uint32(exp)
		}
		return ci
	}
	switch {
	case src.Conversation != nil:
		out.ExtendedTextMessage = &waE2E.ExtendedTextMessage{Text: src.Conversation, ContextInfo: ctxFor(nil)}
	case src.ExtendedTextMessage != nil:
		m := proto.Clone(src.ExtendedTextMessage).(*waE2E.ExtendedTextMessage)
		m.ContextInfo = ctxFor(m.GetContextInfo())
		out.ExtendedTextMessage = m
	case src.ImageMessage != nil:
		if src.ImageMessage.GetViewOnce() {
			return nil, errors.New("view-once photos can't be forwarded")
		}
		m := proto.Clone(src.ImageMessage).(*waE2E.ImageMessage)
		m.ContextInfo = ctxFor(m.GetContextInfo())
		out.ImageMessage = m
	case src.VideoMessage != nil:
		if src.VideoMessage.GetViewOnce() {
			return nil, errors.New("view-once videos can't be forwarded")
		}
		m := proto.Clone(src.VideoMessage).(*waE2E.VideoMessage)
		m.ContextInfo = ctxFor(m.GetContextInfo())
		out.VideoMessage = m
	case src.AudioMessage != nil:
		if src.AudioMessage.GetViewOnce() {
			return nil, errors.New("view-once voice messages can't be forwarded")
		}
		m := proto.Clone(src.AudioMessage).(*waE2E.AudioMessage)
		m.ContextInfo = ctxFor(m.GetContextInfo())
		out.AudioMessage = m
	case src.DocumentMessage != nil:
		m := proto.Clone(src.DocumentMessage).(*waE2E.DocumentMessage)
		m.ContextInfo = ctxFor(m.GetContextInfo())
		out.DocumentMessage = m
	case src.GetDocumentWithCaptionMessage().GetMessage().GetDocumentMessage() != nil:
		m := proto.Clone(src.GetDocumentWithCaptionMessage().GetMessage().GetDocumentMessage()).(*waE2E.DocumentMessage)
		m.ContextInfo = ctxFor(m.GetContextInfo())
		out.DocumentMessage = m
	case src.StickerMessage != nil:
		m := proto.Clone(src.StickerMessage).(*waE2E.StickerMessage)
		m.ContextInfo = ctxFor(m.GetContextInfo())
		out.StickerMessage = m
	case src.LocationMessage != nil:
		m := proto.Clone(src.LocationMessage).(*waE2E.LocationMessage)
		m.ContextInfo = ctxFor(m.GetContextInfo())
		out.LocationMessage = m
	case src.ContactMessage != nil:
		m := proto.Clone(src.ContactMessage).(*waE2E.ContactMessage)
		m.ContextInfo = ctxFor(m.GetContextInfo())
		out.ContactMessage = m
	default:
		return nil, errors.New("this kind of message can't be forwarded")
	}
	return out, nil
}

// ForwardMessages forwards messages (JSON array of ids, in order) from one
// chat to others (JSON array of chat ids). Returns the new messages as a
// JSON array; delivery updates follow as usual.
func (c *Client) ForwardMessages(fromChat, idsJSON, toJSON string) (string, error) {
	var ids, targets []string
	if err := json.Unmarshal([]byte(idsJSON), &ids); err != nil {
		return "", err
	}
	if err := json.Unmarshal([]byte(toJSON), &targets); err != nil {
		return "", err
	}
	if len(ids) == 0 || len(targets) == 0 {
		return "[]", nil
	}
	if len(targets) > 5 {
		return "", errors.New("you can forward to up to 5 chats at a time")
	}
	type source struct {
		raw   []byte
		local string
	}
	sources := make([]source, 0, len(ids))
	for _, id := range ids {
		var s source
		if err := c.db.QueryRow(`SELECT raw, local FROM messages WHERE chat=? AND id=? AND revoked=0`, fromChat, id).Scan(&s.raw, &s.local); err != nil {
			continue
		}
		sources = append(sources, s)
	}
	if len(sources) == 0 {
		return "", errors.New("those messages can't be forwarded")
	}
	var out []*msgRow
	var firstErr error
	for _, to := range targets {
		jid, err := parseChat(to)
		if err != nil {
			continue
		}
		exp := c.expirationFor(to)
		for _, s := range sources {
			msg, err := forwardable(s.raw, exp)
			if err != nil {
				if firstErr == nil {
					firstErr = err
				}
				continue
			}
			typ, text, extra := describe(msg)
			if typ == "" {
				continue
			}
			ex := map[string]any{}
			if extra != "" {
				_ = json.Unmarshal([]byte(extra), &ex)
			}
			ex["forwarded"] = true
			b, _ := json.Marshal(ex)
			row := &msgRow{
				Chat: to, ID: c.cli.GenerateMessageID(), Sender: c.ownJID().String(), FromMe: true,
				Ts: time.Now().UnixMilli(), Type: typ, Text: text, Extra: string(b), Status: StatusPending,
				RawChat: jid.String(), RawSender: c.ownJID().String(), Local: s.local,
			}
			row.Raw, _ = proto.Marshal(msg)
			ensureChat(c.db, to, jid.Server == types.GroupServer, jid.String())
			if err := upsertMessage(c.db, row); err != nil {
				continue
			}
			_, _ = c.db.Exec(`UPDATE chats SET last_ts=MAX(last_ts,?) WHERE jid=?`, row.Ts, to)
			c.decorate(row)
			out = append(out, row)
			go c.deliver(jid, msg, row)
			// Keep them in order.
			time.Sleep(5 * time.Millisecond)
		}
	}
	c.emit("chats_changed", nil)
	if len(out) == 0 {
		if firstErr == nil {
			firstErr = errors.New("nothing could be forwarded")
		}
		return "", firstErr
	}
	b, _ := json.Marshal(out)
	return string(b), nil
}

// ---------------------------------------------------------------------------
// Starring, keeping and pinning messages

// msgKeyInfo is what's needed to point at a stored message.
func (c *Client) msgKeyInfo(chat, id string) (chatJID, sender types.JID, fromMe bool, err error) {
	var rawChat, rawSender string
	var fm int
	if err = c.db.QueryRow(`SELECT raw_chat, raw_sender, from_me FROM messages WHERE chat=? AND id=?`, chat, id).
		Scan(&rawChat, &rawSender, &fm); err != nil {
		return
	}
	if rawChat == "" {
		rawChat = chat
	}
	chatJID, err = types.ParseJID(rawChat)
	if err != nil {
		return
	}
	fromMe = fm == 1
	if fromMe {
		sender = c.ownJID()
	} else if sender, err = types.ParseJID(rawSender); err != nil {
		sender = chatJID
		err = nil
	}
	return
}

// StarMessage stars or unstars a message (synced with your phone).
func (c *Client) StarMessage(chat, id string, star bool) error {
	chatJID, sender, fromMe, err := c.msgKeyInfo(chat, id)
	if err != nil {
		return errors.New("message not found")
	}
	_, _ = c.db.Exec(`UPDATE messages SET starred=? WHERE chat=? AND id=?`, b2i(star), chat, id)
	if m := c.loadMessage(chat, id); m != nil {
		c.emit("message_update", m)
	}
	target, _ := c.appStateTarget(chat)
	if target.IsEmpty() {
		target = chatJID
	}
	if fromMe || chatJID.Server != types.GroupServer {
		// WhatsApp writes "0" as the participant for these.
		sender = target
	}
	c.syncAppState(appstate.BuildStar(target, sender.ToNonAD(), id, fromMe, star))
	return nil
}

func (c *Client) onStar(v *events.Star) {
	chat := c.canonical(v.ChatJID).String()
	starred := v.Action.GetStarred()
	_, _ = c.db.Exec(`UPDATE messages SET starred=? WHERE chat=? AND id=?`, b2i(starred), chat, v.MessageID)
	if !v.FromFullSync {
		if m := c.loadMessage(chat, v.MessageID); m != nil {
			c.emit("message_update", m)
		}
	}
}

// GetStarred lists starred messages, newest first; chat "" means all chats.
func (c *Client) GetStarred(chat string, limit int) string {
	if limit <= 0 || limit > 1000 {
		limit = 500
	}
	q := `SELECT ` + msgCols + ` FROM messages m WHERE m.starred=1 AND m.revoked=0`
	args := []any{}
	if chat != "" {
		q += ` AND m.chat=?`
		args = append(args, chat)
	}
	q += ` ORDER BY m.ts DESC LIMIT ?`
	args = append(args, limit)
	return c.queryMessagesJSON(q, args...)
}

func (c *Client) queryMessagesJSON(q string, args ...any) string {
	rows, err := c.db.Query(q, args...)
	if err != nil {
		c.log.Errorf("query: %v", err)
		return "[]"
	}
	var list []*msgRow
	for rows.Next() {
		if m, err := scanMsg(rows); err == nil {
			list = append(list, m)
		}
	}
	rows.Close()
	if list == nil {
		return "[]"
	}
	for _, m := range list {
		c.decorate(m)
	}
	b, _ := json.Marshal(list)
	return string(b)
}

// KeepMessage keeps (or un-keeps) a message in a chat with disappearing
// messages, for everyone.
func (c *Client) KeepMessage(chat, id string, keep bool) error {
	chatJID, sender, _, err := c.msgKeyInfo(chat, id)
	if err != nil {
		return errors.New("message not found")
	}
	kt := waE2E.KeepType_KEEP_FOR_ALL
	if !keep {
		kt = waE2E.KeepType_UNDO_KEEP_FOR_ALL
	}
	msg := &waE2E.Message{KeepInChatMessage: &waE2E.KeepInChatMessage{
		Key:         c.cli.BuildMessageKey(chatJID, sender, id),
		KeepType:    kt.Enum(),
		TimestampMS: proto.Int64(time.Now().UnixMilli()),
	}}
	_, _ = c.db.Exec(`UPDATE messages SET kept=? WHERE chat=? AND id=?`, b2i(keep), chat, id)
	if m := c.loadMessage(chat, id); m != nil {
		c.emit("message_update", m)
	}
	go func() {
		ctx, cancel := context.WithTimeout(c.ctx, time.Minute)
		defer cancel()
		if _, err := c.cli.SendMessage(ctx, chatJID, msg); err != nil {
			c.log.Warnf("keep: %v", err)
		}
	}()
	return nil
}

// PinMessage pins a message at the top of the chat for everyone, for
// seconds (24 h, 7 days or 30 days), or unpins it.
func (c *Client) PinMessage(chat, id string, pin bool, seconds int) error {
	chatJID, sender, _, err := c.msgKeyInfo(chat, id)
	if err != nil {
		return errors.New("message not found")
	}
	if seconds <= 0 {
		seconds = 7 * 24 * 3600
	}
	typ := waE2E.PinInChatMessage_UNPIN_FOR_ALL
	if pin {
		typ = waE2E.PinInChatMessage_PIN_FOR_ALL
	}
	now := time.Now()
	msg := &waE2E.Message{PinInChatMessage: &waE2E.PinInChatMessage{
		Key:               c.cli.BuildMessageKey(chatJID, sender, id),
		Type:              typ.Enum(),
		SenderTimestampMS: proto.Int64(now.UnixMilli()),
	}}
	if pin {
		msg.MessageContextInfo = &waE2E.MessageContextInfo{MessageAddOnDurationInSecs: proto.Uint32(uint32(seconds))}
	}
	c.recordPin(chat, id, pin, now.UnixMilli(), now.Add(time.Duration(seconds)*time.Second).UnixMilli())
	go func() {
		ctx, cancel := context.WithTimeout(c.ctx, time.Minute)
		defer cancel()
		if _, err := c.cli.SendMessage(ctx, chatJID, msg); err != nil {
			c.log.Warnf("pin: %v", err)
		}
	}()
	return nil
}

func (c *Client) recordPin(chat, id string, pin bool, ts, until int64) {
	if pin {
		_, _ = c.db.Exec(`INSERT INTO pinned_msgs (chat, msg_id, until, ts) VALUES (?,?,?,?)
			ON CONFLICT(chat, msg_id) DO UPDATE SET until=excluded.until, ts=excluded.ts`, chat, id, until, ts)
		// WhatsApp keeps at most 3 pins; the oldest goes.
		_, _ = c.db.Exec(`DELETE FROM pinned_msgs WHERE chat=? AND msg_id NOT IN
			(SELECT msg_id FROM pinned_msgs WHERE chat=? ORDER BY ts DESC LIMIT 3)`, chat, chat)
	} else {
		_, _ = c.db.Exec(`DELETE FROM pinned_msgs WHERE chat=? AND msg_id=?`, chat, id)
	}
	c.emit("pins_changed", map[string]string{"chat": chat})
}

// GetPinned lists a chat's pinned messages that haven't expired, newest pin first.
func (c *Client) GetPinned(chat string) string {
	return c.queryMessagesJSON(`SELECT `+msgCols+` FROM messages m JOIN pinned_msgs p ON p.chat=m.chat AND p.msg_id=m.id
		WHERE m.chat=? AND p.until>? AND m.revoked=0 ORDER BY p.ts DESC`, chat, time.Now().UnixMilli())
}

// onAddOn handles pins and keeps arriving from others (or your phone).
// Returns true if the message was one of those.
func (c *Client) onAddOn(evt *events.Message, chat string) bool {
	msg := evt.Message
	if p := msg.GetPinInChatMessage(); p != nil {
		target := p.GetKey().GetID()
		dur := int64(msg.GetMessageContextInfo().GetMessageAddOnDurationInSecs())
		if dur <= 0 {
			dur = 7 * 24 * 3600
		}
		ts := evt.Info.Timestamp.UnixMilli()
		c.recordPin(chat, target, p.GetType() == waE2E.PinInChatMessage_PIN_FOR_ALL, ts, ts+dur*1000)
		return true
	}
	if k := msg.GetKeepInChatMessage(); k != nil {
		target := k.GetKey().GetID()
		_, _ = c.db.Exec(`UPDATE messages SET kept=? WHERE chat=? AND id=?`, b2i(k.GetKeepType() == waE2E.KeepType_KEEP_FOR_ALL), chat, target)
		if m := c.loadMessage(chat, target); m != nil {
			c.emit("message_update", m)
		}
		return true
	}
	return false
}

// ---------------------------------------------------------------------------
// Message info (who received / read it, and when)

type receiptOut struct {
	JID  string `json:"jid"`
	Name string `json:"name"`
	Ts   int64  `json:"ts"`
}

type messageInfoOut struct {
	Sent      int64        `json:"sent"`
	Status    int          `json:"status"`
	Read      []receiptOut `json:"read"`
	Played    []receiptOut `json:"played"`
	Delivered []receiptOut `json:"delivered"`
}

// MessageInfo returns delivery details for one of your messages.
func (c *Client) MessageInfo(chat, id string) string {
	var out messageInfoOut
	if c.db.QueryRow(`SELECT ts, status FROM messages WHERE chat=? AND id=?`, chat, id).Scan(&out.Sent, &out.Status) != nil {
		return "{}"
	}
	rows, err := c.db.Query(`SELECT user, status, ts FROM receipts WHERE chat=? AND msg_id=? ORDER BY ts`, chat, id)
	if err == nil {
		for rows.Next() {
			var user string
			var status int
			var ts int64
			if rows.Scan(&user, &status, &ts) != nil {
				continue
			}
			r := receiptOut{JID: user, Ts: ts}
			if j, err := types.ParseJID(user); err == nil {
				r.Name = strings.TrimPrefix(c.resolveName(j).Sender, "~ ")
			}
			switch {
			case status >= StatusPlayed:
				out.Played = append(out.Played, r)
			case status >= StatusRead:
				out.Read = append(out.Read, r)
			default:
				out.Delivered = append(out.Delivered, r)
			}
		}
		rows.Close()
	}
	b, _ := json.Marshal(out)
	return string(b)
}
