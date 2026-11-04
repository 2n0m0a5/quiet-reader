// The page curl: a tap on the right side curls the page away (a fold with the page's back and a
// shadow), the next page is there afterwards, nothing is left behind; going back works too.
const path = require('path')
const t = require('./common.cjs')

const position = page => page.evaluate(() => document.querySelector('foliate-view').lastLocation?.fraction ?? -1)

t.suite('curl', async ({ page, check, shot, newPage }) => {
  await t.addBooks(page, path.join(t.BOOKS, 'lamp-and-clock.epub'))
  await page.waitForTimeout(1200)
  const { width, height } = page.viewportSize()
  const start = await position(page)

  await page.mouse.click(width - 20, height / 2)
  const curled = await page.waitForFunction(() => document.documentElement.dataset.curl === 'next' && document.querySelector('.curl-flap'), null, { timeout: 3000, polling: 16 }).then(() => true).catch(() => false)
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

  // A PDF on the Night page (fixed pages, smaller than the screen): only the page curls, and the
  // back of the page is paper-coloured, not the dark screen.
  const pdf = await newPage({ settings: { flow: 'paginated', pageTurn: 'curl', motion: 'full', theme: 'dark' }, context: { serviceWorkers: 'block' } })
  await t.addBooks(pdf, path.join(t.FORMATS, 'sample.pdf'))
  await pdf.waitForTimeout(1500)
  await pdf.mouse.click(width - 20, height / 2)
  await pdf.waitForFunction(() => document.querySelector('.curl-flap'), null, { timeout: 3000, polling: 16 }).catch(() => {})
  const look = await pdf.evaluate(() => {
    const flap = document.querySelector('.curl-flap')
    if (!flap) return null
    const r = flap.getBoundingClientRect(), stage = document.querySelector('.reader-stage').getBoundingClientRect()
    return { paper: getComputedStyle(flap).getPropertyValue('--paper').trim(), smaller: r.height < stage.height - 4 || r.width < stage.width - 4 }
  })
  check(look && /255, 255, 255|#fff/i.test(look.paper), 'PDF: the back of the page is paper-white', JSON.stringify(look))
  check(look && look.smaller, 'PDF: only the page curls, not the dark margins', JSON.stringify(look))
  await pdf.waitForTimeout(300)
  await shot(pdf, '2-pdf-night-mid-curl')
  await pdf.waitForTimeout(1000)
  check(await pdf.locator('.curl-flap').count() === 0, 'PDF: nothing left behind')
}, { settings: { flow: 'paginated', pageTurn: 'curl', motion: 'full' }, context: { serviceWorkers: 'block' } })
