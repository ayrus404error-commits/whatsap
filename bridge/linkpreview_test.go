package wa

import (
	"bytes"
	"context"
	"image"
	"image/png"
	"net/http"
	"net/http/httptest"
	"testing"
)

func TestFetchPreview(t *testing.T) {
	var img bytes.Buffer
	_ = png.Encode(&img, image.NewRGBA(image.Rect(0, 0, 800, 400)))
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.URL.Path == "/pic.png" {
			_, _ = w.Write(img.Bytes())
			return
		}
		_, _ = w.Write([]byte(`<html><head><meta property="og:title" content="Sikkim &amp; Travel"><meta name="description" content="A fair"><meta property="og:image" content="/pic.png"></head></html>`))
	}))
	defer srv.Close()
	c := &Client{http: srv.Client()}
	p := c.fetchPreview(context.Background(), srv.URL+"/page")
	if p == nil || p.Title != "Sikkim & Travel" || p.Description != "A fair" || len(p.Thumb) == 0 {
		t.Fatalf("bad preview: %+v", p)
	}
	if got := firstURL("see www.example.com/x."); got != "https://www.example.com/x" {
		t.Fatalf("firstURL: %q", got)
	}
}
