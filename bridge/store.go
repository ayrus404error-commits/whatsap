package wa

import (
	"database/sql"
	"strings"

	_ "github.com/mattn/go-sqlite3"
	"go.mau.fi/whatsmeow/proto/waE2E"
	"google.golang.org/protobuf/proto"
)

// The app keeps its own chat/message tables (whatsmeow only stores crypto
// state and contacts). Everything the UI shows comes from here.

const schema = `
CREATE TABLE IF NOT EXISTS chats (
	jid         TEXT PRIMARY KEY,
	name        TEXT NOT NULL DEFAULT '',
	is_group    INTEGER NOT NULL DEFAULT 0,
	last_ts     INTEGER NOT NULL DEFAULT 0,
	unread      INTEGER NOT NULL DEFAULT 0,
	marked_unread INTEGER NOT NULL DEFAULT 0,
	pinned      INTEGER NOT NULL DEFAULT 0,
	archived    INTEGER NOT NULL DEFAULT 0,
	muted_until INTEGER NOT NULL DEFAULT 0,
	participants INTEGER NOT NULL DEFAULT 0,
	group_fetched INTEGER NOT NULL DEFAULT 0,
	raw_jid     TEXT NOT NULL DEFAULT ''
);
CREATE TABLE IF NOT EXISTS messages (
	chat        TEXT NOT NULL,
	id          TEXT NOT NULL,
	sender      TEXT NOT NULL DEFAULT '',
	raw_chat    TEXT NOT NULL DEFAULT '',
	raw_sender  TEXT NOT NULL DEFAULT '',
	from_me     INTEGER NOT NULL DEFAULT 0,
	ts          INTEGER NOT NULL,
	type        TEXT NOT NULL,
	text        TEXT NOT NULL DEFAULT '',
	extra       TEXT NOT NULL DEFAULT '',
	status      INTEGER NOT NULL DEFAULT 0,
	quoted_id   TEXT NOT NULL DEFAULT '',
	quoted_sender TEXT NOT NULL DEFAULT '',
	quoted_text TEXT NOT NULL DEFAULT '',
	quoted_type TEXT NOT NULL DEFAULT '',
	edited      INTEGER NOT NULL DEFAULT 0,
	revoked     INTEGER NOT NULL DEFAULT 0,
	read_sent   INTEGER NOT NULL DEFAULT 0,
	raw         BLOB,
	PRIMARY KEY (chat, id)
);
CREATE INDEX IF NOT EXISTS messages_chat_ts ON messages(chat, ts);
CREATE TABLE IF NOT EXISTS poll_votes (
	chat    TEXT NOT NULL,
	poll_id TEXT NOT NULL,
	voter   TEXT NOT NULL,
	options TEXT NOT NULL DEFAULT '[]',
	ts      INTEGER NOT NULL DEFAULT 0,
	PRIMARY KEY (chat, poll_id, voter)
);
CREATE TABLE IF NOT EXISTS stickers (
	hash     TEXT PRIMARY KEY,
	raw      BLOB NOT NULL,
	ts       INTEGER NOT NULL DEFAULT 0,
	fav      INTEGER NOT NULL DEFAULT 0,
	animated INTEGER NOT NULL DEFAULT 0
);
CREATE TABLE IF NOT EXISTS reactions (
	chat    TEXT NOT NULL,
	msg_id  TEXT NOT NULL,
	sender  TEXT NOT NULL,
	emoji   TEXT NOT NULL,
	ts      INTEGER NOT NULL,
	PRIMARY KEY (chat, msg_id, sender)
);
CREATE TABLE IF NOT EXISTS group_info (
	jid  TEXT PRIMARY KEY,
	data TEXT NOT NULL,
	ts   INTEGER NOT NULL
);
CREATE TABLE IF NOT EXISTS blocked (
	jid TEXT PRIMARY KEY
);
CREATE TABLE IF NOT EXISTS pinned_msgs (
	chat   TEXT NOT NULL,
	msg_id TEXT NOT NULL,
	until  INTEGER NOT NULL DEFAULT 0,
	ts     INTEGER NOT NULL DEFAULT 0,
	PRIMARY KEY (chat, msg_id)
);
CREATE TABLE IF NOT EXISTS receipts (
	chat    TEXT NOT NULL,
	msg_id  TEXT NOT NULL,
	user    TEXT NOT NULL,
	status  INTEGER NOT NULL,
	PRIMARY KEY (chat, msg_id, user)
);
`

