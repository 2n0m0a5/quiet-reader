# Quiet roadmap: every feature idea and plan

This is the full list we've discussed, kept in one place. Non-feature plans (videos, showcase repos, design inspirations, stores) are in [ideas-and-plans.md](ideas-and-plans.md). The numbers 1–70 are from the original list; the owner chooses features by number.
Sizes: **S** = hours, **M** = a day or two, **L** = several days or more.
Status: ✅ done (with the version) · ⏭ next · 🕒 placed in a later version · ○ not chosen yet · ✕ dropped.

## A. Library and organizing
1. ✅ 0.4.0 **Library backup and restore** (M): everything in one file (books, positions, notes, quotes, stats, fonts, settings), to import on another phone or the Play version. Needed before the switch to Google Play, which requires uninstalling the test app and so deletes the library. **Next, in 3.x.**
2. ✅ 0.3.1, done differently: **book folders**. Instead of scanning the whole phone, you choose one or more folders and Quiet adds every book in them. The app checks the folders on start and when you come back after 10 minutes. The app explains that you should prepare a folder full of books first.
3. ✅ 0.3.4 **Series**: a Series shelf of covers, each series in order with a "next book" button, and Group by series. (Open: series aren't read from the book's own metadata yet; they come from Edit.)
4. ✅ 0.3.0 **Tags**
5. ✅ 0.3.0 **Change a book's cover**
6. ✅ 0.3.0 **Edit more details** (series, description, language)
7. ✅ 0.3.0 **Select several books at once**
8. ✅ 0.3.0 **"Want to read" status**
9. ✅ 0.3.0 **Ratings and a short review**
10. ✅ 0.3.0 **Shelf view**, made bigger with pinch resize after the owner's feedback
11. ✅ 0.3.0 **Library stats card**
12. ✅ 0.3.0 **Sort by series, rating or size**, and group by author (group by series came in 0.3.4)
13. ✅ 0.3.0 **Duplicate finder**
14. ✅ 0.3.0 **Recently deleted** (30 days)

## B. Reading experience
15. ✅ 0.3.0 **Footnote popups**
16. ✕ **Offline dictionary**: dropped, because translation (46) covers it and shows word meanings.
17. ○ **Read aloud (text-to-speech)** (L): uses the phone's own voices, so it's free and offline. It would highlight the sentence being read and have a sleep timer. (Not the same as A below.)
18. ○ **Auto-scroll** (M)
19. ○ **Two pages side by side** on tablets and in landscape (S)
20. ✅ **Page-turn animation**: slide in 0.3.3, curl in 0.3.4; rebuilt like paper in 0.4.0 (page mode only; choosing Curl switches to pages); **follows the finger in 0.4.2** (corner under the fingertip, turns past ~40% or on a flick, otherwise settles back).
21. ✅ 0.3.0 **More themes**: Black, Custom and System. The owner asked to replace "auto night / night theme" with a System theme.
22. ✅ 0.3.4 **Custom fonts** (TTF, OTF, WOFF, WOFF2; Settings → Your fonts)
23. ✅ 0.3.0 **Typography**: weight, paragraph spacing, indent, hyphenation. Whole words are now the default.
24. ✅ 0.3.0 **Warm light**
25. ✅ 0.3.0 **Reading ruler / line focus**
26. ✅ 0.3.0 **Bionic reading**
27. 🕒 v4 **Custom gestures and tap zones** (M): choose what each tap, swipe and volume key does.
28. ✅ 0.3.0 **Orientation lock**
29. ✅ 0.3.0 **Time and battery**
30. ✅ 0.3.0 **Go to a percentage, and back after a link**
31. ✅ 0.3.0 **Full-screen image viewer**
32. ✅ 0.3.0 **Better PDFs**: zoom buttons and pinch zoom. ○ **32b Crop white margins** is the part not done.
33. ○ **Reflowed PDFs** (L)

