// foliate-js's EPUB reader (used by formats/load.ts for EPUBs packed in other archives).
import type { Book } from '../../src/reader/foliate'
export class EPUB {
  constructor(loader: { entries: { filename: string }[]; loadText(name: string): Promise<string>; loadBlob(name: string): Promise<Blob>; getSize(name: string): number })
  init(): Promise<Book>
}
