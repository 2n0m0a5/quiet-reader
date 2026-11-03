// The Motion setting (Follow phone / Full / Reduced), Curl switching to page mode, and Sepia gone.
// The browser pretends the phone asks for less motion, as Battery saver can.
const t = require('./common.cjs')

const motion = page => page.evaluate(() => document.documentElement.dataset.motion)

t.suite('motion-settings', async ({ page, check, shot }) => {
  check(await motion(page) === 'reduced', 'Follow phone: the phone asks for less motion, so motion is reduced')
  check(await page.evaluate(() => document.documentElement.dataset.theme) === 'light', 'a saved Sepia theme opens as Day')

  await page.click('#openSettings')
  await page.waitForTimeout(400)
  check(/asking apps for less motion right now/.test(await page.locator('.motion-hint').innerText()), 'the hint says the phone asks for less motion')
  await page.click('.choice[data-key="motion"][data-value="full"]')
  check(await motion(page) === 'full', 'Full: motion is back even though the phone asks for less')
  await page.click('.choice[data-key="motion"][data-value="reduced"]')
  check(await motion(page) === 'reduced', 'Reduced: motion is reduced')
  await page.click('.choice[data-key="motion"][data-value="full"]')

  check(await page.locator('.choice[data-key="theme"][data-value="sepia"], .theme-choice[data-value="sepia"]').count() === 0, 'no Sepia choice anywhere')

  await page.click('.choice[data-key="flow"][data-value="scrolled"]')
  await page.click('.choice[data-key="pageTurn"][data-value="curl"]')
  await page.waitForTimeout(300)
  const saved = await page.evaluate(() => JSON.parse(localStorage.getItem('quiet:settings')))
  check(saved.pageTurn === 'curl' && saved.flow === 'paginated', 'choosing Curl in scroll mode switches to pages', `${saved.pageTurn} ${saved.flow}`)
  const said = await page.evaluate(() => document.querySelector('.toast')?.textContent ?? '')
  check(/Curl turns pages/.test(said), 'and says so', said)
  check(await page.locator('.choice[data-key="flow"][data-value="paginated"].selected').count() === 1, 'Pages shows as chosen')
  await shot(page, '1-settings')

  await page.reload()
  await page.waitForSelector('#app', { state: 'attached' })
  check(await motion(page) === 'full', 'the Motion choice survives a reload')
}, { settings: { theme: 'sepia' }, context: { reducedMotion: 'reduce', serviceWorkers: 'block' } })
