# Quiet

A calm, distraction-free e-book reader. Web first, then Android, iOS and desktop.

**Live:** hosted on Cloudflare Pages, which rebuilds automatically on every push to `main`.

Opens EPUB, MOBI, AZW3 (DRM-free), FB2 and CBZ. PDF comes later.

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

## Where things are

```
index.html              The page layout
src/main.ts             Switches between library and reader; connects buttons and settings
src/settings.ts         Reading preferences (theme, font size, mode) and saving them
src/library/db.ts       The library database (books, covers, reading positions) in IndexedDB
src/library/import.ts   Adding a book: reads its title, author and cover
src/library/library-view.ts  The library screen
src/reader/reader.ts    Opens a book, turns pages, continuous scroll
src/reader/book-css.ts  How the text inside the book is styled
src/reader/fonts.ts     The bundled reading fonts
src/ui/toc.ts           The contents list
src/ui/toast.ts         Short messages at the bottom of the screen
src/styles/tokens.css   Colours and themes — the design system
src/styles/app.css      Layout and components
vendor/foliate-js/      The e-book engine (see QUIET_VENDOR.md inside)
```

Books are stored in the browser on the device that added them. Nothing is uploaded anywhere.

## Security

Books can contain JavaScript. The Content-Security-Policy in `index.html` makes sure
only Quiet's own code runs. Do not remove it.

## Credits

- [foliate-js](https://github.com/johnfactotum/foliate-js) — e-book engine, MIT License
- [Literata](https://github.com/googlefonts/literata) and [Source Sans 3](https://github.com/adobe-fonts/source-sans) — reading fonts, SIL Open Font License 1.1 (via Fontsource)
- [Lucide](https://lucide.dev) — icons, ISC License
