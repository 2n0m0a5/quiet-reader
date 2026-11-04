// The reader bar: Contents · Day/Night · Light · Text · More at the bottom, a chapter scale,
// More opens every other control, and the four keys can be changed.
const path = require('path')
const t = require('./common.cjs')

const keys = page => page.$$eval('#readerKeys .reader-key', ks => ks.map(k => k.dataset.key))

t.suite('reader-bar', async ({ page, check, shot }) => {
  await t.addBooks(page, path.join(t.BOOKS, 'lamp-and-clock.epub'))
  await t.showBars(page)
  check((await keys(page)).join() === 'contents,daynight,light,text,more', 'the bar: Contents, Day/Night, Light, Text, More', (await keys(page)).join())
  const bar = await page.locator('#readerKeys').boundingBox()
  check(bar && bar.y + bar.height <= page.viewportSize().height && bar.y > page.viewportSize().height * 0.75, 'the bar is at the bottom, fully on screen', JSON.stringify(bar))
  check(await page.evaluate(() => (document.getElementById('progressTicks').style.background.match(/linear-gradient/g) || []).length) === 7, 'the scale has a tick for each of the 7 chapter starts')

  await page.click('#readerKeys [data-key="daynight"]')
  await page.waitForTimeout(600)
  check(await page.evaluate(() => document.documentElement.hasAttribute('data-dark')), 'Day/Night switches to Night')
  await page.click('#readerKeys [data-key="daynight"]')
  await page.waitForTimeout(600)
  check(await page.evaluate(() => !document.documentElement.hasAttribute('data-dark')), 'and back to Day')

  await page.click('#readerKeys [data-key="light"]')
  await page.waitForTimeout(500)
  check(await page.evaluate(() => document.getElementById('settings').classList.contains('open') && document.getElementById('settings').dataset.focus === 'light'), 'Light opens the panel at brightness')
  await page.mouse.click(200, 120)
  await page.waitForTimeout(400)

  await page.click('#readerKeys [data-key="more"]')
  await page.waitForTimeout(500)
  const grid = await page.$$eval('#moreGrid .reader-key', ks => ks.map(k => k.dataset.key ?? k.textContent))
  check(grid.includes('search') && grid.includes('bookmark') && grid.includes('info') && grid.some(k => /Customize/.test(k)), 'More shows the other controls and Customize bar', grid.join())
  await shot(page, '1-more')

  await page.click('#moreGrid .reader-key.customize')
  await page.waitForTimeout(400)
  await page.getByRole('checkbox', { name: /Text/ }).click()
  await page.getByRole('checkbox', { name: /Search/ }).click()
  await page.getByRole('button', { name: 'Done' }).click()
  await page.waitForTimeout(400)
  check((await keys(page)).join() === 'contents,daynight,light,search,more', 'the bar can be changed (Text → Search)', (await keys(page)).join())
  check(await page.evaluate(() => JSON.parse(localStorage.getItem('quiet:settings')).readerKeys.join()) === 'contents,daynight,light,search', 'and the choice is saved')
}, { context: { serviceWorkers: 'block' } })
