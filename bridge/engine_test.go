package wa

import (
	"crypto/sha256"
	"encoding/json"
	"os"
	"path/filepath"
	"strings"
	"testing"
	"time"

	"go.mau.fi/whatsmeow/proto/waCommon"
	"go.mau.fi/whatsmeow/proto/waE2E"
	"go.mau.fi/whatsmeow/proto/waHistorySync"
	"go.mau.fi/whatsmeow/proto/waWeb"
	"go.mau.fi/whatsmeow/types"
	"go.mau.fi/whatsmeow/types/events"
	"google.golang.org/protobuf/proto"
)

type recListener struct{ events []string }

func (r *recListener) OnEvent(kind, payload string) { r.events = append(r.events, kind+" "+payload) }

func mk(t *testing.T) (*Client, *recListener) {
	l := &recListener{}
	c, err := NewClient(t.TempDir(), l)
	if err != nil {
		t.Fatal(err)
	}
	me := types.NewJID("919800000001", types.DefaultUserServer)
	me.Device = 5
	c.cli.Store.ID = &me
	return c, l
}

func TestFlow(t *testing.T) {
	c, l := mk(t)
	defer c.Close()
	alice := types.NewJID("919812345678", types.DefaultUserServer)
	group := types.NewJID("120363000000000001", types.GroupServer)

	// Incoming DM
	c.handleEvent(&events.Message{
		Info:    types.MessageInfo{MessageSource: types.MessageSource{Chat: alice, Sender: alice}, ID: "A1", Timestamp: time.Now().Add(-time.Minute), PushName: "Alice"},
		Message: &waE2E.Message{Conversation: proto.String("hello there")},
	})
	// Incoming group image with caption + reply
	c.handleEvent(&events.Message{
		Info: types.MessageInfo{MessageSource: types.MessageSource{Chat: group, Sender: alice, IsGroup: true}, ID: "G1", Timestamp: time.Now()},
		Message: &waE2E.Message{ImageMessage: &waE2E.ImageMessage{Caption: proto.String("look"), JPEGThumbnail: []byte{1, 2, 3},
			ContextInfo: &waE2E.ContextInfo{StanzaID: proto.String("X"), Participant: proto.String(alice.String()), QuotedMessage: &waE2E.Message{Conversation: proto.String("orig")}}}},
	})
	// Reaction to A1 from me (another device)
	c.handleEvent(&events.Message{
		Info:    types.MessageInfo{MessageSource: types.MessageSource{Chat: alice, Sender: *c.cli.Store.ID, IsFromMe: true}, ID: "R1", Timestamp: time.Now()},
		Message: &waE2E.Message{ReactionMessage: &waE2E.ReactionMessage{Key: &waCommon.MessageKey{ID: proto.String("A1")}, Text: proto.String("👍")}},
	})
	// Edit of A1
	c.handleEvent(&events.Message{
		Info: types.MessageInfo{MessageSource: types.MessageSource{Chat: alice, Sender: alice}, ID: "E1", Timestamp: time.Now()},
		Message: &waE2E.Message{ProtocolMessage: &waE2E.ProtocolMessage{Type: waE2E.ProtocolMessage_MESSAGE_EDIT.Enum(),
			Key: &waCommon.MessageKey{ID: proto.String("A1")}, EditedMessage: &waE2E.Message{Conversation: proto.String("hello (edited)")}}},
	})

	var chats []chatOut
	if err := json.Unmarshal([]byte(c.GetChats()), &chats); err != nil {
		t.Fatal(err)
	}
	if len(chats) != 2 {
		t.Fatalf("want 2 chats, got %d: %s", len(chats), c.GetChats())
	}
	t.Logf("chats: %s", c.GetChats())
	msgs := c.GetMessages(alice.String(), 0, 50)
	t.Logf("alice msgs: %s", msgs)
	if !strings.Contains(msgs, "hello (edited)") || !strings.Contains(msgs, `"emoji":"👍"`) || !strings.Contains(msgs, `"edited":true`) {
		t.Fatal("edit/reaction missing")
	}
	gm := c.GetMessages(group.String(), 0, 50)
	if !strings.Contains(gm, `"quotedText":"orig"`) || !strings.Contains(gm, `thumb`) {
		t.Fatalf("group msg wrong: %s", gm)
	}

	// Outgoing message bookkeeping (can't actually send offline)
	row := &msgRow{Chat: alice.String(), ID: "OUT1", Sender: c.ownJID().String(), FromMe: true, Ts: time.Now().UnixMilli(), Type: "text", Text: "hi", Status: StatusSent, RawChat: alice.String(), RawSender: c.ownJID().String()}
	if err := upsertMessage(c.db, row); err != nil {
		t.Fatal(err)
	}
	c.handleEvent(&events.Receipt{MessageSource: types.MessageSource{Chat: alice, Sender: alice}, MessageIDs: []string{"OUT1"}, Type: types.ReceiptTypeDelivered})
	c.handleEvent(&events.Receipt{MessageSource: types.MessageSource{Chat: alice, Sender: alice}, MessageIDs: []string{"OUT1"}, Type: types.ReceiptTypeRead})
	c.handleEvent(&events.Receipt{MessageSource: types.MessageSource{Chat: alice, Sender: alice}, MessageIDs: []string{"OUT1"}, Type: types.ReceiptTypeDelivered})
	if m := c.loadMessage(alice.String(), "OUT1"); m == nil || m.Status != StatusRead {
		t.Fatalf("receipt status wrong: %+v", m)
	}
	// Revoke
	c.handleEvent(&events.Message{
		Info:    types.MessageInfo{MessageSource: types.MessageSource{Chat: alice, Sender: alice}, ID: "D1", Timestamp: time.Now()},
		Message: &waE2E.Message{ProtocolMessage: &waE2E.ProtocolMessage{Type: waE2E.ProtocolMessage_REVOKE.Enum(), Key: &waCommon.MessageKey{ID: proto.String("A1")}}},
	})
	if m := c.loadMessage(alice.String(), "A1"); m == nil || !m.Revoked {
		t.Fatal("revoke failed")
	}

	// Unread bookkeeping: 1 DM unread from earlier
	for _, ch := range chats {
		t.Logf("%s unread=%d last=%q", ch.Name, ch.Unread, ch.LastText)
	}

	// History sync with a conversation, an edit and a reaction
	bob := types.NewJID("919811111111", types.DefaultUserServer)
	web := func(id, text string, fromMe bool, ts int64) *waHistorySync.HistorySyncMsg {
		return &waHistorySync.HistorySyncMsg{Message: &waWeb.WebMessageInfo{
			Key:              &waCommon.MessageKey{RemoteJID: proto.String(bob.String()), FromMe: proto.Bool(fromMe), ID: proto.String(id)},
			MessageTimestamp: proto.Uint64(uint64(ts)),
			Message:          &waE2E.Message{Conversation: proto.String(text)},
			Status:           waWeb.WebMessageInfo_READ.Enum(),
		}}
	}
	now := time.Now().Unix()
	hist := &waHistorySync.HistorySync{
		SyncType: waHistorySync.HistorySync_INITIAL_BOOTSTRAP.Enum(),
		Conversations: []*waHistorySync.Conversation{{
			ID: proto.String(bob.String()), Name: proto.String("Bob"), UnreadCount: proto.Uint32(1),
			ConversationTimestamp: proto.Uint64(uint64(now)),
			Messages:              []*waHistorySync.HistorySyncMsg{web("H2", "second", false, now), web("H1", "first", true, now-60)},
		}},
		Pushnames: []*waHistorySync.Pushname{{ID: proto.String(bob.String()), Pushname: proto.String("Bobby")}},
	}
	done := make(chan struct{})
	go func() { c.handleEvent(&events.HistorySync{Data: hist}); close(done) }()
	select {
	case <-done:
	case <-time.After(10 * time.Second):
		t.Fatal("history sync deadlocked")
	}
	hm := c.GetMessages(bob.String(), 0, 50)
	t.Logf("bob: %s", hm)
	if !strings.Contains(hm, "first") {
		t.Log(c.LogTail())
	}
	if !strings.Contains(hm, "first") || !strings.Contains(hm, `"status":3`) || !strings.Contains(hm, `"fromMe":true`) {
		t.Fatal("history not stored")
	}
	t.Logf("chats after history: %s", c.GetChats())

	c.MarkChatRead(bob.String(), false)
	var unread int
	_ = c.db.QueryRow(`SELECT unread FROM chats WHERE jid=?`, bob.String()).Scan(&unread)
	if unread != 0 {
		t.Fatal("mark read failed")
	}
	if len(l.events) == 0 {
		t.Fatal("no events emitted")
	}
	t.Logf("%d events, e.g. %s", len(l.events), l.events[0])
}

