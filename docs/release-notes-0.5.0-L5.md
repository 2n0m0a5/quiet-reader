# Quiet 0.5.0 L5 — organizing (native test build)

## 0.5.0 L5.3 (your fixes, and a look at the whole app)
- **"New" always asks first**, from any status, and says what will change (your place is
  cleared, it no longer counts as finished, it leaves Want to read). Highlights stay.
- **Select books right in the library: hold a cover.** Tap more covers to add or take them out.
  With one book selected, **Info** opens its page. (The book's page is also in the reader:
  More → Book info.)
- **Undo instead of questions** for things that can be put back: removing books (one or many,
  and in Duplicates), deleting a note, removing a bookmark from the Notes tab.
- **A question first** for things that can't: deleting a book for good, a collection, a font.
- **Bookmark key** can now be on the reader's bottom bar (Customize bar), in thumb's reach.
- **Bigger square keys** (Back, ⋯, +, Day/Night) so they're easier to hit.
- A filter with no books offers **Show all books**.
- The full review, and what I propose next: `docs/ux-review-native.md`.

## 0.5.0 L5.2
- **Status can go back to New**, after you confirm: "Mark as new? Your place (42%) will be cleared
  and it will start from the beginning. Your highlights, notes and bookmarks stay."
- **Select several books** is now also on a book's page (hold a book): it starts selecting with
  that book ticked; tap other covers to tick them. (It's also in the library's ⋯ → Select books.)

## 0.5.0 L5.1 (your fixes)
- **A collection (or status, or rating) chosen on a book's page didn't stay.** Closing the page
  saved the copy of the book it had opened with, undoing the change. Now every change is saved the
  moment you tap it, on the book as it is, and closing never overwrites anything. (A test does
  exactly what you did.) The page also lets you make a new collection right there.
- **Tags are chips now:** type a tag and tap Add (or Enter, or a comma); tap a tag to remove it
  (×). Tags you've used on other books are offered below, one tap to add.
- More formats (MOBI, AZW3, FB2, Word, comics…) are layer 7, as planned.

Layer 5 (`docs/plan-native.md`). Installs over L4.1, next to 0.4.2.

## What's new
- **Library filters:** All · Reading · Want · Done · Series, and your **collections** as chips.
- **⋯ in the library:** Select books, Search (title, author, series, tags), **Sort** (recently
  read, recently added, title, author, series, rating, size, progress), **Group** (author, series,
  status, collection), **View** (covers or list), Collections, Duplicates, Book folders, Recently deleted.
- **Pinch** the covers with two fingers to make them bigger or smaller (it's remembered).
- **Series shelf:** each series in order, "1 of 3 read", and a **Next** key. Series are read from
  the book itself (EPUB 3 or Calibre series) when you add it, or set in Edit.
- **Hold a book** for its page: Read, **status** (Want, Reading, Finished, New), **rating** and a
  review, collections, tags; **the book** (pages, words, reading time, publisher, date, language,
  ISBN, subjects, description), **the file**, **your reading** (progress, highlights, notes,
  quotes, bookmarks); **Edit** details, change the **Cover**, **Share file**, **Export notes**
  (as text, to Telegram or anywhere), Remove.
- **Select several** (⋯ → Select books): set status, add to a collection, add tags, or remove them.
- **Duplicates:** books that look the same (title and author, or size), to remove the extra one.
- **Book folders** (Settings → Library, or "Add a book folder"): Quiet adds every EPUB, PDF and TXT
  in them (and in folders inside), on start and when you come back after 10 minutes, or "Look now".
  A book you delete stays deleted; if the library is empty, everything comes back.
- Keys with long words now shrink to fit instead of wrapping.

## Check on your phone
1. Filters and the Summer-style collection chips; ⋯ → Sort and Group.
2. Pinch the covers.
3. Hold a book: status, stars, tags, a collection; Edit the title; change the cover.
4. Series: a book from a series shows on the Series shelf with Next.
5. ⋯ → Select books: pick three, set them to Want, add a tag.
6. Settings → Library → Add a folder → pick a folder with books: are they added? Delete one
   and press Look now: it shouldn't come back.
7. Share file and Export notes open the share sheet.
