# Quiet 0.5.0 L7 — every format (native test build)

Layer 7 (`docs/plan-native.md`). Installs over L6.1, next to 0.4.2.

## What's new
- **Every format 0.4.2 reads**, now in the native app:
  - **Kindle:** MOBI, AZW, AZW3 (KF8) and PRC, including "joint" files and Kindle comics, with
    their cover, contents, links and fonts (not DRM-protected ones).
  - **FB2** and **FBZ** (with notes that open in a popup, epigraphs, poems, pictures, series).
  - **Word (DOCX)**, **OpenDocument (ODT)**, **RTF** (Russian Windows-1251 RTF too),
    **Markdown**, **HTML** and **HTMLZ**, **Palm (PDB)**.
  - **Comics:** CBZ, CBR, CB7, CBT (and zip, rar, 7z, tar of pictures), one page a screen, in
    natural order (2 before 10). CBR made with RAR 5 can't be read yet: it says so.
- How: each one becomes an EPUB when you add it, so they all read the same way (Text, highlights,
  search, curl, stats). The original file stays the book (it's what backups carry), and books
  restored from 0.4.2 in these formats are converted the first time you open them.
- **PDF zoom:** pinch with two fingers, or double-tap; drag to move around. In Pages mode the
  page doesn't swipe while zoomed; turning the page resets the zoom. Zoomed pages are drawn sharper.
- The file picker shows every file (phones label book files loosely); Quiet says plainly if one
  isn't a book it can read. "Open with Quiet" offers itself for the new kinds too.

## Check on your phone
1. A MOBI and an AZW3 from your collection: cover, title, contents, pictures.
2. An FB2 (a Russian one): text, notes (tap a note number), cover.
3. A Word, RTF or ODT file.
4. A CBZ or CBR comic: one page a screen, the right order.
5. A PDF: pinch, double-tap, drag; turn pages in Pages mode.
6. Your 0.4.2 backup's MOBI/FB2/comic books: do they open now (the first time takes a moment)?