func TestStatusesStickersMedia(t *testing.T) {
	c, l := mk(t)
	defer c.Close()
	alice := types.NewJID("919812345678", types.DefaultUserServer)
	bob := types.NewJID("919811111111", types.DefaultUserServer)
	status := types.StatusBroadcastJID
	me := *c.cli.Store.ID

	stat := func(id string, from types.JID, fromMe bool, ago time.Duration, m *waE2E.Message) {
		c.handleEvent(&events.Message{
			Info:    types.MessageInfo{MessageSource: types.MessageSource{Chat: status, Sender: from, IsFromMe: fromMe, IsGroup: true}, ID: id, Timestamp: time.Now().Add(-ago)},
			Message: m,
		})
	}
	stat("S1", alice, false, time.Hour, &waE2E.Message{ImageMessage: &waE2E.ImageMessage{Caption: proto.String("beach"), JPEGThumbnail: []byte{1}}})
	stat("S2", alice, false, 30*time.Minute, &waE2E.Message{ExtendedTextMessage: &waE2E.ExtendedTextMessage{
		Text: proto.String("hello status"), BackgroundArgb: proto.Uint32(0xFF7ACBA5), Font: waE2E.ExtendedTextMessage_FontType(2).Enum()}})
	stat("S3", bob, false, 25*time.Hour, &waE2E.Message{Conversation: proto.String("too old")})
	stat("S4", me, true, 10*time.Minute, &waE2E.Message{Conversation: proto.String("mine")})
	stat("S5", bob, false, 5*time.Minute, &waE2E.Message{Conversation: proto.String("bob new")})

	// Statuses never become chats.
	if strings.Contains(c.GetChats(), "status") {
		t.Fatalf("status leaked into chats: %s", c.GetChats())
	}
	var groups []statusGroupOut
	if err := json.Unmarshal([]byte(c.GetStatuses()), &groups); err != nil {
		t.Fatal(err)
	}
	t.Logf("statuses: %s", c.GetStatuses())
	if len(groups) != 3 || !groups[0].Mine || groups[0].Name != "My status" {
		t.Fatalf("want mine first + 2 others, got %+v", groups)
	}
	var al *statusGroupOut
	for i := range groups {
		if groups[i].Sender == alice.String() {
			al = &groups[i]
		}
	}
	if al == nil || len(al.Items) != 2 || al.Unviewed != 2 || !strings.Contains(al.Items[1].Extra, `"bg":4286237605`) {
		t.Fatalf("alice group wrong: %+v", al)
	}
	if strings.Contains(c.GetStatuses(), "too old") {
		t.Fatal("expired status listed")
	}
	// Bob (newer) sorts before Alice among unviewed.
	if groups[1].Sender != bob.String() {
		t.Fatalf("order wrong: %s then %s", groups[1].Sender, groups[2].Sender)
	}
	c.MarkStatusViewed("S1", false)
	c.MarkStatusViewed("S2", false)
	_ = json.Unmarshal([]byte(c.GetStatuses()), &groups)
	if groups[len(groups)-1].Sender != alice.String() || groups[len(groups)-1].Unviewed != 0 {
		t.Fatalf("viewed group should sort last: %s", c.GetStatuses())
	}
	// Someone views my status.
	c.handleEvent(&events.Receipt{MessageSource: types.MessageSource{Chat: status, Sender: alice}, MessageIDs: []string{"S4"}, Type: types.ReceiptTypeRead})
	_ = json.Unmarshal([]byte(c.GetStatuses()), &groups)
	if groups[0].Items[0].Views != 1 {
		t.Fatalf("views not counted: %+v", groups[0])
	}
	// Revoked status disappears.
	stat("R9", alice, false, 0, &waE2E.Message{ProtocolMessage: &waE2E.ProtocolMessage{Type: waE2E.ProtocolMessage_REVOKE.Enum(), Key: &waCommon.MessageKey{ID: proto.String("S2")}}})
	if strings.Contains(c.GetStatuses(), "hello status") {
		t.Fatal("revoked status still listed")
	}

	// Stickers seen in chats and from history sync.
	c.handleEvent(&events.Message{
		Info: types.MessageInfo{MessageSource: types.MessageSource{Chat: alice, Sender: alice}, ID: "ST1", Timestamp: time.Now()},
		Message: &waE2E.Message{StickerMessage: &waE2E.StickerMessage{FileSHA256: []byte{9, 9, 9}, MediaKey: []byte{1}, Mimetype: proto.String("image/webp"),
			IsAnimated: proto.Bool(true), ContextInfo: &waE2E.ContextInfo{StanzaID: proto.String("x")}}},
	})
	c.handleEvent(&events.HistorySync{Data: &waHistorySync.HistorySync{
		SyncType:         waHistorySync.HistorySync_RECENT.Enum(),
		RecentStickers:   []*waHistorySync.StickerMetadata{{FileSHA256: []byte{7, 7}, MediaKey: []byte{2}, LastStickerSentTS: proto.Int64(time.Now().Unix())}},
		FavoriteStickers: []*waHistorySync.StickerMetadata{{FileSHA256: []byte{8, 8}, MediaKey: []byte{3}}},
		StatusV3Messages: []*waWeb.WebMessageInfo{{
			Key:              &waCommon.MessageKey{RemoteJID: proto.String(status.String()), FromMe: proto.Bool(false), ID: proto.String("HS1"), Participant: proto.String(bob.String())},
			Participant:      proto.String(bob.String()),
			MessageTimestamp: proto.Uint64(uint64(time.Now().Add(-time.Hour).Unix())),
			Message:          &waE2E.Message{Conversation: proto.String("history status")},
			Status:           waWeb.WebMessageInfo_READ.Enum(),
		}},
	}})
	var stickers []struct {
		ID       string `json:"id"`
		Animated bool   `json:"animated"`
		Fav      bool   `json:"fav"`
	}
	_ = json.Unmarshal([]byte(c.GetStickers()), &stickers)
	t.Logf("stickers: %s", c.GetStickers())
	if len(stickers) != 3 || !stickers[0].Fav {
		t.Fatalf("stickers wrong: %s", c.GetStickers())
	}
	if !strings.Contains(c.GetStatuses(), "history status") {
		t.Fatalf("history status missing: %s", c.GetStatuses())
	}
	if !strings.Contains(c.GetMessages(alice.String(), 0, 10), `"type":"sticker"`) {
		t.Fatal("sticker message not stored")
	}

	// Sending media: the row is stored straight away with the local file.
	src := t.TempDir() + "/pick.jpg"
	_ = os.WriteFile(src, []byte("fake jpeg bytes"), 0o600)
	spec, _ := json.Marshal(mediaSpec{Kind: "image", Path: src, Mime: "image/jpeg", Caption: "pic", Width: 800, Height: 600})
	out, err := c.SendMedia(alice.String(), string(spec))
	if err != nil {
		t.Fatal(err)
	}
	var sent msgRow
	_ = json.Unmarshal([]byte(out), &sent)
	if sent.Type != "image" || sent.Text != "pic" || !fileOK(sent.Local) || fileOK(src) {
		t.Fatalf("send media bookkeeping wrong: %s", out)
	}
	if p, err := c.DownloadMedia(alice.String(), sent.ID); err != nil || p != sent.Local {
		t.Fatalf("own media should resolve locally: %q %v", p, err)
	}
	if !strings.Contains(c.GetMessages(alice.String(), 0, 10), `"local":"`+sent.Local+`"`) {
		t.Fatal("local path missing from message list")
	}
	if _, err := c.SendMedia(alice.String(), `{"kind":"image","path":"/nope"}`); err == nil {
		t.Fatal("missing file should fail")
	}
	if extFor("audio/ogg; codecs=opus", "", "voice") != ".ogg" || extFor("", "Report.PDF", "document") != ".pdf" {
		t.Fatal("extFor wrong")
	}
	if part, _, name := mediaPart(&waE2E.Message{DocumentWithCaptionMessage: &waE2E.FutureProofMessage{Message: &waE2E.Message{
		DocumentMessage: &waE2E.DocumentMessage{FileName: proto.String("a.pdf")}}}}); part == nil || name != "a.pdf" {
		t.Fatal("document-with-caption not unwrapped")
	}
	// Incoming call event.
	c.handleEvent(&events.CallOfferNotice{BasicCallMeta: types.BasicCallMeta{From: alice, CallCreator: alice, CallID: "C1"}, Media: "video"})
	found := false
	for _, e := range l.events {
		if strings.HasPrefix(e, "call ") && strings.Contains(e, `"video":true`) {
			found = true
		}
	}
	if !found {
		t.Fatal("call event missing")
	}
}

