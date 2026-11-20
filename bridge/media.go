package wa

import (
	"context"
	"encoding/base64"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"os"
	"path/filepath"
	"strings"
	"sync"
	"time"

	"go.mau.fi/whatsmeow"
	"go.mau.fi/whatsmeow/proto/waCommon"
	"go.mau.fi/whatsmeow/proto/waE2E"
	"go.mau.fi/whatsmeow/proto/waMmsRetry"
	"go.mau.fi/whatsmeow/types"
	"go.mau.fi/whatsmeow/types/events"
	"google.golang.org/protobuf/proto"
)

// ---------------------------------------------------------------------------
// Files

var fileSafe = strings.NewReplacer("@", "_", ":", "_", "/", "_", "\\", "_", ".", "_", " ", "_")

func (c *Client) mediaDir() string { return filepath.Join(c.dataDir, "media") }

// mediaFile is where a message's attachment is kept on this device.
func (c *Client) mediaFile(chat, id, ext string) string {
	dir := filepath.Join(c.mediaDir(), fileSafe.Replace(chat))
	_ = os.MkdirAll(dir, 0o700)
	return filepath.Join(dir, fileSafe.Replace(id)+ext)
}

func (c *Client) tmpDir() string {
	d := filepath.Join(c.dataDir, "tmp")
	_ = os.MkdirAll(d, 0o700)
	return d
}

func fileOK(p string) bool {
	if p == "" {
		return false
	}
	st, err := os.Stat(p)
	return err == nil && st.Size() > 0 && !st.IsDir()
}

// moveFile renames src to dst, copying when they're on different volumes.
func moveFile(src, dst string) error {
	if err := os.Rename(src, dst); err == nil {
		return nil
	}
	in, err := os.Open(src)
	if err != nil {
		return err
	}
	defer in.Close()
	out, err := os.OpenFile(dst, os.O_CREATE|os.O_WRONLY|os.O_TRUNC, 0o600)
	if err != nil {
		return err
	}
	if _, err = io.Copy(out, in); err != nil {
		_ = out.Close()
		_ = os.Remove(dst)
		return err
	}
	if err = out.Close(); err != nil {
		return err
	}
	_ = os.Remove(src)
	return nil
}

func extFor(mime, fileName, typ string) string {
	if e := strings.ToLower(filepath.Ext(fileName)); e != "" && len(e) <= 6 {
		return e
	}
	switch strings.ToLower(strings.TrimSpace(strings.Split(mime, ";")[0])) {
	case "image/jpeg", "image/jpg":
		return ".jpg"
	case "image/png":
		return ".png"
	case "image/webp":
		return ".webp"
	case "image/gif":
		return ".gif"
	case "video/mp4":
		return ".mp4"
	case "video/3gpp":
		return ".3gp"
	case "audio/ogg", "audio/opus":
		return ".ogg"
	case "audio/mpeg", "audio/mp3":
		return ".mp3"
	case "audio/mp4", "audio/aac", "audio/x-m4a", "audio/m4a":
		return ".m4a"
	case "audio/amr":
		return ".amr"
	case "application/pdf":
		return ".pdf"
	}
	switch typ {
	case "image":
		return ".jpg"
	case "video", "gif":
		return ".mp4"
	case "voice", "audio":
		return ".ogg"
	case "sticker":
		return ".webp"
	}
	return ".bin"
}

// mediaPart picks the downloadable attachment out of a message.
func mediaPart(m *waE2E.Message) (part whatsmeow.DownloadableMessage, mime, fileName string) {
	if inner := m.GetDocumentWithCaptionMessage().GetMessage(); inner != nil {
		m = inner
	}
	switch {
	case m.GetImageMessage() != nil:
		i := m.GetImageMessage()
		return i, i.GetMimetype(), ""
	case m.GetVideoMessage() != nil:
		v := m.GetVideoMessage()
		return v, v.GetMimetype(), ""
	case m.GetPtvMessage() != nil:
		v := m.GetPtvMessage()
		return v, v.GetMimetype(), ""
	case m.GetAudioMessage() != nil:
		a := m.GetAudioMessage()
		return a, a.GetMimetype(), ""
	case m.GetDocumentMessage() != nil:
		d := m.GetDocumentMessage()
		return d, d.GetMimetype(), firstNonEmpty(d.GetFileName(), d.GetTitle())
	case m.GetStickerMessage() != nil:
		s := m.GetStickerMessage()
		return s, s.GetMimetype(), ""
	}
	return nil, "", ""
}

