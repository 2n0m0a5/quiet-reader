// Bionic reading: the first part of every word is bold, so the eye can skim along the line.
// Word starts are wrapped in <b data-quiet="bionic">. Reading positions, bookmarks and
// highlights ignore these elements (see the data-quiet filter in vendor/foliate-js/epubcfi.js).

const SKIP = 'code, pre, kbd, samp, script, style, svg, math, ruby, [data-quiet]'
const WORD = /\p{L}[\p{L}\p{M}'’-]*/gu

// How many letters of a word to bold: about 40%, at least one.
const boldLength = (word: string) => word.length <= 3 ? 1 : Math.ceil(word.length * 0.4)

export function addBionic(doc: Document) {
  const body = doc.body
  if (!body || body.querySelector('b[data-quiet="bionic"]')) return
  const walker = doc.createTreeWalker(body, NodeFilter.SHOW_TEXT, {
    acceptNode: node => node.parentElement?.closest(SKIP) || !node.nodeValue?.trim()
      ? NodeFilter.FILTER_REJECT : NodeFilter.FILTER_ACCEPT,
  })
  const nodes: Text[] = []
  while (walker.nextNode()) nodes.push(walker.currentNode as Text)

  for (const node of nodes) {
    const text = node.nodeValue!
    const fragment = doc.createDocumentFragment()
    let last = 0
    for (const match of text.matchAll(WORD)) {
      const start = match.index!
      const end = start + boldLength(match[0])
      if (start > last) fragment.append(text.slice(last, start))
      const b = doc.createElement('b')
      b.dataset.quiet = 'bionic'
      b.textContent = text.slice(start, end)
      fragment.append(b)
      last = end
    }
    if (last === 0) continue
    if (last < text.length) fragment.append(text.slice(last))
    node.replaceWith(fragment)
  }
}

export function removeBionic(doc: Document) {
  const marks = doc.querySelectorAll('b[data-quiet="bionic"]')
  const parents = new Set<Node>()
  for (const b of marks) {
    if (b.parentNode) parents.add(b.parentNode)
    b.replaceWith(b.textContent ?? '')
  }
  // Join the pieces back into whole text nodes, as the book had them.
  for (const parent of parents) parent.normalize()
}
