# Quiet — handoff

## 1. Goal
Quiet is a calm, private e-book reader (EPUB, PDF, MOBI, FB2, Word, comics and more). It's a Vite + TypeScript web app, wrapped as an Android app with Capacitor. The owner (a frontend developer, not a software engineer, on Windows with a Redmi Note 13 Pro) wants it to feel Apple-like: fluid springy motion, physical gestures, several distinct design styles. It's headed for Google Play later.

## 2. Current status
- **Done and pushed:** every version up to **0.3.4** (commit `5d93a1b` on `main`; the working tree is clean).
  - 0.3.0: v3 phases 1–7.
  - 0.3.1: book folders and quote owners.
  - 0.3.2: Quiet Star and Paper styles, tab bar.
  - 0.3.3: five stats, iOS scroll physics, chapter pull, Old Tashkent style, per-style motion.
  - 0.3.4: new formats, custom fonts, series, quote pictures, page curl, motion everywhere, Frosty Glass style.
- **Not verified on a real phone:**
  - 0.3.3–0.3.4 gestures: swipe back, curl on swipe, long-press, sheet drags, chapter pull.
  - A real compressed CBR (only a hand-made stored RAR was tested).
  - Android share/save of quote pictures (`shareImage` and `saveImage` in Java).
  - Frosty Glass blur performance.
  - Vibration, pinch zoom, translation on mobile data, book folders.
- **Not started (next update, 3.x):** 1 library backup/restore; 66 "Report a problem" button.
- **Still promised in the app (Settings → Style):** more styles, and "Build your own style · Coming soon".
- **Owner's to-dos:**
  - Publish GitHub Releases v0.1.0 to v0.3.4, each with its APK and `docs/release-notes-*.md`. I can't create releases.
  - Enable GitHub Pages (Source: GitHub Actions) in the `quiet-reader-version-2` to `-5` showcase repos.
  - Send the promo video footage.

## 3. Key decisions (don't re-argue)
- **Four styles:** `star` (default), `paper`, `tashkent`, `frost`, set as `<html data-style>`.
  - Lamplight was rejected as "just Star with other colours". Saved `lamp` falls back to `star` (`DESIGN_STYLES` in `src/settings.ts`).
  - Each style must differ in kind, and each has its own signature motion:
    - **Star** turns its khatam star.
    - **Paper** is ink: the underline redraws, ink fades, nothing bounces.
    - **Tashkent** works through arches: arch zoom, arch sheets, niche rise, rosette, tassel.
    - **Frost** follows Liquid Glass: it materialises, lights up under the press, has a liquid lens, and glass only on the floating layer.
- **Motion** uses Apple's spring model (duration and bounce compiled to CSS `linear()` in `src/ui/motion.ts`), bounce 0 by default.
  - Rubber-band overscroll uses the UIScrollView formula.
  - The chapter turns only on release, past 88 px.
  - Screens change through View Transitions: zoom from the cover into the book and back. Paper uses its fade.
- **Privacy:** nothing leaves the device except translation (Google, falling back to MyMemory). Report-a-problem will be a user-sent email/Telegram log with no servers.
- **App store build:** stay on Capacitor for now (fully native is only an idea).
  - **iOS (item 56), noted only:** Capacitor iOS → `.ipa`, needing a Mac or GitHub Actions macOS.
  - **Free iPhone testing:** AltStore or Sideloadly (free Apple ID, auto-refreshed every 7 days), or Appetize.io with a simulator build. The simplest is Safari → Add to Home Screen.
  - No usable Android emulator exists on iOS, or iOS emulator on Windows.
- **Not answered yet:** should the selection-menu colour dots use the last-used style? And the licence for the showcase repos (my suggestion: none).

## 4. Important facts
- **Repo:** github.com/2n0m0a5/quiet-reader (private), branch `main`. Clone at `/home/user/quiet-reader`.
  - Push with `git push -u origin main`.
  - End each commit message with:
    `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`
    `Claude-Session: https://claude.ai/code/session_01Ndr9g78Q5XsLXGvGGhVDz1`
