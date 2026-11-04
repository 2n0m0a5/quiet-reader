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
| **L0 The skeleton** | Kotlin project and APK build; Radio colours, fonts (Archivo, DM Mono, Crimson Pro) and the knob icon; the 4 tabs; Day / Night / Follow phone; keys that press down, springs without bounce, vibration; Settings with Interface and About | ✅ sent (0.5.0 L0) |
| **L1 Read a book** | Add books (picker, "Open with Quiet"); library grid with covers; EPUB, PDF and TXT in scroll or pages; remembers your place; contents, text size, font, Day/Night; bars hide while reading | ✅ built (0.5.0 L2) |
| **L2 Your data is safe** | Import a 0.4.2 `.quietbackup`; back up and restore (same format, both ways); safety copy; Recently deleted | ✅ built (0.5.0 L2) |
| **L3 The reader, fully** (L3a ✅ bar, keys, More, light, layout, go to, volume keys, clock, keep awake · L3b ✅ curl (`reader/Curl.kt`: PixelCopy picture + fold), footnotes, pictures, bionic, ruler, your fonts (`reader/PageScripts.kt`)) | Tuning-scale bar, 5 keys, Customize bar, More panel; brightness, warmth, Look & layout, your own fonts; native slide and curl; volume keys, rotation lock, time and battery, go to page, footnotes, picture viewer, bionic, ruler | |
| **L4 Notes** | Highlights (colours, underline, strikethrough), notes, quotes with owners, bookmarks; Notes tab; quote pictures; translation; search in a book | ✅ built (0.5.0 L4): Readium selection menu + decorations (`reader/Highlights.kt`, `NoteSheets.kt`, `ui/NotesTab.kt`); web notes anchor by chapter CFI + text |
| **L5 Organizing** | Collections, tags, series, statuses, ratings; edit details and covers; select several; sort, filter, group; shelf view with pinch; duplicates; book info; book folders | ✅ built (0.5.0 L5): `data/Shelf.kt`, `data/Folders.kt`, `ui/LibraryScreen.kt`, `ui/BookSheets.kt`; series from book metadata |
| **L6 Stats** | Tracker, needle meter, week lights, readouts; goals, streak, session timer; the 5 detailed stats | |
| **L7 Every format** | MOBI/AZW3, FB2, Word, ODT, RTF, Markdown, HTML, Palm; comics CBZ/CBR/CB7/CBT; fixed-layout EPUB; PDF zoom | |
| **L8 Polish and the switch** | All Settings sections, the tour, What's new, Report a problem; Motion setting complete; app text size; TalkBack pass; **the switch** to `app.quietreader` as 0.5.0 | |
| After | The native route: read aloud (17), widget (55), app lock (65), auto-scroll (18), then the rest of the roadmap | |

## Notes from building L1–L2
- After L0 the owner asked for a **text size slider** (done: `Dial` in `ui/Components.kt`, also used
  for book text size) and for **vibration that really works** (done, with tests).
- Readium **3.1.2** (Kotlin 2.1 metadata); 3.2+ needs Kotlin 2.3+. It needs core library desugaring.
- EPUB and TXT: Readium's `EpubNavigatorFragment` hosted in Compose (`reader/EpubReader.kt`). TXT is
  turned into a small EPUB at import (`data/TextBook.kt`); the .txt stays the book file.
- PDF: Android's `PdfRenderer` in Compose (`reader/PdfReader.kt`); zoom comes in L7.
- Library: SQLite with each book kept as the **web app's record** (JSON), plus Readium's locator.
  Book IDs use the web app's fingerprint, so the same file has the same ID in both apps.
- Backups: `data/Backup.kt`, the web format exactly. Positions: the native app writes a
  chapter-level CFI (`epubcfi(/6/N!/4)`) for the web app; from a web backup it starts at the
  CFI's chapter and the saved progress.
- Records the native app doesn't show yet (collections, annotations, reading, fonts) are kept as
  they came, and go back out in backups.

## How to build and test (native)
- `bash scripts/build-native.sh` builds `native/` and writes `dist-apk/Quiet-<version>-<layer>.apk`
  (checks the app ID, version and signing key).
- `bash scripts/build-native.sh test` also runs the JVM tests, including Roborazzi screenshots
  of every screen (Robolectric, no emulator: the cloud machine has no KVM). Pictures land in
  `native/app/build/outputs/roborazzi/`.
- The feel (motion, vibration, fonts on the real screen) can only be checked on the Redmi.
