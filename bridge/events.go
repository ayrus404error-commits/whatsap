package wa

import (
	"context"
	"database/sql"
	"encoding/json"
	"fmt"
	"strings"
	"time"

	"go.mau.fi/whatsmeow/proto/waE2E"
	"go.mau.fi/whatsmeow/proto/waHistorySync"
	"go.mau.fi/whatsmeow/proto/waWeb"
	"go.mau.fi/whatsmeow/types"
	"go.mau.fi/whatsmeow/types/events"
	"google.golang.org/protobuf/proto"
)

func (c *Client) handleEvent(evt any) {
	defer func() {
		if r := recover(); r != nil {
			c.log.Errorf("event handler panic on %T: %v", evt, r)
		}
	}()
	switch v := evt.(type) {
	case *events.Connected:
		trailf("connected")
		c.emit("connected", nil)
		go c.RefreshBlocklist()
		go func() {
			time.Sleep(8 * time.Second)
			c.RequestStatusSync(false)
		}()
		if c.active {
			c.SetActive(true)
		}
	case *events.Disconnected:
		trailf("disconnected")
		c.emit("disconnected", nil)
	case *events.KeepAliveTimeout:
		c.emit("connecting", nil)
	case *events.KeepAliveRestored:
		c.emit("connected", nil)
	case *events.PairSuccess:
		c.log.Infof("paired as %s", v.ID)
		c.emit("pair_success", map[string]string{"jid": v.ID.ToNonAD().String()})
	case *events.LoggedOut:
		c.log.Warnf("logged out: %s", v.Reason.String())
		c.wipeLocal()
		_ = c.newWAClient()
		c.emit("logged_out", v.Reason.String())
	case *events.StreamReplaced:
		c.emit("stream_replaced", nil)
	case *events.TemporaryBan:
		c.emit("banned", v.String())
	case *events.ClientOutdated:
		c.emit("outdated", nil)
	case *events.Message:
		c.onMessage(v, false, -1)
	case *events.MediaRetry:
		c.onMediaRetry(v)
	case *events.CallOffer:
		c.onCallOffer(v.BasicCallMeta, callIsVideo(v), false)
	case *events.CallOfferNotice:
		c.onCallOffer(v.BasicCallMeta, v.Media == "video", v.Type == "group")
	case *events.CallTerminate:
		c.emit("call_end", map[string]string{"id": v.CallID})
	case *events.CallAccept:
		c.emit("call_end", map[string]string{"id": v.CallID})
	case *events.CallReject:
		c.emit("call_end", map[string]string{"id": v.CallID})
	case *events.UndecryptableMessage:
		c.onUndecryptable(v)
	case *events.Receipt:
		c.onReceipt(v)
	case *events.HistorySync:
		c.onHistory(v)
	case *events.PushName:
		c.invalidateNames()
	case *events.Contact:
		c.invalidateNames()
		if !v.FromFullSync {
			c.emit("chats_changed", nil)
		}
	case *events.ChatPresence:
		chat := c.canonical(v.Chat)
		c.emit("typing", map[string]any{
			"chat":   chat.String(),
			"sender": c.resolveName(c.canonical(v.Sender)).Sender,
			"state":  string(v.State),
			"media":  string(v.Media),
		})
	case *events.Presence:
		c.emit("presence", map[string]any{
			"jid":      c.canonical(v.From).String(),
			"online":   !v.Unavailable,
			"lastSeen": unixMs(v.LastSeen),
		})
	case *events.Star:
		c.onStar(v)
	case *events.ClearChat:
		chat := c.canonical(v.JID).String()
		c.wipeChatMessages(chat, true)
		c.emit("chat_cleared", map[string]string{"chat": chat})
		c.emit("chats_changed", nil)
	case *events.DeleteChat:
		chat := c.canonical(v.JID).String()
		c.wipeChatMessages(chat, false)
		_, _ = c.db.Exec(`DELETE FROM chats WHERE jid=?`, chat)
		c.emit("chat_cleared", map[string]string{"chat": chat})
		c.emit("chats_changed", nil)
	case *events.Blocklist:
		go c.RefreshBlocklist()
	case *events.Pin:
		c.setChatFlag(v.JID, "pinned", b2i(v.Action.GetPinned()), v.FromFullSync)
	case *events.Archive:
		c.setChatFlag(v.JID, "archived", b2i(v.Action.GetArchived()), v.FromFullSync)
	case *events.Mute:
		until := int64(0)
		if v.Action.GetMuted() {
			until = v.Action.GetMuteEndTimestamp()
			if until == 0 {
				until = -1
			}
		}
		c.setChatFlag(v.JID, "muted_until", until, v.FromFullSync)
	case *events.MarkChatAsRead:
		chat := c.canonical(v.JID).String()
		if v.Action.GetRead() {
			_, _ = c.db.Exec(`UPDATE chats SET unread=0, marked_unread=0 WHERE jid=?`, chat)
			_, _ = c.db.Exec(`UPDATE messages SET read_sent=1 WHERE chat=? AND from_me=0`, chat)
		} else {
			_, _ = c.db.Exec(`UPDATE chats SET marked_unread=1 WHERE jid=?`, chat)
		}
		if !v.FromFullSync {
			c.emit("chats_changed", nil)
		}
	case *events.DeleteForMe:
		chat := c.canonical(v.ChatJID).String()
		_, _ = c.db.Exec(`DELETE FROM messages WHERE chat=? AND id=?`, chat, v.MessageID)
		c.emit("message_removed", map[string]string{"chat": chat, "id": v.MessageID})
	case *events.GroupInfo:
		if v.Name != nil {
			_, _ = c.db.Exec(`UPDATE chats SET name=? WHERE jid=?`, v.Name.Name, v.JID.String())
			c.emit("chats_changed", nil)
		}
		if len(v.Join) > 0 || len(v.Leave) > 0 {
			_, _ = c.db.Exec(`UPDATE chats SET participants=MAX(0, participants+?) WHERE jid=? AND participants>0`, len(v.Join)-len(v.Leave), v.JID.String())
		}
	case *events.JoinedGroup:
		ensureChat(c.db, v.JID.String(), true, v.JID.String())
		_, _ = c.db.Exec(`UPDATE chats SET name=?, participants=?, group_fetched=1, last_ts=MAX(last_ts,?) WHERE jid=?`,
			v.Name, len(v.Participants), time.Now().UnixMilli(), v.JID.String())
		c.emit("chats_changed", nil)
	case *events.Picture:
		c.dropAvatar(c.canonical(v.JID).String())
		c.emit("avatar", c.canonical(v.JID).String())
	case *events.OfflineSyncCompleted:
		trailf("caught up after being offline (%d queued items)", v.Count)
		c.emit("chats_changed", nil)
	case *events.AppStateSyncComplete:
		c.invalidateNames()
		c.emit("chats_changed", nil)
	}
}

