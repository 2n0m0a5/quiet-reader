# Tests

Browser tests for Quiet, run with Playwright and Chromium against the built app.

```bash
npm run build
node tests/run-all.cjs          # every suite
node tests/smoke.cjs            # one suite
```

- The sample books are made by `tests/gen/mkbooks.py` (it runs automatically the first time).
  They go to `tests/books/`, which git ignores, so nothing copyrighted or large is stored.
- Screenshots of each suite go to `tests/out/<suite>/` (also ignored).
- `common.cjs` starts `vite preview` on port 4173 if nothing is running there. It opens a
  phone-sized page as a returning reader (no welcome screen) and has helpers: `addBooks`,
  `waitForReader`, `bookText`, `showBars`, `backToLibrary` and `touch` (real touches through
  CDP `Input.dispatchTouchEvent` for swipes, drags and long presses).

| Suite | What it checks |
| --- | --- |
| `smoke` | Adding books, opening one, Contents, the four tabs, the four styles, and a reload |
| `formats` | Every sample format opens (EPUB, FB2, FBZ, CBZ, CBT, CBR, DOCX, ODT, RTF, TXT, MD, HTML, HTMLZ, PDB, PDF) |
| `gestures` | Long-press for details; swipe back from the left edge (short springs back, long leaves) |
| `safety` | The library database is wiped and comes back from the safety copy; "Delete everything" stays deleted |

To add a suite, make `tests/<name>.cjs` with `require('./common.cjs').suite('<name>', async ({ page, check, shot }) => { … })`.
`run-all` picks it up by itself.

Gotchas (learned the hard way):
- While reading, the top bar hides. Tap the middle of the page (`showBars`) before you press it.
- Inside the book's iframe, CDP touches report `screenY` 0. Work from `clientY` and the frame's rect.
- Touch handlers must ignore events where `touches.length !== 1` (pinch tests send empty lists).
- Translation tests can fail when Google answers 429; MyMemory is the fallback. Run them again.
- CB7 isn't generated (no 7z writer without extra tools). Test a real compressed CBR on the phone too.
