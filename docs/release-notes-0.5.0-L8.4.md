# Quiet 0.5.0 L8.4 — opening, the chapter pull, fixes

Installs over L8.3, next to 0.4.2.

## New
- **Opening Quiet.** Android's splash shows the knob; Quiet takes over with the same knob in the
  same place. The knob turns up like switching a radio on, its orange dot lights with a click,
  "quiet" appears under it; then knob and word fly into the top bar (the knob turning back down,
  quiet) while the library rises in behind them. Under a second. Reduced motion: a short fade.
- **The chapter pull has a sign.** In Scroll mode, at a chapter's end: a ring fills as you pull,
  with an arrow; past the point of no return the ring turns orange, the arrow pops and you feel
  the click. It sits in the gap the page leaves and names where you're going ("Next: The Devil's
  Cohort"; going back, "Back to: …").

## Fixed
- **Chapter changes jumped.** The old chapter now fades out completely before the swap, the new
  one comes in only once it's laid out (going back, after Readium has scrolled it to its end),
  and Android's own overscroll stretch no longer fights Quiet's rubber band.
- **Leaving a Settings page flashed.** After the back swipe, the page you left snapped back into
  view for a moment before disappearing. Now it stays out of sight until the page behind has
  taken its place. (The same fix for closing a book with the back swipe.)

## Check on your phone
1. Close Quiet fully (swipe it away from recent apps) and open it: the knob's opening.
2. Scroll mode at a chapter's end: pull slowly; watch the ring, the arrow, the chapter's name;
   let go past the click. Then at a chapter's start, pull down for the previous one.
3. Settings → any page → back swipe (and the back key): no flash.
4. If anything still glitches, a short screen recording helps me see it exactly.