func unixMs(t time.Time) int64 {
	if t.IsZero() {
		return 0
	}
	return t.UnixMilli()
}

func (c *Client) setChatFlag(jid types.JID, col string, val any, quiet bool) {
	chat := c.canonical(jid)
	ensureChat(c.db, chat.String(), chat.Server == types.GroupServer, jid.ToNonAD().String())
	_, _ = c.db.Exec(`UPDATE chats SET `+col+`=? WHERE jid=?`, val, chat.String())
	if !quiet {
		c.emit("chats_changed", nil)
	}
}

func skipChat(j types.JID) bool {
	switch j.Server {
	case types.BroadcastServer, types.NewsletterServer, types.BotServer:
		return true
	}
	return j.User == "status"
}

// onMessage stores an incoming (or own-device) message. historical marks
// messages from the initial history sync; webStatus carries their tick state.
func (c *Client) onMessage(evt *events.Message, historical bool, webStatus int) {
	info := evt.Info
	if info.Chat == types.StatusBroadcastJID {
		c.onStatus(evt, historical, false)
		return
	}
	if skipChat(info.Chat) || evt.Message == nil {
		return
	}
	chatJID := c.canonical(info.Chat)
	chat := chatJID.String()
	msg := evt.Message

	if pm := msg.GetProtocolMessage(); pm != nil {
		switch pm.GetType() {
		case waE2E.ProtocolMessage_REVOKE:
			c.markRevoked(chat, pm.GetKey().GetID())
		case waE2E.ProtocolMessage_EPHEMERAL_SETTING:
			if !historical {
				c.setEphemeral(chat, pm.GetEphemeralExpiration())
			}
		case waE2E.ProtocolMessage_MESSAGE_EDIT:
			_, text, _ := describe(pm.GetEditedMessage())
			if text != "" {
				_, _ = c.db.Exec(`UPDATE messages SET text=?, edited=1 WHERE chat=? AND id=?`, text, chat, pm.GetKey().GetID())
				if m := c.loadMessage(chat, pm.GetKey().GetID()); m != nil && !historical {
					c.emit("message_update", m)
				}
			}
		}
		return
	}
	if r := msg.GetReactionMessage(); r != nil {
		sender := c.canonical(info.Sender).String()
		if info.IsFromMe {
			sender = c.ownJID().String()
		}
		target := r.GetKey().GetID()
		_, _ = c.db.Exec(`INSERT INTO reactions (chat,msg_id,sender,emoji,ts) VALUES (?,?,?,?,?)
			ON CONFLICT(chat,msg_id,sender) DO UPDATE SET emoji=excluded.emoji, ts=excluded.ts WHERE excluded.ts>=reactions.ts`,
			chat, target, sender, r.GetText(), info.Timestamp.UnixMilli())
		if !info.IsFromMe {
			c.noteReaction(chat, r, info, historical)
		}
		if !historical {
			if m := c.loadMessage(chat, target); m != nil {
				c.emit("message_update", m)
				if !info.IsFromMe && m.FromMe && r.GetText() != "" {
					c.emit("reaction", map[string]any{
						"chat": chat, "id": target, "emoji": r.GetText(), "text": m.Text,
						"sender": c.resolveName(c.canonical(info.Sender)).Sender,
					})
				}
			}
		}
		return
	}
	if msg.GetPollUpdateMessage() != nil {
		c.onPollVote(evt, chat, historical)
		return
	}
	if c.onAddOn(evt, chat) {
		return
	}

	typ, text, extra := describe(msg)
	extra = withAlbum(msg, extra)
	if isViewOnce(evt, msg) {
		// View once: linked devices usually get it without the photo itself.
		typ, text, extra = viewOnceRow(msg, typ, text, extra)
	}
	if typ == "" {
		return
	}
	senderJID := c.canonical(info.Sender)
	if info.IsFromMe {
		senderJID = c.ownJID()
	}
	row := &msgRow{
		Chat: chat, ID: info.ID, Sender: senderJID.String(),
		RawChat: info.Chat.ToNonAD().String(), RawSender: info.Sender.ToNonAD().String(),
		FromMe: info.IsFromMe, Ts: info.Timestamp.UnixMilli(),
		Type: typ, Text: text, Extra: extra, Edited: evt.IsEdit,
	}
	if row.FromMe {
		row.Status = StatusSent
		if webStatus >= 0 {
			row.Status = webStatus
		}
	}
	if ci := contextInfo(msg); ci != nil {
		if exp := ci.GetExpiration(); exp > 0 && !historical {
			// The chat has disappearing messages on.
			ensureChat(c.db, chat, info.IsGroup, info.Chat.ToNonAD().String())
			c.setEphemeral(chat, exp)
		}
		if ci.GetStanzaID() != "" && ci.GetQuotedMessage() != nil {
			row.QuotedID = ci.GetStanzaID()
			if p := ci.GetParticipant(); p != "" {
				if pj, err := types.ParseJID(p); err == nil {
					row.QuotedSender = c.canonical(pj).String()
				}
			}
			row.QuotedType, row.QuotedText, _ = describe(ci.GetQuotedMessage())
		}
		row.Text, row.Mentions = c.applyMentions(row.Text, ci.GetMentionedJID())
	}
	row.Raw, _ = proto.Marshal(msg)
	if sm := msg.GetStickerMessage(); sm != nil {
		rememberSticker(c.db, sm, row.Ts, false)
	}

	ensureChat(c.db, chat, info.IsGroup, info.Chat.ToNonAD().String())
	// History messages count as already read; the conversation's own
	// unread count is applied separately.
	row.ReadSent = historical
	if err := upsertMessage(c.db, row); err != nil {
		c.log.Errorf("store message: %v", err)
		return
	}
	if !historical && !info.IsFromMe && info.PushName != "" {
		c.invalidateNames()
	}
	bumpChat(c.db, chat, row.Ts, !historical && !info.IsFromMe)
	if info.IsFromMe && !historical {
		// You replied from the phone: that chat is read.
		_, _ = c.db.Exec(`UPDATE chats SET unread=0, marked_unread=0 WHERE jid=?`, chat)
	}
	if info.IsGroup {
		c.ensureGroupName(chat)
	}
	if historical {
		return
	}
	c.decorate(row)
	notif := map[string]any{"message": row}
	var name string
	var muted int64
	_ = c.db.QueryRow(`SELECT name, muted_until FROM chats WHERE jid=?`, chat).Scan(&name, &muted)
	if !info.IsGroup {
		name = c.resolveName(chatJID).Title
	}
	notif["chatName"], notif["isGroup"], notif["mutedUntil"] = name, info.IsGroup, muted
	c.emit("message", notif)
}

