# Quiet 0.4.0 — plan

## Progress (updated 4 November 2026)
- ✅ Released as 0.4.0: fixes F1–F9, curl on phones, backup and restore, Radio design and icon,
  reader bar, book info, Settings sections, About, tour, Report a problem. Follow-up: the Stats
  tab's needle meter and light-up week bars (it has the Radio look, not the new layout yet).

## Earlier progress notes
- ✅ F1–F9 fixed, tested, pushed (see the commits "0.4.0 F1…F9"). Test suites: safety, legibility,
  motion-settings, desktop, quote-save, curl (+ smoke, formats, gestures). All pass.
- ⏭ Next: backup and restore (section 2). Design notes for it:
  - One `.quietbackup` file = a stored (uncompressed) zip: `quiet-backup.json` (same records as the
    safety copy's library.json: books without covers, collections, annotations, reading, font
    records, `quiet:*` settings) + `books/<id>`, `books/<id>.cover-<size>`, `fonts/<id>`.
  - Refactor `src/library/safety.ts` so backup and the safety copy share one "collect everything"
    function. Small zip writer/reader in `src/library/zip.ts` (store only; read deflate via
    DecompressionStream for safety).
  - Android: save with ACTION_CREATE_DOCUMENT (the reader picks where), written in chunks through
    the bridge (like safetyWrite). Website: a download.
  - Restore merges (adds and updates, never deletes); settings are restored only into an empty library.
  - Settings: "Back up library", "Restore from a backup", "Last backup: …" (localStorage
    `quiet:last-backup`), and the "No backup in N days" reminder.
- Then: sections 3–6 (Radio design, structure, reader bar, book info, settings, about, tour, report).
- Owner's answers: one release (0.4.0, no 0.3.5); reader keys Contents · Day/Night · Light · Text ·
  More; tab name Stats; About says "made by extrasolarions on howling dark"; curl like Apple Books
  (done); data loss happened after installing over the old APK, EPUB, plenty of storage; phone's
  "Remove animations" is off.
- Still to ask the owner: if the desktop reading-settings button still fails after 0.4.0, which browser.

0.4.0 = the fixes you reported + the new **Radio** design + the new app structure.
Design canvas: https://claude.ai/artifact/63xpVE679aXM9svyeBvaGk (page "Radio").
Size: big (L). Version 0.4.0 / versionCode 400.

## 1. Fixes (first, before any design work)

