# Quiet native — UX review (0.5.0 L5.3, 5 November 2026)

The owner asked for a look at the whole app with ergonomics and design psychology in mind,
after two findings: "New" didn't always ask, and selecting books was hidden in the book's page.

## Rules the app now follows
1. **Ask before what can't be undone; offer Undo for what can.** (Shneiderman's "permit easy
   reversal"; Nielsen's "user control and freedom".) Questions interrupt, so they're kept for
   real losses; everything reversible gets an Undo in the message instead.
2. **Platform conventions:** on Android, holding an item selects it (Photos, Files, Gmail).
3. **Thumb reach** (Hoober 2013: about half of phone use is one-handed): actions you use often
   sit at the bottom or can be put there.
4. **Touch targets of 44–48 dp** (Apple HIG 44 pt, Material 48 dp; Fitts's law).
5. **No dead ends:** an empty result always offers the way back.

## Done in L5.3
| Where | Before | Now | Why |
|---|---|---|---|
| Book status | "New" asked only if the book had been opened | "New" always asks, and says what changes (place cleared, no longer finished, leaves Want to read) | It starts the book over |
| Several books at once | ⋯ → Select books, or the book page | **Hold a cover** to select it, tap more to add; Info appears when one book is selected | Android convention, one gesture |
| Book page | Opened by holding a cover | Info in the selection bar, and **More → Book info** in the reader | Hold now selects |
| Remove a book (page, duplicates, several) | Silent, or a question | Done at once, with **Undo** (5 s) | Reversible: no interruption |
| Delete a note (editor), remove a bookmark (Notes tab) | Gone | **Undo** | Same |
| Delete a book for good (Recently deleted) | One tap | Asks first | Can't be undone |
| Delete a collection, remove a font | One tap | Asks first (books stay) | Irreversible |
| Bookmark | Only at the top right | Also a **Bookmark key** for the bottom bar (Customize bar), filled when set | Thumb reach |
| Square keys (Back, ⋯, +, Day/Night) | 36–38 dp | 42–44 dp | Touch target size |
| A filter with no books | "No books here." | Plus **Show all books** | No dead ends |

## Proposed next (owner to choose)
1. **Search key in the library's top bar** (one tap instead of ⋯ → Search).
2. **Tap the Library tab again** to scroll to the top and leave selection (common in phone apps).
3. **Slide a finger across covers** to select a run of books (Google Photos style).
4. **The end of a book:** when you reach the last page, offer "Finished? · Rate it · Next in
   series" (the peak-end rule: how something ends colours the whole memory of it).
5. **Undo after deleting a highlight from inside the reader** (the Notes tab has it).
6. **The tour** (L8) points at the hidden gestures: hold to select, hold the ruler, pull at a
   chapter's end, pinch the covers.