// applyMentions swaps "@number" for "@Name" and lists the names, marking
// the one that is you, so the app can highlight them.
func (c *Client) applyMentions(text string, mentioned []string) (string, string) {
	type mention struct {
		T  string `json:"t"`
		Me bool   `json:"me"`
	}
	var list []mention
	var me types.JID
	if c.cli != nil && c.cli.Store.ID != nil {
		me = c.cli.Store.ID.ToNonAD()
	}
	var meLID types.JID
	if c.cli != nil {
		meLID = c.cli.Store.LID.ToNonAD()
	}
	for _, m := range mentioned {
		j, err := types.ParseJID(m)
		if err != nil {
			continue
		}
		pn := c.resolveLID(j).ToNonAD()
		isMe := (!me.IsEmpty() && pn.User == me.User) || (!meLID.IsEmpty() && j.User == meLID.User)
		label := strings.TrimPrefix(c.resolveName(pn).Sender, "~ ")
		if isMe && c.cli != nil && c.cli.Store.PushName != "" {
			label = c.cli.Store.PushName
		}
		if !strings.Contains(text, "@"+j.User) {
			continue
		}
		text = strings.ReplaceAll(text, "@"+j.User, "@"+label)
		list = append(list, mention{T: "@" + label, Me: isMe})
	}
	if len(list) == 0 {
		return text, ""
	}
	b, _ := json.Marshal(list)
	return text, string(b)
}

