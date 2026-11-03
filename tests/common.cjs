// Shared helpers for Quiet's browser tests (Playwright + Chromium).
//
//   const { chromium } = require('playwright')
//   const t = require('./common.cjs')
//   t.suite('name', async ({ page, check }) => { ... })
//
// A suite gets a fresh phone-sized page of a "returning reader" (the welcome and What's new
// screens are skipped), collects page errors, and prints PASS/FAIL lines. Screenshots go to
// tests/out/<suite>/. The preview server (npm run build first) is started if it isn't running.
const { chromium } = require('playwright')
const { spawn } = require('child_process')
const fs = require('fs')
const http = require('http')
const path = require('path')

const ROOT = path.resolve(__dirname, '..')
const PORT = 4173
const URL = `http://localhost:${PORT}/`
const BOOKS = path.join(__dirname, 'books')
const FORMATS = path.join(BOOKS, 'formats')
const OUT = path.join(__dirname, 'out')
const PHONE = { width: 393, height: 852 } // about a Redmi Note 13 Pro in CSS pixels

const pkg = require(path.join(ROOT, 'package.json'))

function ping() {
  return new Promise(resolve => {
    http.get(URL, res => { res.resume(); resolve(res.statusCode === 200) }).on('error', () => resolve(false))
  })
}

// Starts `vite preview` on 4173 unless something already answers there.
async function ensureServer() {
  if (await ping()) return null
  if (!fs.existsSync(path.join(ROOT, 'dist', 'index.html'))) throw new Error('No dist/: run `npm run build` first.')
  const child = spawn('npx', ['vite', 'preview', '--port', String(PORT), '--strictPort'], { cwd: ROOT, stdio: 'ignore', detached: true })
  for (let i = 0; i < 60; i++) {
    if (await ping()) return child
    await new Promise(r => setTimeout(r, 250))
  }
  throw new Error('Preview server did not start.')
}

function ensureBooks() {
  if (fs.existsSync(path.join(FORMATS, 'sample.epub'))) return
  require('child_process').execFileSync('python3', [path.join(__dirname, 'gen', 'mkbooks.py')], { stdio: 'inherit' })
}

// A fresh browser context that looks like a returning reader on a phone.
async function newPage(browser, { size = PHONE, touch = true, fresh = false, settings = null, storage = {}, context: extra = {} } = {}) {
  const context = await browser.newContext({ viewport: size, hasTouch: touch, isMobile: touch, deviceScaleFactor: 1, ...extra })
  await context.addInitScript(([version, fresh, settings, storage]) => {
    if (sessionStorage.getItem('quiet-test-init')) return
    sessionStorage.setItem('quiet-test-init', '1')
    if (!fresh) {
      localStorage.setItem('quiet:welcome-seen', '1')
      localStorage.setItem('quiet:seen-version', version)
    }
    if (settings) localStorage.setItem('quiet:settings', JSON.stringify(settings))
    for (const [key, value] of Object.entries(storage)) localStorage.setItem(key, value)
  }, [pkg.version, fresh, settings, storage])
  const page = await context.newPage()
  page.errors = []
  page.on('pageerror', e => page.errors.push(e.message))
  page.on('console', m => { if (m.type() === 'error') page.errors.push(`console: ${m.text()}`) })
  await page.goto(URL)
  await page.waitForSelector('#library')
  return page
}

// Adds book files through the hidden file input, like "Add books".
// One file opens straight into the reader; several land in the library.
async function addBooks(page, files) {
  const list = Array.isArray(files) ? files : [files]
  await page.setInputFiles('#fileInput', list)
  if (list.length === 1) await waitForReader(page)
  else await page.waitForFunction(n => document.querySelectorAll('#library [data-book-id]').length >= n, list.length, { timeout: 30000 }).catch(() => {})
}

// Waits until a book is shown: reader view, and the book's chapter label or a rendered page.
async function waitForReader(page, timeout = 30000) {
  await page.waitForFunction(() => document.getElementById('app').dataset.view === 'reader', null, { timeout })
  await page.waitForFunction(() => {
    const view = document.querySelector('foliate-view')
    const r = view?.renderer
    // Text books have text; comics and PDFs (fixed layout) have a picture or canvas instead.
    return (r?.getContents?.() ?? []).some(({ doc }) => {
      const img = doc?.querySelector('img, canvas, svg image')
      return !!(doc?.body?.textContent?.trim() || (img && (img.naturalWidth || img.width)))
    })
  }, null, { timeout })
  await page.waitForTimeout(300)
}

