// Saving a quote picture says so: on the button, and with a message shown above the dialog
// (messages used to appear behind open dialogs, so "Saved" was never seen).
const path = require('path')
const t = require('./common.cjs')

t.suite('quote-save', async ({ page, check, shot }) => {
  await t.addBooks(page, [path.join(t.BOOKS, 'lamp-and-clock.epub'), path.join(t.BOOKS, 'short-notes.epub')])
  await page.waitForTimeout(600)
  // A saved quote, put straight into the database.
  await page.evaluate(() => new Promise((resolve, reject) => {
    const open = indexedDB.open('quiet')
    open.onsuccess = () => {
      const db = open.result
      const books = db.transaction('books').objectStore('books').getAll()
      books.onsuccess = () => {
        const book = books.result.find(b => b.title === 'The Lamp and the Clock')
        const tx = db.transaction('annotations', 'readwrite')
        tx.objectStore('annotations').put({ id: 'q1', bookId: book.id, type: 'highlight', cfi: 'epubcfi(/6/2!/4/4,/1:0,/1:20)', text: 'She turned the page slowly, as if the words might run away.', color: '#f2d675', quote: true, createdAt: Date.now(), progress: 0.1 })
        tx.oncomplete = () => { db.close(); resolve() }
        tx.onerror = () => reject(tx.error)
      }
    }
    open.onerror = () => reject(open.error)
  }))
  await page.click('#openNotes')
  await page.waitForTimeout(600)
  await shot(page, '0-notes')
  await page.getByRole('button', { name: 'Share as a picture' }).first().click({ timeout: 8000 })
  await page.waitForSelector('dialog[open] .dialog-quote-image, dialog.dialog-quote-image[open]', { timeout: 8000 }).catch(() => {})
  await page.waitForTimeout(800)
  const download = page.waitForEvent('download', { timeout: 8000 }).catch(() => null)
  await page.getByRole('button', { name: 'Save picture' }).click()
  check(!!await download, 'the picture is downloaded (website)')
  await page.waitForTimeout(400)
  const label = await page.locator('dialog[open] .dialog-actions button').first().innerText()
  check(/Downloaded/.test(label), 'the Save button says it worked', label)
  // Shown as a popover after the dialog opened, it's drawn above it (top layer, last on top).
  // (It can't be pressed while the dialog is open, so hit-testing can't tell; the screenshot shows it.)
  const shown = await page.evaluate(() => { const el = document.querySelector('.toast.show'); return !!el && el.matches(':popover-open') })
  check(shown, 'the message is shown in the top layer, above the dialog')
  const box = await page.evaluate(() => { const r = document.querySelector('.toast.show').getBoundingClientRect(); return { w: Math.round(r.width), bottom: Math.round(innerHeight - r.bottom) } })
  check(box.w < 393 && box.bottom >= 10 && box.bottom < 80, 'the message keeps its size and place at the bottom', JSON.stringify(box))
  await shot(page, '1-saved')
}, { context: { serviceWorkers: 'block', acceptDownloads: true } })
