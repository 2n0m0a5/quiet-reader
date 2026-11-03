// Every sample format opens in the reader and shows something (text, or a comic page).
const fs = require('fs')
const path = require('path')
const t = require('./common.cjs')

t.suite('formats', async ({ page, check, shot, newPage }) => {
  const files = fs.readdirSync(t.FORMATS).sort()
  for (const name of files) {
    const p = name === files[0] ? page : await newPage()
    const started = Date.now()
    try {
      await t.addBooks(p, path.join(t.FORMATS, name))
      const text = await t.bookText(p)
      const comic = /\.(cb[zrt7]|zip|rar|tar|7z)$/.test(name)
      const pdf = name.endsWith('.pdf')
      check(comic || pdf || /Chapter|lamp|rain|river/i.test(text), `${name} opens (${Date.now() - started} ms)`, text.slice(0, 60))
      await shot(p, name.replace('.', '-'))
    } catch (err) {
      check(false, `${name} opens`, String(err.message).split('\n')[0])
      await shot(p, name.replace('.', '-') + '-failed').catch(() => {})
    }
    if (p !== page) await p.context().close()
  }
})