// Text of the book page currently on screen (first loaded section).
const bookText = page => page.evaluate(() => {
  const doc = document.querySelector('foliate-view')?.renderer?.getContents?.()?.[0]?.doc
  return doc?.body?.innerText ?? ''
})

// Is the element really on top (not hidden under the reading bars or a sheet)?
const onTop = (page, selector) => page.evaluate(sel => {
  const el = document.querySelector(sel)
  const r = el?.getBoundingClientRect()
  return !!r && r.width > 0 && el.contains(document.elementFromPoint(r.x + r.width / 2, r.y + r.height / 2))
}, selector)

// While reading, the bars hide; a tap in the middle of the page brings them back.
async function showBars(page) {
  if (await onTop(page, '#backToLibrary')) return
  const { width, height } = page.viewportSize()
  await page.mouse.click(width / 2, height / 2)
  await page.waitForTimeout(500)
}

const backToLibrary = async page => {
  await showBars(page)
  await page.click('#backToLibrary')
  await page.waitForFunction(() => document.getElementById('app').dataset.view === 'library')
  await page.waitForTimeout(300)
}

// Real touch input through the Chrome DevTools Protocol, for swipes, long presses and drags.
async function touch(page) {
  const cdp = await page.context().newCDPSession(page)
  const send = (type, points) => cdp.send('Input.dispatchTouchEvent', { type, touchPoints: points.map(([x, y]) => ({ x, y })) })
  return {
    tap: async (x, y) => { await send('touchStart', [[x, y]]); await send('touchEnd', []) },
    longPress: async (x, y, ms = 650) => { await send('touchStart', [[x, y]]); await page.waitForTimeout(ms); await send('touchEnd', []) },
    // Drags from (x1,y1) to (x2,y2) in `steps` moves over about `ms` milliseconds.
    drag: async (x1, y1, x2, y2, { steps = 12, ms = 240, hold = 0 } = {}) => {
      await send('touchStart', [[x1, y1]])
      for (let i = 1; i <= steps; i++) {
        await page.waitForTimeout(ms / steps)
        await send('touchMove', [[x1 + (x2 - x1) * i / steps, y1 + (y2 - y1) * i / steps]])
      }
      if (hold) await page.waitForTimeout(hold)
      await send('touchEnd', [])
    },
    send,
  }
}

// Runs one suite and prints the result. Exit code 1 if anything failed.
async function suite(name, fn, options = {}) {
  // options: passed to newPage (size, touch, fresh, settings, storage, context: extra browser-context options)
  ensureBooks()
  const server = await ensureServer()
  const browser = await chromium.launch()
  const outDir = path.join(OUT, name)
  fs.mkdirSync(outDir, { recursive: true })
  let failed = 0, passed = 0
  const check = (ok, label, detail = '') => {
    if (ok) passed++; else failed++
    console.log(`${ok ? 'PASS' : 'FAIL'}  ${label}${!ok && detail ? `  (${detail})` : ''}`)
  }
  const shot = (page, label) => page.screenshot({ path: path.join(outDir, `${label}.png`) })
  try {
    const page = await newPage(browser, options)
    await fn({ browser, page, check, shot, newPage: o => newPage(browser, o) })
    const errors = page.errors.filter(e => !/Failed to load resource|translate|mymemory|favicon/i.test(e))
    check(errors.length === 0, 'no page errors', errors.slice(0, 3).join(' | '))
  } catch (err) {
    failed++
    console.log(`FAIL  crashed: ${err.stack || err}`)
  } finally {
    await browser.close()
    if (server) process.kill(-server.pid)
  }
  console.log(`${name}: ${passed} passed, ${failed} failed`)
  process.exitCode = failed ? 1 : 0
}

module.exports = { suite, newPage, addBooks, waitForReader, bookText, backToLibrary, showBars, onTop, touch, ensureServer, URL, BOOKS, FORMATS, OUT, PHONE, pkg }
