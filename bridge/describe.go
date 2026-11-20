package wa

import (
	"encoding/base64"
	"encoding/json"

	"go.mau.fi/whatsmeow/proto/waE2E"
)

// describe turns a WhatsApp message into (type, text, extra-json).
// An empty type means "nothing to show" (protocol noise, poll votes, etc.).
func describe(m *waE2E.Message) (typ, text, extra string) {
	if m == nil {
		return "", "", ""
	}
	ex := map[string]any{}
	thumb := func(b []byte) {
		if len(b) > 0 && len(b) < 64*1024 {
			ex["thumb"] = base64.StdEncoding.EncodeToString(b)
		}
	}
	switch {
	case m.GetConversation() != "":
		typ, text = "text", m.GetConversation()
	case m.GetExtendedTextMessage() != nil:
		e := m.GetExtendedTextMessage()
		typ, text = "text", e.GetText()
		if e.GetMatchedText() != "" {
			ex["link"] = e.GetMatchedText()
			if e.GetTitle() != "" {
				ex["linkTitle"] = e.GetTitle()
			}
			if e.GetDescription() != "" {
				ex["linkDesc"] = e.GetDescription()
			}
			thumb(e.GetJPEGThumbnail())
			if e.GetPreviewType() == waE2E.ExtendedTextMessage_VIDEO {
				ex["linkVideo"] = true
			}
		}
	case m.GetImageMessage() != nil:
		i := m.GetImageMessage()
		typ, text = "image", i.GetCaption()
		ex["w"], ex["h"], ex["mime"] = i.GetWidth(), i.GetHeight(), i.GetMimetype()
		thumb(i.GetJPEGThumbnail())
	case m.GetVideoMessage() != nil:
		v := m.GetVideoMessage()
		typ, text = "video", v.GetCaption()
		if v.GetGifPlayback() {
			typ = "gif"
		}
		ex["w"], ex["h"], ex["seconds"] = v.GetWidth(), v.GetHeight(), v.GetSeconds()
		thumb(v.GetJPEGThumbnail())
	case m.GetPtvMessage() != nil:
		v := m.GetPtvMessage()
		typ = "video"
		ex["seconds"], ex["round"] = v.GetSeconds(), true
		thumb(v.GetJPEGThumbnail())
	case m.GetAudioMessage() != nil:
		a := m.GetAudioMessage()
		typ = "audio"
		if a.GetPTT() {
			typ = "voice"
		}
		ex["seconds"] = a.GetSeconds()
	case m.GetDocumentMessage() != nil:
		d := m.GetDocumentMessage()
		typ, text = "document", d.GetCaption()
		name := d.GetFileName()
		if name == "" {
			name = d.GetTitle()
		}
		ex["fileName"], ex["size"], ex["pages"], ex["mime"] = name, d.GetFileLength(), d.GetPageCount(), d.GetMimetype()
		thumb(d.GetJPEGThumbnail())
	case m.GetStickerMessage() != nil:
		typ = "sticker"
		if m.GetStickerMessage().GetIsAnimated() {
			ex["animated"] = true
		}
	case m.GetLocationMessage() != nil:
		l := m.GetLocationMessage()
		typ, text = "location", firstNonEmpty(l.GetName(), l.GetAddress())
		ex["lat"], ex["lng"] = l.GetDegreesLatitude(), l.GetDegreesLongitude()
		thumb(l.GetJPEGThumbnail())
	case m.GetLiveLocationMessage() != nil:
		l := m.GetLiveLocationMessage()
		typ, text = "location", l.GetCaption()
		ex["lat"], ex["lng"], ex["live"] = l.GetDegreesLatitude(), l.GetDegreesLongitude(), true
	case m.GetContactMessage() != nil:
		typ, text = "contact", m.GetContactMessage().GetDisplayName()
		if v := m.GetContactMessage().GetVcard(); v != "" && len(v) < 8192 {
			ex["vcard"] = v
		}
	case m.GetContactsArrayMessage() != nil:
		typ, text = "contact", m.GetContactsArrayMessage().GetDisplayName()
		ex["count"] = len(m.GetContactsArrayMessage().GetContacts())
	case m.GetPollCreationMessage() != nil || m.GetPollCreationMessageV2() != nil || m.GetPollCreationMessageV3() != nil:
		p := m.GetPollCreationMessage()
		if p == nil {
			p = m.GetPollCreationMessageV2()
		}
		if p == nil {
			p = m.GetPollCreationMessageV3()
		}
		typ, text = "poll", p.GetName()
		ex["selectable"] = p.GetSelectableOptionsCount()
		opts := []string{}
		for _, o := range p.GetOptions() {
			opts = append(opts, o.GetOptionName())
		}
		ex["options"] = opts
	case m.GetEventMessage() != nil:
		typ, text = "event", m.GetEventMessage().GetName()
	case m.GetGroupInviteMessage() != nil:
		typ, text = "invite", m.GetGroupInviteMessage().GetGroupName()
	case m.GetButtonsResponseMessage() != nil:
		typ, text = "text", m.GetButtonsResponseMessage().GetSelectedDisplayText()
	case m.GetTemplateButtonReplyMessage() != nil:
		typ, text = "text", m.GetTemplateButtonReplyMessage().GetSelectedDisplayText()
	case m.GetListResponseMessage() != nil:
		typ, text = "text", m.GetListResponseMessage().GetTitle()
	case m.GetButtonsMessage() != nil:
		b := m.GetButtonsMessage()
		typ, text = "text", joinLines(b.GetText(), b.GetContentText(), b.GetFooterText())
		var btns []map[string]string
		for _, x := range b.GetButtons() {
			if t := x.GetButtonText().GetDisplayText(); t != "" {
				btns = append(btns, map[string]string{"text": t})
			}
		}
		addButtons(ex, btns)
		thumb(b.GetImageMessage().GetJPEGThumbnail())
	case m.GetListMessage() != nil:
		l := m.GetListMessage()
		typ, text = "text", joinLines(l.GetTitle(), l.GetDescription(), l.GetFooterText())
		if l.GetButtonText() != "" {
			addButtons(ex, []map[string]string{{"text": l.GetButtonText()}})
		}
	case m.GetTemplateMessage() != nil:
		// Business templates (bills, OTPs, offers): the text can sit in any of
		// three formats, plus buttons.
		tm := m.GetTemplateMessage()
		typ = "text"
		h := tm.GetHydratedFourRowTemplate()
		if h == nil {
			h = tm.GetHydratedTemplate()
		}
		if h != nil {
			text = joinLines(h.GetHydratedTitleText(), h.GetHydratedContentText(), h.GetHydratedFooterText())
			var btns []map[string]string
			for _, x := range h.GetHydratedButtons() {
				switch {
				case x.GetUrlButton() != nil:
					btns = append(btns, map[string]string{"text": x.GetUrlButton().GetDisplayText(), "url": x.GetUrlButton().GetURL()})
				case x.GetCallButton() != nil:
					btns = append(btns, map[string]string{"text": x.GetCallButton().GetDisplayText(), "url": "tel:" + x.GetCallButton().GetPhoneNumber()})
				case x.GetQuickReplyButton() != nil:
					btns = append(btns, map[string]string{"text": x.GetQuickReplyButton().GetDisplayText()})
				}
			}
			addButtons(ex, btns)
			thumb(h.GetImageMessage().GetJPEGThumbnail())
			if h.GetMaskLinkedDevices() && text == "" {
				text = "This message from a business can only be opened on your phone."
			}
		}
		if text == "" {
			if im := tm.GetInteractiveMessageTemplate(); im != nil {
				text = interactiveText(im, ex)
			}
		}
		if text == "" {
			if fr := tm.GetFourRowTemplate(); fr != nil {
				text = joinLines(firstNonEmpty(fr.GetContent().GetFallbackLc(), fr.GetContent().GetFallbackLg()),
					firstNonEmpty(fr.GetFooter().GetFallbackLc(), fr.GetFooter().GetFallbackLg()))
			}
		}
		if text == "" {
			text = "Message from a business — open it on your phone to see it."
		}
	case m.GetInteractiveMessage() != nil:
		typ = "text"
		text = interactiveText(m.GetInteractiveMessage(), ex)
		if text == "" {
			text = "Message from a business — open it on your phone to see it."
		}
	case m.GetHighlyStructuredMessage() != nil:
		hs := m.GetHighlyStructuredMessage()
		typ, text = "text", firstNonEmpty(hs.GetFallbackLc(), hs.GetFallbackLg(), hs.GetDeterministicLc())
	case m.GetCall() != nil:
		typ = "call"
	default:
		return "", "", ""
	}
	if len(ex) > 0 {
		b, _ := json.Marshal(ex)
		extra = string(b)
	}
	return
}

