# Quiet native — security and speed plan (5 November 2026, after 0.5.0 L8.4)

Full page for the owner: https://claude.ai/artifact/3XYFCvtj7qs2Xc14Tk39th (private to the owner).
Every fix ships with proof: a test that fails before and passes after, numbers before/after,
an on-phone check, and an evidence report per release.

Found while planning: the repo is **public** and contains the signing key
(`android/app/quiet-test.keystore`) with its password in `native/app/build.gradle.kts`.

## L8.5 — security first
- S1 New release key kept out of git (owner's password manager + GitHub secret); APK v3 key
  rotation so updates still install over L8.4/0.4.2; build refuses the test key for releases.
- S2 Books can't reach the internet: request filtering + a content policy on every chapter;
  counter in Diagnostics; test book `quiet-security-test.epub`.
- S3 Page bridge needs a per-session secret; refusals counted; font call checks ownership.
- S4 Comics converted page by page to disk; zip-bomb / size / entry-count limits; import
  time limit; MOBI loop guards. Tests: zip bomb, 1 GB comic, 200 damaged files.
- V1 GitHub Actions workflow running all native tests on every push.
- Diagnostics page (Settings → About → Diagnostics): open time, memory, cache, blocked
  requests, refused bridge calls, last safety copy.

## L8.6 — speed
E1 cover thumbnails + memory cache · E2 notes/reading days cached (no re-parse; bar no longer
reads all history) · E3 library filters/sorts remembered · E4 safety copy only on change ·
E5 startup profile, startup work off the main thread · E6 bionic in chunks · E7 space per book
+ Free up space · E8 solid bar in Battery saver.

## L8.7 — privacy options and watchdogs
P1 optional backup password (AES-256-GCM) · P2 app lock (biometric/PIN) + private mode
(FLAG_SECURE) · P3 Dependabot · P4 StrictMode in test builds, zero violations · P5 scrub
paths/titles from reports · P6 network rule: no cleartext.

## Waiting on the owner
Keep the repo public? · new key custody · rewrite history to erase the old key? · encrypted
backups native-only? · app lock now or later?
