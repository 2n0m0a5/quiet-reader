// Library backup and restore: back up on one "phone", restore on a fresh one (books, reading place,
// notes and settings come back), restore again (nothing doubles).
const path = require('path')
const t = require('./common.cjs')

const settingsScreen = async page => { await page.click('#openSettings'); await page.waitForTimeout(500); await page.click('.settings-home-row[data-page="files"]'); await page.waitForTimeout(300) }

t.suite('backup', async ({ page, check, shot, newPage }) => {
  await t.addBooks(page, ['quiet-river-1.epub', 'lamp-and-clock.epub', 'short-notes.epub'].map(f => path.join(t.BOOKS, f)))
  await page.waitForTimeout(600)
  await page.getByText('The Lamp and the Clock').first().click()
  await t.waitForReader(page)
  await t.backToLibrary(page)
  await page.evaluate(() => new Promise(resolve => {
    const open = indexedDB.open('quiet')
    open.onsuccess = () => {
      const db = open.result
      const r = db.transaction('books').objectStore('books').getAll()
      r.onsuccess = () => {
        const book = r.result.find(b => b.title === 'The Lamp and the Clock')
        const tx = db.transaction('annotations', 'readwrite')
        tx.objectStore('annotations').put({ id: 'n1', bookId: book.id, type: 'highlight', cfi: 'epubcfi(/6/2!/4/4,/1:0,/1:20)', text: 'The river below the bridge.', note: 'A note to keep', color: '#f2d675', createdAt: Date.now() })
        tx.oncomplete = () => { db.close(); resolve() }
      }
    }
  }))
  await page.evaluate(() => { const s = JSON.parse(localStorage.getItem('quiet:settings') || '{}'); s.fontSize = 135; localStorage.setItem('quiet:settings', JSON.stringify(s)) })

  await settingsScreen(page)
  const download = page.waitForEvent('download', { timeout: 30000 })
  await page.getByRole('button', { name: 'Back up', exact: true }).click()
  const file = await download.catch(() => null)
  check(!!file && /^Quiet backup .*\.quietbackup$/.test(file.suggestedFilename()), 'Back up saves one .quietbackup file', file?.suggestedFilename())
  const saved = path.join(t.OUT, 'backup', 'library.quietbackup')
  await file.saveAs(saved)
  await page.waitForTimeout(500)
  check(/Last backup:/.test(await page.locator('#settingsScreen').innerText()), 'Settings shows when the last backup was made')

  // A fresh phone.
  const fresh = await newPage({ context: { serviceWorkers: 'block' } })
  await settingsScreen(fresh)
  const chooser = fresh.waitForEvent('filechooser')
  await fresh.getByRole('button', { name: 'Restore', exact: true }).click()
  await (await chooser).setFiles(saved)
  await fresh.locator('dialog[open] .dialog-actions button').last().click()
  // Settings came along (the library was empty), so Quiet reloads.
  await fresh.waitForTimeout(3500)
  await fresh.waitForSelector('#app', { state: 'attached' })
  await fresh.click('#navLibrary').catch(() => {})
  await fresh.waitForTimeout(800)
  const text = await fresh.locator('#library').innerText()
  check(['The Quiet River', 'The Lamp and the Clock', 'Short Notes'].every(s => text.includes(s)), 'all 3 books come back on a fresh phone', text.slice(0, 120))
  check(await fresh.locator('.continue-card').count() > 0, 'the reading place comes back')
  check(await fresh.evaluate(() => JSON.parse(localStorage.getItem('quiet:settings')).fontSize) === 135, 'settings come back into an empty library')
  await fresh.click('#openNotes')
  await fresh.waitForTimeout(600)
  check(/A note to keep/.test(await fresh.locator('#notesScreen').innerText()), 'notes come back')
  await shot(fresh, '1-restored-notes')

  await fresh.getByText('The Lamp and the Clock').first().click().catch(() => {})
  await fresh.click('#navLibrary').catch(() => {})
  await settingsScreen(fresh)
  const chooser2 = fresh.waitForEvent('filechooser')
  await fresh.getByRole('button', { name: 'Restore', exact: true }).click()
  await (await chooser2).setFiles(saved)
  await fresh.locator('dialog[open] .dialog-actions button').last().click()
  await fresh.waitForFunction(() => /Restored:/.test(document.querySelector('.toast')?.textContent ?? ''), null, { timeout: 15000 }).catch(() => {})
  const toastText = await fresh.evaluate(() => document.querySelector('.toast')?.textContent ?? '')
  check(/Restored: 0 new books/.test(toastText) && /0 notes/.test(toastText), 'restoring again adds nothing twice', toastText)
  const count = await fresh.evaluate(() => new Promise(resolve => { const o = indexedDB.open('quiet'); o.onsuccess = () => { const r = o.result.transaction('books').objectStore('books').count(); r.onsuccess = () => { o.result.close(); resolve(r.result) } } }))
  check(count === 3, 'still 3 books', String(count))
}, { context: { serviceWorkers: 'block', acceptDownloads: true } })
