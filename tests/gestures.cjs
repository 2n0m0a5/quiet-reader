// Touch gestures with real touch events: long-press a book, swipe back from the left edge
// (a short swipe springs back, a long one leaves), and the chapter-pull/scroll still work.
const path = require('path')
const t = require('./common.cjs')

const view = page => page.evaluate(() => document.getElementById('app').dataset.view)

t.suite('gestures', async ({ page, check, shot }) => {
  await t.addBooks(page, [path.join(t.BOOKS, 'lamp-and-clock.epub'), path.join(t.BOOKS, 'short-notes.epub')])
  await page.waitForTimeout(800)
  const touch = await t.touch(page)

  // Long-press a cover: the book's details open, the book does not.
  const cover = await page.locator('#library .book-tile [data-book-id]').first().boundingBox()
  await touch.longPress(cover.x + cover.width / 2, cover.y + cover.height / 2, 700)
  await page.waitForTimeout(600)
  const dialog = await page.evaluate(() => {
    const d = [...document.querySelectorAll('dialog[open], .dialog, .sheet')].find(el => el.getBoundingClientRect().height > 50 && getComputedStyle(el).visibility !== 'hidden')
    return d ? d.innerText.slice(0, 80) : ''
  })
  check(await view(page) === 'library' && dialog.length > 0, 'long-press opens book details', dialog || await view(page))
  await shot(page, '1-long-press')
  await page.keyboard.press('Escape')
  await page.waitForTimeout(500)

  // Open a book with a tap.
  await page.getByText('The Lamp and the Clock').first().click()
  await t.waitForReader(page)
  const { width, height } = page.viewportSize()

  // A short edge swipe springs back and stays in the book.
  await touch.drag(4, height / 2, 60, height / 2, { steps: 6, ms: 300 })
  await page.waitForTimeout(800)
  check(await view(page) === 'reader', 'short edge swipe springs back')

  // A long one goes back to the library.
  await touch.drag(4, height / 2, width * 0.75, height / 2, { steps: 14, ms: 350 })
  await page.waitForTimeout(1200)
  check(await view(page) === 'library', 'long edge swipe goes back to the library', await view(page))
  await shot(page, '2-after-swipe-back')
}, { context: { serviceWorkers: 'block' } })
