# Quiet — handoff (updated 4 November 2026)

Read this first, then `docs/roadmap.md` (every feature and its status) and `docs/ideas-and-plans.md`
(everything else the owner asked to remember). Run `bash scripts/setup-session.sh` before working.

## 1. What Quiet is
A calm, private e-book reader: EPUB, PDF, MOBI/AZW3, FB2, Word, ODT, RTF, Markdown, TXT, HTML,
Palm, and comics (CBZ, CBR, CB7, CBT). A Vite + TypeScript web app, wrapped with Capacitor as an
**Android app** and (since 4 Nov) an **iPhone app**. The owner is a frontend developer (not a
software engineer) on Windows with a Redmi Note 13 Pro. They want it Apple-like: fluid motion,
physical gestures, research-backed design that doesn't look AI-made. Headed for Google Play (and
maybe the App Store) later. Core message: "a reader should be very simple. Period." **No AI features**.

## 2. Current status
- **Native app (started 4 Nov):** the owner chose a fully native Android app (Kotlin + Jetpack
  Compose) built in layers L0–L8 until it replaces 0.4.2. Plan and decisions: `docs/plan-native.md`.
  Code in `native/`, build with `bash scripts/build-native.sh [test]`. **L0 (the skeleton) is done
  and sent** as `Quiet-0.5.0-L0.apk` (app ID `app.quietreader.n`, installs next to 0.4.2).
  Next: L1 + L2 together. 0.4.x gets bug fixes only. The owner has some 0.4.2 issues to report.
- **Android 0.4.2 (versionCode 420)** and **iPhone 1.0 (build 1)**, all committed and pushed to
  `main` (last commit `cb20689`). The working tree is clean.
- **Sent to the owner:** `Quiet-0.4.0.apk`, `Quiet-0.4.1.apk`, `Quiet-0.4.2.apk`,
  `Quiet-iOS-1.0-1.ipa`, and `Quiet-iOS-simulator.zip` (for Appetize.io), the last two with the
  sample books.
- **The owner said:** "quite satisfied" with the 0.4.2 curl. No phone results reported yet for
  0.4.x or iOS 1.0. Ask for them (the lists are in `docs/release-notes-0.4.0.md`, `-0.4.1.md`,
  `-0.4.2.md`, `-ios-1.0.md`, section "Check on your phone").
- **All 13 test suites pass** (`node tests/run-all.cjs`).

