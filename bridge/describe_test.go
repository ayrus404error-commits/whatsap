package wa

import (
	"strings"
	"testing"

	"go.mau.fi/whatsmeow/proto/waE2E"
	"google.golang.org/protobuf/proto"
)

func TestDescribeBusinessTemplate(t *testing.T) {
	m := &waE2E.Message{TemplateMessage: &waE2E.TemplateMessage{
		Format: &waE2E.TemplateMessage_HydratedFourRowTemplate_{HydratedFourRowTemplate: &waE2E.TemplateMessage_HydratedFourRowTemplate{
			HydratedContentText: proto.String("Your bill of ₹500 is due"),
			HydratedFooterText:  proto.String("Pay early to avoid any late payment fees"),
			HydratedButtons: []*waE2E.HydratedTemplateButton{{
				HydratedButton: &waE2E.HydratedTemplateButton_UrlButton{UrlButton: &waE2E.HydratedTemplateButton_HydratedURLButton{
					DisplayText: proto.String("Pay now"), URL: proto.String("https://example.com/pay"),
				}},
			}},
		}},
	}}
	typ, text, extra := describe(m)
	if typ != "text" || !strings.Contains(text, "₹500") || !strings.Contains(text, "late payment") {
		t.Fatalf("got %q %q", typ, text)
	}
	if !strings.Contains(extra, "Pay now") || !strings.Contains(extra, "https://example.com/pay") {
		t.Fatalf("buttons missing: %s", extra)
	}
	im := &waE2E.Message{InteractiveMessage: &waE2E.InteractiveMessage{
		Body: &waE2E.InteractiveMessage_Body{Text: proto.String("Hello")},
		InteractiveMessage: &waE2E.InteractiveMessage_NativeFlowMessage_{NativeFlowMessage: &waE2E.InteractiveMessage_NativeFlowMessage{
			Buttons: []*waE2E.InteractiveMessage_NativeFlowMessage_NativeFlowButton{{
				Name: proto.String("cta_url"), ButtonParamsJSON: proto.String(`{"display_text":"Open","url":"https://x.y"}`),
			}},
		}},
	}}
	if _, text, extra := describe(im); text != "Hello" || !strings.Contains(extra, "https://x.y") {
		t.Fatalf("interactive: %q %s", text, extra)
	}
}