- **Website:** https://quiet-reader-bgz.pages.dev (Cloudflare Pages, deploys on every push).
- **Android:** app ID `app.quietreader`; current version **0.3.4 / versionCode 340** (`android/app/build.gradle`, `package.json`).
  - Test signing key: `android/app/quiet-test.keystore`. Its SHA-256 starts with `305b375e`.
- **Build:**
  1. `npm run build`
  2. `npm run android` (build + cap sync)
  3. `cd android && ANDROID_HOME=/home/user/android-sdk ./gradlew assembleDebug --no-daemon -q`
  4. Copy `android/app/build/outputs/apk/debug/app-debug.apk` to `Quiet-<ver>.apk` and send it.
  5. Verify the signer with `/home/user/android-sdk/build-tools/36.0.0/apksigner verify --print-certs`, and the version with `aapt2 dump badging`.
- **Preview server for tests:** `npx vite preview --port 4173 --strictPort`. Run it in the background with a long timeout; it dies after 2 h.
- **Tests:** Playwright scripts are run as `node <name>.cjs <scratchpad>`.
  - They use `require('./common.cjs')(await chromium.launch())` (a returning reader: sets `quiet:welcome-seen` and `quiet:seen-version`), and CDP `Input.dispatchTouchEvent` for touches.
  - Suites:
    - Older: phone, library2, formats, reader2, settings, offline, phase1–8, fixes4.
    - New: motion, stats3, physics, formats3, fonts, series, quoteimg, curl, motion2.
  - Sample files: `books/` and `books/formats/` (made by `gen/mkformats.py`, which includes a hand-written stored RAR4 writer).
- **Key code:**
  - Main app wiring: `src/main.ts` (routing, tab bar, lens, swipe back, rubber bands, font choices).
  - Motion: `src/ui/motion.ts` (springs, drag sheets, marks, symbol effects, rollNumbers, animateLayout, rubberBand).
  - Styles: `src/styles/{tokens,app,styles,motion}.css`.
  - Reader:
    - `src/reader/reader.ts`: curl, swipe-turn, `animated` attribute.
    - `src/reader/chapter-pull.ts`
    - `src/reader/custom-fonts.ts`
    - `src/reader/fonts.ts` and `src/reader/themes.ts`: per-style fonts and palettes.
  - Formats:
    - `src/formats/formats.ts`: the single list of extensions.
    - `src/formats/load.ts`: dispatch.
    - `src/formats/office.ts`, `rtf.ts`, `palm.ts`, `archive.ts`: individual readers (`archive.ts` uses libarchive).
    - `public/vendor/libarchive/`: the libarchive worker and wasm (about 1 MB, cached on first use).
  - Stats: `src/stats/{stats,charts,tracker}.ts`, `src/stats-screen.ts`.
  - Quote cards: `src/ui/quote-image.ts`.
  - Native: `android/app/src/main/java/app/quietreader/QuietNativePlugin.java` (plus `shareImage` and `saveImage`). Its `BOOK_EXTENSIONS` must match `formats.ts`.
  - Database: IndexedDB `quiet`, `DB_VERSION` 4 (stores: books, files, collections, annotations, reading with hours/sittings, fonts).
- **Patched vendor foliate-js:**
  - `paginator.js`: `swipe-turn` attribute, spring page slide, overscroll none.
  - `comic-book.js`: case-blind natural sort, junk skip.
  - Type stubs: `vendor/foliate-js/*.d.ts` and `vendor/foliate-js/vendor/zip.d.ts`.
- **Docs:** `docs/release-notes-0.3.0`–`0.3.4.md`, `docs/research-0.3.3.md`, `docs/research-0.3.4.md` (sources: WWDC 2018/2023/2024/2025 and reading studies).
- **Owner's preferences:**
  - Plain words, short.
  - Wants an APK sent after every version, and a list of what to check on the phone.
  - Sometimes says "just remember, don't do anything": then only acknowledge.
  - Picks features by number from the 1–70 list.
  - Wants research-backed design, and nothing that looks AI-made.