func TestGalleryPollsContacts(t *testing.T) {
	c, _ := mk(t)
	defer c.Close()
	bob := types.NewJID("919811111111", types.DefaultUserServer)
	group := types.NewJID("120363000000000002", types.GroupServer)
	in := func(chat, sender types.JID, id string, ago time.Duration, m *waE2E.Message) {
		c.handleEvent(&events.Message{
			Info:    types.MessageInfo{MessageSource: types.MessageSource{Chat: chat, Sender: sender, IsGroup: chat.Server == types.GroupServer}, ID: id, Timestamp: time.Now().Add(-ago)},
			Message: m,
		})
	}
	in(bob, bob, "P1", 5*time.Hour, &waE2E.Message{ImageMessage: &waE2E.ImageMessage{Caption: proto.String("one")}})
	in(bob, bob, "P2", 4*time.Hour, &waE2E.Message{VideoMessage: &waE2E.VideoMessage{Seconds: proto.Uint32(3)}})
	in(bob, bob, "D1", 3*time.Hour, &waE2E.Message{DocumentMessage: &waE2E.DocumentMessage{FileName: proto.String("bill.pdf")}})
	in(bob, bob, "L1", 2*time.Hour, &waE2E.Message{Conversation: proto.String("see https://example.com/x")})
	in(bob, bob, "T1", time.Hour, &waE2E.Message{Conversation: proto.String("no link here")})
	in(bob, bob, "C1", 30*time.Minute, &waE2E.Message{ContactMessage: &waE2E.ContactMessage{DisplayName: proto.String("Ravi"), Vcard: proto.String("BEGIN:VCARD\nFN:Ravi\nTEL:+919800000002\nEND:VCARD")}})

	var counts map[string]int
	_ = json.Unmarshal([]byte(c.ChatMediaCounts(bob.String())), &counts)
	if counts["media"] != 2 || counts["docs"] != 1 || counts["links"] != 1 {
		t.Fatalf("counts wrong: %v", counts)
	}
	var media []msgRow
	_ = json.Unmarshal([]byte(c.GetChatMedia(bob.String(), "media", 0, 50)), &media)
	if len(media) != 2 || media[0].ID != "P2" {
		t.Fatalf("media should be newest first: %+v", media)
	}
	if !strings.Contains(c.GetMessages(bob.String(), 0, 50), `TEL:+919800000002`) {
		t.Fatal("vcard missing from contact message")
	}

	// A poll in a group, with votes from history and live bookkeeping.
	in(group, bob, "POLL1", time.Minute, &waE2E.Message{PollCreationMessageV3: &waE2E.PollCreationMessage{
		Name:                   proto.String("Lunch?"),
		Options:                []*waE2E.PollCreationMessage_Option{{OptionName: proto.String("Pizza")}, {OptionName: proto.String("Thali")}},
		SelectableOptionsCount: proto.Uint32(1),
	}})
	recordPollVote(c.db, group.String(), "POLL1", bob.String(), optionNames([][]byte{func() []byte { s := sha256.Sum256([]byte("Thali")); return s[:] }()}, []string{"Pizza", "Thali"}), 1)
	recordPollVote(c.db, group.String(), "POLL1", c.ownJID().String(), []string{"Thali"}, 2)
	m := c.loadMessage(group.String(), "POLL1")
	if m == nil || m.Poll == nil || m.Poll.Voters != 2 || m.Poll.Options[1].Votes != 2 || !m.Poll.Options[1].Mine || m.Poll.Multi {
		t.Fatalf("poll state wrong: %+v", m.Poll)
	}
	// Changing a vote replaces the old one.
	recordPollVote(c.db, group.String(), "POLL1", bob.String(), []string{"Pizza"}, 3)
	m = c.loadMessage(group.String(), "POLL1")
	if m.Poll.Options[0].Votes != 1 || m.Poll.Options[1].Votes != 1 {
		t.Fatalf("vote change wrong: %+v", m.Poll)
	}

	// Sending a poll / contact / location stores them straight away.
	out, err := c.SendPoll(bob.String(), "Meet?", `["Yes","No","Yes"," "]`, true)
	if err != nil {
		t.Fatal(err)
	}
	var sent msgRow
	_ = json.Unmarshal([]byte(out), &sent)
	if sent.Type != "poll" || sent.Poll == nil || len(sent.Poll.Options) != 2 || !sent.Poll.Multi {
		t.Fatalf("send poll wrong: %s", out)
	}
	if _, err := c.SendPoll(bob.String(), "Meet?", `["Only one"]`, false); err == nil {
		t.Fatal("one-option poll should fail")
	}
	if out, err = c.SendContact(bob.String(), "Ravi", "BEGIN:VCARD\nFN:Ravi\nEND:VCARD", ""); err != nil || !strings.Contains(out, `"type":"contact"`) {
		t.Fatalf("send contact: %s %v", out, err)
	}
	if out, err = c.SendLocation(bob.String(), 27.33, 88.61, "MG Marg", "Gangtok", ""); err != nil || !strings.Contains(out, `27.33`) || !strings.Contains(out, `"type":"location"`) {
		t.Fatalf("send location: %s %v", out, err)
	}
}

