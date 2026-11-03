// The page curl: a tap on the right side curls the page away (a fold with the page's back and a
// shadow), the next page is there afterwards, nothing is left behind; going back works too.
const path = require('path')
const t = require('./common.cjs')

const position = page => page.evaluate(() => document.querySelector('foliate-view').lastLocation?.fraction ?? -1)

t.suite('curl', async ({ page, check, shot }) => {
  await t.addBooks(page, path.join(t.BOOKS, 'lamp-and-clock.epub'))
  await page.waitForTimeout(1200)
  const { width, height } = page.viewportSize()
  const start = await position(page)

  await page.mouse.click(width - 20, height / 2)
  const curled = await page.waitForFunction(() => document.getAnimations().some(a => a.effect?.pseudoElement?.includes('curl-flap')), null, { timeout: 3000 }).then(() => true).catch(() => false)
  check(curled, 'tapping the right side curls the page (fold, back of the page, shadow)')
  await page.waitForTimeout(250)
  await shot(page, '1-mid-curl')
  await page.waitForTimeout(900)
  const after = await position(page)
  check(after > start, 'the next page is shown after the curl', `${start} → ${after}`)
  check(await page.locator('.curl-flap, .curl-shade').count() === 0, 'nothing is left behind after the curl')
  check(await page.evaluate(() => !document.documentElement.dataset.curl), 'the curl is finished')

  await page.mouse.click(20, height / 2)
  await page.waitForTimeout(1200)
  check(Math.abs(await position(page) - start) < 1e-6, 'tapping the left side curls back to the first page')
}, { settings: { flow: 'paginated', pageTurn: 'curl', motion: 'full' }, context: { serviceWorkers: 'block' } })
