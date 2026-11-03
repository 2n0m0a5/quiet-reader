// Desktop with a mouse: the wheel scrolls every long list (What's new, settings, the reading
// settings), and the reading settings button opens its panel, even when clicked while the book is
// still zooming in (clicks during a screen change used to be lost).
const path = require('path')
const fs = require('fs')
const t = require('./common.cjs')

// Wheels over the element and says whether anything under the pointer scrolled.
async function wheelScrolls(page, selector) {
  const box = await page.locator(selector).first().boundingBox()
  if (!box) return 'not shown'
  const x = box.x + box.width / 2, y = box.y + Math.min(box.height / 2, 200)
  const before = await page.evaluate(([x, y]) => { let e = document.elementFromPoint(x, y); const out = []; for (; e; e = e.parentElement) out.push(e.scrollTop); return out }, [x, y])
  await page.mouse.move(x, y)
  for (let i = 0; i < 4; i++) { await page.mouse.wheel(0, 240); await page.waitForTimeout(80) }
  await page.waitForTimeout(400)
  const after = await page.evaluate(([x, y]) => { let e = document.elementFromPoint(x, y); const out = []; for (; e; e = e.parentElement) out.push(e.scrollTop); return out }, [x, y])
  return after.some((v, i) => v !== before[i]) ? 'scrolled' : `stuck (${before.join(',')} → ${after.join(',')})`
}

t.suite('desktop', async ({ page, check, shot }) => {
  // What's new is long.
  await page.waitForSelector('dialog[open]', { timeout: 8000 })
  await page.waitForTimeout(600)
  let r = await wheelScrolls(page, 'dialog[open] .dialog-body')
  check(r === 'scrolled', 'mouse wheel scrolls What’s new', r)
  await page.keyboard.press('Escape')
  await page.waitForTimeout(400)

  // A library long enough to scroll.
  const all = [...fs.readdirSync(t.BOOKS).filter(f => f.endsWith('.epub')).map(f => path.join(t.BOOKS, f)),
    ...['sample.fb2', 'sample.docx', 'sample.md', 'sample.txt', 'sample.rtf', 'sample.odt'].map(f => path.join(t.FORMATS, f))]
  await t.addBooks(page, all)
  await page.waitForTimeout(800)
  r = await wheelScrolls(page, '#library')
  check(r === 'scrolled', 'mouse wheel scrolls the library', r)

  await page.click('#openSettings')
  await page.waitForTimeout(500)
  r = await wheelScrolls(page, '#settingsScreen')
  check(r === 'scrolled', 'mouse wheel scrolls Settings', r)

  // A book: the reading settings button, once the opening animation is done.
  await page.click('#navLibrary')
  await page.waitForTimeout(500)
  await page.getByText('The Lamp and the Clock').first().click()
  await t.waitForReader(page)
  // Clicked at once, while the book is still zooming in: the click must still count.
  const toggle = await page.locator('#settingsToggle').boundingBox()
  await page.mouse.click(toggle.x + toggle.width / 2, toggle.y + toggle.height / 2)
  await page.waitForTimeout(400)
  const pop = await page.evaluate(() => { const s = document.getElementById('settings'); const r = s.getBoundingClientRect(); return { open: s.classList.contains('open'), inside: r.width > 0 && r.top >= 0 && r.left >= 0 && r.right <= innerWidth } })
  check(pop.open && pop.inside, 'the reading settings button opens its panel (even clicked during the opening animation)', JSON.stringify(pop))
  r = await wheelScrolls(page, '#settings')
  check(r === 'scrolled' || r.startsWith('stuck (0'), 'the reading settings panel takes the wheel (scrolls if it is long)', r)
  await shot(page, '1-reading-settings')
  await page.mouse.click(20, 400)
  await page.waitForTimeout(300)
  check(await page.evaluate(() => !document.getElementById('settings').classList.contains('open')), 'clicking elsewhere closes it')
}, { size: { width: 1366, height: 640 }, touch: false, storage: { 'quiet:seen-version': '0.2.0' }, context: { serviceWorkers: 'block' } })