func TestKeepAliveAndCachedAvatar(t *testing.T) {
	c, _ := mk(t)
	defer c.Close()
	// Not linked: nothing to keep alive (and no connection attempt).
	id := c.cli.Store.ID
	c.cli.Store.ID = nil
	if got := c.KeepAlive(5); got != "" {
		t.Fatalf("KeepAlive on unlinked client = %q", got)
	}
	c.cli.Store.ID = id
	jid := "919811111111@s.whatsapp.net"
	if p := c.CachedAvatar(jid); p != "" {
		t.Fatalf("CachedAvatar before download = %q", p)
	}
	p := c.avatarPath(jid)
	if err := os.MkdirAll(filepath.Dir(p), 0o700); err != nil {
		t.Fatal(err)
	}
	if err := os.WriteFile(p, []byte("jpeg"), 0o600); err != nil {
		t.Fatal(err)
	}
	if got := c.CachedAvatar(jid); got != p {
		t.Fatalf("CachedAvatar = %q, want %q", got, p)
	}
}

func TestSearchChatAndGroupInfo(t *testing.T) {
	c, _ := mk(t)
	defer c.Close()
	bob := types.NewJID("919811111111", types.DefaultUserServer)
	in := func(id string, ago time.Duration, m *waE2E.Message) {
		c.handleEvent(&events.Message{
			Info:    types.MessageInfo{MessageSource: types.MessageSource{Chat: bob, Sender: bob}, ID: id, Timestamp: time.Now().Add(-ago)},
			Message: m,
		})
	}
	in("S1", 5*time.Hour, &waE2E.Message{Conversation: proto.String("Booking for Gangtok confirmed")})
	in("S2", 4*time.Hour, &waE2E.Message{ImageMessage: &waE2E.ImageMessage{Caption: proto.String("gangtok view")}})
	in("S3", 3*time.Hour, &waE2E.Message{DocumentMessage: &waE2E.DocumentMessage{FileName: proto.String("Invoice_Gangtok.pdf")}})
	in("S4", 2*time.Hour, &waE2E.Message{Conversation: proto.String("100% done_now")})
	in("S5", time.Hour, &waE2E.Message{Conversation: proto.String("link https://example.com")})

	ids := func(js string) []string {
		var rows []struct {
			ID string `json:"id"`
		}
		if err := json.Unmarshal([]byte(js), &rows); err != nil {
			t.Fatal(err)
		}
		var out []string
		for _, r := range rows {
			out = append(out, r.ID)
		}
		return out
	}
	chat := bob.String()
	if got := ids(c.SearchChat(chat, "GANGTOK", "", 50)); strings.Join(got, ",") != "S3,S2,S1" {
		t.Fatalf("search all = %v", got)
	}
	if got := ids(c.SearchChat(chat, "gangtok", "docs", 50)); strings.Join(got, ",") != "S3" {
		t.Fatalf("search docs = %v", got)
	}
	if got := ids(c.SearchChat(chat, "gangtok", "media", 50)); strings.Join(got, ",") != "S2" {
		t.Fatalf("search media = %v", got)
	}
	// % and _ are matched literally.
	if got := ids(c.SearchChat(chat, "0% done_", "", 50)); strings.Join(got, ",") != "S4" {
		t.Fatalf("search literal = %v", got)
	}
	if got := ids(c.SearchChat(chat, "", "links", 50)); strings.Join(got, ",") != "S5" {
		t.Fatalf("list links = %v", got)
	}
	if got := c.SearchChat(chat, "", "", 50); got != "[]" {
		t.Fatalf("empty search = %s", got)
	}

	// Group members from the saved copy, sorted: you, admins, others.
	group := "120363000000000009@g.us"
	saved := `{"jid":"` + group + `","name":"Trip","topic":"Plans","participants":[` +
		`{"jid":"919822222222@s.whatsapp.net"},` +
		`{"jid":"919811111111@s.whatsapp.net","admin":true},` +
		`{"jid":"919800000001@s.whatsapp.net"}]}`
	if _, err := c.db.Exec(`INSERT INTO group_info (jid, data, ts) VALUES (?,?,?)`, group, saved, 1); err != nil {
		t.Fatal(err)
	}
	js, err := c.GetGroupInfo(group, false)
	if err != nil {
		t.Fatal(err)
	}
	var g groupInfoOut
	if err := json.Unmarshal([]byte(js), &g); err != nil {
		t.Fatal(err)
	}
	if g.Topic != "Plans" || len(g.Participants) != 3 {
		t.Fatalf("group info = %+v", g)
	}
	if !g.Participants[0].IsMe || g.Participants[0].Name != "You" {
		t.Fatalf("first member should be you: %+v", g.Participants[0])
	}
	if !g.Participants[1].Admin || g.Participants[1].Phone != "+91 98111 11111" {
		t.Fatalf("second member should be the admin: %+v", g.Participants[1])
	}
}

