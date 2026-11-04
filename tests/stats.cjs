// The Stats tab (0.4.1): a needle meter for today against the goal, the week as lights (today in
// orange), three readouts, and the details behind one key.
const t = require('./common.cjs')

t.suite('stats', async ({ page, check, shot }) => {
  await page.evaluate(() => new Promise(resolve => {
    const o = indexedDB.open('quiet')
    o.onsuccess = () => {
      const tx = o.result.transaction('reading', 'readwrite')
      const st = tx.objectStore('reading')
      ;[12, 25, 8, 40, 18, 33, 24].forEach((m, i) => {
        const d = new Date(Date.now() - (6 - i) * 86400000)
        const day = `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
        st.put({ id: `${day}|b1`, day, bookId: 'b1', seconds: m * 60, pages: m })
      })
      tx.oncomplete = () => { o.result.close(); resolve() }
    }
  }))
  await page.click('#openStats')
  await page.waitForTimeout(1600)
  check(/24\s*min today/.test(await page.locator('.stats-meter-read').innerText()), 'the meter reads today’s 24 minutes')
  const angle = await page.evaluate(() => getComputedStyle(document.querySelector('.meter-needle')).getPropertyValue('--angle').trim())
  check(angle === '30deg', 'the needle points at 24 of 30 minutes', angle)
  const lit = await page.$$eval('.light-col', cols => cols.map(c => c.querySelectorAll('span.on').length))
  check(lit.length === 7 && lit[3] === 10 && lit.every(n => n > 0), 'the week shows 7 days of lights, the longest day full', lit.join())
  check(await page.locator('.light-col.today').count() === 1, 'today is marked')
  check(await page.locator('.stats-readout').count() === 3, 'three readouts: streak, words a minute, books this year')
  check(await page.locator('.stats-details').isHidden(), 'the details wait behind a key')
  await page.click('.stats-more')
  await page.waitForTimeout(500)
  check(await page.locator('.stats-details').isVisible(), 'the key opens the year, hours, pace and every book')
  await shot(page, '1-stats')
}, { settings: { dailyGoal: 30 }, context: { serviceWorkers: 'block' } })
