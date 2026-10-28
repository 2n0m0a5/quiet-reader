// "Who says it?": quotes have owners. In a book that's usually a character; when nobody is named,
// the quote is the narrator's, and it's credited to the book's author.
import { button, h, openDialog } from './dialog'

export function askSpeaker(options: { text: string; current?: string; known: string[]; author?: string; editing?: boolean }): Promise<string | null> {
  return new Promise(resolve => {
    let answer: string | null = null
    const passage = h('blockquote', 'quote-preview', `“${options.text.length > 220 ? `${options.text.slice(0, 220)}…` : options.text}”`)
    const form = h('form', 'dialog-form')
    const label = h('label', 'field')
    const input = h('input')
    input.value = options.current ?? ''
    input.placeholder = 'A character’s name'
    input.autocomplete = 'off'
    input.enterKeyHint = 'done'
    label.append(h('span', '', 'Who says it?'), input)
    const hint = h('p', 'settings-hint quote-hint', options.author
      ? `Leave it empty if it’s the narrator: the quote is credited to ${options.author}.`
      : 'Leave it empty if it’s the narrator or the author.')
    form.append(passage, label)
    // People already quoted in this book, one tap away (characters come back).
    if (options.known.length) {
      const chips = h('div', 'chips quote-people')
      for (const name of options.known.slice(0, 12)) {
        const chip = h('button', 'chip', name)
        chip.type = 'button'
        chip.addEventListener('click', () => { input.value = name; input.focus() })
        chips.append(chip)
      }
      form.append(chips)
    }
    const actions = h('div', 'dialog-actions')
    const save = h('button', 'btn primary', options.editing ? 'Save' : 'Save quote')
    save.type = 'submit'
    actions.append(button('Cancel', '', () => d.close()), save)
    form.append(hint, actions)
    const d = openDialog({ title: options.editing ? 'Who says it?' : 'Save as a quote', className: 'dialog-small', content: form, onClose: () => resolve(answer) })
    form.addEventListener('submit', e => {
      e.preventDefault()
      answer = input.value.trim().replace(/\s+/g, ' ')
      d.close()
    })
    input.focus()
  })
}

// The names already used for quotes, most used first.
export function knownSpeakers(items: { speaker?: string; quote?: boolean }[]) {
  const counts = new Map<string, number>()
  for (const a of items) if (a.quote && a.speaker) counts.set(a.speaker, (counts.get(a.speaker) ?? 0) + 1)
  return [...counts.keys()].sort((a, b) => counts.get(b)! - counts.get(a)! || a.localeCompare(b))
}
