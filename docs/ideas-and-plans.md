# Ideas, plans and things the owner asked to remember

This covers everything beyond the feature list. The features themselves (1–70, plus A and B) are in [roadmap.md](roadmap.md).
It's gathered from every "remember this" and every topic the owner was keen on, from 21 October to 3 November 2026.

## Why Quiet exists (the owner's own answers, first day)
- It started as one HTML file (`quiet_epub_reader4.html`) that the owner made and was happy with. The aim is to turn it into an official app: **Android first, then iOS, then Windows/desktop and macOS, maybe more.**
- It's personal: the owner wanted their own reader, even though many exist. The idea of publishing on Google Play and the App Store came while building it: **"maybe I can earn money on it"** (see Quiet+, item 69).
- **Continuous scroll is the default**; page mode is an option.
- It should open **any kind of book file** that's appropriate.
- The name is **Quiet**. Caution: it may be taken in the stores and is hard to search for, so check the store listing name early (for example "Quiet Reader").
- The owner wants to be **consulted, and to have Claude think before coding**. They want **every step explained from scratch**, from opening the project to publishing on Google Play and hosting.
- The core message, from the owner's video idea: most reader apps are "oversaturated" with too many features, accounts and payments. **A reader should be very simple. Period.**

## Promotion: building in public on TikTok, YouTube and Instagram
- **The owner's idea:** post real videos of the building process. Not the "AI dropshipper / small business" style, which the owner called "shitty and goofy".
- **The series, one video per version:**
  1. The problem (oversaturated readers), then "I'll use AI to build a small HTML file that opens EPUBs". That was the first version.
  2. The web app on GitHub, hosted online.
  3. The installable web app (an Add to Home Screen shortcut, works offline).
  4. Wrapped as an APK (Android 0.1.0).
  5. The second APK (0.2.0).
  6. And so on, for each later version (0.3.x and the styles, motion and features from this session are material for more videos).
- **How it works:**
  - The owner records the footage and sends it to Claude to edit.
  - Source code for each video is linked in the caption.
  - The goal is to go viral and get traction, so the owner wants a content recipe, strategy and formula.
