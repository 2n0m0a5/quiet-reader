// Text a book colours or hides by itself stays readable (the "invisible chapter" bug):
// black text on the Night page, white text on the Day page, a page the book fades out.
// A readable colour the book chose (a red heading) is kept.
const path = require('path')
const t = require('./common.cjs')

// Contrast of each paragraph's text against the page, in the chapter on screen.
const check = (page, selector) => page.evaluate(sel => {
  const doc = document.querySelector('foliate-view').renderer.getContents()[0].doc
  const win = doc.defaultView
  const rgb = c => c.match(/[\d.]+/g).slice(0, 3).map(Number)
  const lum = ([r, g, b]) => [r, g, b].map(v => { v /= 255; return v <= 0.03928 ? v / 12.92 : ((v + 0.055) / 1.055) ** 2.4 }).reduce((s, v, i) => s + v * [0.2126, 0.7152, 0.0722][i], 0)
  const ratio = (a, b) => { const [x, y] = [lum(a), lum(b)].sort((p, q) => q - p); return (x + 0.05) / (y + 0.05) }
  const bg = rgb(win.getComputedStyle(doc.documentElement).backgroundColor)
  const el = doc.querySelector(sel)
  return { contrast: ratio(rgb(win.getComputedStyle(el).color), bg), color: win.getComputedStyle(el).color, opacity: win.getComputedStyle(doc.body).opacity }
}, selector)

async function goToChapter(page, n) {
  await t.showBars(page)
  await page.click('#tocToggle')
  await page.waitForTimeout(400)
  await page.locator('#toc').getByText(`Chapter ${n}`, { exact: true }).click()
  await page.waitForFunction(n => new RegExp(`Chapter ${n}`).test(document.getElementById('chapterLabel').textContent), n, { timeout: 8000 }).catch(() => {})
  await page.waitForTimeout(900)
}

t.suite('legibility', async ({ page, check: ok, shot, newPage }) => {
  // Night page: chapter 1's black text must be readable.
  await page.evaluate(() => { const s = JSON.parse(localStorage.getItem('quiet:settings') || '{}'); s.theme = 'dark'; localStorage.setItem('quiet:settings', JSON.stringify(s)) })
  await page.reload()
  await page.waitForSelector('#library', { state: 'attached' })
  await t.addBooks(page, path.join(t.BOOKS, 'colours.epub'))
  let r = await check(page, 'p')
  ok(r.contrast >= 4, 'Night: black book text becomes readable', `${r.color} ${r.contrast.toFixed(2)}`)
  await shot(page, '1-night-black-text')

  // Day page.
  const day = await newPage()
  await t.addBooks(day, path.join(t.BOOKS, 'colours.epub'))
  r = await check(day, 'p')
  ok(r.contrast >= 4, 'Day: black book text stays as it is (readable)', `${r.color} ${r.contrast.toFixed(2)}`)
  await goToChapter(day, 2)
  r = await check(day, 'p')
  ok(r.contrast >= 4, 'Day: white book text becomes readable', `${r.color} ${r.contrast.toFixed(2)}`)
  await shot(day, '2-day-white-text')
  await goToChapter(day, 3)
  r = await check(day, 'p')
  ok(r.opacity === '1', 'a page the book fades out is shown', `opacity ${r.opacity}`)
  await goToChapter(day, 4)
  r = await check(day, 'h1')
  ok(/176, 48, 42/.test(r.color), 'a readable red heading keeps its colour', r.color)
  day.errors.length && ok(false, 'no page errors (day)', day.errors.slice(0, 2).join(' | '))
})
