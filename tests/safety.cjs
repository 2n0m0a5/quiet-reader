// The safety copy: the library database is wiped (as happened on a phone), and Quiet puts
// everything back from its copy. "Delete everything" must stay deleted.
// (On the website the safety copy is off; "quiet:test-safety" turns on its browser stand-in.)
const path = require('path')
const t = require('./common.cjs')

const WIPE = `${t.URL}__wipe`
const count = page => page.locator('#library .book-tile [data-book-id], #library .list-row [data-book-id]').count()
const safetyBooks = page => page.evaluate(async () => {
  try {
    const dir = await (await navigator.storage.getDirectory()).getDirectoryHandle('safety')
    const json = JSON.parse(await (await (await dir.getFileHandle('library.json')).getFile()).text())
    return json.books.length
  } catch { return -1 }
})
// Leaves the app (closing its database), deletes the database from a blank page of the same site, comes back.
async function wipeDatabase(page) {
  await page.route(WIPE, route => route.fulfill({ contentType: 'text/html', body: '<p>wipe</p>' }))
  await page.goto(WIPE)
  const result = await page.evaluate(() => new Promise(resolve => {
    const r = indexedDB.deleteDatabase('quiet')
    r.onsuccess = () => resolve('deleted'); r.onerror = () => resolve('error'); r.onblocked = () => resolve('blocked')
  }))
  await page.goto(t.URL)
  await page.waitForSelector('#library', { state: 'attached' })
  return result
}

t.suite('safety', async ({ page, check, shot }) => {
  const books = ['quiet-river-1.epub', 'quiet-river-2.epub', 'lamp-and-clock.epub'].map(f => path.join(t.BOOKS, f))
  await t.addBooks(page, books)
  await page.waitForTimeout(500)
  // Read a little, so there's a reading place to bring back.
  await page.getByText('The Lamp and the Clock').first().click()
  await t.waitForReader(page)
  await t.backToLibrary(page)

  for (let i = 0; i < 40 && await safetyBooks(page) !== 3; i++) await page.waitForTimeout(500)
  check(await safetyBooks(page) === 3, 'safety copy holds the 3 books', String(await safetyBooks(page)))

  check(await wipeDatabase(page) === 'deleted', 'database wiped')
  await page.waitForFunction(() => /safety copy/.test(document.body.innerText), null, { timeout: 15000 }).catch(() => {})
  check(/put back 3 books/.test(await page.locator('body').innerText()), 'Quiet says it put the books back')
  await page.waitForTimeout(800)
  const text = await page.locator('#library').innerText()
  check(['The Quiet River', 'The Long Bridge', 'The Lamp and the Clock'].every(s => text.includes(s)), 'all 3 books are back', text.slice(0, 100))
  check(await page.locator('.continue-card').count() > 0, 'the reading place came back (continue card)')
  await shot(page, '1-restored')

  await page.getByText('The Quiet River').first().click()
  await t.waitForReader(page)
  check(/Chapter 1/.test(await t.bookText(page)), 'a restored book opens')
  await t.backToLibrary(page)

  // "Delete everything" must not come back.
  await page.click('#openSettings')
  await page.waitForTimeout(400)
  await page.click('.settings-home-row[data-page="files"]')
  await page.waitForTimeout(300)
  await page.getByRole('button', { name: 'Delete', exact: true }).click()
  await page.getByRole('button', { name: 'Delete everything' }).click()
  for (let i = 0; i < 20 && await safetyBooks(page) !== 0; i++) await page.waitForTimeout(500)
  check(await safetyBooks(page) === 0, 'safety copy emptied after Delete everything', String(await safetyBooks(page)))
  await page.goto(t.URL)
  await page.waitForTimeout(2500)
  check(!/safety copy/.test(await page.locator('body').innerText()), 'deleted library stays deleted')
}, { storage: { 'quiet:test-safety': '1' }, context: { serviceWorkers: 'block' } })