func (c *Client) markRevoked(chat, id string) {
	if c.keepDeleted.Load() {
		// "Keep deleted messages": others' deleted messages stay readable here.
		_, _ = c.db.Exec(`UPDATE messages SET revoked=1 WHERE chat=? AND id=? AND from_me=0`, chat, id)
		_, _ = c.db.Exec(`UPDATE messages SET revoked=1, text='', extra='', raw=NULL WHERE chat=? AND id=? AND from_me=1`, chat, id)
	} else {
		_, _ = c.db.Exec(`UPDATE messages SET revoked=1, text='', extra='', raw=NULL WHERE chat=? AND id=?`, chat, id)
	}
	if m := c.loadMessage(chat, id); m != nil {
		c.emit("message_update", m)
	}
	c.emit("chats_changed", nil)
}

func (c *Client) onUndecryptable(evt *events.UndecryptableMessage) {
	info := evt.Info
	if info.Chat == types.StatusBroadcastJID && !info.IsFromMe && evt.DecryptFailMode != events.DecryptFailHide {
		// A status that couldn't be decrypted yet (WhatsApp usually re-sends
		// it within moments). Keep a placeholder so the person still shows
		// up in Updates; the real update replaces it when it arrives.
		trailf("couldn't decrypt %s from %s yet (asked phone to resend)", info.ID, info.Sender.ToNonAD())
		_, _ = c.db.Exec(`INSERT OR IGNORE INTO messages (chat,id,sender,raw_chat,raw_sender,from_me,ts,type,extra) VALUES (?,?,?,?,?,0,?,'waiting','')`,
			statusChat, info.ID, c.canonical(info.Sender).String(), statusChat, info.Sender.ToNonAD().String(), info.Timestamp.UnixMilli())
		c.emit("status", map[string]string{"sender": c.canonical(info.Sender).String(), "id": info.ID})
		return
	}
	if info.Chat == types.StatusBroadcastJID {
		if !info.IsFromMe {
			trailf("couldn't decrypt hidden item %s from %s (not an update)", info.ID, info.Sender.ToNonAD())
		}
		return
	}
	if skipChat(info.Chat) || evt.DecryptFailMode == events.DecryptFailHide {
		return
	}
	chat := c.canonical(info.Chat).String()
	ensureChat(c.db, chat, info.IsGroup, info.Chat.ToNonAD().String())
	// WhatsApp doesn't give view-once photos/videos to linked devices: it
	// sends a placeholder instead. Show it as a view-once message (open it on
	// the phone), not as "waiting for this message".
	typ, extra := "waiting", ""
	if evt.IsUnavailable && evt.UnavailableType == events.UnavailableTypeViewOnce {
		typ, extra = "viewonce", `{"viewOnce":true,"kind":"message"}`
	}
	_, err := c.db.Exec(`INSERT OR IGNORE INTO messages (chat,id,sender,raw_chat,raw_sender,from_me,ts,type,extra) VALUES (?,?,?,?,?,?,?,?,?)`,
		chat, info.ID, c.canonical(info.Sender).String(), info.Chat.ToNonAD().String(), info.Sender.ToNonAD().String(),
		b2i(info.IsFromMe), info.Timestamp.UnixMilli(), typ, extra)
	if err == nil {
		bumpChat(c.db, chat, info.Timestamp.UnixMilli(), false)
		if m := c.loadMessage(chat, info.ID); m != nil {
			c.emit("message_update", m)
		}
	}
}