func setDirectPath(part whatsmeow.DownloadableMessage, p string) {
	switch v := part.(type) {
	case *waE2E.ImageMessage:
		v.DirectPath = proto.String(p)
	case *waE2E.VideoMessage:
		v.DirectPath = proto.String(p)
	case *waE2E.AudioMessage:
		v.DirectPath = proto.String(p)
	case *waE2E.DocumentMessage:
		v.DirectPath = proto.String(p)
	case *waE2E.StickerMessage:
		v.DirectPath = proto.String(p)
	}
}

type keyedLocks struct {
	mu sync.Mutex
	m  map[string]*sync.Mutex
}

func (k *keyedLocks) lock(key string) func() {
	k.mu.Lock()
	if k.m == nil {
		k.m = map[string]*sync.Mutex{}
	}
	l := k.m[key]
	if l == nil {
		l = &sync.Mutex{}
		k.m[key] = l
	}
	k.mu.Unlock()
	l.Lock()
	return l.Unlock
}

// ---------------------------------------------------------------------------
// Downloading

// DownloadMedia returns a local file path for a message's photo, video,
// voice note, document or sticker, downloading it first if needed. It blocks
// (call it off the main thread). Media that has expired on WhatsApp's servers
// is requested again from the phone automatically.
func (c *Client) DownloadMedia(chat, id string) (string, error) {
	var raw []byte
	var rawChat, rawSender, typ, local string
	var fromMe int
	err := c.db.QueryRow(`SELECT raw, raw_chat, raw_sender, from_me, type, local FROM messages WHERE chat=? AND id=?`, chat, id).
		Scan(&raw, &rawChat, &rawSender, &fromMe, &typ, &local)
	if err != nil {
		return "", errors.New("message not found")
	}
	if fileOK(local) {
		return local, nil
	}
	msg := &waE2E.Message{}
	if len(raw) == 0 || proto.Unmarshal(raw, msg) != nil {
		return "", errors.New("nothing to download")
	}
	part, mime, name := mediaPart(msg)
	if part == nil {
		return "", errors.New("nothing to download")
	}
	dest := c.mediaFile(chat, id, extFor(mime, name, typ))
	unlock := c.dlLocks.lock(dest)
	defer unlock()
	if !fileOK(dest) {
		cj, _ := types.ParseJID(rawChat)
		sj, _ := types.ParseJID(rawSender)
		info := &types.MessageInfo{
			MessageSource: types.MessageSource{
				Chat: cj, Sender: sj, IsFromMe: fromMe == 1,
				IsGroup: cj.Server == types.GroupServer || cj.Server == types.BroadcastServer,
			},
			ID: id,
		}
		retried, err := c.fetchMedia(part, dest, info)
		if retried {
			// The phone gave a fresh path; keep it for next time.
			if b, merr := proto.Marshal(msg); merr == nil {
				_, _ = c.db.Exec(`UPDATE messages SET raw=? WHERE chat=? AND id=?`, b, chat, id)
			}
		}
		if err != nil {
			c.log.Warnf("download %s/%s: %v", chat, id, err)
			return "", err
		}
	}
	_, _ = c.db.Exec(`UPDATE messages SET local=? WHERE chat=? AND id=?`, dest, chat, id)
	return dest, nil
}

func (c *Client) downloadTo(part whatsmeow.DownloadableMessage, dest string) error {
	tmp := dest + ".part"
	f, err := os.OpenFile(tmp, os.O_CREATE|os.O_RDWR|os.O_TRUNC, 0o600)
	if err != nil {
		return err
	}
	ctx, cancel := context.WithTimeout(c.ctx, 5*time.Minute)
	defer cancel()
	err = c.cli.DownloadToFile(ctx, part, f)
	if cerr := f.Close(); err == nil {
		err = cerr
	}
	if err != nil {
		_ = os.Remove(tmp)
		return err
	}
	return os.Rename(tmp, dest)
}

