package wa

import (
	"bytes"
	"context"
	"encoding/json"
	"html"
	"image"
	_ "image/gif"
	"image/jpeg"
	_ "image/png"
	"io"
	"net/http"
	"net/url"
	"regexp"
	"strings"
	"time"

	"go.mau.fi/whatsmeow/proto/waE2E"
	"google.golang.org/protobuf/proto"
)

var urlRe = regexp.MustCompile(`(?i)\b((?:https?://|www\.)[^\s<>"]+)`)

// firstURL finds the first link in a message (without trailing punctuation).
func firstURL(text string) string {
	m := urlRe.FindString(text)
	m = strings.TrimRight(m, ".,!?)]};:'\"")
	if m == "" {
		return ""
	}
	if !strings.HasPrefix(strings.ToLower(m), "http") {
		m = "https://" + m
	}
	return m
}

type linkPreview struct {
	URL, Title, Description string
	Thumb                   []byte
	Video                   bool
}

var metaRe = regexp.MustCompile(`(?is)<meta\s+[^>]*>`)
var attrRe = regexp.MustCompile(`(?is)(property|name|content)\s*=\s*("([^"]*)"|'([^']*)')`)
var titleRe = regexp.MustCompile(`(?is)<title[^>]*>(.*?)</title>`)

// fetchPreview reads a page's title, description and picture (Open Graph
// tags, like WhatsApp does). YouTube links use YouTube's oEmbed.
func (c *Client) fetchPreview(ctx context.Context, link string) *linkPreview {
	u, err := url.Parse(link)
	if err != nil || u.Host == "" {
		return nil
	}
	host := strings.TrimPrefix(strings.ToLower(u.Host), "www.")
	p := &linkPreview{URL: link}
	var imageURL string
	if host == "youtube.com" || host == "m.youtube.com" || host == "youtu.be" || host == "music.youtube.com" {
		var o struct {
			Title        string `json:"title"`
			Author       string `json:"author_name"`
			ThumbnailURL string `json:"thumbnail_url"`
		}
		if b := c.httpGet(ctx, "https://www.youtube.com/oembed?format=json&url="+url.QueryEscape(link), 256<<10); b != nil && json.Unmarshal(b, &o) == nil {
			p.Title, p.Description, imageURL, p.Video = o.Title, o.Author, o.ThumbnailURL, true
		}
	}
	if p.Title == "" {
		page := c.httpGet(ctx, link, 1<<20)
		if page == nil {
			return nil
		}
		meta := map[string]string{}
		for _, tag := range metaRe.FindAllString(string(page), 300) {
			var key, val string
			for _, a := range attrRe.FindAllStringSubmatch(tag, -1) {
				v := a[3] + a[4]
				switch strings.ToLower(a[1]) {
				case "property", "name":
					key = strings.ToLower(v)
				case "content":
					val = v
				}
			}
			if key != "" && val != "" {
				if _, seen := meta[key]; !seen {
					meta[key] = html.UnescapeString(val)
				}
			}
		}
		p.Title = firstNonEmpty(meta["og:title"], meta["twitter:title"])
		if p.Title == "" {
			if m := titleRe.FindSubmatch(page); m != nil {
				p.Title = strings.TrimSpace(html.UnescapeString(string(m[1])))
			}
		}
		p.Description = firstNonEmpty(meta["og:description"], meta["twitter:description"], meta["description"])
		imageURL = firstNonEmpty(meta["og:image"], meta["og:image:url"], meta["twitter:image"])
		p.Video = strings.HasPrefix(meta["og:type"], "video")
	}
	if p.Title == "" && p.Description == "" {
		return nil
	}
	if len([]rune(p.Title)) > 200 {
		p.Title = string([]rune(p.Title)[:200])
	}
	if len([]rune(p.Description)) > 300 {
		p.Description = string([]rune(p.Description)[:300]) + "…"
	}
	if imageURL != "" {
		if iu, err := u.Parse(imageURL); err == nil {
			if b := c.httpGet(ctx, iu.String(), 4<<20); b != nil {
				p.Thumb = thumbnailJPEG(b, 320)
			}
		}
	}
	return p
}

func (c *Client) httpGet(ctx context.Context, link string, limit int64) []byte {
	req, err := http.NewRequestWithContext(ctx, http.MethodGet, link, nil)
	if err != nil {
		return nil
	}
	// Sites (Instagram, Facebook…) give their preview tags to WhatsApp's fetcher.
	req.Header.Set("User-Agent", "WhatsApp/2.24.20.80 A")
	req.Header.Set("Accept-Language", "en")
	resp, err := c.http.Do(req)
	if err != nil {
		return nil
	}
	defer resp.Body.Close()
	if resp.StatusCode != http.StatusOK {
		return nil
	}
	b, err := io.ReadAll(io.LimitReader(resp.Body, limit))
	if err != nil {
		return nil
	}
	return b
}

// thumbnailJPEG shrinks a JPEG/PNG/GIF to at most max px wide, as a small JPEG.
func thumbnailJPEG(data []byte, max int) []byte {
	src, _, err := image.Decode(bytes.NewReader(data))
	if err != nil {
		return nil
	}
	b := src.Bounds()
	w, h := b.Dx(), b.Dy()
	if w == 0 || h == 0 {
		return nil
	}
	nw, nh := w, h
	if w > max {
		nw, nh = max, h*max/w
	}
	if nh > max {
		nw, nh = nw*max/nh, max
	}
	if nw < 1 || nh < 1 {
		return nil
	}
	dst := image.NewRGBA(image.Rect(0, 0, nw, nh))
	for y := 0; y < nh; y++ {
		for x := 0; x < nw; x++ {
			dst.Set(x, y, src.At(b.Min.X+x*w/nw, b.Min.Y+y*h/nh))
		}
	}
	var out bytes.Buffer
	if jpeg.Encode(&out, dst, &jpeg.Options{Quality: 72}) != nil {
		return nil
	}
	return out.Bytes()
}

// addPreview fills a text message with a link preview (when the text has a
// link and the page could be read). Returns true if one was added.
func (c *Client) addPreview(msg *waE2E.Message, row *msgRow) bool {
	text := row.Text
	link := firstURL(text)
	if link == "" {
		return false
	}
	ctx, cancel := context.WithTimeout(c.ctx, 8*time.Second)
	defer cancel()
	p := c.fetchPreview(ctx, link)
	if p == nil {
		return false
	}
	e := msg.GetExtendedTextMessage()
	if e == nil {
		e = &waE2E.ExtendedTextMessage{Text: proto.String(text)}
		msg.ExtendedTextMessage = e
		msg.Conversation = nil
	}
	matched := urlRe.FindString(text)
	e.MatchedText = proto.String(strings.TrimRight(matched, ".,!?)]};:'\""))
	e.Title = optString(p.Title)
	e.Description = optString(p.Description)
	if len(p.Thumb) > 0 {
		e.JPEGThumbnail = p.Thumb
	}
	if p.Video {
		e.PreviewType = waE2E.ExtendedTextMessage_VIDEO.Enum()
	} else {
		e.PreviewType = waE2E.ExtendedTextMessage_NONE.Enum()
	}
	_, _, extra := describe(msg)
	row.Extra = extra
	row.Raw, _ = proto.Marshal(msg)
	_, _ = c.db.Exec(`UPDATE messages SET extra=?, raw=? WHERE chat=? AND id=?`, row.Extra, row.Raw, row.Chat, row.ID)
	if m := c.loadMessage(row.Chat, row.ID); m != nil {
		c.emit("message_update", m)
	}
	return true
}
