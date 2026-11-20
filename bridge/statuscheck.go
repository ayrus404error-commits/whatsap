package wa

import (
	"context"
	"fmt"
	"os"
	"sort"
	"strings"
	"sync"
	"sync/atomic"
	"time"

	"go.mau.fi/whatsmeow/proto/waE2E"
	"go.mau.fi/whatsmeow/types"
	"google.golang.org/protobuf/reflect/protoreflect"
)

// A short in-memory trail of what happened to each status that reached
// this device, for Settings → Status check.
// It is kept in a small file so it survives the app being closed.
var statusTrail struct {
	sync.Mutex
	lines []string
	path  string
}

func openTrail(path string) {
	statusTrail.Lock()
	defer statusTrail.Unlock()
	statusTrail.path = path
	if b, err := os.ReadFile(path); err == nil {
		for _, l := range strings.Split(strings.TrimSpace(string(b)), "\n") {
			if l != "" {
				statusTrail.lines = append(statusTrail.lines, l)
			}
		}
	}
}

func trailf(format string, args ...any) {
	line := time.Now().Format("01-02 15:04:05 ") + strings.ReplaceAll(fmt.Sprintf(format, args...), "\n", " ")
	statusTrail.Lock()
	defer statusTrail.Unlock()
	statusTrail.lines = append(statusTrail.lines, line)
	if n := len(statusTrail.lines); n > 400 {
		statusTrail.lines = statusTrail.lines[n-400:]
	}
	if statusTrail.path != "" {
		_ = os.WriteFile(statusTrail.path, []byte(strings.Join(statusTrail.lines, "\n")+"\n"), 0o600)
	}
}

func fieldNames(m *waE2E.Message) string {
	var names []string
	if m != nil {
		m.ProtoReflect().Range(func(fd protoreflect.FieldDescriptor, _ protoreflect.Value) bool {
			names = append(names, string(fd.Name()))
			return true
		})
	}
	return strings.Join(names, ",")
}

// StatusCheck explains what this device has of the last day's statuses:
// who posted, what arrived, what is still waiting, and whether each
// person's ring can be shown on a chat.
func (c *Client) StatusCheck() string {
	var b strings.Builder
	now := time.Now()
	rows, err := c.db.Query(`SELECT sender, type, read_sent FROM messages WHERE chat=? AND from_me=0 AND revoked=0 AND ts>?`,
		statusChat, now.Add(-statusLife).UnixMilli())
	type agg struct{ total, unseen, waiting, other int }
	per := map[string]*agg{}
	if err == nil {
		for rows.Next() {
			var s, typ string
			var seen int
			if rows.Scan(&s, &typ, &seen) != nil {
				continue
			}
			if j, err := types.ParseJID(s); err == nil {
				s = c.resolveLID(j).String()
			}
			a := per[s]
			if a == nil {
				a = &agg{}
				per[s] = a
			}
			a.total++
			if seen == 0 {
				a.unseen++
			}
			switch typ {
			case "waiting":
				a.waiting++
			case "other":
				a.other++
			}
		}
		rows.Close()
	}
	keys := make([]string, 0, len(per))
	for k := range per {
		keys = append(keys, k)
	}
	sort.Strings(keys)
	fmt.Fprintf(&b, "STATUSES ON THIS DEVICE (last 24 h): %d people\n", len(keys))
	b.WriteString("name | updates | unseen | waiting | can't draw | chat for ring\n")
	for _, k := range keys {
		a := per[k]
		name := k
		if j, err := types.ParseJID(k); err == nil {
			name = strings.TrimPrefix(c.resolveName(j).Title, "~ ")
		}
		var n int
		_ = c.db.QueryRow(`SELECT COUNT(*) FROM chats WHERE jid=?`, k).Scan(&n)
		chat := "yes"
		if n == 0 {
			chat = "NO CHAT"
		}
		fmt.Fprintf(&b, "%s (%s) | %d | %d | %d | %d | %s\n", name, k, a.total, a.unseen, a.waiting, a.other, chat)
	}
	b.WriteString("\nCONNECTION AND STATUS TRAIL (newest last):\n")
	statusTrail.Lock()
	lines := append([]string(nil), statusTrail.lines...)
	statusTrail.Unlock()
	if len(lines) == 0 {
		b.WriteString("Nothing yet. Leave the app running for a few hours, then check again.\n")
	}
	for _, l := range lines {
		b.WriteString(l)
		b.WriteByte('\n')
	}
	return b.String()
}

var lastStatusAsk atomic.Int64

// RequestStatusSync asks your phone to send this device the latest status
// updates, the same way they arrive when you link the device again. WhatsApp
// doesn't always push every status to linked devices live, so the app asks
// on connect, every 15 minutes and when Updates is opened. The phone needs
// to be online to answer. force skips the 2-minute spacing.
func (c *Client) RequestStatusSync(force bool) {
	if c.cli == nil || !c.cli.IsLoggedIn() || c.cli.Store.ID == nil {
		return
	}
	now := time.Now()
	if !force && now.Unix()-lastStatusAsk.Load() < 120 {
		return
	}
	lastStatusAsk.Store(now.Unix())
	go func() {
		ctx, cancel := context.WithTimeout(c.ctx, 30*time.Second)
		defer cancel()
		// "Messages before now" in the status chat = the latest statuses.
		anchor := &types.MessageInfo{
			MessageSource: types.MessageSource{Chat: types.StatusBroadcastJID},
			ID:            c.cli.GenerateMessageID(),
			Timestamp:     now.Add(time.Minute),
		}
		_, err := c.cli.SendPeerMessage(ctx, c.cli.BuildHistorySyncRequest(anchor, 100))
		if err != nil {
			trailf("asked phone for statuses: failed (%v)", err)
			return
		}
		trailf("asked phone for latest statuses")
	}()
}

// statusPoller asks for statuses regularly while connected.
func (c *Client) statusPoller() {
	t := time.NewTicker(15 * time.Minute)
	defer t.Stop()
	for {
		select {
		case <-c.ctx.Done():
			return
		case <-t.C:
			if c.cli.IsConnected() {
				c.RequestStatusSync(false)
			}
		}
	}
}
