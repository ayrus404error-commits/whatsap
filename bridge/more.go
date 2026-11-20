package wa

import (
	"context"
	"encoding/json"
	"errors"
	"os"
	"strings"
	"time"

	"go.mau.fi/whatsmeow"
	"go.mau.fi/whatsmeow/proto/waE2E"
	"go.mau.fi/whatsmeow/types"
	"google.golang.org/protobuf/proto"
)

// ReplyToStatus answers someone's status update in your chat with them,
// quoting the update (like replying from the status viewer in WhatsApp).
func (c *Client) ReplyToStatus(statusID, text string) (string, error) {
	text = strings.TrimSpace(text)
	if text == "" {
		return "", errors.New("empty message")
	}
	var sender string
	if err := c.db.QueryRow(`SELECT sender FROM messages WHERE chat=? AND id=?`, statusChat, statusID).Scan(&sender); err != nil {
		return "", errors.New("status update not found")
	}
	jid, err := parseChat(sender)
	if err != nil {
		return "", err
	}
	row, _ := c.newOutgoing(sender, jid, "text", text, nil, "")
	var ci *waE2E.ContextInfo
	if q := c.loadQuoteSource(statusChat, statusID); q != nil {
		ci = q.ctx
		ci.RemoteJID = proto.String(statusChat)
		row.QuotedID, row.QuotedSender, row.QuotedType = statusID, q.sender, q.typ
		row.QuotedText = q.text
		if row.QuotedText == "" {
			row.QuotedText = "Status"
		}
	}
	ci = c.withExpiration(sender, ci)
	msg := &waE2E.Message{}
	if ci != nil {
		msg.ExtendedTextMessage = &waE2E.ExtendedTextMessage{Text: proto.String(text), ContextInfo: ci}
	} else {
		msg.Conversation = proto.String(text)
	}
	return c.queueOutgoing(sender, jid, msg, row)
}

// SendStickerFile sends your own sticker: a 512×512 WebP made on the phone
// (from a photo or an emoji). It is also kept in the sticker panel.
func (c *Client) SendStickerFile(chat, path, quotedID string) (string, error) {
	jid, err := parseChat(chat)
	if err != nil {
		return "", err
	}
	data, err := os.ReadFile(path)
	if err != nil || len(data) == 0 {
		return "", errors.New("the sticker couldn't be read")
	}
	if len(data) > 1<<20 {
		return "", errors.New("the sticker is too big")
	}
	ctx, cancel := context.WithTimeout(c.ctx, 2*time.Minute)
	defer cancel()
	up, err := c.cli.Upload(ctx, data, whatsmeow.MediaImage)
	if err != nil {
		return "", err
	}
	sm := &waE2E.StickerMessage{
		URL:           proto.String(up.URL),
		DirectPath:    proto.String(up.DirectPath),
		MediaKey:      up.MediaKey,
		FileEncSHA256: up.FileEncSHA256,
		FileSHA256:    up.FileSHA256,
		FileLength:    proto.Uint64(up.FileLength),
		Mimetype:      proto.String("image/webp"),
		Width:         proto.Uint32(512),
		Height:        proto.Uint32(512),
	}
	now := time.Now().UnixMilli()
	rememberSticker(c.db, sm, now, true)
	hash := stickerKey(sm)
	local := c.stickerFile(hash)
	_ = os.WriteFile(local, data, 0o600)

	row, ci := c.newOutgoing(chat, jid, "sticker", "", nil, quotedID)
	row.Local = local
	if ci != nil {
		sm = proto.Clone(sm).(*waE2E.StickerMessage)
		sm.ContextInfo = ci
	}
	return c.queueOutgoing(chat, jid, &waE2E.Message{StickerMessage: sm}, row)
}

// PrivacySettings returns who can see your personal info, as WhatsApp has it:
// {"last","online","profile","status","groupadd","readreceipts"}.
func (c *Client) PrivacySettings() (string, error) {
	if !c.cli.IsConnected() {
		return "", errors.New("not connected")
	}
	ctx, cancel := context.WithTimeout(c.ctx, 20*time.Second)
	defer cancel()
	// Ask the server fresh, so changes made on the phone show up here.
	if _, err := c.cli.TryFetchPrivacySettings(ctx, true); err != nil {
		return "", err
	}
	s := c.cli.GetPrivacySettings(ctx)
	out := map[string]string{
		"last":         string(s.LastSeen),
		"online":       string(s.Online),
		"profile":      string(s.Profile),
		"status":       string(s.Status),
		"groupadd":     string(s.GroupAdd),
		"readreceipts": string(s.ReadReceipts),
	}
	b, _ := json.Marshal(out)
	return string(b), nil
}

// SetPrivacy changes one privacy setting (name: last, online, profile,
// status, groupadd, readreceipts; value: all, contacts, contact_blacklist,
// none, match_last_seen). The phone shows the same setting.
func (c *Client) SetPrivacy(name, value string) error {
	ctx, cancel := context.WithTimeout(c.ctx, 20*time.Second)
	defer cancel()
	_, err := c.cli.SetPrivacySetting(ctx, types.PrivacySettingType(name), types.PrivacySetting(value))
	return err
}

// SetKeepDeleted: when on, messages others delete for everyone stay
// readable on this device (marked as deleted).
func (c *Client) SetKeepDeleted(on bool) {
	c.keepDeleted.Store(on)
}

// SetDisappearing turns disappearing messages on or off for a chat
// (seconds: 0 = off, 86400 = 24 hours, 604800 = 7 days, 7776000 = 90 days).
func (c *Client) SetDisappearing(chat string, seconds int) error {
	jid, err := parseChat(chat)
	if err != nil {
		return err
	}
	ctx, cancel := context.WithTimeout(c.ctx, 20*time.Second)
	defer cancel()
	if err := c.cli.SetDisappearingTimer(ctx, jid, time.Duration(seconds)*time.Second, time.Now()); err != nil {
		return err
	}
	c.setEphemeral(chat, uint32(seconds))
	return nil
}

// CreateGroup makes a new group with you and the given people (a JSON array
// of chat JIDs) and returns the new group's JID. Names are cut to 25 letters.
func (c *Client) CreateGroup(name, membersJSON string) (string, error) {
	name = strings.TrimSpace(name)
	if name == "" {
		return "", errors.New("give the group a name")
	}
	if r := []rune(name); len(r) > 25 {
		name = string(r[:25])
	}
	var members []string
	if err := json.Unmarshal([]byte(membersJSON), &members); err != nil || len(members) == 0 {
		return "", errors.New("pick at least one person")
	}
	var jids []types.JID
	for _, m := range members {
		j, err := parseChat(m)
		if err != nil || j.Server == types.GroupServer {
			continue
		}
		jids = append(jids, j.ToNonAD())
	}
	if len(jids) == 0 {
		return "", errors.New("pick at least one person")
	}
	ctx, cancel := context.WithTimeout(c.ctx, 30*time.Second)
	defer cancel()
	info, err := c.cli.CreateGroup(ctx, whatsmeow.ReqCreateGroup{Name: name, Participants: jids})
	if err != nil {
		return "", err
	}
	chat := info.JID.String()
	ensureChat(c.db, chat, true, chat)
	_, _ = c.db.Exec(`UPDATE chats SET name=?, participants=?, group_fetched=1, last_ts=? WHERE jid=?`,
		info.Name, len(info.Participants), time.Now().UnixMilli(), chat)
	c.emit("chats_changed", nil)
	return chat, nil
}