// fetchMedia downloads part to dest. On a 404/410 (expired) it asks the
// phone to upload the file again and retries with the new path.
func (c *Client) fetchMedia(part whatsmeow.DownloadableMessage, dest string, info *types.MessageInfo) (retried bool, err error) {
	if !c.cli.IsConnected() {
		c.cli.WaitForConnection(15 * time.Second)
	}
	err = c.downloadTo(part, dest)
	if err == nil {
		return false, nil
	}
	expired := errors.Is(err, whatsmeow.ErrMediaDownloadFailedWith404) ||
		errors.Is(err, whatsmeow.ErrMediaDownloadFailedWith410) ||
		errors.Is(err, whatsmeow.ErrNoURLPresent)
	if info == nil || info.ID == "" || info.Chat.IsEmpty() || !expired {
		return false, friendlyDownloadError(err)
	}
	c.log.Infof("media %s expired, asking the phone to send it again", info.ID)
	ch := make(chan *events.MediaRetry, 1)
	c.retryMu.Lock()
	c.retryWait[info.ID] = ch
	c.retryMu.Unlock()
	defer func() {
		c.retryMu.Lock()
		delete(c.retryWait, info.ID)
		c.retryMu.Unlock()
	}()
	ctx, cancel := context.WithTimeout(c.ctx, 20*time.Second)
	err = c.cli.SendMediaRetryReceipt(ctx, info, part.GetMediaKey())
	cancel()
	if err != nil {
		return false, fmt.Errorf("this media has expired and couldn't be requested again: %w", err)
	}
	select {
	case evt := <-ch:
		notif, derr := whatsmeow.DecryptMediaRetryNotification(evt, part.GetMediaKey())
		if derr != nil {
			if errors.Is(derr, whatsmeow.ErrMediaNotAvailableOnPhone) {
				return false, errors.New("this media is no longer on your phone")
			}
			return false, derr
		}
		if notif.GetResult() != waMmsRetry.MediaRetryNotification_SUCCESS || notif.GetDirectPath() == "" {
			return false, errors.New("your phone couldn't send this media again")
		}
		setDirectPath(part, notif.GetDirectPath())
		if err = c.downloadTo(part, dest); err != nil {
			return true, friendlyDownloadError(err)
		}
		return true, nil
	case <-time.After(45 * time.Second):
		return false, errors.New("this media has expired; keep WhatsApp open on your phone and try again")
	case <-c.ctx.Done():
		return false, c.ctx.Err()
	}
}

func friendlyDownloadError(err error) error {
	switch {
	case errors.Is(err, context.DeadlineExceeded):
		return errors.New("download timed out; check your connection")
	case errors.Is(err, whatsmeow.ErrInvalidMediaHMAC), errors.Is(err, whatsmeow.ErrInvalidMediaEncSHA256), errors.Is(err, whatsmeow.ErrInvalidMediaSHA256):
		return errors.New("the downloaded file was damaged; try again")
	}
	return err
}

func (c *Client) onMediaRetry(evt *events.MediaRetry) {
	c.retryMu.Lock()
	ch := c.retryWait[evt.MessageID]
	c.retryMu.Unlock()
	if ch != nil {
		select {
		case ch <- evt:
		default:
		}
	}
}

// MediaCacheSize returns the bytes used by downloaded and sent media.
func (c *Client) MediaCacheSize() int64 {
	var total int64
	_ = filepath.Walk(c.mediaDir(), func(_ string, info os.FileInfo, err error) error {
		if err == nil && !info.IsDir() {
			total += info.Size()
		}
		return nil
	})
	return total
}

// ClearMediaCache deletes downloaded media. Everything can be downloaded again.
func (c *Client) ClearMediaCache() {
	_ = os.RemoveAll(c.mediaDir())
	_ = os.MkdirAll(c.mediaDir(), 0o700)
	_, _ = c.db.Exec(`UPDATE messages SET local='' WHERE local!=''`)
}

// ---------------------------------------------------------------------------
// Sending

// mediaSpec describes an attachment to send (JSON from the app).
type mediaSpec struct {
	Kind     string `json:"kind"` // image, video, gif, document, voice, audio
	Path     string `json:"path,omitempty"`
	Thumb    string `json:"thumb,omitempty"` // small JPEG preview (file path)
	Mime     string `json:"mime,omitempty"`
	FileName string `json:"fileName,omitempty"`
	Caption  string `json:"caption,omitempty"`
	Width    int    `json:"w,omitempty"`
	Height   int    `json:"h,omitempty"`
	Seconds  int    `json:"seconds,omitempty"`
	Waveform string `json:"waveform,omitempty"` // base64, up to 64 bytes 0-100
	QuotedID string `json:"quotedId,omitempty"`
	// ViewOnce: the photo/video can be opened once by the recipient.
	ViewOnce bool `json:"viewOnce,omitempty"`
	// AlbumParent/AlbumIndex: part of an album started with SendAlbum.
	AlbumParent string `json:"albumParent,omitempty"`
	AlbumIndex  int    `json:"albumIndex,omitempty"`
}

