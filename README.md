# Quiet

A calm, distraction-free e-book reader. Web first, then Android, iOS and desktop.

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
src/main.ts             Connects the buttons, settings and reader together
src/settings.ts         Reading preferences (theme, font size, mode) and saving them
src/reader/reader.ts    Opens a book, turns pages, continuous scroll, remembers your place
src/reader/book-css.ts  How the text inside the book is styled
src/ui/toc.ts           The contents list
src/styles/tokens.css   Colours and themes — the design system
src/styles/app.css      Layout and components
vendor/foliate-js/      The e-book engine (see QUIET_VENDOR.md inside)
```

## Security

Books can contain JavaScript. The Content-Security-Policy in `index.html` makes sure
only Quiet's own code runs. Do not remove it.
