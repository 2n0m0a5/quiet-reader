// Runs every suite in this folder, one after another, and lists the result of each.
//   node tests/run-all.cjs            (after `npm run build`)
//   node tests/run-all.cjs smoke formats   (only these)
const { execFileSync } = require('child_process')
const fs = require('fs')
const path = require('path')
const t = require('./common.cjs')

;(async () => {
  const only = process.argv.slice(2)
  const suites = fs.readdirSync(__dirname)
    .filter(f => f.endsWith('.cjs') && !['common.cjs', 'run-all.cjs'].includes(f))
    .map(f => f.replace('.cjs', ''))
    .filter(name => !only.length || only.includes(name))
  const server = await t.ensureServer() // one server for every suite
  const results = []
  for (const name of suites) {
    let out = '', ok = true
    try { out = execFileSync('node', [path.join(__dirname, `${name}.cjs`)], { encoding: 'utf8' }) }
    catch (err) { out = String(err.stdout || err); ok = false }
    const failures = out.split('\n').filter(l => l.startsWith('FAIL'))
    results.push([name, ok, out.trim().split('\n').pop()])
    console.log(`${ok ? '✓' : '✗'} ${out.trim().split('\n').pop()}`)
    for (const f of failures) console.log(`    ${f}`)
  }
  if (server) process.kill(-server.pid)
  const bad = results.filter(r => !r[1]).length
  console.log(bad ? `\n${bad} of ${results.length} suites failed.` : `\nAll ${results.length} suites passed.`)
  process.exitCode = bad ? 1 : 0
})()
