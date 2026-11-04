// The main paths in one quick run: add books, open and move through a book,
// the four tabs, the Radio design with Night, and that choices survive a reload.
const path = require('path')
const t = require('./common.cjs')

const view = page => page.evaluate(() => document.getElementById('app').dataset.view)

t.suite('smoke', async ({ page, check, shot }) => {
  // Library
  const books = ['quiet-river-1.epub', 'quiet-river-2.epub', 'lamp-and-clock.epub', 'short-notes.epub'].map(f => path.join(t.BOOKS, f))
  await t.addBooks(page, books)
  await page.waitForTimeout(800)
  const titles = await page.locator('#library').innerText()
  check(['The Quiet River', 'The Long Bridge', 'The Lamp and the Clock', 'Short Notes'].every(s => titles.includes(s)), 'four books in the library', titles.slice(0, 120))
  await shot(page, '1-library')

  // Reader
  await page.locator('#library [data-book-id]').filter({ has: page.locator('img') }).first().waitFor()
  await page.getByText('The Lamp and the Clock').first().click()
  await t.waitForReader(page)
  check(await view(page) === 'reader', 'book opens in the reader')
  check(/Chapter 1/.test(await t.bookText(page)), 'first chapter shows')
  await shot(page, '2-reader')

  await t.showBars(page)
  await page.click('#tocToggle')
  await page.waitForTimeout(500)
  const toc = await page.locator('#toc').innerText()
  check((toc.match(/Chapter \d/g) || []).length === 8, 'contents lists 8 chapters', toc.slice(0, 80))
  await page.locator('#toc').getByText('Chapter 3', { exact: true }).click()
  await page.waitForFunction(() => /Chapter 3/.test(document.getElementById('chapterLabel').textContent), null, { timeout: 8000 }).catch(() => {})
  check(/Chapter 3/.test(await page.locator('#chapterLabel').innerText()), 'contents jumps to chapter 3')
  await shot(page, '3-chapter3')

  await t.backToLibrary(page)
  check(await page.locator('.continue-card').count() > 0, 'continue-reading card appears')

  // Tabs
  for (const [id, name] of [['#openNotes', 'notes'], ['#openStats', 'stats'], ['#openSettings', 'settings'], ['#navLibrary', 'library']]) {
    await page.click(id)
    await page.waitForTimeout(500)
    check(await view(page) === name, `${name} tab opens`)
    if (name !== 'library') await shot(page, `4-${name}`)
  }

  // The design: Radio only (0.4.0), and no style picker left in Settings.
  await page.click('#openSettings')
  await page.waitForTimeout(400)
  check(await page.evaluate(() => document.documentElement.dataset.style) === 'radio', 'the Radio design is on')
  check(await page.locator('.style-card').count() === 0, 'no style picker in Settings')
  await page.click('.choice[data-key="theme"][data-value="dark"]')
  await page.waitForTimeout(700)
  check(await page.evaluate(() => document.documentElement.hasAttribute('data-dark')), 'Night applies')
  await shot(page, '5-night')

  // Persistence
  await page.reload()
  await page.waitForSelector('#app', { state: 'attached' })
  await page.waitForTimeout(800)
  check(await view(page) === 'settings', 'reload keeps the open screen')
  check(await page.evaluate(() => document.documentElement.hasAttribute('data-dark')), 'Night survives a reload')
  await page.click('#navLibrary')
  await page.waitForTimeout(600)
  check((await page.locator('#library').innerText()).includes('The Quiet River'), 'library survives a reload')
})
