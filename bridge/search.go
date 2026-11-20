package wa

import (
	"context"
	"encoding/json"
	"errors"
	"sort"
	"strings"
	"time"

	"go.mau.fi/whatsmeow/types"
)

// ---------------------------------------------------------------------------
// Search inside one chat

// searchKinds narrows a chat search to one kind of message.
var searchKinds = map[string]string{
	"":       "",
	"all":    "",
	"text":   `AND m.type='text'`,
	"media":  `AND m.type IN ('image','video','gif')`,
	"photos": `AND m.type='image'`,
	"videos": `AND m.type IN ('video','gif')`,
	"docs":   `AND m.type='document'`,
	"links":  `AND m.type='text' AND (m.text LIKE '%http://%' OR m.text LIKE '%https://%' OR m.text LIKE '%www.%')`,
	"audio":  `AND m.type IN ('voice','audio')`,
	"places": `AND m.type IN ('location','contact','poll')`,
}

// likePattern turns user text into a LIKE pattern matching it anywhere.
func likePattern(q string) string {
	r := strings.NewReplacer(`\`, `\\`, `%`, `\%`, `_`, `\_`)
	return "%" + r.Replace(q) + "%"
}

// SearchChat finds messages in one chat whose text, caption, file name,
// contact or place name contains query (case-insensitive for Latin
// letters), newest first. kind is "", "text", "media", "docs", "links",
// "audio" or "places". With an empty query it lists that kind (not "all").
func (c *Client) SearchChat(chat, query, kind string, limit int) string {
	filter, ok := searchKinds[kind]
	if !ok {
		return "[]"
	}
	query = strings.TrimSpace(query)
	if query == "" && filter == "" {
		return "[]"
	}
	if limit <= 0 || limit > 1000 {
		limit = 300
	}
	// An empty chat searches every chat (not status updates).
	where := `m.chat=? AND m.revoked=0 AND m.type NOT IN ('waiting','reaction') ` + filter
	args := []any{chat}
	if chat == "" {
		where = `m.chat!='status@broadcast' AND m.revoked=0 AND m.type NOT IN ('waiting','reaction') ` + filter
		args = nil
	}
	if query != "" {
		p := likePattern(query)
		where += ` AND (m.text LIKE ? ESCAPE '\' OR IFNULL(json_extract(CASE WHEN json_valid(m.extra) THEN m.extra END, '$.fileName'), '') LIKE ? ESCAPE '\')`
		args = append(args, p, p)
	}
	args = append(args, limit)
	rows, err := c.db.Query(`SELECT `+msgCols+` FROM messages m WHERE `+where+` ORDER BY m.ts DESC, m.rowid DESC LIMIT ?`, args...)
	if err != nil {
		c.log.Errorf("SearchChat: %v", err)
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

// ---------------------------------------------------------------------------
// Group details and members

type groupMemberOut struct {
	JID        string `json:"jid"`
	Name       string `json:"name"`
	Phone      string `json:"phone"`
	Admin      bool   `json:"admin"`
	SuperAdmin bool   `json:"superAdmin"`
	IsMe       bool   `json:"isMe"`
	Saved      bool   `json:"saved"`
}

type groupInfoOut struct {
	JID          string           `json:"jid"`
	Name         string           `json:"name"`
	Topic        string           `json:"topic"`
	Created      int64            `json:"created"`
	CreatedBy    string           `json:"createdBy"`
	Announce     bool             `json:"announce"`
	Locked       bool             `json:"locked"`
	Participants []groupMemberOut `json:"participants"`
	Fetched      int64            `json:"fetched"`
	Cached       bool             `json:"cached"`
}

// GetGroupInfo returns a group's name, description and members (with
// names as your phone would show them). With refresh it asks WhatsApp and
// saves the answer; otherwise, or when offline, it returns the saved copy.
func (c *Client) GetGroupInfo(jid string, refresh bool) (string, error) {
	var cached string
	var ts int64
	_ = c.db.QueryRow(`SELECT data, ts FROM group_info WHERE jid=?`, jid).Scan(&cached, &ts)
	if cached != "" && (!refresh || !c.cli.IsConnected()) {
		return c.renameMembers(cached), nil
	}
	if !c.cli.IsConnected() {
		return "", errors.New("not connected")
	}
	gj, err := types.ParseJID(jid)
	if err != nil || gj.Server != types.GroupServer {
		return "", errors.New("not a group")
	}
	ctx, cancel := context.WithTimeout(c.ctx, 20*time.Second)
	defer cancel()
	info, err := c.cli.GetGroupInfo(ctx, gj)
	if err != nil {
		if cached != "" {
			return c.renameMembers(cached), nil
		}
		return "", err
	}
	out := groupInfoOut{
		JID: jid, Name: info.Name, Topic: info.Topic, Announce: info.IsAnnounce, Locked: info.IsLocked,
		Fetched: time.Now().UnixMilli(),
	}
	if !info.GroupCreated.IsZero() {
		out.Created = info.GroupCreated.UnixMilli()
	}
	if !info.OwnerJID.IsEmpty() {
		out.CreatedBy = c.resolveName(c.resolveLID(info.OwnerJID)).Title
	}
	for _, p := range info.Participants {
		who := p.JID
		if !p.PhoneNumber.IsEmpty() {
			who = p.PhoneNumber
		}
		out.Participants = append(out.Participants, groupMemberOut{
			JID: c.resolveLID(who).String(), Admin: p.IsAdmin || p.IsSuperAdmin, SuperAdmin: p.IsSuperAdmin,
			Name: p.DisplayName,
		})
	}
	b, _ := json.Marshal(out)
	_, _ = c.db.Exec(`INSERT INTO group_info (jid, data, ts) VALUES (?,?,?)
		ON CONFLICT(jid) DO UPDATE SET data=excluded.data, ts=excluded.ts`, jid, string(b), out.Fetched)
	if info.Name != "" {
		_, _ = c.db.Exec(`UPDATE chats SET name=?, participants=? WHERE jid=?`, info.Name, len(info.Participants), jid)
	}
	return c.renameMembers(string(b)), nil
}

// renameMembers fills in names as they are now (contacts change) and sorts
// the list like WhatsApp: you first, then admins, then saved contacts, then
// everyone else, each alphabetically.
func (c *Client) renameMembers(data string) string {
	var g groupInfoOut
	if json.Unmarshal([]byte(data), &g) != nil {
		return data
	}
	own := c.ownJID().ToNonAD().User
	for i := range g.Participants {
		p := &g.Participants[i]
		j, err := types.ParseJID(p.JID)
		if err != nil {
			continue
		}
		r := c.resolveName(j)
		p.IsMe = j.User == own
		if p.IsMe {
			p.Name = "You"
		} else if r.Title != "" && r.Title != "WhatsApp user" {
			p.Name = r.Title
			if !r.Saved && r.Sender != "" && strings.HasPrefix(r.Sender, "~ ") {
				p.Name = r.Title + " " + r.Sender
			}
		} else if p.Name == "" {
			p.Name = r.Title
		}
		p.Phone = r.Phone
		p.Saved = r.Saved
	}
	rank := func(p groupMemberOut) int {
		switch {
		case p.IsMe:
			return 0
		case p.Admin:
			return 1
		case p.Saved:
			return 2
		}
		return 3
	}
	sort.SliceStable(g.Participants, func(a, b int) bool {
		pa, pb := g.Participants[a], g.Participants[b]
		if ra, rb := rank(pa), rank(pb); ra != rb {
			return ra < rb
		}
		return strings.ToLower(pa.Name) < strings.ToLower(pb.Name)
	})
	g.Cached = true
	b, _ := json.Marshal(g)
	return string(b)
}
