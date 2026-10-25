// Quiet: types for the parts of view.js that Quiet imports directly.
import type { Book } from '../../src/reader/foliate'

export function makeBook(file: File | Blob | string): Promise<Book>
