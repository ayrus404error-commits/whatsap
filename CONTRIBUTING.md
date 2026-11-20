# Contributing

Thanks for helping. A few ground rules keep this project healthy:

1. **Open an issue first** for anything bigger than a small fix, so we can agree
   on the approach before you spend time on it.
2. **Keep it unofficial-safe.** No WhatsApp or Meta logos, artwork or trade dress,
   no features that spam, scrape or mass-message, and nothing that weakens
   end-to-end encryption.
3. **Build before you push.** The CI workflow runs the Go engine tests
   (`bridge/engine_test.go`) and builds the APK. Please add or update a test when you
   change engine behaviour.
4. **Style:** Kotlin follows the official style (4 spaces, trailing commas);
   Go code is `gofmt`-ed. User-facing text should be short, plain English.
5. **Never commit secrets.** That includes keystores, passwords and personal phone
   numbers or chats in screenshots. Use made-up data.

By contributing you agree that your contribution is licensed under the
project's GPL-3.0-or-later licence.
