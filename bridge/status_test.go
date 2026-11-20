package wa

import (
	"testing"

	"go.mau.fi/whatsmeow/proto/waE2E"
	"google.golang.org/protobuf/proto"
)

func TestUnwrapStatus(t *testing.T) {
	img := &waE2E.Message{ImageMessage: &waE2E.ImageMessage{Caption: proto.String("hi")}}
	wrapped := &waE2E.Message{StatusAddYours: &waE2E.FutureProofMessage{Message: &waE2E.Message{
		SpoilerMessage: &waE2E.FutureProofMessage{Message: img}}}}
	if typ, _, _ := describe(wrapped); typ != "" {
		t.Fatalf("expected wrapped to be unknown before unwrap, got %q", typ)
	}
	if typ, text, _ := describe(unwrapStatus(wrapped)); typ != "image" || text != "hi" {
		t.Fatalf("unwrap: got %q %q", typ, text)
	}
	skdm := &waE2E.Message{SenderKeyDistributionMessage: &waE2E.SenderKeyDistributionMessage{GroupID: proto.String("status@broadcast")},
		MessageContextInfo: &waE2E.MessageContextInfo{}}
	if hasStatusContent(skdm) {
		t.Fatal("key distribution counted as an update")
	}
	if !hasStatusContent(&waE2E.Message{QuestionMessage: &waE2E.FutureProofMessage{}}) {
		t.Fatal("question sticker not counted")
	}
}