func openAppDB(path string) (*sql.DB, error) {
	db, err := sql.Open("sqlite3", "file:"+path+"?_journal_mode=WAL&_busy_timeout=10000&_synchronous=NORMAL")
	if err != nil {
		return nil, err
	}
	// One connection keeps SQLite simple and avoids "database is locked".
	db.SetMaxOpenConns(1)
	if _, err = db.Exec(schema); err != nil {
		_ = db.Close()
		return nil, err
	}
	// Columns added after the first release.
	for _, q := range []string{
		`ALTER TABLE messages ADD COLUMN local TEXT NOT NULL DEFAULT ''`,
		`ALTER TABLE messages ADD COLUMN starred INTEGER NOT NULL DEFAULT 0`,
		`ALTER TABLE messages ADD COLUMN kept INTEGER NOT NULL DEFAULT 0`,
		`ALTER TABLE chats ADD COLUMN ephemeral INTEGER NOT NULL DEFAULT 0`,
		`ALTER TABLE receipts ADD COLUMN ts INTEGER NOT NULL DEFAULT 0`,
		`ALTER TABLE messages ADD COLUMN mentions TEXT NOT NULL DEFAULT ''`,
		`ALTER TABLE chats ADD COLUMN react_ts INTEGER NOT NULL DEFAULT 0`,
		`ALTER TABLE chats ADD COLUMN react_text TEXT NOT NULL DEFAULT ''`,
	} {
		if _, err = db.Exec(q); err != nil && !strings.Contains(err.Error(), "duplicate column") {
			_ = db.Close()
			return nil, err
		}
	}
	redescribeBlank(db)
	return db, nil
}

// redescribeBlank re-reads stored text messages that came out empty (business
// templates the old decoder didn't understand), so they show their text now.
func redescribeBlank(db *sql.DB) {
	rows, err := db.Query(`SELECT chat, id, raw FROM messages WHERE type='text' AND text='' AND length(raw) > 0 LIMIT 5000`)
	if err != nil {
		return
	}
	type fix struct{ chat, id, text, extra string }
	var fixes []fix
	for rows.Next() {
		var chat, id string
		var raw []byte
		if rows.Scan(&chat, &id, &raw) != nil {
			continue
		}
		m := &waE2E.Message{}
		if proto.Unmarshal(raw, m) != nil {
			continue
		}
		if typ, text, extra := describe(m); typ == "text" && text != "" {
			fixes = append(fixes, fix{chat, id, text, extra})
		}
	}
	_ = rows.Close()
	for _, f := range fixes {
		_, _ = db.Exec(`UPDATE messages SET text=?, extra=CASE WHEN ?<>'' THEN ? ELSE extra END WHERE chat=? AND id=?`, f.text, f.extra, f.extra, f.chat, f.id)
	}
}

// Message statuses (outgoing): -1 failed, 0 pending (clock), 1 sent, 2 delivered, 3 read, 4 played.
const (
	StatusFailed    = -1
	StatusPending   = 0
	StatusSent      = 1
	StatusDelivered = 2
	StatusRead      = 3
	StatusPlayed    = 4
)

type msgRow struct {
	Chat         string        `json:"chat"`
	ID           string        `json:"id"`
	Sender       string        `json:"sender"`
	SenderName   string        `json:"senderName"`
	RawChat      string        `json:"-"`
	RawSender    string        `json:"-"`
	FromMe       bool          `json:"fromMe"`
	Ts           int64         `json:"ts"`
	Type         string        `json:"type"`
	Text         string        `json:"text"`
	Extra        string        `json:"extra"`
	Status       int           `json:"status"`
	QuotedID     string        `json:"quotedId"`
	QuotedSender string        `json:"quotedSender"`
	QuotedName   string        `json:"quotedName"`
	QuotedText   string        `json:"quotedText"`
	QuotedType   string        `json:"quotedType"`
	Edited       bool          `json:"edited"`
	Revoked      bool          `json:"revoked"`
	Raw          []byte        `json:"-"`
	ReadSent     bool          `json:"-"`
	Local        string        `json:"local"` // downloaded / sent file on this device
	Starred      bool          `json:"starred"`
	Kept         bool          `json:"kept"`
	Poll         *pollOut      `json:"poll,omitempty"`
	Reactions    []reactionAgg `json:"reactions"`
	// Mentions: JSON list of {"t":"@Name","me":bool} found in Text.
	Mentions string `json:"mentions"`
}

type reactionAgg struct {
	Emoji string `json:"emoji"`
	Count int    `json:"count"`
	Mine  bool   `json:"mine"`
}

func b2i(b bool) int {
	if b {
		return 1
	}
	return 0
}