func (c *Client) onReceipt(evt *events.Receipt) {
	if evt.Chat == types.StatusBroadcastJID {
		// You watched these statuses on your phone: mark them seen here too
		// (their ring goes away, as in WhatsApp).
		if evt.IsFromMe && (evt.Type == types.ReceiptTypeReadSelf || evt.Type == types.ReceiptTypeRead || evt.Type == types.ReceiptTypePlayedSelf || evt.Type == types.ReceiptTypePlayed) {
			for _, id := range evt.MessageIDs {
				_, _ = c.db.Exec(`UPDATE messages SET read_sent=1 WHERE chat=? AND id=? AND from_me=0`, statusChat, id)
			}
			c.emit("status", map[string]string{"viewed": "1"})
			return
		}
		// Someone viewed one of your statuses.
		if evt.Type == types.ReceiptTypeRead || evt.Type == types.ReceiptTypePlayed {
			viewer := c.canonical(evt.Sender).String()
			for _, id := range evt.MessageIDs {
				_, _ = c.db.Exec(`INSERT OR IGNORE INTO receipts (chat,msg_id,user,status) VALUES (?,?,?,?)`, statusChat, id, viewer, StatusRead)
			}
			c.emit("status", map[string]string{"views": "1"})
		}
		return
	}
	chat := c.canonical(evt.Chat).String()
	switch evt.Type {
	case types.ReceiptTypeReadSelf:
		// You read these on another device.
		for _, id := range evt.MessageIDs {
			_, _ = c.db.Exec(`UPDATE messages SET read_sent=1 WHERE chat=? AND id=?`, chat, id)
		}
		var left int
		_ = c.db.QueryRow(`SELECT COUNT(*) FROM messages WHERE chat=? AND from_me=0 AND read_sent=0`, chat).Scan(&left)
		_, _ = c.db.Exec(`UPDATE chats SET unread=?, marked_unread=0 WHERE jid=?`, left, chat)
		c.emit("chats_changed", nil)
		return
	case types.ReceiptTypeDelivered, types.ReceiptTypeRead, types.ReceiptTypePlayed:
	default:
		return
	}
	status := StatusDelivered
	if evt.Type == types.ReceiptTypeRead {
		status = StatusRead
	} else if evt.Type == types.ReceiptTypePlayed {
		status = StatusPlayed
	}
	if evt.IsGroup {
		c.groupReceipt(chat, evt, status)
		return
	}
	var changed []string
	for _, id := range evt.MessageIDs {
		// Kept for Message info (when it was delivered / read).
		_, _ = c.db.Exec(`INSERT INTO receipts (chat,msg_id,user,status,ts) VALUES (?,?,?,?,?)
			ON CONFLICT(chat,msg_id,user) DO UPDATE SET status=MAX(receipts.status, excluded.status),
			ts=CASE WHEN excluded.status>receipts.status THEN excluded.ts ELSE receipts.ts END`, chat, id, chat, status, evt.Timestamp.UnixMilli())
		res, err := c.db.Exec(`UPDATE messages SET status=? WHERE chat=? AND id=? AND from_me=1 AND status<? AND status>=0`, status, chat, id, status)
		if err == nil {
			if n, _ := res.RowsAffected(); n > 0 {
				changed = append(changed, id)
			}
		}
	}
	c.emitReceipt(chat, changed, status)
}

// groupReceipt turns ticks blue only once everyone in the group has read.
func (c *Client) groupReceipt(chat string, evt *events.Receipt, status int) {
	user := c.canonical(evt.Sender).String()
	var participants int
	_ = c.db.QueryRow(`SELECT participants FROM chats WHERE jid=?`, chat).Scan(&participants)
	needed := participants - 1
	var changed []string
	byStatus := map[int][]string{}
	for _, id := range evt.MessageIDs {
		_, _ = c.db.Exec(`INSERT INTO receipts (chat,msg_id,user,status,ts) VALUES (?,?,?,?,?)
			ON CONFLICT(chat,msg_id,user) DO UPDATE SET status=MAX(receipts.status, excluded.status),
			ts=CASE WHEN excluded.status>receipts.status THEN excluded.ts ELSE receipts.ts END`, chat, id, user, status, evt.Timestamp.UnixMilli())
		var delivered, read int
		_ = c.db.QueryRow(`SELECT COUNT(*), COALESCE(SUM(status>=3),0) FROM receipts WHERE chat=? AND msg_id=?`, chat, id).Scan(&delivered, &read)
		newStatus := StatusDelivered
		if needed > 0 && read >= needed {
			newStatus = StatusRead
		} else if needed <= 0 && read > 0 && participants == 0 {
			// Group size unknown yet: stay on grey ticks.
			newStatus = StatusDelivered
		}
		res, err := c.db.Exec(`UPDATE messages SET status=? WHERE chat=? AND id=? AND from_me=1 AND status<? AND status>=0`, newStatus, chat, id, newStatus)
		if err == nil {
			if n, _ := res.RowsAffected(); n > 0 {
				byStatus[newStatus] = append(byStatus[newStatus], id)
				changed = append(changed, id)
			}
		}
	}
	for st, ids := range byStatus {
		c.emitReceipt(chat, ids, st)
	}
	if participants == 0 {
		c.fetchGroupAsync(chat)
	}
	_ = changed
}

func (c *Client) emitReceipt(chat string, ids []string, status int) {
	if len(ids) == 0 {
		return
	}
	c.emit("receipt", map[string]any{"chat": chat, "ids": ids, "status": status})
	c.emit("chats_changed", nil)
}

// ---------------------------------------------------------------------------
// History sync: chats and recent messages sent by the phone after linking.