## C. Notes, highlights and learning
34. ○ **Export highlights** as a Markdown or text file, or share them (S). "Copy all" exists; a file export doesn't.
35. ✅ 0.3.0 **All notes in one place** (the Notes tab; called the "Commonplace book" in Paper)
36. ○ **Daily highlight review** (M)
37. ○ **Vocabulary builder** from words you translated (M)
38. ○ **Flashcards from highlights**, with spaced repetition (L)
39. ✅ 0.3.0 **Underline and strikethrough**
40. ✅ 0.3.4 **Share a quote as an image** (post, square or story, in the current style)
41. ✅ 0.3.0 **Tags on highlights**
- ✅ Owner's additions: **Quotes** kept separate from highlights (0.3.0), and **quote owners**: who says it, a character or the narrator, credited to the book's author (0.3.1).

## D. Reading stats and goals
42. ✅ 0.3.0 **Reading statistics** (the tab is called **Stats** since 0.4.0; Radio look with a needle meter, week lights and readouts in 0.4.1). ✅ 0.3.3 added five more:
    - the reading-year calendar
    - when you read
    - your pace
    - finished books by month
    - book by book, with time left
43. ✅ 0.3.0 **Reading goals** (daily and yearly, with a gentle streak)
44. ○ **Year in review** summary card (M). The reading-year calendar is a start.
45. ✅ 0.3.0 **Session timer** ("You read for 25 min")

## E. Languages
46. ✅ 0.3.0 **Translate inside Quiet**: Google, with MyMemory as a backup when Google refuses (0.3.1). The privacy note says the selected text is sent.
47. ○ **Known-words tracking**, like LingQ (L)
48. ○ **Parallel reading**: original and translation side by side (L)

## F. Getting books
49. 🕒 later **Free book catalogues (OPDS)**: Project Gutenberg, Standard Ebooks (M)
50. ○ **Download from a link** (S)
51. 🕒 v4 **Wi-Fi transfer**, built together with 57. The phone runs a tiny local server, and you drag books in from the computer's browser on the same Wi-Fi.
52. ○ **Send to Quiet by email or a Telegram bot** (L, needs a server)

## G. Sync and devices
53. ○ **Sync through your own Google Drive or WebDAV**, with no Quiet server (L)
54. ○ **Quiet accounts with cloud sync** (L, needs a server and money)
55. 🕒 v4 **Android home-screen widget**: "Continue reading" with the cover and progress
56. ✅ iOS 1.0 (build 1), 4 Nov 2026: Capacitor iOS, built unsigned on GitHub's macOS machines (`.github/workflows/ios.yml`), plus a simulator zip for Appetize.io. Book folders, volume keys and rotation lock are Android-only. Next: real-iPhone feedback; App Store/TestFlight needs $99/year. Earlier notes:
    - **Building:** Capacitor iOS turns the same web code into an `.ipa`. It needs a Mac, or a cloud Mac such as GitHub Actions macOS.
    - **Native parts to port:** vibration, keep-awake, book folders, "Open with Quiet", and sharing pictures. Volume-key page turns probably can't work on iOS.
    - **Installing:** the App Store and TestFlight need Apple's $99 a year. A free Apple ID gives 7-day installs. AltStore or Sideloadly on Windows renews them automatically, for free long-term testing with up to 3 apps.
    - **Emulators:** there's no usable Android emulator on iPhone (UTM SE is too slow), and no iOS emulator on Windows.
    - **Online option:** Appetize.io streams an iOS simulator to the browser. It needs a simulator build and has a limited free tier.
    - **Simplest test now:** Safari → Add to Home Screen on https://quiet-reader-bgz.pages.dev.
57. 🕒 v4 **Desktop app for Windows** (also Mac and Linux), with 51.

## H. Comics and manga
58. ○ **Manga and webtoon modes**: right-to-left, double pages, vertical strip (M)
59. ○ **Panel-by-panel view** on phones (L)
60. ✅ 0.3.4 **CBR and 7z**, plus more formats:
    - comics: CB7, CBT, and zip/rar/7z/tar archives of pictures
    - books: DOCX, ODT, RTF, Markdown, PDB/PRC and HTMLZ

## I. App polish
61. ✅ 0.3.0 **First-run welcome** (the wording follows the chosen style)
62. 🕒 later **App languages**: Russian first, then Spanish, German, French, Portuguese and Turkish, maybe Uzbek. Best done once the screens stop changing.
63. ✅ 0.3.0 **Basic accessibility pass**
64. ✅ 0.3.0 **Haptics** (fixed to use the vibration motor in dev.4)
65. 🕒 v4 **App lock** (PIN or fingerprint)
66. ✅ 0.4.0 **Report a problem**, option A: a button that prepares a short log the user sends themselves by email or Telegram. No servers involved, so the privacy promise holds (Sentry and Crashlytics were rejected). **Next, in 3.x.**
67. ✅ 0.3.0 **"What's new" screen** (updated every version)

