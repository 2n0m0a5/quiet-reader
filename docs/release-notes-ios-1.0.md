# Quiet for iPhone 1.0 (build 1)

The same Quiet as Android 0.4.2, as an iPhone app (Capacitor), with the knob icon.

## Works on iPhone
- Everything in the reader: the Radio design, the reader bar, the page curl that follows your finger,
  notes, quotes, stats, book info, translate.
- Vibration on taps, the screen stays on while reading, the battery in the corner.
- The safety copy of your library (the app's private storage) and automatic recovery.
- Back up: iOS asks where to save the file (Files, iCloud Drive). Restore from Files.
- Quote pictures: share them, or save them to Photos.

## Android only
- Book folders, page turns with the volume keys, and the rotation lock.

## Installing (no App Store yet)
The file is not signed: Sideloadly or AltStore signs it with your own (free) Apple ID.
1. On a Windows PC: install Sideloadly (sideloadly.io) and iTunes from Apple's website.
2. Connect the iPhone with a cable, open Sideloadly, drop in `Quiet-iOS-1.0-1.ipa`, enter your
   Apple ID, press Start.
3. On the iPhone: Settings → General → VPN & Device Management → your Apple ID → Trust.
   On iOS 16 or later, also turn on Settings → Privacy & Security → Developer Mode (it restarts).
4. A free Apple ID's apps stop opening after 7 days; open Sideloadly again (or use AltStore, which
   renews them over Wi-Fi) to refresh. Your library stays.

## Building it again
GitHub → Actions → "iOS app" → Run workflow. It also runs by itself when the app or the iOS
project changes. The .ipa is under the run's Artifacts.

## Check on the iPhone
1. Add books from Files; open one; Pages + Curl: the curl under your finger.
2. Settings → Files & storage → Back up: the Save to Files sheet appears.
3. Share a quote picture; save one to Photos (it asks for permission once).
4. The tab bar and the reader bar sit above the home indicator; the top clears the notch.
