// Runs the native app's page script (written by L3bTest) in Chromium on a small book page:
// bionic on bolds word starts, off restores the text exactly; picture taps reach the bridge;
// your font's @font-face is added.   node scripts/check-page-script.cjs
const fs = require('fs'), path = require('path')
const { chromium } = require('playwright')
const out = path.join(__dirname, '..', 'native', 'app', 'build', 'outputs')
;(async () => {
  const browser = await chromium.launch()
  const page = await browser.newPage()
  await page.setContent(`<html><head></head><body><p id="p">The quiet river, “ran” — slowly.</p><img id="i" src="data:image/gif;base64,R0lGODlhAQABAAAAACw=" width="50" height="50"></body></html>`)
  await page.evaluate(() => { window.opened = null; window.QuietPage = { openImage: u => { window.opened = u }, font: f => `@font-face{font-family:'${f}';src:url(data:font/ttf;base64,AA==)}` } })
  const before = await page.$eval('#p', e => e.textContent)
  let fails = 0
  const check = (ok, what, extra = '') => { console.log(`${ok ? 'PASS' : 'FAIL'}  ${what} ${extra}`); if (!ok) fails++ }
  await page.evaluate(fs.readFileSync(path.join(out, 'page-script-on.js'), 'utf8'))
  check(await page.$eval('#p', e => e.querySelectorAll('b').length) === 5, 'bionic bolds the start of each word', await page.$eval('#p', e => e.innerHTML))
  check(await page.$eval('#p', e => e.textContent) === before, 'the text reads the same')
  await page.evaluate(fs.readFileSync(path.join(out, 'page-script-on.js'), 'utf8'))
  check(await page.$eval('#p', e => e.querySelectorAll('b').length) === 5, 'running it twice changes nothing')
  check(await page.evaluate(() => !!document.getElementById('quiet-font')), 'your font is added')
  await page.click('#i')
  check(await page.evaluate(() => (window.opened || '').startsWith('data:image')), 'a tap on a picture reaches the app')
  await page.evaluate(fs.readFileSync(path.join(out, 'page-script-off.js'), 'utf8'))
  check(await page.$eval('#p', e => e.innerHTML) === before, 'bionic off restores the page exactly')
  // Volume keys in scroll mode: a screen at a time, then "edge" at the chapter's end.
  const tall = await browser.newPage({ viewport: { width: 400, height: 800 } })
  await tall.setContent('<body style="margin:0"><div style="height:2000px">long chapter</div></body>')
  const down = fs.readFileSync(path.join(out, 'scroll-down.js'), 'utf8'), up = fs.readFileSync(path.join(out, 'scroll-up.js'), 'utf8')
  const r1 = await tall.evaluate(down), y1 = await tall.evaluate(() => scrollY)
  check(r1 === 'ok' && y1 > 600 && y1 < 800, 'volume down scrolls about one screen', `y=${y1}`)
  await tall.evaluate(down); await tall.evaluate(down)
  check(await tall.evaluate(down) === 'edge', 'at the chapter end it says so (then the next chapter opens)')
  await tall.evaluate(() => scrollTo(0, 0))
  check(await tall.evaluate(up) === 'edge', 'at the chapter start, up goes to the previous chapter')
  await browser.close()
  console.log(`page-script: ${fails ? fails + ' failed' : 'all passed'}`)
  process.exit(fails ? 1 : 0)
})()