## 5. What changed (all committed and pushed; nothing uncommitted)
- **0.3.4 (`5d93a1b`):**
  - New files: `src/formats/{formats,archive,office,rtf,palm}.ts`, `src/reader/custom-fonts.ts`, `src/ui/quote-image.ts`, `src/ui/range.ts`.
  - Rewritten: `load.ts`.
  - Edited:
    - `library-view.ts`: series, `data-book-id`, glide layout, long-press.
    - `main.ts`: zoom nav, swipe back, lens, fonts.
    - `reader.ts`: curl.
    - `motion.ts`: icons, numbers, layout.
    - `styles.css`: Frost.
    - `motion.css`: zoom, list, curl, frost motion.
  - Native: `QuietNativePlugin.java` (share/save); `AndroidManifest.xml` (new MIME types, WRITE_EXTERNAL_STORAGE up to SDK 28).
  - Config: `vite.config.ts` (libarchive runtime cache).
- **0.3.3 (`756ad5f`, fix `109f318`):** `src/stats/charts.ts`, `src/reader/chapter-pull.ts`, `src/ui/motion.ts`, `src/styles/motion.css`, the Tashkent block in `styles.css`; tracker records hours and sittings.

## 6. Problems and gotchas
- **The test scripts and sample books live only in the old session's scratchpad.** That's `/tmp/claude-0/.../scratchpad/test`, `books` and `gen`. A new session won't have them. Recreate them, or better, add a `tests/` folder to the repo.
- **View Transitions:** the update callback must not wait for `requestAnimationFrame`, or it's aborted with "timeout in DOM update". The fix: race the change against a timeout (`transition()` in `motion.ts`, `#curl` in `reader.ts`).
- **Chrome** draws a backdrop-filter element square while capturing it for a View Transition, and doesn't clip it on the zoomed tab bar. Frost uses `clip-path` and turns off the tab bar's own `view-transition-name`.
- **Inside the book iframe,** CDP touches report `screenY` 0. `chapter-pull.ts` computes the position from the frame's rect plus `clientY`.
- **Touch handlers** must guard `e.touches.length !== 1` (the pinch test sends empty touch lists).
- **The left 18 px `.edge-back` strip** forwards a plain click to `onTap('left')`. Without that, phase4 broke.
- **Phone `.btn.small`** squashes text buttons; override it with width/height auto (done for the series button).
- **phase5** can fail when Google translate rate-limits (429); MyMemory is the fallback. Rerun it.
- **The old `library.cjs` test** is obsolete (pre-v3 sort buttons). `library2` covers it.
- **libarchive Archive.init** uses `new URL('vendor/libarchive/worker-bundle.js', document.baseURI)`. The wasm must sit next to the worker.
- **The owner must do on the phone:** the checks listed in section 2.

## 7. Next steps
1. Copy the regression test scripts and sample-file generators into the repo (for example `tests/` and `tests/gen/mkformats.py`), so future sessions have them. Commit that.
2. Ask the owner for phone results on 0.3.4 (section 2 list) and fix what they report.
3. Start 3.x:
   - **Library backup and restore (1):** export books, files, positions, notes, quotes, stats, fonts and settings to one file; import it on another phone. Needed before the switch to the Play Store, which requires uninstalling.
4. Then **Report a problem (66):** a button that builds a short technical log and opens email or Telegram, so the user sends it themselves.
5. Bump the version (0.3.5 / versionCode 350), update `NEWS` and `showWhatsNew` in `src/app/onboarding.ts`, write `docs/release-notes-0.3.5.md`, run all suites, build the APK, commit, push and send.
6. Later, when asked: more styles (unused canvas ideas: House of Wisdom, Samarkand, Alhambra, Atlas silk), build-your-own style, iOS via Capacitor (item 56), Play Store setup (68), landing page (70), languages (62).

## 8. Starter prompt
Read `/home/user/quiet-reader/HANDOFF.md` first. It's the full context for my e-book reader Quiet: repo `2n0m0a5/quiet-reader`, branch `main`, current version 0.3.4. Then continue from step 1 of "Next steps". Keep explanations plain and short, send me an APK whenever a version is built, and tell me what to check on my phone.