func uptr32(v int) *uint32 {
	if v <= 0 {
		return nil
	}
	return proto.Uint32(uint32(v))
}

func optString(s string) *string {
	if s == "" {
		return nil
	}
	return proto.String(s)
}

// SendMedia sends a photo, video, GIF, document, voice note or audio file.
// spec is JSON: {"kind","path","thumb","mime","fileName","caption","w","h",
// "seconds","waveform","quotedId"}. The file is moved into the app's media
// folder. It returns the new message as JSON straight away; upload and
// delivery continue in the background ("message_update" events).
// Use chat "status@broadcast" to post a status.
func (c *Client) SendMedia(chat, specJSON string) (string, error) {
	var s mediaSpec
	if err := json.Unmarshal([]byte(specJSON), &s); err != nil {
		return "", fmt.Errorf("bad media spec: %w", err)
	}
	switch s.Kind {
	case "image", "video", "gif", "document", "voice", "audio", "ptv":
	default:
		return "", errors.New("unsupported media type")
	}
	jid, err := parseChat(chat)
	if err != nil {
		return "", err
	}
	st, err := os.Stat(s.Path)
	if err != nil || st.Size() == 0 {
		return "", errors.New("the file couldn't be read")
	}
	if st.Size() > 100<<20 && s.Kind != "document" {
		return "", errors.New("files over 100 MB can only be sent as documents")
	}
	id := c.cli.GenerateMessageID()
	local := c.mediaFile(chat, id, extFor(s.Mime, s.FileName, s.Kind))
	if err = moveFile(s.Path, local); err != nil {
		return "", err
	}
	s.Path = ""
	var thumb []byte
	if s.Thumb != "" {
		thumb, _ = os.ReadFile(s.Thumb)
		_ = os.Remove(s.Thumb)
		s.Thumb = ""
	}
	ex := map[string]any{"size": st.Size()}
	if s.Width > 0 && s.Height > 0 {
		ex["w"], ex["h"] = s.Width, s.Height
	}
	if s.Seconds > 0 {
		ex["seconds"] = s.Seconds
	}
	if s.Mime != "" {
		ex["mime"] = s.Mime
	}
	if s.FileName != "" {
		ex["fileName"] = s.FileName
	}
	if len(thumb) > 0 && len(thumb) < 64*1024 {
		ex["thumb"] = base64.StdEncoding.EncodeToString(thumb)
	}
	if s.Waveform != "" {
		ex["waveform"] = s.Waveform
	}
	if s.Kind == "ptv" {
		ex["round"] = true
	}
	if s.ViewOnce {
		ex["viewOnce"] = true
	}
	if s.AlbumParent != "" {
		ex["album"] = s.AlbumParent
	}
	ex["spec"] = s
	exJSON, _ := json.Marshal(ex)
	rowType := s.Kind
	if rowType == "ptv" {
		rowType = "video"
	}
	row := &msgRow{
		Chat: chat, ID: id, Sender: c.ownJID().String(), FromMe: true,
		Ts: time.Now().UnixMilli(), Type: rowType, Text: strings.TrimSpace(s.Caption), Extra: string(exJSON),
		Status: StatusPending, RawChat: jid.String(), RawSender: c.ownJID().String(), Local: local,
	}
	if s.QuotedID != "" {
		if q := c.loadQuoteSource(chat, s.QuotedID); q != nil {
			row.QuotedID, row.QuotedSender, row.QuotedText, row.QuotedType = s.QuotedID, q.sender, q.text, q.typ
		}
	}
	isStatus := jid == types.StatusBroadcastJID
	if !isStatus {
		ensureChat(c.db, chat, jid.Server == types.GroupServer, jid.String())
	}
	if err = upsertMessage(c.db, row); err != nil {
		return "", err
	}
	if !isStatus {
		_, _ = c.db.Exec(`UPDATE chats SET last_ts=MAX(last_ts,?), unread=0, marked_unread=0 WHERE jid=?`, row.Ts, chat)
	}
	c.decorate(row)
	go c.deliverMedia(jid, row, s, thumb)
	b, _ := json.Marshal(row)
	return string(b), nil
}

