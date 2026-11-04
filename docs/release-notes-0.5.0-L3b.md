# Quiet 0.5.0 L3b — curl, footnotes, pictures, bionic, ruler, your fonts (native test build)

## 0.5.0 L3b.1 (your fixes)
- **Scroll mode reads by scrolling:** a sideways swipe no longer jumps to another chapter. At a
  chapter's end, keep pulling up and let go to go on to the next one (at the start, pull down
  for the previous one).
- **Volume keys in Scroll mode** move one screen at a time, and only go on to the next chapter at
  its end. (In Pages mode they turn pages, as before.)
- **Page turn** now sits in **Text**, right under Reading mode (and out of More).
- **Reading ruler:** no handle on the side. **Hold the ruler for a moment** (anywhere along it,
  either hand) until it lights up and the phone clicks, then drag it. A quick touch or a scroll
  that starts on it scrolls the book as usual.

The second half of layer 3 (`docs/plan-native.md`). Installs over L3a, next to 0.4.2.
With it, the native reader has everything 0.4.2's reader has, except notes (L4).

## What's new
- **Page turn: Slide, Curl or None** (Settings → Reader, or More). **Curl** works like 0.4.2's:
  the page corner follows your finger, the page folds with its back and a soft shadow, and it
  turns if you let go past about 40% (or flick), otherwise it settles back. Taps on the edges
  and the volume keys curl too. Choosing Curl switches to Pages.
- **Footnotes** open in a sheet over the page instead of jumping away. Other links jump, with
  "Back to page N".
- **Pictures:** tap one to see it full screen; pinch or double-tap to zoom.
- **Bionic reading:** the start of each word in bold (More, or Settings → Reader).
- **Reading ruler:** Line (a soft band on the line you read) or Focus (the rest dims). Drag
  the small ≡ handle on the right to move it.
- **Your fonts:** Settings → Reader → Your fonts → Add a font (TTF, OTF, WOFF, WOFF2), then
  Text → Font. Fonts from a 0.4.2 backup are there too.

## Check on your phone
1. Curl: drag a page from the right edge slowly, then let go early (it should settle back),
   then all the way (it turns). Drag from the left to go back. Tap the edges. Volume keys.
2. While Curl is on: tapping the middle still shows the bars, and links still work.
3. A book with footnotes: tap a note number → the note shows in a sheet.
4. A book with pictures: tap one → full screen, zoom, close.
5. Bionic on and off; the text must come back exactly.
6. Ruler: Line and Focus; drag it.
7. Add a .ttf font, choose it under Text → Font: does the book use it?
