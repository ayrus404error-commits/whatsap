module glasschat/bridge

go 1.27

require (
	github.com/mattn/go-sqlite3 v1.14.52
	github.com/skip2/go-qrcode v0.0.0
	go.mau.fi/whatsmeow v0.0.0
	golang.org/x/mobile v0.0.0
	google.golang.org/protobuf v1.36.12
)

require (
	filippo.io/edwards25519 v1.2.0 // indirect
	github.com/beeper/argo-go v1.1.2 // indirect
	github.com/coder/websocket v1.8.15 // indirect
	github.com/elliotchance/orderedmap/v3 v3.1.0 // indirect
	github.com/google/uuid v1.6.0 // indirect
	github.com/mattn/go-colorable v0.1.14 // indirect
	github.com/mattn/go-isatty v0.0.20 // indirect
	github.com/petermattis/goid v0.0.0-20260918085751-abfca077860b // indirect
	github.com/rs/zerolog v1.35.1 // indirect
	github.com/vektah/gqlparser/v2 v2.5.27 // indirect
	go.mau.fi/libsignal v0.2.2 // indirect
	go.mau.fi/util v0.11.0 // indirect
	golang.org/x/crypto v0.58.0 // indirect
	golang.org/x/exp v0.0.0-20261014143641-f0251eb4aa3e // indirect
	golang.org/x/net v0.61.0 // indirect
	golang.org/x/sync v0.24.0 // indirect
	golang.org/x/sys v0.49.0 // indirect
	golang.org/x/text v0.43.0 // indirect
)

replace (
	filippo.io/edwards25519 => ../deps/mods/filippo.io/edwards25519
	github.com/beeper/argo-go => ../deps/mods/github.com/beeper/argo-go
	github.com/coder/websocket => ../deps/mods/github.com/coder/websocket
	github.com/elliotchance/orderedmap/v3 => ../deps/mods/github.com/elliotchance/orderedmap/v3
	github.com/google/uuid => ../deps/mods/github.com/google/uuid
	github.com/mattn/go-colorable => ../deps/mods/github.com/mattn/go-colorable
	github.com/mattn/go-isatty => ../deps/mods/github.com/mattn/go-isatty
	github.com/mattn/go-sqlite3 => ../deps/mods/github.com/mattn/go-sqlite3
	github.com/petermattis/goid => ../deps/mods/github.com/petermattis/goid
	github.com/rs/zerolog => ../deps/mods/github.com/rs/zerolog
	github.com/skip2/go-qrcode => ../deps/mods/github.com/skip2/go-qrcode
	github.com/vektah/gqlparser/v2 => ../deps/mods/github.com/vektah/gqlparser
	go.mau.fi/libsignal => ../deps/mods/go.mau.fi/libsignal
	go.mau.fi/util => ../deps/mods/go.mau.fi/util
	go.mau.fi/whatsmeow => ../deps/mods/go.mau.fi/whatsmeow
	golang.org/x/crypto => ../deps/mods/golang.org/x/crypto
	golang.org/x/exp => ../deps/mods/golang.org/x/exp
	golang.org/x/mobile => ../deps/mods/golang.org/x/mobile
	golang.org/x/mod => ../deps/mods/golang.org/x/mod
	golang.org/x/net => ../deps/mods/golang.org/x/net
	golang.org/x/sync => ../deps/mods/golang.org/x/sync
	golang.org/x/sys => ../deps/mods/golang.org/x/sys
	golang.org/x/text => ../deps/mods/golang.org/x/text
	golang.org/x/tools => ../deps/mods/golang.org/x/tools
	google.golang.org/protobuf => ../deps/mods/google.golang.org/protobuf
)
