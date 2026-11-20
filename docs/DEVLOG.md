# Development log

Notes written while the app was being built, newest first. Build numbers are
the CI run numbers of the original private repository.

## Build 18 (5 Nov 2026)

- Chat header is back to capsules (back + count · photo, name and subtitle · calls), now on dark frosted glass.
- Status privacy can be changed here: My contacts, My contacts except…, or Only share with…, using a people picker. Sent as a `status` privacy IQ; if WhatsApp refuses, the app says to change it on the phone.
- @mentions in groups: typing "@" lists the members, plus "all". Sent as "@number" with MentionedJID, like WhatsApp, and names are shown here.
- The chat-bubble and laptop icons were still filled in Material's "outlined" set; they're now custom line icons.
- Profile pages (yours, contacts, groups):
  - Round photo, bold name, number and About, round action buttons, and Edit on contacts.
  - "Media, links and docs / Free up storage / Starred" card with counts and size.
- Media / Links / Docs: a compact segmented capsule.

## Build 17 (5 Nov 2026)

- All filled icons are now outline icons. The release build is minified, so the old runtime filled→outline lookup never worked; the icons are swapped in the source instead.
- Back from a Settings page returns to Settings. The hidden screen underneath had kept its back handler (Home's "go to Chats") and caught the gesture first; hidden screens now get a dead back dispatcher.
- App colour and link colour are their own settings (colour picker), separate from bubble colours.
- Account privacy is only on the Privacy page.
- Chat header: darker frosted bar, lighter buttons.
- "Not in your contacts · Add" in chats and "Add to contacts" in contact info. Chats now carry a `saved` flag.
- Sharing from other apps: photos and videos now actually send. The send ran on the share screen's scope, which was cancelled as soon as that screen closed.
- View-once messages others send show as a view-once notice. WhatsApp gives linked devices only a placeholder; it used to appear as "waiting".
- Media / Links / Docs: one row with back, the segmented control and Select (forward or delete several).
- New group: New chat → New group → pick people → name → Create.
- "Send to" sheet like iPhone (×, search, Frequently contacted, Recent chats, round ticks, preview + send), used for forwarding, sharing and the camera.
- Copy works for photos and videos.
- Notifications → "Hide 'Connected' notification" uses a silent, minimised channel.

## Build 16 (4 Nov 2026)

- Line icons drawn to match WhatsApp on iPhone (`ui/glass/LineIcons.kt`): photo, storage, star, bell, palette, save, timer, chat lock, shield, lock, pin, archive and person. They're used in contact info, Settings and the long-press menus; other rows use outline icons.
- Contact info is laid out like iOS:
  - Starred.
  - Notifications and Chat theme.
  - Disappearing messages (now settable: Off / 24 h / 7 d / 90 d), a Lock chat switch and Encryption.
  - Pin and Archive.
- Search: the search field and search button open a full search page.
  - Chips: Unread, Photos, Videos, Links, Documents, Audio.
  - Recent searches with photos, and Clear all.
  - Matching chats and messages across all chats; photos and videos show as a grid.
  - The chat-lock secret code works in this field.
- Bubble colours sheet redesigned:
  - Tap a bubble in the preview, or choose Their bubble / My bubble / Gradient end.
  - Full colour picker: saturation/brightness square, hue strip, new vs current colour, hex code with RGB/HSB values, plus quick picks.
- Alerts: the two buttons (e.g. Cancel / Clear) are centred in their halves.
- Video notes now show in the chat. They were drawn as "bare" bubbles, like stickers, and the bare renderer only handled stickers and emoji, so it drew nothing.
- Menus: the thick bar before "More…" is a normal hairline.
- App icons:
  - Six more pastel bubble icons: Ocean, Mint, Sunset, Lavender, Midnight, Gold.
  - "Your photo" adds a home-screen shortcut with any picture as its icon. Android only allows custom images as shortcuts; the app's own icon must be built in.
- Chat header: a full-width glass bar behind the status bar, with back + unread count, round photo and name, and call buttons.
- No more blink when coming back to a chat: the screen underneath stays alive (hidden, taps blocked) and is moved, not rebuilt.

## Build 15 (4 Nov 2026)

- Chat header like WhatsApp on iPhone: back button with unread count, then a round photo and the name (no capsule). "online" or "typing…" appears under the name when it applies.
- Message long-press menu: Reply, Forward, Copy, Save (media and files), Delete, More…. More… holds Edit, Info, Star/Keep, Pin, Select, Share and Search.
- Chat long-press menu: View contact, Pin, Mute, Lock chat, More…. More… holds read/unread, Archive, Favourites, Add to list, Block or Exit, Clear and Delete (red).
- Pop-ups use the iOS 26 sheet style: a title, a round × button and one grouped card, with destructive items in red. The ⋯ menu on Chats is a small drop-down glass menu. The attach panel and location sheet are glass overlays now (no Dialog windows).
- Icons are black and white: settings rows, the attach panel and the chat info rows.
- New default app icon: a pastel bubble. The old green glass icon is still in Chat settings → App icon as "Green glass".
- Share from other apps (gallery, file manager, any app):
  - Whatsap appears in Android's share sheet.
  - Recent chats appear as direct-share contacts.
  - Photos and videos go to the send preview; other files are sent as documents.
  - Text is placed in the chat's input box.
- Archived moves from a row in the list to a chip next to All / Unread / Favorites / Groups.
- Location sheet has a "Share live location" row. WhatsApp only lets the main phone share live location, so the row explains that; "Send your current location" works as before.
- Links: a quick tap opens the link; a long press anywhere on the text, link included, opens the message menu.
- Chat settings: "My bubble & their bubble colours" sets your colour or gradient and their bubble colour. Their text switches between black and white for contrast.
- Status viewer:
  - Download button for photos, videos and voice statuses.
  - Long captions show 3 lines with "Read more", and the status pauses while one is open.
  - More space above the reply bar.
- Privacy page: "Account" section with an "Account privacy" row (last seen, profile photo, about).
- Video notes:
  - Every sent video has its MP4 index moved to the front (faststart, tested with ffmpeg). This is so WhatsApp can play video notes while they download.
  - Video notes play inside their circle with a progress ring; tap again to stop.

## Build 14 (1 Nov 2026)

- Real liquid glass for every pop-up: message menu, chat menu, action sheets, alerts, mute/options sheets, emoji picker, edit dialogs. The app is drawn into one backdrop (`GlassHost`) and pop-ups render above it instead of in separate Dialog windows.
- Chat long-press menu: "View contact" / "Group info".
- Link previews (title, description, picture) for links you send, and for previews others send. YouTube uses oEmbed; other sites use Open Graph tags.
- Keep deleted messages (Privacy → Messages, on by default). Messages others delete for everyone stay readable, marked "Deleted by sender".
- View once:
  - Received: shown as a ① chip that opens once.
  - Send: the "1" button in the photo/video preview.
  - WhatsApp usually withholds view-once media from linked devices; those show a "open it on your phone" line.
- Instant (round) video messages: hold the camera button by the input, or Attach → Video note. Front camera, up to 60 s, sent as a real video note (PTV).
- "Play voice" button on message notifications: downloads and plays the latest voice note without opening the app.
- Scheduled messages (10 min – 2 weeks): hold Send for text and links, or hold Send in the photo/video preview. A bar above the input lists them: send now, change the time, or cancel. Exact alarms, re-armed after reboot.
- Photo editor: blur brush in 3 sizes.
- Video trim before sending: no re-encode, cuts on the nearest key frame.
- App text size (Chat settings → Font), 80–140%.
- Search results jump to and flash the message. Links in the Links tab open directly.
- About: tries both new-style (mex) methods, then the legacy IQ. If WhatsApp refuses all three for linked devices, it explains to change About on the phone.
- Business templates, buttons and lists are decoded; blank rows are re-decoded at startup.

## Build 13 (1 Nov 2026)

- Voice notes are now recorded with our own Ogg Opus writer (`OpusRecorder`:
  AudioRecord → MediaCodec Opus → Ogg pages with correct CRC/granules),
  checked with ffmpeg; MediaRecorder stays as a fallback.
- Business messages (templates, interactive, buttons, lists) decoded,
  including buttons; old blank rows are re-decoded at start-up.
- About is saved with the classic `status` IQ (GraphQL fallback).
- Clickable links (`LinkAnnotation.Url`).
- 12 more colour themes + a Custom theme (colour + optional gradient).
- App font from a .ttf/.otf file (`AppFont`), used for text, stickers and
  photo text.
- Contact page with the big photo header; avatar fills the chat header.
- Locked chats opened from a notification always ask again (`ChatLock.epoch`);
  removing the secret code needs the code.
- Status: quick reactions, like, reply.
- Back from Updates/Settings goes to Chats; swipe along the tab bar;
  search button retries focus.
- Lock-screen notifications (new `*_v2` channels, public visibility) and
  contact photos fetched for notifications.
- Chats camera button sends to a chosen chat (or status).

## Build 12 (31 Oct 2026)

- **App icon** picker (Chat Settings → App icon): 7 original styles via
  launcher `activity-alias`es (`AppIcons.kt`, `res/drawable/icon_*`).
- **Notifications**: Telegram-style centre (private / groups / status /
  reactions toggles, per-type previews, exceptions = muted chats, badge
  counter options), smart replies (`RemoteInput.setChoices` + generated
  replies), reaction and status-update notifications (new channel).
- Back from a Settings page returns to the Settings tab (`HomeTabs`).
- **Stickers**: My stickers (make from a photo), emoji sticker pack;
  Go `SendStickerFile` uploads 512×512 WebP.
- Newer emoji (Unicode 14–17) shown when the phone's font has them.
- Tab icons: custom Updates / Chats line icons; lens tinted with theme.
- **Photo editor** in the send preview: crop, rotate, draw, text, emoji.
- **Status**: Voice status, Layout (2–4 photo collage), reply to others'
  status (Go `ReplyToStatus`, quoted), viewers list with names, voice
  status playback.
- Contact photo full screen from contact info.
- **Privacy** (Go `PrivacySettings` / `SetPrivacy`): last seen & online,
  profile photo, about, groups.

## Build 11 (30 Oct 2026)

- **In-app camera** (`CameraScreen.kt`, CameraX 1.4.2): preview, flash, flip,
  Photo/Video modes, recent-photos strip (swipe up for the full grid) and the
  system picker. Used by the chat camera button, Attach → Camera and status.
  Adds the `CAMERA` permission; the system-camera fallback now asks for it.
- **Chat lock secret code** (`ChatLock`, `ChatLockSettingsScreen`): salted
  SHA-256 code, "Hide locked chats", type the code in Chats search to open
  them; LockGate asks for the code (fingerprint still offered).
- **Updates tab** redesigned: tall status cards, Add status sheet (Text,
  Camera, Gallery, Recents grid), search, ⋮ menu, status privacy (read from
  WhatsApp — only the phone can change it), **Secretly view status** (no
  read receipt for statuses).
- **Profile** (`ProfileScreen`): big photo, Set Photo (square 640 px JPEG via
  `SetGroupPhoto` with no target), Edit Info (push name via app state, About
  via `SetStatusMessage`), remove photo. Go: `bridge/profile.go`.
- **Settings** redesigned with sub-pages (`SettingsPages.kt`): Account,
  Chat Settings, Privacy & Security, Notifications, Data and Storage, Chat
  Folders, Devices, Always-on connection, Starred.
- **Data and Storage**: photo upload quality Standard (1600 px) / HD
  (4096 px); auto-download per network (mobile / Wi-Fi / roaming × photos,
  audio, videos, documents).
- **Emoji & stickers panel** (`EmojiPanel.kt`): iPhone-style emoji grid with
  categories, frequently used and backspace; Stickers tab.
- Tab bar with a separate search button; swipe between tabs
  (`HorizontalPager`); header shade only after scrolling.
- Fixes: swipe-to-reply always springs back; typing bubble scrolls into view
  instead of hiding behind the input bar.
- Always-on page text is device-neutral.

## Build 10 (30 Oct 2026)

- **Screen-on check**: the background service listens for screen on / unlock
  and immediately checks the WhatsApp connection, so messages that waited
  arrive and notify without opening the app.
- **Back-from-pause check**: when the minute ticker notices it was frozen
  (gap > 4 min) it checks/reconnects right away instead of waiting for the
  next heartbeat.
- **Alarm-clock wake-ups** (Settings → Always-on connection): optional
  heartbeat every 3 min via `AlarmManager.setAlarmClock`, which OEM battery
  savers don't defer; shows the alarm icon while on.
- Checklist step 6 for Infinix/Tecno "Allow background running" /
  "Allow background launch" / "Run in background".
- Delivery log now records screen on/off.

## Build 9 (30 Oct 2026)

- **Chat long-press menu** (`ChatActions.kt`): Mark as read/unread, Pin,
  Archive, Mute, **Lock chat**, **Add to Favorites**, **Add to list**,
  **Block / Unblock** (or **Exit group**), **Clear chat**, **Delete chat**.
  - Lock chat uses the phone's fingerprint, face or PIN. Locked chats leave
    the main list for a "Locked chats" row and show no name or text in
    notifications. They lock again after 5 minutes away from the app.
  - Favorites and custom lists appear as filter chips; "+" makes a new list.
  - Block uses WhatsApp's own block list, so it's the same on all devices.
  - Delete chat syncs to the phone. Clear chat is this phone only and
    keeps starred messages.
- **Message long-press menu** (`MessageActions.kt`):
  - Reactions with **+** for any emoji.
  - Reply, **Forward** (up to 5 chats), Copy, **Info** (who it was
    delivered to and read by, with times), **Star**, or **Keep** in chats
    with disappearing messages, **Pin** (24 h / 7 days / 30 days, shown as
    a banner under the header), Edit, Delete.
  - **More…** offers Select (several messages: star, copy, forward,
    delete), Share and Search in chat.
  - Forwarded messages show "Forwarded". Starred messages show ★, listed
    per chat (contact info) and for all chats (Settings).
- **Disappearing messages:** the chat's timer is now tracked, and your
  messages get it too, so they disappear for others like WhatsApp's own.
- **Always-on connection** (Settings → Always-on connection):
  - One switch, plus a checklist of every phone setting that can stop
    background messages: notifications, battery optimisation, Restricted
    battery, Data Saver, Auto-start and the Recent-apps lock.
  - A real **3-minute background test** that reports with a notification.
  - The service now also holds a Wi-Fi lock and has a 15-minute
    JobScheduler watchdog next to the 5-minute heartbeat.
  - The app records when the phone **closed or paused** it in the background
    (from the gap between its once-a-minute alive ticks) and shows "Your phone stopped
    Whatsap 12:05–12:49" on the chats screen. The real reason messages
    were late is visible instead of guessed.
- **Crash reports:** if the app crashes, the next start offers to share the
  error details.
- Engine: new `actions.go` (block, leave, clear/delete chat, forward, star,
  keep, pin, message info). App-state sends can no longer crash the app.
  Tests cover it all.

## Build 8 (30 Oct 2026)

- **Crash fix (Chat settings, Search in chat):** a glass button or control
  was placed inside the content its own glass reads from, so it drew that
  layer into itself and the app crashed. Both screens are restructured, and
  `liquidGlass` now detects this case and falls back to a plain fill instead
  of crashing.
- **Staying alive in the background:**
  - The "connected" notification is now a visible, silent one ("Stay
    connected"). Many phones are less likely to close apps that show one.
  - Swiping the app away from Recent apps schedules a restart.
  - A red "Messages may not arrive while the app is closed" banner sits on
    the chats screen while notifications are off, the battery is optimised
    or battery use is Restricted. Tap it to fix.
  - The delivery log records these settings, the Android version and the
    phone maker at every start.

## Build 7 (30 Oct 2026)

- **Messages while minimised — two real bugs fixed:**
  - Fresh notifications were being cleared moments after they appeared. On
    "chats changed" the app compared them with a chat list read a moment too
    early (still showing 0 unread) and treated them as read on the phone.
    The comparison now waits for a fresh list and never clears a
    notification newer than what the list knows about.
  - The connection check used a privacy-settings request as its ping. It
    now uses whatsmeow's own keepalive ping, so a slow or unusual answer no
    longer forces a needless reconnect.
- **Delivery log** (Settings → Background messages → Delivery log & test):
  - Records when the app process starts (a start you didn't cause means the
    phone killed it), service start/stop, connection changes, heartbeats,
    and messages that arrived in the background with whether a notification
    was shown or why not.
  - Has a test-notification button and Share. Chats show only as their last
    4 digits.
- **Search in chat** (contact/group info → Search): searches message text,
  photo/video captions, document names, links, audio, places, contacts and
  polls, with filter chips. Tapping a result opens the chat at that message,
  loading older history if needed (engine `search.go`).
- **Group members** on group info: description, "N members", and everyone
  listed (you, admins, saved contacts, others) with Admin badges.
  - Tap a member to message them, view their info or save the contact.
  - The list is saved for offline use (`group_info` table).
- **Chat settings** (Settings → Chat settings):
  - Message text size and message corners, with a live preview.
  - 8 colour themes (your bubbles, as a gradient, plus the accent colour)
    and Automatic / Day / Night.
  - Chat list two or three lines.
  - Tab bar height (48–80) and width (60–100 %) with a live preview.
  - Links to chat wallpaper and the chats screen background.
- **Dim slider** for every background kind (chats screen and chat wallpapers).
- The launcher icon remains the app's own design (the WhatsApp logo isn't used).

## Latest changes (29 Oct 2026, late)

- **Message corners:** the tail on incoming bubbles cut a notch into the
  bubble (the two shapes cancelled where they overlapped). The tail is now
  merged into the bubble, and the shape is built once per size instead of on
  every frame.
- **Chat header:** a soft gradient behind the header (dark in dark mode,
  light in light mode), so it reads over any wallpaper or photo.
- **Tab bar:** only Updates, Chats and Settings.
- **Chats screen background:** Settings → Chats screen background, or
  Chats → ••• → Change background. Choose the plain default, colours,
  gradients (optionally with doodles) or a photo, softened so the list stays
  readable.
- **Staying connected while minimised:**
  - New *Instant messages* switch, on by default, under Settings →
    Background messages. It keeps the processor awake while the background
    service runs, so the engine's own 20–30 s pings keep the connection open
    with the screen off. It uses more battery and can be switched off.
  - On returning to the app the connection check now gives up after 6 s
    (not 12 s), so a dead connection is replaced faster.
  - The "Connecting…" banner only appears if reconnecting takes more than
    3 seconds.

## Status (29 Oct 2026)

This build adds what the third round of testing asked for:

- **Chat list spacing** matches WhatsApp: 50 dp avatars, no dividers,
  single-line previews.
- **Attach panel** (`ui/screens/Attach.kt`): a glass sheet with the camera,
  your recent photos and videos (tap several, then Next) and Gallery,
  Location, Contact, Document, Audio, Poll and Sticker.
  - Location: sends your current location with its accuracy and street
    address (`PhoneLocation` in `media/Gallery.kt`).
  - Contact: the phone's contact picker. No contacts permission is needed.
    It's sent as a WhatsApp contact card. Tapping a received card offers
    Message, Save contact or Phone call.
  - Poll: question, 2–12 options and "allow multiple answers". **Polls can
    now be voted on** in the bubble, with live counts (engine `extras.go`
    decrypts votes).
  - Audio: music or audio files are sent as audio messages.
- **Photo viewer** (`MediaViewer.kt`):
  - Swipe left and right through every photo and video in the chat.
  - Pinch or double-tap to zoom.
  - Swipe down to close.
  - A thumbnail strip at the bottom.
  - Videos play only on the page you're looking at.
- **Media, links and docs** for every chat (`ChatMedia.kt`). It's on the
  contact/group info page, with Media | Links | Docs tabs:
  - Media: a grid by month.
  - Links: open in the browser.
  - Docs: download and open.
- **Chat wallpaper** (`WallpaperScreen.kt`, `components/Wallpapers.kt`):
  - Choose the default doodles, 18 colours, 8 gradients or your own photo
    (with dimming).
  - Set it for all chats (Settings → Chat wallpaper) or for one chat (its
    info page).
- **Messages while the app is closed** (`service/ConnectionService.kt`):
  - The background service now starts every time the app opens. Before,
    Android could refuse to start it during app launch, and the phone then
    froze the app once it was minimised.
  - A heartbeat alarm every 5 minutes (even in Doze) checks the connection
    with a real round trip to WhatsApp, revives a connection that died
    silently, and restarts the service if the phone stopped it.
  - Connection checks also run when switching Wi-Fi ↔ mobile data and on
    returning to the app, so new chats show at once.
  - A short wake lock lets incoming messages be stored and notified while
    the phone sleeps.
  - Notifications no longer wait on downloading profile photos.
  - Settings → Background messages shows notification and battery status,
    with shortcuts to fix them and to the phone maker's Auto-start screen
    (Infinix/Tecno Phone Master and others). A one-time prompt explains this
    after linking.
- **App name** on the phone is now “Whatsap”. The package and signing key
  are unchanged, so it installs over the previous build and keeps the link.
  The launcher icon stays the app's own design (the WhatsApp logo isn't
  used).
- Engine tests cover gallery queries, locations, contacts, polls and votes, plus the
  connection check and offline avatar lookup.
  All Kotlin passes a kotlinc syntax check, and new AndroidX imports and
  icons were checked against source.

Not possible on a linked device: sending payments, Meta AI images and
creating events.

### Calls

whatsmeow (the engine) can't do calls. The open-source
[whatsapp-rust](https://github.com/oxidezap/whatsapp-rust) library (used by
ZapFast) has an experimental VoIP stack. The plan is to add it as a second,
Rust "calls engine" that links as its own device and handles only calls,
with Android audio (AudioRecord/AudioTrack) on our side.

That has to be compiled for Android with the NDK (cargo-ndk) in GitHub
Actions, which takes several build-and-test rounds. Video would come after
voice works.