// contextInfo finds the reply/mention context attached to any message type.
func contextInfo(m *waE2E.Message) *waE2E.ContextInfo {
	switch {
	case m.GetExtendedTextMessage() != nil:
		return m.GetExtendedTextMessage().GetContextInfo()
	case m.GetImageMessage() != nil:
		return m.GetImageMessage().GetContextInfo()
	case m.GetVideoMessage() != nil:
		return m.GetVideoMessage().GetContextInfo()
	case m.GetAudioMessage() != nil:
		return m.GetAudioMessage().GetContextInfo()
	case m.GetDocumentMessage() != nil:
		return m.GetDocumentMessage().GetContextInfo()
	case m.GetStickerMessage() != nil:
		return m.GetStickerMessage().GetContextInfo()
	case m.GetLocationMessage() != nil:
		return m.GetLocationMessage().GetContextInfo()
	case m.GetContactMessage() != nil:
		return m.GetContactMessage().GetContextInfo()
	case m.GetPtvMessage() != nil:
		return m.GetPtvMessage().GetContextInfo()
	}
	return nil
}

// interactiveText pulls the header, body, footer and button labels out of a
// business "interactive" message.
func interactiveText(im *waE2E.InteractiveMessage, ex map[string]any) string {
	text := joinLines(im.GetHeader().GetTitle(), im.GetHeader().GetSubtitle(), im.GetBody().GetText(), im.GetFooter().GetText())
	var btns []map[string]string
	for _, b := range im.GetNativeFlowMessage().GetButtons() {
		var p struct {
			DisplayText string `json:"display_text"`
			URL         string `json:"url"`
			Phone       string `json:"phone_number"`
		}
		_ = json.Unmarshal([]byte(b.GetButtonParamsJSON()), &p)
		if p.DisplayText == "" {
			continue
		}
		btn := map[string]string{"text": p.DisplayText}
		if p.URL != "" {
			btn["url"] = p.URL
		} else if p.Phone != "" {
			btn["url"] = "tel:" + p.Phone
		}
		btns = append(btns, btn)
	}
	addButtons(ex, btns)
	if t := im.GetHeader().GetJPEGThumbnail(); len(t) > 0 && len(t) < 64*1024 {
		ex["thumb"] = base64.StdEncoding.EncodeToString(t)
	}
	return text
}

func addButtons(ex map[string]any, btns []map[string]string) {
	if len(btns) > 0 {
		ex["buttons"] = btns
	}
}

// joinLines joins the non-empty parts with blank lines in between.
func joinLines(parts ...string) string {
	out := ""
	for _, p := range parts {
		if p == "" {
			continue
		}
		if out != "" {
			out += "\n\n"
		}
		out += p
	}
	return out
}

func firstNonEmpty(s ...string) string {
	for _, v := range s {
		if v != "" {
			return v
		}
	}
	return ""
}