| # | Problem you saw | What will be done |
|---|---|---|
| F1 | **Library wiped** after the app was closed: books, notes and stats gone | Find the real cause and fix it. Make the phone keep Quiet's storage ("persistent storage"). Never wipe the library silently: if the stored data can't be read, keep it and show a message. |
| F2 | **Folder says "no new books"** while the library is empty | The folder check will compare against what's really in the library, not its own old list. |
| F3 | **Blank chapter while scrolling** (invisible text, but links still work) | Reproduce it in the tests, then fix it. |
| F4 | **Animations missing** on the Android app and the mobile web | Find why (likely the phone's "remove animations" setting) and fix it. Add a setting: Motion: Full · Reduced · Follow phone. |
| F5 | **Page curl looks weird and unnatural** | Rebuild the curl properly (a real page bending around a cylinder, with shading). If it still doesn't feel right, Slide stays the default. |
| F6 | **Desktop: the reading settings button does nothing** | Fix. |
| F7 | **Desktop: the mouse wheel doesn't scroll "What's new"** | Fix, and check every other scrolling list with a mouse. |
| F8 | **No message after saving a quote picture** | Show "Saved to Pictures/Quiet" (or the error). |
| F9 | **Remove Sepia** everywhere | Removed; anyone using Sepia moves to Day. |

## 2. Safety: library backup and restore (roadmap #1)

Because of F1, this moves into 0.4.0.
- **Back up:** one file with your books, places, notes, quotes, stats, fonts and settings.
- **Restore:** from that file, on this phone or another one (and later on the Play Store version).
- **Reminder:** Settings shows "No backup in N days".

## 3. New design: Radio

- **One design** instead of four styles. Star, Paper, Old Tashkent and Frosty Glass are removed; anyone using one moves to Radio.
- **Day and Night versions,** plus Follow phone.
- **Look:** warm grey "casing"; orange means "you are here", green means "finished". There's a dotted speaker grille, keys that press down a little, and numbers like a radio readout.
- **New app icon:** a volume knob turned all the way down. It's also used on the splash screen and the website icon.
- **Fonts, all free to ship and working offline:**
  - Archivo for menus and labels
  - DM Mono for numbers
  - Crimson Pro for book text (the other book fonts and your own fonts stay)
- **Motion:** keys press and click (vibration), the needle glides, sheets slide. No bounce.

## 4. New structure

**Tabs:** Library · Notes · Stats · Settings.

**Library**
- The stats strip at the top is removed.
- "Reading list" is removed from the ⋯ menu; Stats opens only from its own tab.
- The ⋯ menu only has library actions: select, collections, duplicates, recently deleted.
- Filters: All · Reading · Series · Done.

**Reader**
- **Top:** Back, chapter name, Bookmark.
- **Bottom (within your thumb's reach):**
  - Progress as a tuning scale: chapters are the stations, the needle is your place, with the page and minutes left.
  - Five keys: **Contents · Day/Night · Light · Text · More** (you can change the first four).
- **More panel:**
  - Day / Night / Follow phone
  - Brightness (new)
  - Warmth
  - Text size and font
  - Look & layout
  - Bookmarks
  - Notes & quotes
  - Go to page
  - Book info
  - Customize bar
- **Customize bar:** pick four keys and drag them into order; More always stays.

**Book info (bigger)**
- **The book:** length in pages and words, reading time at your pace, chapters, publisher, date, language, ISBN, subjects, description.
- **The file:** format and version, size, file name, the folder it came from, pictures and fonts inside, date added, copy protection.
- **Your reading:** hours, sittings, highlights, notes, quotes, date started.
- **Actions:** Read, Status, Tags, Share file, Edit, Export notes, Remove.

**Stats**
- A needle meter for today's goal, the week as light-up bars, streak, reading speed, books this year.
- The five detailed stats (year, hours, pace, finished by month, every book) stay one tap away.

**Settings**
- Six sections:
  1. Interface
  2. Reader
  3. Library
  4. Files & storage
  5. Notes & goals
  6. Privacy & translation
- Then the backup reminder, About Quiet and Report a problem.

**About Quiet**
- Version, Take the tour, How to…, What's new.
- Your data: books, space used, highlights, notes, fonts, last backup.
- Promises: no account, no ads, nothing leaves the phone except translation, **no AI features**.
- Privacy policy, fonts and code credits, "Made by".

**Tour (replaces the welcome screens)**
- 7 tooltips on the real screens: add books, open a book, the reader bar, Text, Light, highlighting, backup.
- It can be skipped, and replayed from About.

**Report a problem (roadmap #66)**
- Builds a short technical report you can read first, then send yourself by email or Telegram. No servers.

## 5. Order of work

1. Fixes F1–F3 and backup/restore (data safety first).
2. Fixes F4–F9.
3. Design foundation: colours, fonts, icon, keys; remove the old styles and Sepia.
4. Library, tabs, Stats, Notes in the new design.
5. Reader: bottom bar, More panel, brightness, customize bar, new curl.
6. Book info, Settings sections, About, tour, Report a problem.
7. Tests for all of it, release notes, What's new, APK, and a list of what to check on your phone.

Option: steps 1–2 can go out first as a 0.3.5 test APK, so you're protected from data loss sooner.

## 6. Not in 0.4.0

- iOS app (#56): a separate step after 0.4.0.
- Fully native Android app without Capacitor: idea only, for later.
- AI features: none, by decision ("no AI washing").
- Play Store release (#68): after 0.4.0 and backup are proven on your phone.

## 7. Open questions

1. ✅ Answered: Contents · Day/Night · Light · Text · More (Search lives in More).
2. Tab name "Stats" — OK?
3. "Made by" line in About: your name and city?
4. About the data loss: had you just installed a new APK over the old one? Was the phone low on storage? Which book format?
5. Is "Remove animations" (or any animation scale other than 1x) turned on in your phone's developer or accessibility settings?
6. Which page curl feels right to you: Apple Books, Google Play Books, or a real paper book?
7. Ship steps 1–2 early as 0.3.5, or everything together as 0.4.0?
