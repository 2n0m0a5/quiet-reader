# Quiet native — plan (agreed 4 November 2026)

The owner asked for a fully native Android app (Kotlin, no Capacitor) that grows layer by layer
until it replaces the Capacitor app (0.4.2). Each layer is a working APK the owner tests on the
Redmi. The native app takes its own route; the layers only say what it can do at each step.

## Decisions
- **Kotlin + Jetpack Compose, Android first.** The project lives in `native/` (its own Gradle
  build). The Capacitor app in `android/` and the website keep working; 0.4.x gets bug fixes only.
- **Book pages: Readium Kotlin Toolkit** (BSD-3, free to sell with). Everything around the page is
  native; EPUB text is drawn by Readium's small web view inside the page area (option A).
- **Same name, same look:** the phone shows "Quiet", with the knob icon and the Radio design.
  Only the app ID differs while building: **`app.quietreader.n`**, so it installs next to 0.4.2.
  About says "native" next to the version, so the two icons can be told apart.
- **At the switch (end of L8)** the app ID becomes `app.quietreader`, signed with the same key
  (`android/app/quiet-test.keystore`, SHA-256 `305b375e…`), so it installs over 0.4.2 and moves
  the library over (from the safety copy in private storage, or a `.quietbackup`).
- **Versions:** the layer builds are `0.5.0 L0`, `0.5.0 L1`, …; the switch is plain `0.5.0`.
- **iPhone:** later, as its own Swift app on Readium's Swift toolkit. Until then the Capacitor
  iOS 1.0 app stays.
- **No AGPL code** (MuPDF, KOReader, Readest): only licences that allow selling.

## Layers
| Layer | What it adds | Status |
|---|---|---|
| **L0 The skeleton** | Kotlin project and APK build; Radio colours, fonts (Archivo, DM Mono, Crimson Pro) and the knob icon; the 4 tabs; Day / Night / Follow phone; keys that press down, springs without bounce, vibration; Settings with Interface and About | ✅ built |
| **L1 Read a book** | Add books (picker, "Open with Quiet"); library grid with covers; EPUB, PDF and TXT in scroll or pages; remembers your place; contents, text size, font, Day/Night; bars hide while reading | ⏭ next (with L2) |
| **L2 Your data is safe** | Import a 0.4.2 `.quietbackup`; back up and restore (same format, both ways); safety copy; Recently deleted | ⏭ next (with L1) |
| **L3 The reader, fully** | Tuning-scale bar, 5 keys, Customize bar, More panel; brightness, warmth, Look & layout, your own fonts; native slide and curl; volume keys, rotation lock, time and battery, go to page, footnotes, picture viewer, bionic, ruler | |
| **L4 Notes** | Highlights (colours, underline, strikethrough), notes, quotes with owners, bookmarks; Notes tab; quote pictures; translation; search in a book | |
| **L5 Organizing** | Collections, tags, series, statuses, ratings; edit details and covers; select several; sort, filter, group; shelf view with pinch; duplicates; book info; book folders | |
| **L6 Stats** | Tracker, needle meter, week lights, readouts; goals, streak, session timer; the 5 detailed stats | |
| **L7 Every format** | MOBI/AZW3, FB2, Word, ODT, RTF, Markdown, HTML, Palm; comics CBZ/CBR/CB7/CBT; fixed-layout EPUB; PDF zoom | |
| **L8 Polish and the switch** | All Settings sections, the tour, What's new, Report a problem; Motion setting complete; app text size; TalkBack pass; **the switch** to `app.quietreader` as 0.5.0 | |
| After | The native route: read aloud (17), widget (55), app lock (65), auto-scroll (18), then the rest of the roadmap | |

## How to build and test (native)
- `bash scripts/build-native.sh` builds `native/` and writes `dist-apk/Quiet-<version>-<layer>.apk`
  (checks the app ID, version and signing key).
- `bash scripts/build-native.sh test` also runs the JVM tests, including Roborazzi screenshots
  of every screen (Robolectric, no emulator: the cloud machine has no KVM). Pictures land in
  `native/app/build/outputs/roborazzi/`.
- The feel (motion, vibration, fonts on the real screen) can only be checked on the Redmi.
