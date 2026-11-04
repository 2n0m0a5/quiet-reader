// Restores a backup made by the native app (native/app/build/outputs/native.quietbackup, written
// by DataTest) in the web app, to check backups work from native to 0.4.2 too.
//   npm run build && node scripts/check-native-backup.cjs
const path = require('path')
const t = require('../tests/common.cjs')
const FILE = path.join(__dirname, '..', 'native', 'app', 'build', 'outputs', 'native.quietbackup')

t.suite('native-backup', async ({ page, check }) => {
  await page.click('#openSettings'); await page.waitForTimeout(500)
  await page.click('.settings-home-row[data-page="files"]'); await page.waitForTimeout(300)
  const chooser = page.waitForEvent('filechooser')
  await page.getByRole('button', { name: 'Restore', exact: true }).click()
  await (await chooser).setFiles(FILE)
  await page.locator('dialog[open] .dialog-actions button').last().click()
  await page.waitForTimeout(3500)
  await page.click('#navLibrary').catch(() => {})
  await page.waitForTimeout(800)
  const text = await page.locator('#library').innerText()
  check(['The Quiet River', 'The Lamp and the Clock', 'Short Notes', 'Plain'].every(s => text.includes(s)), 'the web app restores all 4 books from a native backup', text.slice(0, 160))
  check(await page.evaluate(() => JSON.parse(localStorage.getItem('quiet:settings')).theme) === 'dark', 'the native app\'s settings come along')
  await page.getByText('The Lamp and the Clock').first().click()
  await t.waitForReader(page)
  check((await t.bookText(page)).length > 20, 'a book from the native backup opens in the web app')
}, { context: { serviceWorkers: 'block' } })
