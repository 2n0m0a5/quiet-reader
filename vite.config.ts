import { defineConfig } from 'vite'
import { VitePWA } from 'vite-plugin-pwa'
import { version } from './package.json'

export default defineConfig({
  // Relative paths, so the same build works on a website and inside the Android app.
  base: './',
  define: {
    __APP_VERSION__: JSON.stringify(version),
  },
  build: {
    // foliate-js uses modern JavaScript (top-level await, private fields).
    target: 'es2022',
  },
  plugins: [
    // Makes Quiet installable and lets it work offline: a service worker keeps a copy of
    // every file of the app on the device. Books themselves are already stored on the device.
    VitePWA({
      // A new version waits until Quiet is closed, so it never reloads in the middle of a chapter.
      registerType: 'prompt',
      injectRegister: false,
      includeAssets: ['favicon.svg', 'apple-touch-icon.png'],
      manifest: {
        name: 'Quiet',
        short_name: 'Quiet',
        description: 'A calm, distraction-free e-book reader.',
        lang: 'en',
        start_url: './',
        scope: './',
        display: 'standalone',
        background_color: '#f7f5f0',
        theme_color: '#fffdf9',
        icons: [
          { src: 'icon-192.png', sizes: '192x192', type: 'image/png', purpose: 'any' },
          { src: 'icon-512.png', sizes: '512x512', type: 'image/png', purpose: 'any' },
          { src: 'icon-maskable-512.png', sizes: '512x512', type: 'image/png', purpose: 'maskable' },
        ],
      },
      workbox: {
        globPatterns: ['**/*.{js,mjs,css,html,svg,png,woff2}'],
        globIgnores: ['pdfjs/**'],
        navigateFallback: 'index.html',
        // PDF.js data files (character maps, fonts) are kept once they've been used.
        runtimeCaching: [{
          urlPattern: /\/pdfjs\//,
          handler: 'CacheFirst',
          options: { cacheName: 'pdfjs-data' },
        }],
      },
    }),
  ],
})
