package wa

import (
	"context"
	"strings"
	"sync"

	"go.mau.fi/whatsmeow/types"
)

// nameCache resolves JIDs to the names WhatsApp would show: the saved contact
// name, otherwise the number, with the sender's own push name as a fallback.
type nameCache struct {
	mu sync.RWMutex
	m  map[string]resolvedName
}

type resolvedName struct {
	Title  string // what to show as a chat title (saved name or number)
	Sender string // what to show above a group message ("~ Push" for unsaved)
	Phone  string // formatted number if known
	Saved  bool
}

func (c *Client) resolveName(jid types.JID) resolvedName {
	key := jid.ToNonAD().String()
	c.names.mu.RLock()
	r, ok := c.names.m[key]
	c.names.mu.RUnlock()
	if ok {
		return r
	}
	r = c.lookupName(jid.ToNonAD())
	c.names.mu.Lock()
	c.names.m[key] = r
	c.names.mu.Unlock()
	return r
}

func (c *Client) invalidateNames() {
	c.names.mu.Lock()
	c.names.m = map[string]resolvedName{}
	c.names.mu.Unlock()
}

func (c *Client) lookupName(jid types.JID) resolvedName {
	ctx := context.Background()
	var r resolvedName
	store := c.cli.Store
	if store == nil || store.Contacts == nil {
		p := formatPhone(jid.User)
		return resolvedName{Title: p, Sender: p, Phone: p}
	}
	pn := jid
	info, _ := store.Contacts.GetContact(ctx, jid)
	if jid.Server == types.HiddenUserServer {
		if mapped, err := store.LIDs.GetPNForLID(ctx, jid); err == nil && !mapped.IsEmpty() {
			pn = mapped
			if !info.Found || info.FullName == "" {
				if pinfo, _ := store.Contacts.GetContact(ctx, mapped); pinfo.Found {
					if info.PushName != "" && pinfo.PushName == "" {
						pinfo.PushName = info.PushName
					}
					info = pinfo
				}
			}
		}
	}
	if pn.Server == types.DefaultUserServer {
		r.Phone = formatPhone(pn.User)
	} else if info.RedactedPhone != "" {
		r.Phone = info.RedactedPhone
	}
	saved := firstNonEmpty(info.FullName, info.FirstName)
	switch {
	case saved != "":
		r.Title, r.Sender, r.Saved = saved, saved, true
	case info.BusinessName != "":
		r.Title, r.Sender = info.BusinessName, info.BusinessName
	case r.Phone != "":
		r.Title = r.Phone
		if info.PushName != "" {
			r.Sender = "~ " + info.PushName
		} else {
			r.Sender = r.Phone
		}
	case info.PushName != "":
		r.Title, r.Sender = info.PushName, "~ "+info.PushName
	default:
		r.Title, r.Sender = "WhatsApp user", "Unknown"
	}
	if own := store.ID; own != nil && (pn.User == own.User || jid.User == store.LID.User) {
		r.Title, r.Sender = "You", "You"
	}
	return r
}

// formatPhone gives "+91 98765 43210" for Indian numbers and "+<digits>" otherwise.
func formatPhone(user string) string {
	if user == "" {
		return ""
	}
	if strings.HasPrefix(user, "91") && len(user) == 12 {
		return "+91 " + user[2:7] + " " + user[7:]
	}
	if strings.HasPrefix(user, "1") && len(user) == 11 {
		return "+1 " + user[1:4] + "-" + user[4:7] + "-" + user[7:]
	}
	return "+" + user
}

// SetContactName records the name you saved for a number in your phone's
// contacts, so the chat shows it right away instead of waiting for WhatsApp
// on your phone to sync the new contact (which can take a long time).
func (c *Client) SetContactName(phone, name string) {
	digits := strings.Map(func(r rune) rune {
		if r >= '0' && r <= '9' {
			return r
		}
		return -1
	}, phone)
	name = strings.TrimSpace(name)
	if digits == "" || name == "" || c.cli == nil || c.cli.Store == nil || c.cli.Store.Contacts == nil {
		return
	}
	jid := types.NewJID(digits, types.DefaultUserServer)
	first := strings.Fields(name)[0]
	if err := c.cli.Store.Contacts.PutContactName(context.Background(), jid, name, first); err != nil {
		c.log.Warnf("save contact name: %v", err)
		return
	}
	c.invalidateNames()
	c.emit("chats_changed", nil)
}
