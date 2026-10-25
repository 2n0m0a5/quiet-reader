import { defineConfig } from 'vite'

export default defineConfig({
  // Relative paths, so the same build works on a website and inside the Android app.
  base: './',
  build: {
    // foliate-js uses modern JavaScript (top-level await, private fields).
    target: 'es2022',
  },
})