func (c *Client) deliverMedia(to types.JID, row *msgRow, s mediaSpec, thumb []byte) {
	msg, err := c.uploadMedia(row, s, thumb)
	if err != nil {
		c.log.Errorf("upload %s: %v", row.ID, err)
		_, _ = c.db.Exec(`UPDATE messages SET status=? WHERE chat=? AND id=? AND status<=0`, StatusFailed, row.Chat, row.ID)
		if m := c.loadMessage(row.Chat, row.ID); m != nil {
			c.emit("message_update", m)
		}
		c.emit("chats_changed", nil)
		return
	}
	if s.AlbumParent != "" {
		if msg.MessageContextInfo == nil {
			msg.MessageContextInfo = &waE2E.MessageContextInfo{}
		}
		msg.MessageContextInfo.MessageAssociation = &waE2E.MessageAssociation{
			AssociationType: waE2E.MessageAssociation_MEDIA_ALBUM.Enum(),
			ParentMessageKey: &waCommon.MessageKey{
				RemoteJID: proto.String(to.String()),
				FromMe:    proto.Bool(true),
				ID:        proto.String(s.AlbumParent),
			},
			MessageIndex: proto.Int32(int32(s.AlbumIndex)),
		}
	}
	row.Raw, _ = proto.Marshal(msg)
	_, _ = c.db.Exec(`UPDATE messages SET raw=? WHERE chat=? AND id=?`, row.Raw, row.Chat, row.ID)
	c.deliver(to, msg, row)
}

func (c *Client) uploadMedia(row *msgRow, s mediaSpec, thumb []byte) (*waE2E.Message, error) {
	f, err := os.Open(row.Local)
	if err != nil {
		return nil, err
	}
	defer f.Close()
	tmp, err := os.CreateTemp(c.tmpDir(), "up-*")
	if err != nil {
		return nil, err
	}
	defer func() {
		_ = tmp.Close()
		_ = os.Remove(tmp.Name())
	}()
	appInfo := whatsmeow.MediaDocument
	switch s.Kind {
	case "image":
		appInfo = whatsmeow.MediaImage
	case "video", "gif", "ptv":
		appInfo = whatsmeow.MediaVideo
	case "voice", "audio":
		appInfo = whatsmeow.MediaAudio
	}
	if !c.cli.IsConnected() {
		c.cli.WaitForConnection(30 * time.Second)
	}
	ctx, cancel := context.WithTimeout(c.ctx, 10*time.Minute)
	defer cancel()
	up, err := c.cli.UploadReader(ctx, f, tmp, appInfo)
	if err != nil {
		return nil, err
	}
	var ci *waE2E.ContextInfo
	if row.QuotedID != "" {
		if q := c.loadQuoteSource(row.Chat, row.QuotedID); q != nil {
			ci = q.ctx
		}
	}
	ci = c.withExpiration(row.Chat, ci)
	caption := optString(row.Text)
	fileLen := proto.Uint64(up.FileLength)
	switch s.Kind {
	case "image":
		img := &waE2E.ImageMessage{
			Caption: caption, Mimetype: proto.String(firstNonEmpty(s.Mime, "image/jpeg")),
			URL: proto.String(up.URL), DirectPath: proto.String(up.DirectPath), MediaKey: up.MediaKey,
			FileEncSHA256: up.FileEncSHA256, FileSHA256: up.FileSHA256, FileLength: fileLen,
			Width: uptr32(s.Width), Height: uptr32(s.Height), JPEGThumbnail: thumb, ContextInfo: ci,
			MediaKeyTimestamp: proto.Int64(time.Now().Unix()),
		}
		if s.ViewOnce {
			img.ViewOnce = proto.Bool(true)
			img.JPEGThumbnail = nil
			return &waE2E.Message{ViewOnceMessage: &waE2E.FutureProofMessage{Message: &waE2E.Message{ImageMessage: img}}}, nil
		}
		return &waE2E.Message{ImageMessage: img}, nil
	case "ptv":
		// Instant video message (the round one).
		return &waE2E.Message{PtvMessage: &waE2E.VideoMessage{
			Mimetype: proto.String("video/mp4"),
			URL:      proto.String(up.URL), DirectPath: proto.String(up.DirectPath), MediaKey: up.MediaKey,
			FileEncSHA256: up.FileEncSHA256, FileSHA256: up.FileSHA256, FileLength: fileLen,
			Width: uptr32(s.Width), Height: uptr32(s.Height), Seconds: uptr32(s.Seconds),
			JPEGThumbnail: thumb, StreamingSidecar: up.StreamingSidecar, ContextInfo: ci,
			MediaKeyTimestamp: proto.Int64(time.Now().Unix()),
		}}, nil
	case "video", "gif":
		if s.ViewOnce {
			return &waE2E.Message{ViewOnceMessage: &waE2E.FutureProofMessage{Message: &waE2E.Message{VideoMessage: &waE2E.VideoMessage{
				Caption: caption, Mimetype: proto.String(firstNonEmpty(s.Mime, "video/mp4")),
				URL: proto.String(up.URL), DirectPath: proto.String(up.DirectPath), MediaKey: up.MediaKey,
				FileEncSHA256: up.FileEncSHA256, FileSHA256: up.FileSHA256, FileLength: fileLen,
				Width: uptr32(s.Width), Height: uptr32(s.Height), Seconds: uptr32(s.Seconds),
				StreamingSidecar: up.StreamingSidecar, ContextInfo: ci, ViewOnce: proto.Bool(true),
			}}}}, nil
		}
		return &waE2E.Message{VideoMessage: &waE2E.VideoMessage{
			Caption: caption, Mimetype: proto.String(firstNonEmpty(s.Mime, "video/mp4")),
			URL: proto.String(up.URL), DirectPath: proto.String(up.DirectPath), MediaKey: up.MediaKey,
			FileEncSHA256: up.FileEncSHA256, FileSHA256: up.FileSHA256, FileLength: fileLen,
			Width: uptr32(s.Width), Height: uptr32(s.Height), Seconds: uptr32(s.Seconds),
			GifPlayback: proto.Bool(s.Kind == "gif"), JPEGThumbnail: thumb, StreamingSidecar: up.StreamingSidecar,
			ContextInfo: ci,
		}}, nil
	case "voice", "audio":
		var wave []byte
		if s.Waveform != "" {
			wave, _ = base64.StdEncoding.DecodeString(s.Waveform)
		}
		return &waE2E.Message{AudioMessage: &waE2E.AudioMessage{
			Mimetype: proto.String(firstNonEmpty(s.Mime, "audio/ogg; codecs=opus")),
			URL:      proto.String(up.URL), DirectPath: proto.String(up.DirectPath), MediaKey: up.MediaKey,
			FileEncSHA256: up.FileEncSHA256, FileSHA256: up.FileSHA256, FileLength: fileLen,
			Seconds: uptr32(s.Seconds), PTT: proto.Bool(s.Kind == "voice"), Waveform: wave, ContextInfo: ci,
		}}, nil
	default:
		name := firstNonEmpty(s.FileName, "file"+filepath.Ext(row.Local))
		doc := &waE2E.DocumentMessage{
			Mimetype: proto.String(firstNonEmpty(s.Mime, "application/octet-stream")),
			Title:    proto.String(name), FileName: proto.String(name),
			URL: proto.String(up.URL), DirectPath: proto.String(up.DirectPath), MediaKey: up.MediaKey,
			FileEncSHA256: up.FileEncSHA256, FileSHA256: up.FileSHA256, FileLength: fileLen,
			JPEGThumbnail: thumb, ContextInfo: ci,
		}
		if caption != nil {
			doc.Caption = caption
			return &waE2E.Message{DocumentWithCaptionMessage: &waE2E.FutureProofMessage{
				Message: &waE2E.Message{DocumentMessage: doc},
			}}, nil
		}
		return &waE2E.Message{DocumentMessage: doc}, nil
	}
}

