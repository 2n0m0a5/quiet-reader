# Quiet 0.5.0 L0 — the native app, layer 0 (test build)

The first build of Quiet as a fully native Android app (Kotlin and Jetpack Compose, no web view
around the app). This layer is the skeleton: it looks and moves like Quiet but doesn't open books
yet. It installs **next to** Quiet 0.4.2 (app ID `app.quietreader.n`), so your library is safe.
The plan, layer by layer, is in `docs/plan-native.md`.

## What's in it
- The Radio design, rebuilt natively: casing, keys that press down, the speaker grille, the band
  selector tab bar with its orange light, Day, Night and Black.
- Archivo, DM Mono and Crimson Pro fonts, bundled. The knob icon and splash.
- The four tabs: Library, Notes, Stats, Settings. The first three show their welcome; small
  "L1 / L4 / L6" tags say when each part arrives.
- Settings: **Interface** works (theme, text size, motion, screen rotation, vibration) and
  **About** (version, your data, promises, credits). The other sections show which layer brings them.
- Motion: the tab key glides to your tap, Settings pages slide in, colours fade between Day and
  Night, nothing bounces. Back gesture goes back a page, then to Library.
- A 1.6 MB app (0.4.2 is much bigger).

## Check on your phone
1. It installs next to 0.4.2 and both open. About says "0.5.0 · L0 · native".
2. The icon, the splash and the fonts look like 0.4.2.
3. Tap the tabs: the raised key glides, the phone clicks, no bounce. Does it feel smooth?
4. Keys (+, Day/Night, Choose a book) press down when touched.
5. Day/Night key in the top bar; Settings → Interface → Theme: System, Day, Night, Black.
   The status bar icons stay readable in each.
6. Text size Larger: everything grows, nothing gets cut off.
7. Motion: Reduced makes things change instantly; Full and Follow phone glide.
8. Screen rotation: Portrait and Landscape lock it.
9. Vibration off: no more clicks.
10. Back gesture: from Interface back to Settings, then to Library, then out of the app.
11. Close the app and open it again: your theme and settings are kept.
