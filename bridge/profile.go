package wa

import (
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"net/http"
	"os"
	"strings"
	"time"

	"go.mau.fi/whatsmeow"
	"go.mau.fi/whatsmeow/appstate"
	waBinary "go.mau.fi/whatsmeow/binary"
	"go.mau.fi/whatsmeow/types"
)

func (c *Client) myJID() (types.JID, bool) {
	if c.cli == nil || c.cli.Store == nil || c.cli.Store.ID == nil {
		return types.EmptyJID, false
	}
	return c.cli.Store.ID.ToNonAD(), true
}

func (c *Client) fullAvatarPath(jid string) string {
	return strings.TrimSuffix(c.avatarPath(jid), ".jpg") + "_full.jpg"
}

// SetProfilePhoto changes the account's profile photo (shown everywhere,
// including on the phone) to the given square JPEG. An empty path removes it.
func (c *Client) SetProfilePhoto(path string) error {
	me, ok := c.myJID()
	if !ok {
		return errors.New("not linked")
	}
	var data []byte
	if path != "" {
		b, err := os.ReadFile(path)
		if err != nil {
			return err
		}
		data = b
	}
	ctx, cancel := context.WithTimeout(c.ctx, 60*time.Second)
	defer cancel()
	// No target = the account's own photo.
	if _, err := c.cli.SetGroupPhoto(ctx, types.EmptyJID, data); err != nil {
		if errors.Is(err, whatsmeow.ErrInvalidImageFormat) {
			return errors.New("WhatsApp didn't accept this photo; try another one")
		}
		return err
	}
	c.dropAvatar(me.String())
	_ = os.Remove(c.fullAvatarPath(me.String()))
	c.emit("avatar", me.String())
	return nil
}

// SetPushName changes the account's profile name.
func (c *Client) SetPushName(name string) error {
	name = strings.TrimSpace(name)
	if name == "" {
		return errors.New("the name can't be empty")
	}
	if len([]rune(name)) > 25 {
		return errors.New("the name can be at most 25 characters")
	}
	if c.cli.Store == nil || c.cli.Store.AppState == nil || c.cli.Store.AppStateKeys == nil {
		return errors.New("not ready yet, try again in a moment")
	}
	ctx, cancel := context.WithTimeout(c.ctx, 60*time.Second)
	defer cancel()
	if err := c.cli.SendAppState(ctx, appstate.BuildSettingPushName(name)); err != nil {
		return err
	}
	c.cli.Store.PushName = name
	_ = c.cli.Store.Save(ctx)
	// Presence carries the name to contacts.
	_ = c.cli.SendPresence(ctx, types.PresenceAvailable)
	return nil
}

// SetAbout changes the account's "About" text. WhatsApp has moved this
// between a GraphQL call and an older IQ over time, and accounts differ, so
// each known form is tried in turn.
func (c *Client) SetAbout(text string) error {
	text = strings.TrimSpace(text)
	if text == "" {
		return errors.New("About can't be empty")
	}
	if len([]rune(text)) > 139 {
		return errors.New("About can be at most 139 characters")
	}
	ctx, cancel := context.WithTimeout(c.ctx, 45*time.Second)
	defer cancel()
	const query = "9152604461510864"
	attempts := []func() error{
		// Just the text (newer apps leave out the timer when it isn't used).
		func() error {
			_, err := c.cli.DangerousInternals().SendMexIQ(ctx, query, map[string]any{"input": map[string]any{"text": text}})
			return err
		},
		func() error { return c.cli.SetStatusMessage(ctx, types.SetStatusInput{Text: &text}) },
		func() error {
			_, err := c.cli.DangerousInternals().SendIQ(ctx, whatsmeow.DangerousInfoQuery{
				Namespace: "status",
				Type:      whatsmeow.DangerousInfoQueryType("set"),
				To:        types.ServerJID,
				Content:   []waBinary.Node{{Tag: "status", Content: []byte(text)}},
			})
			return err
		},
	}
	var errs []string
	for _, try := range attempts {
		err := try()
		if err == nil {
			return nil
		}
		errs = append(errs, err.Error())
	}
	c.log.Warnf("set about failed: %s", strings.Join(errs, " | "))
	return errors.New("WhatsApp didn't accept the change from a linked device (" + errs[0] + "). Change About on your phone; it shows here too")
}

