// Quiet: types for the parts of overlayer.js that Quiet uses.
export class Overlayer {
  static highlight(rects: DOMRect[], options?: { color?: string }): SVGElement
  static underline(rects: DOMRect[], options?: { color?: string; width?: number }): SVGElement
  static strikethrough(rects: DOMRect[], options?: { color?: string; width?: number }): SVGElement
  static outline(rects: DOMRect[], options?: { color?: string; width?: number; radius?: number }): SVGElement
}