## J. Business and Play Store
68. 🕒 later **Google Play release setup**: real signing key, store listing, screenshots, privacy policy. Needs 1 (backup) first.
69. ○ **Quiet+** one-time purchase (L)
70. 🕒 later **Website landing page**, right before the Play launch

## Owner's own later ideas
- **No AI features** (owner, 0.4.0: no "AI washing"). About says so.
- **A. AI voiceover / read aloud with excellent voices.** (On hold: see No AI features.) Only if the quality is truly natural. It would probably be a paid online service that sends text out (a privacy change), so possibly a Quiet+ feature. Placed in 🕒 v4.
- **B. Fully native Android app (Kotlin, no Capacitor).** Idea only: we agreed the current approach is better for now.
- ✅ Done from the owner's small requests: Contents button moved, app-wide text size, whole-word line breaks (hyphenation off by default), Quotes.

## Design and motion
- ✅ Styles (Settings → Style):
  - **Quiet Star** (default) and **Paper**: 0.3.2
  - **Old Tashkent**: 0.3.3. It replaced Lamplight, which was rejected as "just Star with different colours".
  - **Frosty Glass**: 0.3.4
- ✕ 0.4.0 replaced the styles with one design, **Radio** (the owner disliked all four). Old idea list: Unused ideas from the design canvas (https://claude.ai/artifact/Grn4yeEzztYdj9LLeS5vzR): House of Wisdom (Baghdad manuscripts), Samarkand (turquoise tiles at night), Alhambra, Atlas silk (Margilan ikat). The owner liked styles 1–4 (Star, Paper, Old Tashkent, House of Wisdom).
- ✕ **Build your own style** (dropped with the styles in 0.4.0) (marked "Coming soon"): your own colours, type, shapes and ornament.
- ✅ Motion direction: Apple-like and responsive.
  - 0.3.3: springs, rubber-band scrolling, the chapter pull that turns only on release, drag-to-close sheets, and each style's own motion.
  - 0.3.4: zoom open/close from covers, interactive swipe back, icon effects, gliding lists, long press, rolling numbers, page curl.
  - The research is in `docs/research-0.3.3.md` and `docs/research-0.3.4.md`.

## Shipped in 0.4.x (3–4 November 2026)
- 0.4.0: safety copy and automatic recovery (the lost-library bug), readable book colours (the invisible chapter), Motion setting, clicks during screen changes, mouse wheel in dialogs, messages above dialogs, Sepia removed, backup/restore (1), Report a problem (66), the Radio design and knob icon, the reader bar (customizable keys), fuller book info, Settings in sections, About, the tour.
- 0.4.1: Stats in the Radio look. 0.4.2: the curl follows the finger.

## Open questions for the owner
- Should the colour dots in the selection menu keep the last-used colour, or always be a plain highlight?
- Read series from the book's own metadata (EPUB calibre:series / belongs-to-collection)?
- Licence for the public showcase repos? (My suggestion: none.)

## Not realistic
- Books with DRM (Kindle purchases, Adobe DRM, LCP library loans).
- Kindle-style X-Ray, unless much later with AI.

## Owner's to-dos (not features)
- Publish GitHub Releases for v0.1.0 to v0.4.2 (each with its APK and `docs/release-notes-*.md`), and iOS `ios-v1.0` with the .ipa and `docs/release-notes-ios-1.0.md`. I can't create releases.
- Enable GitHub Pages (Source: GitHub Actions) in the showcase repos `quiet-reader-version-2` to `-5`, then ask me to re-run their deploys.
- Promotion: post the building process (coding, design decisions, before/after, new features) on TikTok, YouTube and Instagram. Send the video footage and I'll edit it.
- Check on the phone: the lists under "Check on your phone" in `docs/release-notes-0.4.0.md`, `-0.4.1.md`, `-0.4.2.md` and `-ios-1.0.md`.