func TestChatAndMessageActions(t *testing.T) {
	c, _ := mk(t)
	defer c.Close()
	bob := types.NewJID("919811111111", types.DefaultUserServer)
	chat := bob.String()
	in := func(id string, ago time.Duration, m *waE2E.Message) {
		c.handleEvent(&events.Message{
			Info:    types.MessageInfo{MessageSource: types.MessageSource{Chat: bob, Sender: bob}, ID: id, Timestamp: time.Now().Add(-ago)},
			Message: m,
		})
	}
	// A message with a disappearing timer turns it on for the chat.
	in("E1", 3*time.Hour, &waE2E.Message{ExtendedTextMessage: &waE2E.ExtendedTextMessage{
		Text: proto.String("hi"), ContextInfo: &waE2E.ContextInfo{Expiration: proto.Uint32(86400)}}})
	if got := c.expirationFor(chat); got != 86400 {
		t.Fatalf("ephemeral = %d", got)
	}
	in("E2", 2*time.Hour, &waE2E.Message{Conversation: proto.String("keep me")})
	in("E3", time.Hour, &waE2E.Message{Conversation: proto.String("other")})

	// Forwarding: marked forwarded, no quote, with the target's timer.
	var raw []byte
	_ = c.db.QueryRow(`SELECT raw FROM messages WHERE chat=? AND id='E2'`, chat).Scan(&raw)
	fwd, err := forwardable(raw, 3600)
	if err != nil {
		t.Fatal(err)
	}
	ci := fwd.GetExtendedTextMessage().GetContextInfo()
	if fwd.GetExtendedTextMessage().GetText() != "keep me" || !ci.GetIsForwarded() || ci.GetForwardingScore() != 1 || ci.GetExpiration() != 3600 {
		t.Fatalf("forwardable = %v", fwd)
	}
	viewOnce, _ := proto.Marshal(&waE2E.Message{ImageMessage: &waE2E.ImageMessage{ViewOnce: proto.Bool(true)}})
	if _, err := forwardable(viewOnce, 0); err == nil {
		t.Fatal("view-once should not be forwardable")
	}
	group := "120363000000000003@g.us"
	js, err := c.ForwardMessages(chat, `["E2","E3"]`, `["`+group+`"]`)
	if err != nil {
		t.Fatal(err)
	}
	var rows []struct {
		Chat  string `json:"chat"`
		Text  string `json:"text"`
		Extra string `json:"extra"`
	}
	_ = json.Unmarshal([]byte(js), &rows)
	if len(rows) != 2 || rows[0].Chat != group || rows[0].Text != "keep me" || !strings.Contains(rows[0].Extra, `"forwarded":true`) {
		t.Fatalf("forward rows = %s", js)
	}

	// Star, then clear the chat: the starred message stays.
	if err := c.StarMessage(chat, "E2", true); err != nil {
		t.Fatal(err)
	}
	if got := c.GetStarred(chat, 10); !strings.Contains(got, `"id":"E2"`) || !strings.Contains(got, `"starred":true`) {
		t.Fatalf("starred = %s", got)
	}

	// A pin from the other person, then message info for our message.
	c.handleEvent(&events.Message{
		Info: types.MessageInfo{MessageSource: types.MessageSource{Chat: bob, Sender: bob}, ID: "PIN1", Timestamp: time.Now()},
		Message: &waE2E.Message{
			PinInChatMessage: &waE2E.PinInChatMessage{
				Key:  &waCommon.MessageKey{RemoteJID: proto.String(chat), FromMe: proto.Bool(false), ID: proto.String("E3")},
				Type: waE2E.PinInChatMessage_PIN_FOR_ALL.Enum(),
			},
			MessageContextInfo: &waE2E.MessageContextInfo{MessageAddOnDurationInSecs: proto.Uint32(86400)},
		},
	})
	if got := c.GetPinned(chat); !strings.Contains(got, `"id":"E3"`) {
		t.Fatalf("pinned = %s", got)
	}
	var pinRows int
	_ = c.db.QueryRow(`SELECT COUNT(*) FROM messages WHERE id='PIN1'`).Scan(&pinRows)
	if pinRows != 0 {
		t.Fatal("pin should not be stored as a message")
	}

	_, _ = c.db.Exec(`INSERT INTO messages (chat,id,sender,from_me,ts,type,text,status) VALUES (?,?,?,?,?,?,?,?)`,
		chat, "OUT9", c.ownJID().String(), 1, time.Now().UnixMilli(), "text", "sent", StatusSent)
	c.handleEvent(&events.Receipt{
		MessageSource: types.MessageSource{Chat: bob, Sender: bob},
		MessageIDs:    []types.MessageID{"OUT9"}, Timestamp: time.Now(), Type: types.ReceiptTypeRead,
	})
	if got := c.MessageInfo(chat, "OUT9"); !strings.Contains(got, `"read":[{"jid":"`+chat) {
		t.Fatalf("message info = %s", got)
	}

	c.ClearChat(chat)
	var left []string
	r, _ := c.db.Query(`SELECT id FROM messages WHERE chat=?`, chat)
	for r.Next() {
		var id string
		_ = r.Scan(&id)
		left = append(left, id)
	}
	r.Close()
	if strings.Join(left, ",") != "E2" {
		t.Fatalf("after clear = %v", left)
	}
	if got := c.GetPinned(chat); got != "[]" {
		t.Fatalf("pins after clear = %s", got)
	}
	c.DeleteChat(chat)
	var n int
	_ = c.db.QueryRow(`SELECT COUNT(*) FROM chats WHERE jid=?`, chat).Scan(&n)
	if n != 0 {
		t.Fatal("chat not deleted")
	}
}
