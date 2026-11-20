package wa

import (
	"fmt"
	"io"
	"os"
	"strings"
	"sync"
	"time"

	waLog "go.mau.fi/whatsmeow/util/log"
)

// fileLogger writes whatsmeow logs to a small rotating file inside the app's
// private storage, so problems can be looked at from the in-app debug screen.
type fileLogger struct {
	mod string
	out *logSink
}

type logSink struct {
	mu   sync.Mutex
	path string
	f    *os.File
	size int64
}

const maxLogSize = 2 << 20 // 2 MB, then the file is rotated to .old

func newLogSink(path string) *logSink {
	s := &logSink{path: path}
	s.open()
	return s
}

func (s *logSink) open() {
	f, err := os.OpenFile(s.path, os.O_CREATE|os.O_WRONLY|os.O_APPEND, 0600)
	if err != nil {
		return
	}
	st, _ := f.Stat()
	s.f = f
	if st != nil {
		s.size = st.Size()
	}
}

func (s *logSink) write(line string) {
	s.mu.Lock()
	defer s.mu.Unlock()
	if s.f == nil {
		return
	}
	if s.size > maxLogSize {
		_ = s.f.Close()
		_ = os.Rename(s.path, s.path+".old")
		s.size = 0
		s.open()
		if s.f == nil {
			return
		}
	}
	n, _ := io.WriteString(s.f, line)
	s.size += int64(n)
}

func (s *logSink) tail(max int64) string {
	s.mu.Lock()
	defer s.mu.Unlock()
	data, err := os.ReadFile(s.path)
	if err != nil {
		return ""
	}
	if int64(len(data)) > max {
		data = data[int64(len(data))-max:]
	}
	return string(data)
}

func (l *fileLogger) outputf(level, msg string, args ...any) {
	text := fmt.Sprintf(msg, args...)
	line := fmt.Sprintf("%s [%s %s] %s\n", time.Now().Format("01-02 15:04:05.000"), l.mod, level, text)
	// Anything the engine says about statuses also goes to Status check.
	if strings.Contains(text, "status@broadcast") && level != "INFO" {
		trailf("engine %s: %s", level, text)
	}
	l.out.write(line)
}

func (l *fileLogger) Errorf(msg string, args ...any) { l.outputf("ERROR", msg, args...) }
func (l *fileLogger) Warnf(msg string, args ...any)  { l.outputf("WARN", msg, args...) }
func (l *fileLogger) Infof(msg string, args ...any)  { l.outputf("INFO", msg, args...) }
func (l *fileLogger) Debugf(string, ...any)          {}
func (l *fileLogger) Sub(mod string) waLog.Logger {
	return &fileLogger{mod: l.mod + "/" + mod, out: l.out}
}