// retryMedia re-runs a failed media send from the copy kept on this device.
func (c *Client) retryMedia(chat, id string) (bool, error) {
	m := c.loadMessage(chat, id)
	if m == nil {
		return false, errors.New("message not found")
	}
	var ex struct {
		Spec *mediaSpec `json:"spec"`
	}
	if json.Unmarshal([]byte(m.Extra), &ex) != nil || ex.Spec == nil {
		return false, nil
	}
	var raw []byte
	_ = c.db.QueryRow(`SELECT raw, local FROM messages WHERE chat=? AND id=?`, chat, id).Scan(&raw, &m.Local)
	if len(raw) > 0 {
		// Already uploaded: a normal re-send will do.
		return false, nil
	}
	if !fileOK(m.Local) {
		return true, errors.New("the original file is gone; send it again")
	}
	jid, err := parseChat(chat)
	if err != nil {
		return true, err
	}
	thumb := []byte(nil)
	var exThumb struct {
		Thumb string `json:"thumb"`
	}
	if json.Unmarshal([]byte(m.Extra), &exThumb) == nil && exThumb.Thumb != "" {
		thumb, _ = base64.StdEncoding.DecodeString(exThumb.Thumb)
	}
	_, _ = c.db.Exec(`UPDATE messages SET status=? WHERE chat=? AND id=?`, StatusPending, chat, id)
	m.Status = StatusPending
	c.emit("message_update", m)
	row := &msgRow{Chat: chat, ID: id, Text: m.Text, QuotedID: m.QuotedID, Local: m.Local}
	go c.deliverMedia(jid, row, *ex.Spec, thumb)
	return true, nil
}

