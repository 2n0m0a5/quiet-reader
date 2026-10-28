# Research behind Quiet 0.3.4: motion everywhere, and Frosty Glass

These notes follow on from [research-0.3.3.md](research-0.3.3.md), which covers springs, scrolling and colour.

## Icons that answer: SF Symbols' effects
**Apple, "Animate symbols in your app" (WWDC 2023).** Symbols have a small set of effects:
- **Bounce** plays once "in response to certain events".
- **Pulse** and **Variable Color** show that something is in progress.
- **Scale** changes size and stays changed.
- **Appear** and **Disappear** bring a symbol in or out.
- **Replace** animates one symbol into another (play and pause).

[Session](https://developer.apple.com/videos/play/wwdc2023/10258/)

**What Quiet does** (`watchSymbols` in `src/ui/motion.ts`): tapping a button plays its icon's own effect.
- A gear turns, and a plus turns into place.
- Arrows and chevrons nudge the way they point.
- A bin, tag or ✕ wiggles, and the ⋯ stretches.
- Search tilts; everything else bounces.
- The moon and sun *replace* each other, growing out of a blur.
- Tab icons spring when chosen.

## Opening and closing: the zoom transition
**Apple, "Enhance your UI animations and transitions" (WWDC 2024).** "The cell you tap morphs into the incoming view", and it stays interactive: it can be grabbed and interrupted at any time. Two rules from the talk:
- Use a stable identifier (the model, not the view), so the zoom returns to the right source.
- "The system never cancels an interrupted push": it turns into a pop.

[Session](https://developer.apple.com/videos/play/wwdc2024/10145/)

**What Quiet does:**
- A book opens by zooming its cover up into the page, and closes by zooming the page back down into the same cover. The cover is found again by the book's id, even after the library redraws.
- In Old Tashkent the zoom unfolds from the cover's arch. Paper keeps its ink fade.
- On a phone, a swipe from the left edge of a book follows the finger, with the library underneath. Past a third of the way, or with a flick, it goes back; otherwise it springs back.

## Lists that rearrange
**Heer & Robertson (2007)** showed that animated transitions help people follow what changed, if the motion stays simple and true to the data (see the 0.3.3 notes).

**What Quiet does:** sorting, filtering, grouping, switching views, and removing or restoring books animate the list:
- Each book glides from its old place to its new one.
- Books that leave shrink away, and new ones grow in.
- Only books on screen move, so long libraries stay quick.

## Holding, counting, curling
- **Long press**, like iOS's haptic touch: a held book sinks slowly under the finger, then lifts as its details open, with a tap.
- **Numbers** count up to their value on the Reading screen, like iOS's numeric text transition.
- **Page curl**, a choice in page mode: the page on screen is captured and turns away from the spine. It lifts at the outer edge first, bends slightly, and darkens as it turns from the light, over the next page in its shadow. Going back lays the previous page down on top. Taps, keys, volume keys and swipes all curl.

## Frosty Glass, after Liquid Glass
**Apple, "Meet Liquid Glass" (WWDC 2025)** sets these rules for glass:
- It "is best reserved for the navigation layer that floats above the content". Avoid "stacking Liquid Glass elements on top of each other". Content such as lists stays in the content layer "to ensure clarity".
- **Materialising:** "Instead of fading, Liquid Glass objects materialize in and out by gradually modulating the light bending and lensing."
- **Touch:** it "responds to interaction by instantly flexing and energizing with light", with "an inherent gel-like flexibility". Controls morph between states on one floating plane, and a menu "pops open" from its button.
- **Adapting:** shadows and tint adapt to keep it legible. Reduce Transparency makes it "frostier", and Reduce Motion "disables any elastic properties".
- **Tint** is only for primary actions: "When every element is tinted, nothing stands out."

[Session](https://developer.apple.com/videos/play/wwdc2025/219/)

**What Quiet does with it:**
- **Where the glass goes:** only floating things are glass, never glass on glass. That means the top bar, the floating tab capsule, the reading toolbars, sheets, menus, toasts and the selection bar. Buttons inside a glass bar are clear.
- **Content stays clear:** book pages, covers and lists sit on soft, icy light. Over a book's text the bars are frostier, so their own words stay readable.
- **Materialising:** sheets, menus and toasts appear by gaining their blur and light, not by fading in.
- **Pressing:** glass swells a little and lights up from inside where your finger is.
- **The lens:** a liquid lens under the chosen tab moves with its leading edge first, so it stretches like a drop. The lens in rows of choices squashes and stretches as it slides.
- **Tint:** only the main action is tinted.
- **Accessibility:** Reduce Transparency turns the glass solid.
- **Chapter pull:** the sign is a drop of glass that grows as you pull and fills with light when letting go will turn the page.
