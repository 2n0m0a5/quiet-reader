// Sliders are drawn by each design style (styles.css). The part before the handle is filled,
// using --fill: call this after setting a slider's value in code; dragging updates it by itself.
export function fillRange(input: HTMLInputElement) {
  const min = Number(input.min || 0)
  const max = Number(input.max || 100)
  input.style.setProperty('--fill', `${((Number(input.value) - min) / (max - min || 1)) * 100}%`)
}

document.addEventListener('input', e => {
  if (e.target instanceof HTMLInputElement && e.target.type === 'range') fillRange(e.target)
})