### What each version did (this session, 3–4 November)
- **0.4.0** (`169c804`), one release, as the owner asked (no 0.3.5):
  - **Fixes F1–F9** (details in `docs/plan-0.4.0.md`):
    - F1/F2: the library vanished on the phone (the WebView lost IndexedDB while localStorage
      survived). Now a **safety copy** in Android private storage plus automatic recovery
      (`src/library/safety.ts`). Book folders rescan everything when the library is empty.
    - F3: "invisible chapter" (book text coloured black on Night or white on Day, or a page faded
      by the book's CSS). Fixed by `src/reader/legibility.ts` and book-css.ts rules; the chapter
      pull also recovers after 4 s.
    - F4: animations missing on the phone. Curl only works in page mode (the default is scroll);
      choosing Curl now switches to pages. A **Motion** setting (Follow phone/Full/Reduced) whose
      hint says when the phone asks for less motion.
    - F5: a curl like paper. F6: clicks during View Transitions were lost (now the transition is
      skipped and the tap replayed). F7: mouse wheel in dialogs on desktop. F8: toasts hidden
      behind dialogs (now popovers in the top layer). F9: Sepia removed.
  - **Library backup and restore** (#1): `.quietbackup` zip (`src/library/backup.ts`, `zip.ts`).
  - **Radio design** (the only design now), knob icon, Archivo/DM Mono/Crimson Pro fonts.
  - **Reader bar** at the bottom (`src/reader-keys.ts`): chapter tuning scale plus Contents ·
    Day/Night · Light · Text · More, customizable (settings.readerKeys).
  - **Book info** (`src/library/book-facts.ts`): words, pages, reading time at your pace,
    chapters, ISBN, file details, your reading.
  - **Settings in sections**, **About**, **Report a problem** (#66, `src/app/report.ts`), and
    **the tour** (`src/app/tour.ts`), which replaced the welcome.
  - Stats only from its tab (strip and menu item removed). The "Reading" tab was renamed **Stats**.
- **0.4.1** (`eb20ea9`): Stats in the Radio look. Needle meter for today vs the daily goal, the
  week as level lights, three readouts (streak, words/min, books this year), details behind one key.
- **0.4.2** (`641250b`): **the curl follows the finger.** The corner stays under the fingertip,
  the fold halfway to the edge. Letting go past about 40% (q > 0.2), or with a flick, turns the
  page; otherwise it settles back (the page underneath is turned back before the real page shows).
- **iOS 1.0 (1)** (`abe553a`, `e86c50c`, `cb20689`): Capacitor iOS with Swift Package Manager.
  Swift plugin for vibration, keep-awake, battery, safety copy, quote share/save (Photos), and
  backup saved via iOS's "Save to Files". Book folders, volume keys and rotation lock are
  Android-only and hidden on iPhone. Three sample books are added on the first start (iPhone only).
  Built on GitHub's macOS machines: unsigned .ipa plus a simulator zip for Appetize.

### Not verified on a real device (ask the owner)
- Everything since 0.4.0 on the Redmi: install over 0.3.4 keeps the library; backup/restore; reader
  bar; curl (tap and finger drag), EPUB and PDF, Day and Night; the Motion hint; book info; tour;
  Report a problem → Telegram; the knob icon.
- iOS 1.0: nothing has run on an iPhone or on Appetize yet. Check safe areas (notch, home bar),
  the curl, Save to Files, Photos permission, and sample books.
- From before: a real compressed CBR, vibration, pinch zoom, translation on mobile data.
- **F6 (desktop reading-settings button)** couldn't be reproduced in Chrome. The likely cause
  (clicks lost during the opening animation) is fixed. If it still fails, ask which browser.
- **F1 root cause** is a well-supported guess (WebView reset IndexedDB, likely after a crash), not
  proven. The safety copy handles it either way.

## 3. Key decisions (don't re-argue)
- **One design: Radio** (after the Dial direction, then "more like a radio").
  - The owner rejected all four old styles (Star, Paper, Old Tashkent, Frosty Glass) and
    design round 1 (Grey, Moss, Cover: "look AI generated, logos generic").
  - In round 2 (Rest from music, Dial from hi-fi, Do not disturb from hotels) they chose **Dial**,
    then asked to lean into "radio".
  - Design canvas with every round: https://claude.ai/artifact/63xpVE679aXM9svyeBvaGk (pages
    "Radio", "Round 2", "Round 1"). The older canvas: https://claude.ai/artifact/Grn4yeEzztYdj9LLeS5vzR.
- **Radio rules:** warm grey casing `#e8e6e1`, keys `#f1efea` with edge `#bdb9af` that press down,
  graphite `#24231f`, orange `#c9541f` only for "you are here", green `#4e7d34` only for
  "finished"; a speaker-grille dot texture; numbers in DM Mono; the band-selector tab bar.
  Night version included. Tokens are in `src/styles/styles.css` (`[data-style="radio"]`).
- **The icon** is a volume knob turned all the way down, with the orange zero dot (drawn in
  Python; see section 5).
- **Controls at the thumb:** the reader bar is at the bottom. Default keys: Contents ·
  Day/Night · Light · Text, then More. Search and the rest live in More.
- **Settings sections:** Interface, Reader, Library, Files & storage, Privacy & translation, plus
  About (with "made by extrasolarions on howling dark", promises including "No AI features").
- **No AI features in Quiet** ("no AI washing"). Roadmap idea A (AI voiceover) is on hold.
- **Privacy:** nothing leaves the device except text the reader chooses to translate (Google, with
  MyMemory as fallback). Report a problem is sent by the reader; there are no servers.
- **Motion:** Apple's spring model compiled to CSS `linear()` (`src/ui/motion.ts`), bounce 0; no
  bounce in Radio. Respect the Motion setting (`<html data-motion>`).
- **Platforms:** Capacitor for Android and iOS. A fully native Android (Kotlin) app is an idea
  only, for later. iOS testing: Sideloadly/AltStore (free Apple ID, 7-day renewals), Appetize.io
  (the owner has an account on the free plan, 30 min/month). App Store/TestFlight needs $99/year.
- **Versioning:** Android continues 0.4.x; iOS started at 1.0 (1) by the owner's choice.

## 4. Important facts
- **Repo:** github.com/2n0m0a5/quiet-reader (private), branch `main`, clone at
  `/home/user/quiet-reader`.
  - A new cloud session needs the repo attached (add_repo with push; the owner allowed all their
    repos), then `git clone`.
  - Push with `git push -u origin main`.
  - End commits with `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>` and
    `Claude-Session: <this session's link>`.
  - **`gh` works for this repo** (through the proxy): `gh run list/watch/view/download -R 2n0m0a5/quiet-reader`.
- **Website:** https://quiet-reader-bgz.pages.dev (Cloudflare Pages, deploys every push to main).
- **Android:** app ID `app.quietreader`, **0.4.2 / versionCode 420** (`package.json`,
  `android/app/build.gradle`). Test key `android/app/quiet-test.keystore`, SHA-256 starts `305b375e`.
- **iOS:** `ios/App/App.xcodeproj`, bundle `app.quietreader`, MARKETING_VERSION 1.0,
  CURRENT_PROJECT_VERSION 1 (in `project.pbxproj`), iOS 15+.
  - Swift: `ios/App/App/QuietNativePlugin.swift`, registered in `MainViewController.swift` (set
    as the class in `Base.lproj/Main.storyboard`). New Swift files must also be added to
    `project.pbxproj` (four entries; see the `A1C0FFEE…` ids).
  - Info.plist has NSPhotoLibraryAddUsageDescription.
- **Builds:**
  - New session: `bash scripts/setup-session.sh` installs npm packages, the Android SDK at
    `/home/user/android-sdk`, the Gradle mirror for Maven Central's 429s, and the sample books.
  - Android: `bash scripts/build-apk.sh` builds, checks the version and signer, and writes
    `dist-apk/Quiet-<ver>.apk` (git-ignored). Then send it with SendUserFile.
  - iOS: `.github/workflows/ios.yml` runs on macos-15 when `ios/**`, `src/**`, `index.html`,
    `package.json` or the workflow change on main, or by hand (Actions → "iOS app"). Artifacts:
    `Quiet-iOS` (`Quiet-iOS-<ver>-<build>.ipa`, unsigned) and `Quiet-iOS-simulator`
    (`Quiet-iOS-simulator.zip` for Appetize). Takes about 10–15 minutes. Fetch them with
    `gh run download <id> -R 2n0m0a5/quiet-reader -D <dir>`.
  - After any web change, also run `npx cap sync android` (build-apk does it) and, for iOS, CI does
    `npx cap sync ios`.
- **Native app (`native/`):** Kotlin 2.2.21, AGP 8.13, Compose BOM 2025.10.01, minSdk 26.
  Gradle uses Google's Maven Central mirror (in `native/settings.gradle.kts`). There's no JDK 17
  here (Java 21), so no toolchain line. Release builds are minified (Compose is slow in debug).
  Tests: Robolectric + Roborazzi screenshots of every screen (`ScreensTest.kt`); the cloud
  machine has no KVM, so no emulator. Same test key as the Capacitor app.
- **Tests:** `npm run build`, then `node tests/run-all.cjs` (13 suites; `tests/README.md` lists
  them):
  - `backup`, `curl`, `desktop`, `formats`, `gestures`, `legibility`, `motion-settings`,
    `quote-save`, `reader-bar`, `safety`, `settings-pages`, `smoke`, `stats`.
  - `tests/common.cjs`: `suite(name, fn, options)` with options `size`, `touch`, `fresh`,
    `settings`, `storage` (localStorage), and `context` (e.g. `{ serviceWorkers: 'block' }`).
    Helpers: addBooks, waitForReader, bookText, showBars, backToLibrary, touch (CDP).
  - Sample books: `tests/gen/mkbooks.py` writes them to `tests/books/` (git-ignored); it includes
    `colours.epub` for legibility. Copies for iPhone live in `public/samples/`.
- **Key code:**
  - `src/main.ts`: wiring, routing, reader bar actions, chapter ticks, sample books, start-up
    recovery.
  - `src/reader/reader.ts`: curl (`curlFrame`, `curlFrames`, `#beginCurl` controller,
    `#onSwipeDrag`), legibility hook.
  - `src/reader-keys.ts`: the reader bar.
  - `src/settings-screen.ts`: Settings pages and About.
  - `src/stats-screen.ts`: meter, lights, readouts.
  - `src/library/{safety,backup,zip,book-facts,folders,db}.ts`.
  - `src/app/{native,report,tour,onboarding}.ts`.
  - Styles: `src/styles/{tokens,app,styles,motion}.css`. Radio and every new component are at the
    end of `styles.css`; the curl is in `motion.css`.
  - Native Android: `android/app/src/main/java/app/quietreader/QuietNativePlugin.java`. Safety
    copy, createDocument/writeDocument, folders, share/save. Its `BOOK_EXTENSIONS` must match
    `src/formats/formats.ts`.
  - Database: IndexedDB `quiet`, DB_VERSION 4. Every readwrite transaction notifies
    `onLibraryChange` (used by the safety copy).
- **Patched vendor foliate-js** (`vendor/foliate-js/`):
  - `paginator.js`: `swipe-turn`; `swipe-drag` events (start/move/end, measured with
    `pageX/pageY` = clientX + frame offset); touchcancel; spring slide.
  - `comic-book.js`: natural sort, junk skip.
- **Docs:** `docs/plan-0.4.0.md` (the 0.4.0 plan, owner's answers, progress),
  `docs/release-notes-0.3.0` to `0.4.2.md`, `release-notes-ios-1.0.md`,
  `research-0.3.3.md`, `research-0.3.4.md`, `roadmap.md`, `ideas-and-plans.md`.
- **Other links:**
  - Content strategy: https://claude.ai/artifact/PGHx9eLqqXn8jADtMLurik
  - Showcase repos: `2n0m0a5/quiet-reader-version-1` to `-5`.

## 5. Problems and gotchas
- **Maven Central 429** in cloud containers: use the Gradle init mirror (setup-session.sh writes it).
- **View Transitions:**
  - The update callback must not wait for requestAnimationFrame (race it against a timeout).
  - During a transition every click goes to `<html>`. `trackTransition()` in motion.ts skips the
    transition on pointerdown and replays the tap; register every new transition with it.
  - The curl keeps its transition open with a long dummy animation on
    `::view-transition-group(page)` and ends it with `skipTransition()`.
  - The curl's look is driven by registered custom properties (`--curl-*`, `@property` in
    motion.css) animated on `:root`. Animating pseudo-elements directly was unreliable on the phone.
- **Inside the book's iframe** CDP touches report screenX/Y as 0, and clientX jumps when the page
  turns underneath (the frame moves). Use clientX plus the frame rect (paginator `pageX`).
- **Toasts** are `popover="manual"`, re-shown on each call so they sit above modal dialogs. They're
  drawn above, but can't be pressed while a modal is open.
- **Dialogs:** the body is the scroller (`dialog[open]` is a flex column). The rubber band's
  `overscroll-behavior: none` otherwise blocks the mouse wheel.
- **The long-press click** must be swallowed (library-view `#onLongPress`), or it closes the
  details sheet it just opened.
- **The safety copy** must start only after `recoverLibrary()`, and never overwrites a copy that
  has books with an empty library unless the reader chose "Delete everything". On the website it's
  off (`quiet:test-safety` turns on an OPFS stand-in for tests).
- **Curl** needs page mode. Choosing it switches the flow to paginated, with a toast. Fixed-layout
  books curl only the page box. The paper colour comes from the book's page.
- **The service worker's "ready to work offline" toast** can replace a test's toast. Block service
  workers in tests that read toasts.
- **While reading, the bars hide.** Tests use `showBars` before pressing Back or Contents.
- **Series aren't read from book metadata** (`calibre:series` / `belongs-to-collection`); they come
  only from Edit. A cheap improvement to offer.
- **Leftovers to clean up when convenient:**
  - `showWelcome` in onboarding.ts is unused (the tour replaced it).
  - `khatam()` is still used for rating stars.
  - The What's new entries for 0.3.x still mention the removed styles; they show only to people
    updating from 0.3.x.
  - CSS for `.style-*` and the per-style chapter-pull marks is dead.
- **Translate tests** can hit Google 429; MyMemory is the fallback.
- **libarchive:** the worker and wasm must sit together in `public/vendor/libarchive/`.

## 6. Owner's to-dos (they must do these; Claude can't)
- Publish **GitHub Releases** for v0.1.0 to v0.4.2, each with its APK and `docs/release-notes-*.md`,
  and an iOS release `ios-v1.0` with the .ipa and `release-notes-ios-1.0.md`.
- Turn on GitHub Pages (Source: GitHub Actions) in showcase repos `quiet-reader-version-2` to `-5`.
- Send the promo video footage.
- Test on the Redmi and on an iPhone or Appetize, and report back.

## 7. Next steps
0. **Native:** get the owner's L0 phone results, then build L1 (read a book) + L2 (your data is
   safe) together, per `docs/plan-native.md`. Readium Kotlin Toolkit (BSD-3, 3.x on Maven Central)
   is the agreed book engine. Ask for the 0.4.2 issues the owner mentioned.
1. Ask for the phone and iPhone results; fix what they report (curl feel: the 0.2 threshold and
   the corner-under-finger mapping are in `#onSwipeDrag`).
2. Open questions for the owner:
   - Should the selection-menu colour dots keep the last-used colour?
   - The showcase repos' licence (suggestion: none).
   - Read series from book metadata?
3. Candidates the owner hasn't chosen yet (by roadmap number):
   - Read aloud with system voices (17)
   - Export highlights to a file (34)
   - Year in review (44)
   - Download from a link (50)
   - Two pages side by side (19)
   - Manga modes (58)
   - Wi-Fi transfer and a desktop app (51/57)
   - Android widget (55)
   - App lock (65)
   - Languages (62): Russian first
   - Play Store setup (68): a real signing key, store listing, privacy policy (About's promises
     are a start)
   - Landing page (70)
4. iOS next: real-device feedback, then (if the owner wants the App Store) an Apple Developer
   account ($99/year), signing in CI, TestFlight. Possibly "Open in Quiet" document types and iOS
   share/import.
5. Keep `docs/roadmap.md`, `docs/ideas-and-plans.md` and this file current after every release.

## 8. How the owner likes to work
- Plain, short words. They're not a software engineer; explain steps from scratch when needed.
- **After every version:** send the APK (and the iOS files when iOS changes), release notes for
  GitHub, and a short "check on your phone" list.
- Picks features by number; sets clear milestones; sometimes says "just remember, do nothing"
  (then only acknowledge).
- Wants research-backed design, references from outside reading apps, and nothing that looks
  AI-generated. Use only the skills that are needed; be quick when they say "double quick".
- They have usage limits (5-hour windows). When told the limit is low, save progress in the docs
  and stop cleanly. When they say "no handoffs, continue", keep working.
- Speaks English with some typos; their phone UI and tools may be in Russian.

## 9. Starter prompt for a new session
"Read `/home/user/quiet-reader/HANDOFF.md`, `docs/roadmap.md` and `docs/ideas-and-plans.md`. It's
the full context for my e-book reader Quiet (repo `2n0m0a5/quiet-reader`, branch `main`; Android
0.4.2, iPhone 1.0). Run `bash scripts/setup-session.sh`, then ask me for my phone results and
continue from 'Next steps'. Keep it plain and short; send me the APK (and iOS files) after every
version with a list of what to check."
