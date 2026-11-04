# Quiet 0.5.0 L2 — the native app reads books, and keeps them safe (test build)

## 0.5.0 L2.1 (fix)
- **Books opened blank** (EPUB: "Webpage not available", PDF: white pages). The reader closed the
  book file the moment it finished opening it. Now it stays open until you leave the book, and a
  test checks exactly that. If a book really can't be opened, it now says so on the page.
- The release build keeps the e-book engine whole (no parts removed by the optimizer).

Layers 1 and 2 of the native app (`docs/plan-native.md`), plus your two requests from L0.
It still installs **next to** Quiet 0.4.2 (app ID `app.quietreader.n`), so 0.4.2 is untouched.

## Your requests from L0
- **Text size is a slider** now (Settings → Interface): 80% to 150% in steps of 5. The whole
  app grows or shrinks as you slide, with a click at each step. A long tick marks 100%, and
  Reset brings it back.
- **Vibration works, and you can feel that it does:** every key, tab, choice and slider step
  clicks, and turning Vibration on gives one firm click. Turning it off stops them all.
  (Tests check both.)

## L1: read a book
- **Add books:** the + key or "Choose a book" (pick one or several), and **Open with Quiet** /
  **Share to Quiet** from a file manager or Telegram. EPUB, PDF and TXT for now; other formats
  arrive in L7 and say so.
- **Library:** your books as covers (or a plain cover with the title), the one you read last
  first, with a thin progress line (orange while reading, green when done) and New / 42% / Done.
  Long-press a book for Read and Remove.
- **Reading:** EPUB through Readium (the e-book engine), PDF through Android's own PDF renderer,
  TXT turned into a small EPUB (Russian/Uzbek .txt files in Windows-1251 are read correctly).
  Scroll or pages; tap the edges to turn pages in page mode, the middle for the bars.
- **Bars:** Back and the chapter at the top; your place as a scale with the orange needle, the
  page and percent, and **Contents · Day/Night · Text** at the bottom. They step aside while you
  read; the phone's status bar hides too.
- **Text:** book text size (a slider, 70–200%), font (Crimson Pro, Archivo, or the book's own),
  Scroll or Pages, and the theme. Your place is saved as you read.

## L2: your data is safe
- **Back up library** (Settings → Files & storage): one `.quietbackup` file with books, places,
  notes, quotes, stats, fonts and settings, **in the same format as 0.4.2**, so it works both ways.
- **Restore:** pick a backup made by 0.4.2 (or this app). It shows what's inside first, adds what's
  missing and never deletes. Settings come along into an empty library. Notes, collections and
  reading time from 0.4.2 are kept safely until their layers (L4, L5, L6) show them. Books in
  formats this build can't open yet (MOBI, FB2, comics…) are kept too.
- **Recently deleted:** removed books wait 30 days; put them back or delete them for good.
- **Safety copy:** a second copy of the library's records, so a damaged database can be rebuilt.
- **Last backup** date and a "No backup in N days" reminder; **Delete everything** (asks first).

## Check on your phone
1. Settings → Interface → Text size: slide it. Does the app resize smoothly, with clicks?
2. Vibration: off → no clicks; on → one firm click, then clicks again.
3. **In 0.4.2**, make a backup (Settings → Files & storage → Back up). **In the native app**,
   Settings → Files & storage → Restore → pick that file. Do your books and covers come back?
   Are the reading places roughly right (the right chapter)?
4. Open an EPUB: does it open where you were? Scroll, then switch to Pages (Text → Reading mode)
   and tap the edges. Contents: does it jump? Day/Night and font changes: does the page follow?
5. Open a PDF and a TXT. Russian TXT: are the letters right?
6. Close a book and reopen it: same place? Close the app completely and reopen: same place?
7. Open a book from Telegram or a file manager with "Open with" → Quiet (the native one).
8. Long-press a book → Remove → find it in Settings → Files & storage → Recently deleted → put it back.
9. Back up in the native app, then restore that file in 0.4.2: do the books appear there?
10. Anything slow, stuck, or a crash: tell me what you did just before.