// MarkPlayed sends the "played" receipt (blue microphone) for a voice note.
func (c *Client) MarkPlayed(chat, id string, sendReceipt bool) {
	if !sendReceipt {
		return
	}
	var rawChat, rawSender string
	var fromMe int
	if c.db.QueryRow(`SELECT raw_chat, raw_sender, from_me FROM messages WHERE chat=? AND id=?`, chat, id).
		Scan(&rawChat, &rawSender, &fromMe) != nil || fromMe == 1 {
		return
	}
	cj, err1 := types.ParseJID(rawChat)
	sj, err2 := types.ParseJID(rawSender)
	if err1 != nil || err2 != nil {
		return
	}
	go func() {
		ctx, cancel := context.WithTimeout(c.ctx, 30*time.Second)
		defer cancel()
		if err := c.cli.MarkRead(ctx, []types.MessageID{id}, time.Now(), cj, sj, types.ReceiptTypePlayed); err != nil {
			c.log.Warnf("played receipt: %v", err)
		}
	}()
}

// ---------------------------------------------------------------------------
// Stickers: ones you've received or sent, plus your recent and favourite
// stickers from the phone's history sync.

func stickerKey(sm *waE2E.StickerMessage) string {
	if len(sm.GetFileSHA256()) == 0 {
		return ""
	}
	return base64.RawURLEncoding.EncodeToString(sm.GetFileSHA256())
}

func rememberSticker(db execer, sm *waE2E.StickerMessage, ts int64, fav bool) {
	key := stickerKey(sm)
	if key == "" || sm.GetIsLottie() || sm.GetMediaKey() == nil {
		return
	}
	clean := proto.Clone(sm).(*waE2E.StickerMessage)
	clean.ContextInfo = nil
	raw, err := proto.Marshal(clean)
	if err != nil {
		return
	}
	_, _ = db.Exec(`INSERT INTO stickers (hash, raw, ts, fav, animated) VALUES (?,?,?,?,?)
		ON CONFLICT(hash) DO UPDATE SET raw=excluded.raw, ts=MAX(stickers.ts, excluded.ts), fav=MAX(stickers.fav, excluded.fav)`,
		key, raw, ts, b2i(fav), b2i(sm.GetIsAnimated()))
}

// GetStickers lists stickers for the sticker panel as JSON
// [{"id","animated","fav"}], favourites first, then most recent.
func (c *Client) GetStickers() string {
	type out struct {
		ID       string `json:"id"`
		Animated bool   `json:"animated"`
		Fav      bool   `json:"fav"`
	}
	list := []out{}
	rows, err := c.db.Query(`SELECT hash, animated, fav FROM stickers ORDER BY fav DESC, ts DESC LIMIT 150`)
	if err != nil {
		return "[]"
	}
	defer rows.Close()
	for rows.Next() {
		var o out
		var anim, fav int
		if rows.Scan(&o.ID, &anim, &fav) == nil {
			o.Animated, o.Fav = anim == 1, fav == 1
			list = append(list, o)
		}
	}
	b, _ := json.Marshal(list)
	return string(b)
}

func (c *Client) loadSticker(hash string) (*waE2E.StickerMessage, error) {
	var raw []byte
	if err := c.db.QueryRow(`SELECT raw FROM stickers WHERE hash=?`, hash).Scan(&raw); err != nil {
		return nil, errors.New("sticker not found")
	}
	sm := &waE2E.StickerMessage{}
	if err := proto.Unmarshal(raw, sm); err != nil {
		return nil, err
	}
	return sm, nil
}

func (c *Client) stickerFile(hash string) string {
	return c.mediaFile("_stickers", hash, ".webp")
}

// DownloadSticker returns a local path for a sticker from GetStickers.
func (c *Client) DownloadSticker(hash string) (string, error) {
	dest := c.stickerFile(hash)
	if fileOK(dest) {
		return dest, nil
	}
	unlock := c.dlLocks.lock(dest)
	defer unlock()
	if fileOK(dest) {
		return dest, nil
	}
	sm, err := c.loadSticker(hash)
	if err != nil {
		return "", err
	}
	if _, err = c.fetchMedia(sm, dest, nil); err != nil {
		// Too old to download: drop it from the panel.
		if errors.Is(err, whatsmeow.ErrMediaDownloadFailedWith404) || errors.Is(err, whatsmeow.ErrMediaDownloadFailedWith410) {
			_, _ = c.db.Exec(`DELETE FROM stickers WHERE hash=?`, hash)
		}
		return "", err
	}
	return dest, nil
}