// upsertMessage inserts a message, or refreshes its content if it already
// exists (e.g. a "waiting for this message" placeholder that got decrypted).
// It never lowers an outgoing message's status.
func upsertMessage(db execer, m *msgRow) error {
	_, err := db.Exec(`INSERT INTO messages (chat,id,sender,raw_chat,raw_sender,from_me,ts,type,text,extra,status,quoted_id,quoted_sender,quoted_text,quoted_type,edited,revoked,read_sent,raw,local,mentions)
		VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
		ON CONFLICT(chat,id) DO UPDATE SET
			sender=excluded.sender, raw_chat=excluded.raw_chat, raw_sender=excluded.raw_sender, from_me=excluded.from_me,
			type=CASE WHEN messages.revoked=1 THEN messages.type ELSE excluded.type END,
			text=CASE WHEN messages.revoked=1 OR messages.edited=1 THEN messages.text ELSE excluded.text END,
			extra=CASE WHEN messages.revoked=1 THEN '' ELSE excluded.extra END,
			raw=CASE WHEN messages.revoked=1 THEN NULL ELSE excluded.raw END,
			quoted_id=excluded.quoted_id, quoted_sender=excluded.quoted_sender,
			quoted_text=excluded.quoted_text, quoted_type=excluded.quoted_type,
			local=CASE WHEN excluded.local!='' THEN excluded.local ELSE messages.local END,
			mentions=CASE WHEN messages.edited=1 THEN messages.mentions ELSE excluded.mentions END,
			status=MAX(messages.status, excluded.status),
			read_sent=CASE WHEN messages.chat='status@broadcast' THEN MAX(messages.read_sent, excluded.read_sent) ELSE messages.read_sent END`,
		m.Chat, m.ID, m.Sender, m.RawChat, m.RawSender, b2i(m.FromMe), m.Ts, m.Type, m.Text, m.Extra, m.Status,
		m.QuotedID, m.QuotedSender, m.QuotedText, m.QuotedType, b2i(m.Edited), b2i(m.Revoked), b2i(m.ReadSent || m.FromMe), m.Raw, m.Local, m.Mentions)
	return err
}

type execer interface {
	Exec(query string, args ...any) (sql.Result, error)
}

func ensureChat(db execer, jid string, isGroup bool, rawJID string) {
	_, _ = db.Exec(`INSERT INTO chats (jid,is_group,raw_jid) VALUES (?,?,?) ON CONFLICT(jid) DO UPDATE SET raw_jid=CASE WHEN chats.raw_jid='' THEN excluded.raw_jid ELSE chats.raw_jid END`, jid, b2i(isGroup), rawJID)
}

func bumpChat(db execer, jid string, ts int64, incUnread bool) {
	_, _ = db.Exec(`UPDATE chats SET last_ts=MAX(last_ts, ?), unread=unread+?, archived=CASE WHEN ? THEN 0 ELSE archived END WHERE jid=?`,
		ts, b2i(incUnread), b2i(incUnread), jid)
}

const msgCols = `m.chat,m.id,m.sender,m.raw_chat,m.raw_sender,m.from_me,m.ts,m.type,m.text,m.extra,m.status,m.quoted_id,m.quoted_sender,m.quoted_text,m.quoted_type,m.edited,m.revoked,m.local,m.starred,m.kept,m.mentions`

func scanMsg(sc interface{ Scan(...any) error }) (*msgRow, error) {
	var m msgRow
	var fromMe, edited, revoked, starred, kept int
	err := sc.Scan(&m.Chat, &m.ID, &m.Sender, &m.RawChat, &m.RawSender, &fromMe, &m.Ts, &m.Type, &m.Text, &m.Extra, &m.Status,
		&m.QuotedID, &m.QuotedSender, &m.QuotedText, &m.QuotedType, &edited, &revoked, &m.Local, &starred, &kept, &m.Mentions)
	m.FromMe, m.Edited, m.Revoked = fromMe == 1, edited == 1, revoked == 1
	m.Starred, m.Kept = starred == 1, kept == 1
	return &m, err
}

func loadReactions(db *sql.DB, chat string, ids []string, me string) map[string][]reactionAgg {
	out := map[string][]reactionAgg{}
	if len(ids) == 0 {
		return out
	}
	args := []any{chat}
	for _, id := range ids {
		args = append(args, id)
	}
	rows, err := db.Query(`SELECT msg_id, emoji, sender FROM reactions WHERE chat=? AND emoji!='' AND msg_id IN (?`+strings.Repeat(",?", len(ids)-1)+`) ORDER BY ts`, args...)
	if err != nil {
		return out
	}
	defer rows.Close()
	type key struct{ id, emoji string }
	idx := map[key]int{}
	for rows.Next() {
		var id, emoji, sender string
		if rows.Scan(&id, &emoji, &sender) != nil {
			continue
		}
		k := key{id, emoji}
		if i, ok := idx[k]; ok {
			out[id][i].Count++
			if sender == me {
				out[id][i].Mine = true
			}
		} else {
			idx[k] = len(out[id])
			out[id] = append(out[id], reactionAgg{Emoji: emoji, Count: 1, Mine: sender == me})
		}
	}
	return out
}