func (c *Client) onHistory(evt *events.HistorySync) {
	data := evt.Data
	if data == nil {
		return
	}
	ctx := context.Background()
	for _, pn := range data.GetPushnames() {
		if c.cli.Store.Contacts == nil {
			break
		}
		if j, err := types.ParseJID(pn.GetID()); err == nil && pn.GetPushname() != "" {
			_, _, _ = c.cli.Store.Contacts.PutPushName(ctx, j, pn.GetPushname())
		}
	}
	c.invalidateNames()
	if len(data.GetStatusV3Messages()) > 0 {
		trailf("phone sent %d statuses (%s)", len(data.GetStatusV3Messages()), data.GetSyncType())
		c.onHistoryStatuses(data.GetStatusV3Messages())
		c.emit("status", nil)
	}
	for i, list := range [][]*waHistorySync.StickerMetadata{data.GetRecentStickers(), data.GetFavoriteStickers()} {
		fav := i == 1
		for _, st := range list {
			ts := st.GetLastStickerSentTS()
			if ts > 0 && ts < 1e12 {
				ts *= 1000
			}
			rememberSticker(c.db, &waE2E.StickerMessage{
				URL: proto.String(st.GetURL()), DirectPath: proto.String(st.GetDirectPath()),
				FileSHA256: st.GetFileSHA256(), FileEncSHA256: st.GetFileEncSHA256(), MediaKey: st.GetMediaKey(),
				Mimetype: proto.String(firstNonEmpty(st.GetMimetype(), "image/webp")), FileLength: proto.Uint64(st.GetFileLength()),
				Height: proto.Uint32(st.GetHeight()), Width: proto.Uint32(st.GetWidth()), IsLottie: proto.Bool(st.GetIsLottie()),
			}, ts, fav)
		}
	}
	total := 0
	for _, conv := range data.GetConversations() {
		raw, err := types.ParseJID(conv.GetID())
		if err == nil && raw == types.StatusBroadcastJID {
			var list []*waWeb.WebMessageInfo
			for _, m := range conv.GetMessages() {
				if w := m.GetMessage(); w != nil {
					list = append(list, w)
				}
			}
			trailf("phone sent %d statuses (%s)", len(list), data.GetSyncType())
			if len(list) > 0 {
				c.onHistoryStatuses(list)
				c.emit("status", nil)
			}
			continue
		}
		if err != nil || skipChat(raw) {
			continue
		}
		chatJID := c.canonical(raw)
		chat := chatJID.String()
		isGroup := chatJID.Server == types.GroupServer
		ensureChat(c.db, chat, isGroup, raw.ToNonAD().String())
		name := firstNonEmpty(conv.GetName(), conv.GetDisplayName())
		ts := int64(conv.GetConversationTimestamp()) * 1000
		if ts == 0 {
			ts = int64(conv.GetLastMsgTimestamp()) * 1000
		}
		if exp := conv.GetEphemeralExpiration(); exp > 0 {
			_, _ = c.db.Exec(`UPDATE chats SET ephemeral=? WHERE jid=?`, exp, chat)
		}
		mute := int64(conv.GetMuteEndTime())
		if mute > 0 && mute < 1e12 {
			mute *= 1000
		}
		_, _ = c.db.Exec(`UPDATE chats SET name=CASE WHEN ?!='' THEN ? ELSE name END, last_ts=MAX(last_ts,?),
			unread=?, marked_unread=?, pinned=?, archived=?, muted_until=CASE WHEN ?!=0 THEN ? ELSE muted_until END WHERE jid=?`,
			name, name, ts, conv.GetUnreadCount(), b2i(conv.GetMarkedAsUnread()), b2i(conv.GetPinned() > 0), b2i(conv.GetArchived()),
			mute, mute, chat)
		if isGroup && len(conv.GetParticipant()) > 0 {
			_, _ = c.db.Exec(`UPDATE chats SET participants=? WHERE jid=?`, len(conv.GetParticipant()), chat)
		}
		tx, err := c.db.Begin()
		if err != nil {
			continue
		}
		hc := &histCtx{c: c, tx: tx}
		var later []*events.Message
		for _, hm := range conv.GetMessages() {
			web := hm.GetMessage()
			if web == nil {
				continue
			}
			evtMsg, err := c.cli.ParseWebMessage(raw, web)
			if err != nil || evtMsg.Message == nil {
				continue
			}
			if evtMsg.Message.GetProtocolMessage() != nil || evtMsg.Message.GetReactionMessage() != nil {
				later = append(later, evtMsg)
				continue
			}
			hc.store(evtMsg, webStatus(web))
			total++
			if ups := web.GetPollUpdates(); len(ups) > 0 {
				_, _, ex := describe(evtMsg.Message)
				opts, _ := pollOptionsOf(ex)
				for _, pu := range ups {
					key := pu.GetPollUpdateMessageKey()
					voter := c.ownJID()
					if !key.GetFromMe() {
						vj, err := types.ParseJID(firstNonEmpty(key.GetParticipant(), key.GetRemoteJID()))
						if err != nil {
							continue
						}
						voter = c.resolveLID(vj)
					}
					recordPollVote(tx, chat, evtMsg.Info.ID, voter.String(), optionNames(pu.GetVote().GetSelectedOptions(), opts), pu.GetSenderTimestampMS())
				}
			}
		}
		_ = tx.Commit()
		// Edits, deletions and reactions apply on top of stored messages.
		for i := len(later) - 1; i >= 0; i-- {
			c.onMessage(later[i], true, -1)
		}
		// Mark the newest N incoming messages as unread to match the phone.
		if n := conv.GetUnreadCount(); n > 0 {
			_, _ = c.db.Exec(`UPDATE messages SET read_sent=0 WHERE chat=? AND rowid IN
				(SELECT rowid FROM messages WHERE chat=? AND from_me=0 ORDER BY ts DESC LIMIT ?)`, chat, chat, n)
		}
	}
	c.log.Infof("history sync %s: %d conversations, %d messages, progress %d%%",
		data.GetSyncType().String(), len(data.GetConversations()), total, data.GetProgress())
	c.emit("history", map[string]any{"progress": data.GetProgress(), "type": data.GetSyncType().String(), "messages": total})
	c.emit("chats_changed", nil)
}

