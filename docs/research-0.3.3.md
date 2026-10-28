# What the research says, and what Quiet 0.3.3 does with it

Notes from the reading done before 0.3.3: on motion, colour, reading on screens, and stats.
Each section names the source, what it found, and what changed in Quiet because of it.
Where a finding is disputed or the evidence is thin, it says so.

## Motion: why iOS feels comfortable

**Apple, "Designing Fluid Interfaces" (WWDC 2018).** An interface should feel like "an extension of your mind".
The talk's rules:
- Respond at once: a button highlights on touch-down, and the tap counts on release.
- Everything can be interrupted and redirected mid-motion.
- Things leave the way they came ("spatial consistency").
- A gesture's speed decides where it ends ("momentum and projection"), not only how far it moved.
- Edges give way softly ("rubber banding").
- A drag starts only after about 10 points of movement ("hysteresis").
- Springs are described by damping and response, not duration. Use 100% damping (no overshoot) by default, and add a little bounce only when a gesture with momentum ends. Music's tap-to-open uses 100%; swipe-to-dismiss uses 80%.
- [Session](https://developer.apple.com/videos/play/wwdc2018/803/)

**Apple, "Animate with springs" (WWDC 2023).** A spring is set by two numbers, duration and bounce:
- stiffness = (2π / duration)²
- damping = 4π(1 − bounce) / duration, with mass 1.

Bounce 0 "feels like a smooth, gradual change". About 0.15 feels brisk, and above 0.4 is too much for UI. "When you're not sure, use a spring with bounce 0." Springs keep their velocity when interrupted, which ease curves can't. [Session](https://developer.apple.com/videos/play/wwdc2023/10158/)

**Chang & Ungar, "Animation: From Cartoons to the User Interface" (UIST 1993).** Sudden change on screen startles and confuses people. Motion that carries an object from one state to the next lets the eye follow it. [Paper](https://bibliography.selflanguage.org/_static/animation.pdf)

**Heer & Robertson, "Animated Transitions in Statistical Data Graphics" (InfoVis 2007).** Animated transitions between chart states improve how well people read the change. The animations must be simple, staged and true to the data. [Paper](https://homes.cs.washington.edu/~jheer/files/2007-AnimatedTrans-InfoVis.pdf)

**Response-time thresholds (Nielsen Norman Group, after Card, Moran & Newell).** About 100 ms feels instant, and about 1 s is the limit for keeping a train of thought. Small feedback (a toggle, a check) should take around 100 ms. Moving a whole screen takes a few hundred milliseconds; longer feels slow. [Summary](https://valhead.com/?p=2978)

**What Quiet does:**
- Every movement uses Apple's own spring formula, worked out ahead of time into CSS (`src/ui/motion.ts`). Bounce is 0 almost everywhere. A sheet thrown down with speed gets 0.15.
- Sheets and dialogs can be dragged down to close. Past about 10 px the drag starts and follows the finger. A quick flick closes even a short drag (projection). Dragging up gives way softly (rubber band). Letting go springs back from where the finger left it.
- Screens slide in from the right and leave to the right. A swipe from the left edge goes back.
- Buttons, tiles and tabs press in on touch-down (about 100 ms) and spring back on release.
- Charts grow into place, staged: bars rise one after another.
- Reduce Motion on the device turns all of this into short fades.

## Colour, contrast and reading on screens

- **Positive polarity (dark text on light) helps reading in daylight.** Piepenbrock, Mayr & Buchner (Ergonomics 2013, 2014) found better proofreading on light backgrounds at all ages, and the benefit grows as text gets smaller. A 2024 study found dark mode slightly faster for speed but no better for errors. [Record](https://documentacion.fundacionmapfre.org/documentacion/publico/en/bib/150136.do)
- **Evening screens shift sleep.** Chang et al. (PNAS 2015) compared reading on a light-emitting e-reader before bed with a printed book. With the e-reader, people took longer to fall asleep, their melatonin timing moved later by about 1.5 h, and they were less alert the next morning. The likely cause is blue-rich light. The study was small (12 people) and its size of effect is debated. [Paper](https://pmc.ncbi.nlm.nih.gov/articles/PMC4313797/)
- **Text colour at night.** Fan et al. (Sensors 2024), on dark backgrounds in dim light: yellow text caused the least visual fatigue and red the most. [Paper](https://www.ncbi.nlm.nih.gov/pmc/articles/PMC11175232/)
- **Paper-like screens tire the eyes less.** Benedetto et al. (PLoS ONE 2013) found more fatigue (blink rate and ratings) reading on an LCD than on E-ink or paper, with no difference between E-ink and paper. The sample was small. [Paper](https://www.ncbi.nlm.nih.gov/pmc/articles/PMC3873942/)
- **Colour and feeling.** Valdez & Mehrabian (1994): brightness drives pleasure, and saturation drives arousal. Bright, low-saturation colours feel pleasant and calm. Wilms & Oberfeld (2018) confirmed that saturation and brightness matter more than hue. [Abstract](https://pubmed.ncbi.nlm.nih.gov/7996122/)
- **Size matters more than spacing.** Rello, Pielot & Marcos (CHI 2016): readability and comprehension rose steadily up to about 18 pt. Line spacing mattered little, but extremes felt worse. [Summary](https://a11y-paradise.onrender.com/reviews/69c083aebb27ccd8963e779f)
- **Typefaces built for this.** Literata was commissioned by Google for reading books on screens. Atkinson Hyperlegible, from the Braille Institute, makes confusable letters (l, 1, I; 0, O) clearly different. Its benefit comes from user testing, not an independent study.
- **Beauty and use.** Tractinsky, Katz & Ikar (2000): people rated a better-looking interface as easier to use, before and after using it. Later studies dispute how strong this is, and simplicity may be the real cause. [Paper](https://academic.oup.com/iwc/article/13/2/127/898608)

**What Quiet does: a third style, Lamplight.** It is built from these findings:
- Warm, low-blue colours, low in saturation and high in brightness.
- Dark text on warm paper by day.
- Amber text on warm black by night.
- Literata for books, and Atkinson Hyperlegible Next for the app.
- Slightly larger type.
- Soft, rounded shapes and a calm glow for its mark.

## Stats that help, not nag

- **Personal informatics** (Li, Dey & Forlizzi, CHI 2010): self-tracking tools are for *reflection*. People ask different questions at different times: "how am I doing now", and later "what patterns do I have". [Paper](https://courses.cs.washington.edu/courses/cse440/15au/readings/PersonalInformatics-Li2010.pdf)
- **Graphical perception** (Cleveland & McGill, 1984; repeated by Heer & Bostock): people read position and length most accurately, then angle and area, and colour least. So bars and dots are better than pies and colour scales.
- **Goal gradient** (Kivetz, Urminsky & Zheng, 2006): people speed up as a goal gets close, and seeing progress made helps them start. [Paper](https://business.columbia.edu/sites/default/files-efs/pubfiles/1200/goalgradient.pdf)
- **Habits take time** (Lally et al., 2010): a median of 66 days to become automatic, ranging from 18 to 254, and missing a day did not undo progress. Streaks work through loss aversion, which can turn into guilt. The evidence there is mostly industry reports.
- **Peak-end rule** (Kahneman; meta-analysis by Alaybek 2020): an experience is remembered by its best moment and its end. It may not hold for small pleasant things.
- **Progress feedback** (Harrison et al., UIST 2007): how progress is shown changes how long a wait feels.

**What Quiet does:**
- Five new stats, all drawn with position and length: a year heatmap of reading days, time of day, books over time, reading speed, and a per-book table.
- Missed days are never shown as failure. "Days read" counts up and is never lost.
- The stats lead with what you might want to reflect on.
- Charts animate in, staged and in data order.
- Finishing a session ends on a kind summary (peak-end).