// MyProfile returns {"about": "..."} for the account (blocks; network).
func (c *Client) MyProfile() string {
	out := map[string]any{}
	if me, ok := c.myJID(); ok && c.cli.IsConnected() {
		ctx, cancel := context.WithTimeout(c.ctx, 15*time.Second)
		defer cancel()
		if info, err := c.cli.GetUserInfo(ctx, []types.JID{me}); err == nil {
			if u, ok := info[me]; ok {
				out["about"] = u.Status
			}
		}
	}
	b, _ := json.Marshal(out)
	return string(b)
}

// UserAbout returns someone's About text ("" if hidden or unknown).
func (c *Client) UserAbout(jid string) string {
	j, err := types.ParseJID(jid)
	if err != nil || j.Server == types.GroupServer || !c.cli.IsConnected() {
		return ""
	}
	ctx, cancel := context.WithTimeout(c.ctx, 10*time.Second)
	defer cancel()
	info, err := c.cli.GetUserInfo(ctx, []types.JID{j.ToNonAD()})
	if err != nil {
		return ""
	}
	for _, u := range info {
		return u.Status
	}
	return ""
}

// GetAvatarFull is GetAvatar at full resolution, for the big profile header.
func (c *Client) GetAvatarFull(jid string) string {
	p := c.fullAvatarPath(jid)
	if st, err := os.Stat(p); err == nil && time.Since(st.ModTime()) < avatarMaxAge {
		return p
	}
	j, err := types.ParseJID(jid)
	if err != nil || !c.cli.IsConnected() {
		if _, serr := os.Stat(p); serr == nil {
			return p
		}
		return ""
	}
	ctx, cancel := context.WithTimeout(c.ctx, 30*time.Second)
	defer cancel()
	info, err := c.cli.GetProfilePictureInfo(ctx, j, &whatsmeow.GetProfilePictureParams{Preview: false})
	if err != nil || info == nil {
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
	_, err = io.Copy(f, io.LimitReader(resp.Body, 8<<20))
	_ = f.Close()
	if err != nil || os.Rename(tmp, p) != nil {
		_ = os.Remove(tmp)
		return ""
	}
	return p
}

// StatusPrivacy returns who your status updates go to, as set in WhatsApp:
// {"type":"contacts"|"blacklist"|"whitelist","count":N}.
func (c *Client) StatusPrivacy() (string, error) {
	ctx, cancel := context.WithTimeout(c.ctx, 15*time.Second)
	defer cancel()
	list, err := c.cli.GetStatusPrivacy(ctx)
	if err != nil {
		return "", err
	}
	out := map[string]any{"type": "contacts", "count": 0}
	lists := map[string][]string{}
	for _, l := range list {
		ids := make([]string, 0, len(l.List))
		for _, j := range l.List {
			ids = append(ids, c.canonical(j).String())
		}
		lists[string(l.Type)] = ids
	}
	if len(list) > 0 {
		out["type"] = string(list[0].Type)
		out["count"] = len(list[0].List)
	}
	out["lists"] = lists
	b, _ := json.Marshal(out)
	return string(b), nil
}

// SetStatusPrivacy changes who sees your status updates. kind is "contacts",
// "blacklist" (my contacts except…) or "whitelist" (only share with…);
// listJSON is a JSON array of people's JIDs for the last two.
func (c *Client) SetStatusPrivacy(kind, listJSON string) error {
	switch kind {
	case "contacts", "blacklist", "whitelist":
	default:
		return errors.New("unknown status privacy option")
	}
	var ids []string
	_ = json.Unmarshal([]byte(listJSON), &ids)
	var users []waBinary.Node
	if kind != "contacts" {
		for _, id := range ids {
			j, err := types.ParseJID(id)
			if err != nil || j.Server == types.GroupServer {
				continue
			}
			users = append(users, waBinary.Node{Tag: "user", Attrs: waBinary.Attrs{"jid": j.ToNonAD()}})
		}
		if kind == "whitelist" && len(users) == 0 {
			return errors.New("pick at least one person")
		}
	}
	list := waBinary.Node{Tag: "list", Attrs: waBinary.Attrs{"type": kind}}
	if len(users) > 0 {
		list.Content = users
	}
	ctx, cancel := context.WithTimeout(c.ctx, 20*time.Second)
	defer cancel()
	_, err := c.cli.DangerousInternals().SendIQ(ctx, whatsmeow.DangerousInfoQuery{
		Namespace: "status",
		Type:      whatsmeow.DangerousInfoQueryType("set"),
		To:        types.ServerJID,
		Content:   []waBinary.Node{{Tag: "privacy", Content: []waBinary.Node{list}}},
	})
	if err != nil {
		return fmt.Errorf("WhatsApp didn't accept the change (%v). Change it on your phone: Updates → ⋮ → Status privacy", err)
	}
	return nil
}