// histCtx stores history messages inside one transaction for speed.
type histCtx struct {
	c  *Client
	tx *sql.Tx
}

func (h *histCtx) store(evt *events.Message, status int) {
	// Same as the live path, but inside the transaction and without
	// anything that would need a second database connection.
	c := h.c
	info := evt.Info
	msg := evt.Message
	typ, text, extra := describe(msg)
	extra = withAlbum(msg, extra)
	if typ == "" {
		return
	}
	chatJID := c.resolveLID(info.Chat)
	senderJID := c.resolveLID(info.Sender)
	if info.IsFromMe {
		senderJID = c.ownJID()
	}
	row := &msgRow{
		Chat: chatJID.String(), ID: info.ID, Sender: senderJID.String(),
		RawChat: info.Chat.ToNonAD().String(), RawSender: info.Sender.ToNonAD().String(),
		FromMe: info.IsFromMe, Ts: info.Timestamp.UnixMilli(), Type: typ, Text: text, Extra: extra, Edited: evt.IsEdit,
	}
	if row.FromMe {
		row.Status = status
	}
	if ci := contextInfo(msg); ci != nil {
		if ci.GetStanzaID() != "" && ci.GetQuotedMessage() != nil {
			row.QuotedID = ci.GetStanzaID()
			if p := ci.GetParticipant(); p != "" {
				if pj, err := types.ParseJID(p); err == nil {
					row.QuotedSender = c.resolveLID(pj).String()
				}
			}
			row.QuotedType, row.QuotedText, _ = describe(ci.GetQuotedMessage())
		}
		row.Text, row.Mentions = c.applyMentions(row.Text, ci.GetMentionedJID())
	}
	row.Raw, _ = proto.Marshal(msg)
	row.ReadSent = true
	if sm := msg.GetStickerMessage(); sm != nil {
		rememberSticker(h.tx, sm, row.Ts, false)
	}
	if err := upsertMessage(h.tx, row); err != nil {
		c.log.Errorf("history store: %v", err)
	}
}

func webStatus(w *waWeb.WebMessageInfo) int {
	switch w.GetStatus() {
	case waWeb.WebMessageInfo_ERROR:
		return StatusFailed
	case waWeb.WebMessageInfo_PENDING:
		return StatusPending
	case waWeb.WebMessageInfo_SERVER_ACK:
		return StatusSent
	case waWeb.WebMessageInfo_DELIVERY_ACK:
		return StatusDelivered
	case waWeb.WebMessageInfo_READ:
		return StatusRead
	case waWeb.WebMessageInfo_PLAYED:
		return StatusPlayed
	}
	return StatusSent
}

// ---------------------------------------------------------------------------
// Group names

func (c *Client) ensureGroupName(chat string) {
	var name string
	var fetched int
	if c.db.QueryRow(`SELECT name, group_fetched FROM chats WHERE jid=?`, chat).Scan(&name, &fetched) == nil && (name != "" && fetched == 1) {
		return
	}
	c.fetchGroupAsync(chat)
}

func (c *Client) fetchGroupAsync(chat string) {
	c.groupMu.Lock()
	if c.groupBusy[chat] {
		c.groupMu.Unlock()
		return
	}
	c.groupBusy[chat] = true
	c.groupMu.Unlock()
	go func() {
		defer func() {
			c.groupMu.Lock()
			delete(c.groupBusy, chat)
			c.groupMu.Unlock()
		}()
		if !c.cli.IsConnected() {
			return
		}
		jid, err := types.ParseJID(chat)
		if err != nil {
			return
		}
		ctx, cancel := context.WithTimeout(c.ctx, 20*time.Second)
		defer cancel()
		info, err := c.cli.GetGroupInfo(ctx, jid)
		if err != nil {
			c.log.Warnf("group info %s: %v", chat, err)
			_, _ = c.db.Exec(`UPDATE chats SET group_fetched=1 WHERE jid=?`, chat)
			return
		}
		_, _ = c.db.Exec(`UPDATE chats SET name=?, participants=?, group_fetched=1 WHERE jid=?`, info.Name, len(info.Participants), chat)
		c.emit("chats_changed", nil)
	}()
}