// SendSticker sends a sticker from GetStickers. Returns the new message JSON.
func (c *Client) SendSticker(chat, hash, quotedID string) (string, error) {
	jid, err := parseChat(chat)
	if err != nil {
		return "", err
	}
	sm, err := c.loadSticker(hash)
	if err != nil {
		return "", err
	}
	ex := map[string]any{}
	if sm.GetIsAnimated() {
		ex["animated"] = true
	}
	exJSON, _ := json.Marshal(ex)
	row := &msgRow{
		Chat: chat, ID: c.cli.GenerateMessageID(), Sender: c.ownJID().String(), FromMe: true,
		Ts: time.Now().UnixMilli(), Type: "sticker", Extra: string(exJSON), Status: StatusPending,
		RawChat: jid.String(), RawSender: c.ownJID().String(),
	}
	if f := c.stickerFile(hash); fileOK(f) {
		row.Local = f
	}
	if quotedID != "" {
		if q := c.loadQuoteSource(chat, quotedID); q != nil {
			row.QuotedID, row.QuotedSender, row.QuotedText, row.QuotedType = quotedID, q.sender, q.text, q.typ
		}
	}
	ensureChat(c.db, chat, jid.Server == types.GroupServer, jid.String())
	if err = upsertMessage(c.db, row); err != nil {
		return "", err
	}
	_, _ = c.db.Exec(`UPDATE chats SET last_ts=MAX(last_ts,?), unread=0, marked_unread=0 WHERE jid=?`, row.Ts, chat)
	_, _ = c.db.Exec(`UPDATE stickers SET ts=? WHERE hash=?`, row.Ts, hash)
	c.decorate(row)
	go func() {
		out := proto.Clone(sm).(*waE2E.StickerMessage)
		// Re-upload from the local copy when there is one, so the sticker
		// still arrives even if the original upload has expired.
		if path, derr := c.DownloadSticker(hash); derr == nil {
			if f, oerr := os.Open(path); oerr == nil {
				tmp, terr := os.CreateTemp(c.tmpDir(), "up-*")
				if terr == nil {
					ctx, cancel := context.WithTimeout(c.ctx, 2*time.Minute)
					up, uerr := c.cli.UploadReader(ctx, f, tmp, whatsmeow.MediaImage)
					cancel()
					if uerr == nil {
						out.URL, out.DirectPath = proto.String(up.URL), proto.String(up.DirectPath)
						out.MediaKey, out.FileEncSHA256, out.FileSHA256 = up.MediaKey, up.FileEncSHA256, up.FileSHA256
						out.FileLength = proto.Uint64(up.FileLength)
					}
					_ = tmp.Close()
					_ = os.Remove(tmp.Name())
				}
				_ = f.Close()
			}
			_, _ = c.db.Exec(`UPDATE messages SET local=? WHERE chat=? AND id=?`, path, row.Chat, row.ID)
		}
		if row.QuotedID != "" {
			if q := c.loadQuoteSource(chat, row.QuotedID); q != nil {
				out.ContextInfo = q.ctx
			}
		}
		msg := &waE2E.Message{StickerMessage: out}
		row.Raw, _ = proto.Marshal(msg)
		_, _ = c.db.Exec(`UPDATE messages SET raw=? WHERE chat=? AND id=?`, row.Raw, row.Chat, row.ID)
		c.deliver(jid, msg, row)
	}()
	b, _ := json.Marshal(row)
	return string(b), nil
}

// SendAlbum starts an album of photos and videos, so they arrive grouped
// like WhatsApp's own. Send each item afterwards with albumParent set to the
// returned ID.
func (c *Client) SendAlbum(chat string, images, videos int) (string, error) {
	jid, err := parseChat(chat)
	if err != nil {
		return "", err
	}
	ctx, cancel := context.WithTimeout(c.ctx, 30*time.Second)
	defer cancel()
	id := c.cli.GenerateMessageID()
	msg := &waE2E.Message{AlbumMessage: &waE2E.AlbumMessage{
		ExpectedImageCount: proto.Uint32(uint32(images)),
		ExpectedVideoCount: proto.Uint32(uint32(videos)),
	}}
	if _, err := c.cli.SendMessage(ctx, jid, msg, whatsmeow.SendRequestExtra{ID: id}); err != nil {
		return "", err
	}
	return id, nil
}