- **The strategy document:** **[Quiet Content Strategy](https://claude.ai/artifact/PGHx9eLqqXn8jADtMLurik)** (a claude.ai artifact the owner can edit and comment on). It has:
  - The angle: "reading apps got loud, so I'm building a quiet one, in public."
  - A five-beat formula for every video: hook, tension, build, payoff, then a loop to the next part.
  - For videos 1–5: three hook options each, a timed script (what's on screen and what's said), shots to record, the caption, the pinned comment and a closing question.
  - A recording checklist, a TikTok vs Reels vs YouTube comparison, a weekly posting rhythm, the numbers to watch, how editing with Claude works, and a "never do" list (no AI-dropshipper style).
- **Showcase repos (public), one per video:**
  - Repos:
    - `quiet-reader-version-1`: the original HTML file, with the OceanofPDF filename line removed.
    - `quiet-reader-version-2`: the web app with the library, plus automatic GitHub Pages publishing.
    - `quiet-reader-version-3`: installable and offline, phone-first reading.
    - `quiet-reader-version-4`: Android 0.1.0, plus an automatic APK build on GitHub.
    - `quiet-reader-version-5`: Android 0.2.0, plus an automatic APK build on GitHub.
  - All live at github.com/2n0m0a5, and all are pushed.
  - Each has a README for viewers, with a "try it" link (`2n0m0a5.github.io/quiet-reader-version-N`) and the series list.
  - The public copies leave out the private test signing key and use GitHub's no-reply email instead of the owner's Gmail.
  - **The owner still needs to:**
    - Turn on Pages in version-2 to version-5 (Settings → Pages → Source: **GitHub Actions**), then ask Claude to re-run the site builds and check each one loads.
    - Publish the GitHub Releases by hand (Claude's session can't create releases or tags):
      - version-4: tag `v0.1.0`, title "Quiet 0.1.0 — the Android app", file `Quiet-0.1.0.apk`.
      - version-5: tag `v0.2.0`, title "Quiet 0.2.0 — Android app, version 2", file `Quiet-0.2.0.apk`.
      - The release texts were `notes4.md` and `notes5.md`, sent in the old chat. Ask Claude to write them again if they're lost; the APKs can be downloaded again from each repo's Actions tab.
  - **Licence for the showcase repos:** still undecided. The suggestion is none, because Quiet may be sold. With no licence, people can read the code but not legally reuse it.
  - The owner accepted that version-5 makes Quiet 0.2.0 public.
- **Next for promotion:** the owner films Video 1 using the checklist and sends the clips for editing.

## Design: what the owner wants
- **It must look human-made, not AI-made.** It should be minimal, with a "bare minimum" narrative.
- **Inspirations the owner raised:**
  - Uzbek atlas (ikat) silk: "maybe too weird".
  - Old Tashkent: became the third style.
  - The Islamic golden age: House of Wisdom was liked but not built yet.
  - Apple: why iOS feels comfortable (intuitive design and animation).
- **Design canvas:** all seven concepts and the detailed Star and Paper boards are at https://claude.ai/artifact/Grn4yeEzztYdj9LLeS5vzR. The owner liked 1–4 most (Quiet Star, Paper, Old Tashkent, House of Wisdom). Unused so far: House of Wisdom, Samarkand, Alhambra, Atlas silk.
- **Styles must differ in kind, not just in colour** (the reason Lamplight was rejected). **Each style has its own signature animation**, on top of the shared ones.
- **Motion must feel like iOS ("dynamic, responsive animations") on everything:**
  - icons, screen transitions, and opening and closing books
  - rubber-band scrolling that eases back (never snaps)
  - a chapter that turns only when you let go past a set point
  - drag-to-close sheets, swipe back, haptics
  - one clear action per screen
- The owner wants **research-backed** choices: they asked for academic papers on design, colour, animation and why Apple succeeds (see `docs/research-0.3.3.md` and `docs/research-0.3.4.md`).
- **The owner sets clear milestones** so requests don't become endless work. For example, 0.3.3 was exactly 5 stats, the animations and 1 style.

## Design rounds of 3–4 November 2026 (how Radio was chosen)
- The owner rejected all four styles ("I don't like any of them") and asked for one new design, a new icon, and research on design and colour psychology.
- **Round 1** (Grey e-ink, Moss, Cover-coloured): rejected. "All look AI generated; logos generic." But they liked the controls at the bottom of the reader: the thumb zone, as most readers have (Hoober 2013: 49% of touch use is one-handed).
- They asked to start from the name "quiet" and to look outside reading apps (other industries).
- **Round 2:**
  - Rest (music: the whole-rest sign)
  - Dial (1960s Braun hi-fi: a knob turned all the way down; colour only for function)
  - Do not disturb (hotel door hanger)
- **The owner chose Dial**, then "more like a radio": tuning-scale progress, band-selector tabs, keys, a VU-style needle meter, speaker-grille texture.
- Everything is on the canvas: https://claude.ai/artifact/63xpVE679aXM9svyeBvaGk (pages Radio, Round 2, Round 1).
- **Research used:**
  - Valdez & Mehrabian 1994 and Wilms & Oberfeld 2018: saturation and brightness drive emotion.
  - Elliot & Maier 2014: colour meaning depends on context.
  - Piepenbrock et al. 2013: dark text on a light page is read better.
  - Chang et al. 2015: evening screen light disturbs sleep, hence warm night colours.
  - Wallace et al. 2022: one font doesn't fit all.
  - McDougall et al. 1999: simple, familiar icons are recognised fastest.
  - Reber et al. 2004: processing fluency.
  - Braun's functional colour coding.
- Owner's instructions on structure (0.4.0):
  - Settings as separate "rooms": interface, reader, library, files…
  - About with all the data, new features and a tutorial (tooltips).
  - Stats only in its tab.
  - Reader controls: day/night, chapters, brightness, font size, search, visual options, customize the bar.
  - More book info.

## The native app (4 November)
- The owner asked for a fully native app ("no Capacitor, fully on the phone"), built in layers
  like the web → Capacitor path, but on its own route. Agreed: Kotlin + Compose, Android first,
  Readium for book pages, iPhone later as its own Swift app. Same name, icon and design; only the
  app ID differs while building (`app.quietreader.n`). Versions `0.5.0 L0`, `L1`… and plain
  `0.5.0` at the switch. Plan: `docs/plan-native.md`.

## Security and speed (5 November): remind the owner
- After 0.5.0 L8.4 ("the best version for now") the owner asked to think about security and,
  most of all, efficiency, then for a plan they can check: what will be done, and evidence for each
  fix. Plan: https://claude.ai/artifact/3XYFCvtj7qs2Xc14Tk39th and `docs/plan-security-efficiency.md`.
- They said: **"for now just remember everything. I'm gonna need you to remind me later."** So at the
  start of the next session, remind them: the plan, the public repo with the signing key in it, and
  the 5 decisions (repo public/private, new key custody, history rewrite, encrypted backups
  native-only, app lock timing).
- How they want to verify: before/after tests, GitHub Actions checks, a Diagnostics page in the app,
  a test book, an evidence report per release.

## No AI ("no AI washing")
- The owner doesn't want AI features in the reader (like greenwashing: "AI washing"). About says "No AI features". AI voiceover (idea A) is on hold.

## Testing iPhone builds without an iPhone (4 November)
- Appetize.io (the owner has an account; free plan, 30 minutes a month) runs the **simulator** build (`Quiet-iOS-simulator.zip`, from the iOS workflow). A device `.ipa` can't run in a simulator.
- Real iPhone: Sideloadly or AltStore with a free Apple ID (7-day renewals).
- Cloud real devices (BrowserStack App Live, LambdaTest, Sauce Labs) have trials; an unsigned .ipa may need signing first.
- There's no iOS emulator on Windows.
- The iPhone app adds three sample books on first start for this.

## Apps the owner wanted to look at for ideas (research of 25 October)
- **Top 6 to install first on the Redmi:** Readest (same engine as Quiet), Moon+ Reader, ReadEra, KOReader, WEBTOON and Mihon, and Google Play Books.
- **Others with ideas worth borrowing:**
  - E-book readers: Lithium, Apple Books, Kindle (X-Ray, Word Wise, Vocabulary Builder), Kobo (stats), Libby, Thorium (accessibility).
  - Comics: Perfect Viewer, Panels/Chunky, ComiXology Guided View.
  - Papers and PDFs: Zotero, Semantic Reader, LiquidText, MarginNote, Adobe Liquid Mode, ReadCube.
  - Read-later: Readwise Reader and Readwise (daily review), Matter, Instapaper.
  - Languages: LingQ/Readlang, Beelinguapp, ElevenReader/Speechify, Kindle Immersion Reading, Bionic Reading.
  - Reading trackers: StoryGraph, Goodreads, Bookmory.
- **Caution:** Readest and KOReader are AGPL. Borrow ideas, never their code. Mihon is Apache-licensed.
- The owner hasn't yet said which of these apps' features they liked.

## Platforms, stores and money
- **Google Play:** a $25 one-time developer account, a privacy policy, store listing, screenshots and a real (secret) signing key (item 68).
- **Before switching from the test APK** to the Play version, backup and restore (item 1) is needed: the switch means uninstalling, which deletes the library.
- **Apple:** $99 a year for the App Store or TestFlight. The free routes are in roadmap item 56: AltStore or Sideloadly, Appetize.io, and Safari's Add to Home Screen.
- **Desktop:** the original plan said Tauri for desktop. The later decision was a Windows app first, together with Wi-Fi transfer (items 57 and 51).
- **Sync without running servers:** through the user's own Google Drive, iCloud or WebDAV (item 53). It costs nothing and fits the privacy promise.
- **Font licences:** every bundled font is under the SIL Open Font License, so it's fine to ship.
- **Not possible:** DRM books (Kindle purchases, Adobe DRM, library loans). The app should say clearly that it opens DRM-free books.

## Hosting and devices (history)
- The owner had no web domain and wanted free hosting. GitHub Pages didn't offer the "GitHub Actions" source for the main repo, so the main site went to **Cloudflare Pages: https://quiet-reader-bgz.pages.dev**, which deploys on every push.
- **Devices:** a Windows PC and a **Redmi Note 13 Pro**. The owner installs APKs by sending them to themselves on Telegram (no USB debugging or Android Studio).

## How the owner likes to work
- (4 November) Uses Claude with 5-hour usage limits: when low, save progress to the docs and stop cleanly. "No handoffs, you continue" means keep working. "Double quick" means do it fast with minimal ceremony. Use only the skills that are needed.
- About credits line, by the owner's choice: "made by extrasolarions on howling dark".
- **Plain, short explanations** (the owner is a frontend developer, not a software engineer). Step-by-step guidance where needed.
- **"Just remember, do nothing" means exactly that:** only acknowledge, and keep the note.
- **Picks features by number** from the 1–70 list, and sets milestones per version.
- **Wants after every version:**
  - an APK sent
  - release notes for GitHub
  - a list of what to check on the phone
- **Every version goes to GitHub Releases;** the owner publishes them by hand.
- **Asks for a full "remind me everything" list** before starting new work. Keep `roadmap.md` and this file up to date so that's easy.