func isViewOnce(evt *events.Message, m *waE2E.Message) bool {
	if evt.IsViewOnce || evt.IsViewOnceV2 || evt.IsViewOnceV2Extension {
		return true
	}
	return m.GetImageMessage().GetViewOnce() || m.GetVideoMessage().GetViewOnce() || m.GetAudioMessage().GetViewOnce()
}

// viewOnceRow marks a view-once message. When the media keys came along it
// stays a normal photo/video (flagged view once); otherwise it becomes a
// "viewonce" notice telling you to open it on your phone.
func viewOnceRow(m *waE2E.Message, typ, text, extra string) (string, string, string) {
	ex := map[string]any{}
	if extra != "" {
		_ = json.Unmarshal([]byte(extra), &ex)
	}
	ex["viewOnce"] = true
	kind := "photo"
	hasMedia := false
	switch {
	case m.GetImageMessage() != nil:
		hasMedia = len(m.GetImageMessage().GetMediaKey()) > 0 && m.GetImageMessage().GetDirectPath() != ""
	case m.GetVideoMessage() != nil:
		kind = "video"
		hasMedia = len(m.GetVideoMessage().GetMediaKey()) > 0 && m.GetVideoMessage().GetDirectPath() != ""
	case m.GetAudioMessage() != nil:
		kind = "voice message"
		hasMedia = len(m.GetAudioMessage().GetMediaKey()) > 0 && m.GetAudioMessage().GetDirectPath() != ""
	}
	if !hasMedia || typ == "" {
		typ = "viewonce"
		ex["kind"] = kind
	}
	b, _ := json.Marshal(ex)
	return typ, text, string(b)
}

// noteReaction remembers the latest reaction someone made to one of your
// messages (or your status), for the chat list preview.
func (c *Client) noteReaction(chat string, r *waE2E.ReactionMessage, info types.MessageInfo, historical bool) {
	key := r.GetKey()
	if r.GetText() == "" {
		// Reaction removed.
		_, _ = c.db.Exec(`UPDATE chats SET react_text='' WHERE jid=?`, chat)
		if !historical {
			c.emit("chats_changed", nil)
		}
		return
	}
	where := chat
	if key.GetRemoteJID() == types.StatusBroadcastJID.String() {
		where = statusChat
	}
	m := c.loadMessage(where, key.GetID())
	if m == nil || !m.FromMe {
		return
	}
	what := reactionTarget(m)
	if where == statusChat {
		what = "your status"
	} else {
		what = "\u201c" + what + "\u201d"
	}
	name := strings.TrimPrefix(c.resolveName(c.canonical(info.Sender)).Sender, "~ ")
	if f := strings.Fields(name); len(f) > 0 && !info.IsGroup {
		name = f[0]
	}
	text := fmt.Sprintf("%s reacted %s to %s", name, r.GetText(), what)
	ts := info.Timestamp.UnixMilli()
	_, _ = c.db.Exec(`UPDATE chats SET react_ts=?, react_text=? WHERE jid=? AND react_ts<=?`, ts, text, chat, ts)
	if !historical {
		c.emit("chats_changed", nil)
	}
}

func reactionTarget(m *msgRow) string {
	short := func(s string) string {
		s = strings.Join(strings.Fields(s), " ")
		if r := []rune(s); len(r) > 40 {
			return string(r[:40]) + "\u2026"
		}
		return s
	}
	switch m.Type {
	case "text":
		return short(m.Text)
	case "image":
		return "\U0001F4F7 Photo"
	case "video":
		return "\U0001F3A5 Video"
	case "gif":
		return "GIF"
	case "voice":
		return "\U0001F3A4 Voice message"
	case "audio":
		return "\U0001F3B5 Audio"
	case "sticker":
		return "Sticker"
	case "document":
		var ex struct {
			FileName string `json:"fileName"`
		}
		_ = json.Unmarshal([]byte(m.Extra), &ex)
		if ex.FileName != "" {
			return "\U0001F4C4 " + short(ex.FileName)
		}
		return "\U0001F4C4 Document"
	}
	if m.Text != "" {
		return short(m.Text)
	}
	return "Message"
}

// withAlbum notes in extra which album a photo/video belongs to (WhatsApp
// sends several picked together as one album), so the app can show them
// as a grid.
func withAlbum(msg *waE2E.Message, extra string) string {
	a := msg.GetMessageContextInfo().GetMessageAssociation()
	if a == nil || a.GetAssociationType() != waE2E.MessageAssociation_MEDIA_ALBUM || a.GetParentMessageKey().GetID() == "" {
		return extra
	}
	ex := map[string]any{}
	if extra != "" {
		if json.Unmarshal([]byte(extra), &ex) != nil {
			return extra
		}
	}
	ex["album"] = a.GetParentMessageKey().GetID()
	b, err := json.Marshal(ex)
	if err != nil {
		return extra
	}
	return string(b)
}
