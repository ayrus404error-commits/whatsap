package wa

import (
	"strings"

	qrcode "github.com/skip2/go-qrcode"
)

// QrModules encodes a pairing code as a QR matrix: one line per row, '1' for
// a dark module. The UI draws it itself so it can match the theme.
func QrModules(code string) string {
	q, err := qrcode.New(code, qrcode.Low)
	if err != nil {
		return ""
	}
	q.DisableBorder = true
	bm := q.Bitmap()
	var sb strings.Builder
	for i, row := range bm {
		if i > 0 {
			sb.WriteByte('\n')
		}
		for _, on := range row {
			if on {
				sb.WriteByte('1')
			} else {
				sb.WriteByte('0')
			}
		}
	}
	return sb.String()
}
