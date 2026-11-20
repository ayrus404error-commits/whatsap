package wa

import (
	"context"
	"errors"
	"io"
	"net/http"
	"os"
	"path/filepath"
	"strings"
	"time"

	"go.mau.fi/whatsmeow"
	"go.mau.fi/whatsmeow/types"
)

const avatarMaxAge = 72 * time.Hour

func (c *Client) avatarPath(jid string) string {
	safe := strings.NewReplacer("@", "_", ":", "_", "/", "_").Replace(jid)
	return filepath.Join(c.dataDir, "avatars", safe+".jpg")
}

func (c *Client) dropAvatar(jid string) {
	p := c.avatarPath(jid)
	_ = os.Remove(p)
	_ = os.Remove(p + ".none")
}

// GetAvatar returns a local file path to the chat's profile photo (small
// preview), downloading it if needed, or "" if there is none. It blocks, so
// call it off the main thread.
func (c *Client) GetAvatar(jid string) string {
	p := c.avatarPath(jid)
	if st, err := os.Stat(p); err == nil && time.Since(st.ModTime()) < avatarMaxAge {
		return p
	}
	if st, err := os.Stat(p + ".none"); err == nil && time.Since(st.ModTime()) < avatarMaxAge {
		return ""
	}
	if !c.cli.IsConnected() {
		if _, err := os.Stat(p); err == nil {
			return p
		}
		return ""
	}
	j, err := types.ParseJID(jid)
	if err != nil {
		return ""
	}
	select {
	case c.avatarSem <- struct{}{}:
	case <-c.ctx.Done():
		return ""
	}
	defer func() { <-c.avatarSem }()

	ctx, cancel := context.WithTimeout(c.ctx, 20*time.Second)
	defer cancel()
	info, err := c.cli.GetProfilePictureInfo(ctx, j, &whatsmeow.GetProfilePictureParams{Preview: true})
	if errors.Is(err, whatsmeow.ErrProfilePictureNotSet) || errors.Is(err, whatsmeow.ErrProfilePictureUnauthorized) || (err == nil && info == nil) {
		_ = os.Remove(p)
		_ = os.WriteFile(p+".none", nil, 0o600)
		return ""
	}
	if err != nil {
		c.log.Warnf("avatar %s: %v", jid, err)
		if _, serr := os.Stat(p); serr == nil {
			return p
		}
		return ""
	}
	req, _ := http.NewRequestWithContext(ctx, http.MethodGet, info.URL, nil)
	resp, err := c.http.Do(req)
	if err != nil {
		return ""
	}
	defer resp.Body.Close()
	if resp.StatusCode != http.StatusOK {
		return ""
	}
	tmp := p + ".tmp"
	f, err := os.Create(tmp)
	if err != nil {
		return ""
	}
	_, err = io.Copy(f, io.LimitReader(resp.Body, 2<<20))
	_ = f.Close()
	if err != nil {
		_ = os.Remove(tmp)
		return ""
	}
	_ = os.Remove(p + ".none")
	if os.Rename(tmp, p) != nil {
		return ""
	}
	return p
}
