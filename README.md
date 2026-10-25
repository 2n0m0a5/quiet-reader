# Quiet

A calm, distraction-free e-book reader. Web first, then Android, iOS and desktop.

**Live:** hosted on Cloudflare Pages, which rebuilds automatically on every push to `main`.

Opens EPUB, MOBI, AZW3/AZW (DRM-free), FB2, CBZ, PDF, TXT and HTML.

**Features (0.2.0):** library with covers, search, sorting, grid/list view, reading status and
collections; book details; continuous scroll or pages; reading fonts, size, spacing, margins,
alignment and three themes; bookmarks, highlights in four colours with notes, search in book,
look up and translate; time left; works offline; Android app with "Open with Quiet", volume-key
page turns and keep-screen-on.

## Run it on your computer

You need [Node.js](https://nodejs.org) (LTS) installed.

```sh
npm install     # first time only: downloads the tools
npm run dev     # starts Quiet at http://localhost:5173
```

Open the link it prints in Chrome. Changes to the code show up instantly.
Press `Ctrl+C` in the terminal to stop it.

## Other commands

| Command | What it does |
|---|---|
| `npm run build` | Checks the code for errors and builds the final app into `dist/` |
| `npm run preview` | Serves the built app from `dist/`, to check it before publishing |

## Android app

The Android app wraps the same web app with [Capacitor](https://capacitorjs.com).

```sh
npm run android   # builds the web app and copies it into android/
```

Then build the APK, either in Android Studio (open the `android/` folder) or from a terminal:

```sh
cd android
./gradlew assembleDebug   # Windows: gradlew assembleDebug
```

The APK appears in `android/app/build/outputs/apk/debug/`. Test builds are signed with
`android/app/quiet-test.keystore`, so each one installs over the previous and keeps the library.
The Google Play build will use a separate, secret key.

## Where things are

```
index.html              The page layout
src/app/native.ts       Android-only features: back button, status bar
src/app/install.ts      Offline support and the Install button (website)
src/main.ts             Switches between library and reader; connects buttons and settings
src/settings-screen.ts  The Settings screen and About
src/settings.ts         Reading preferences (theme, font size, mode) and saving them
src/library/db.ts       The library database (books, covers, reading positions) in IndexedDB
src/library/import.ts   Adding a book: reads its title, author and cover
src/library/library-view.ts  The library screen
src/reader/reader.ts    Opens a book, turns pages, continuous scroll, highlights on the page
src/reader/notes.ts     Bookmarks, highlights, notes and search in a book
src/reader/selection-menu.ts  The menu shown for selected text
src/formats/            TXT and HTML books; choosing the right reader for a file
src/library/details.ts  The book details sheet and collections picker
src/ui/dialog.ts        Dialogs and sheets
src/reader/book-css.ts  How the text inside the book is styled
src/reader/fonts.ts     The bundled reading fonts
src/ui/toc.ts           The contents list
src/ui/toast.ts         Short messages at the bottom of the screen
src/styles/tokens.css   Colours and themes — the design system
src/styles/app.css      Layout and components
vendor/foliate-js/      The e-book engine (see QUIET_VENDOR.md inside)
android/                The Android app project (Capacitor)
```

Books are stored in the browser on the device that added them. Nothing is uploaded anywhere.

## Security

Books can contain JavaScript. The Content-Security-Policy in `index.html` makes sure
only Quiet's own code runs. Do not remove it.

## Credits

- [foliate-js](https://github.com/johnfactotum/foliate-js) — e-book engine, MIT License
- [Literata](https://github.com/googlefonts/literata) and [Source Sans 3](https://github.com/adobe-fonts/source-sans) — reading fonts, SIL Open Font License 1.1 (via Fontsource)
- [PDF.js](https://mozilla.github.io/pdf.js/) — PDF support, Apache License 2.0
- [Lucide](https://lucide.dev) — icons, ISC License
- [Capacitor](https://capacitorjs.com) — Android app, MIT License
