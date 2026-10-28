// foliate-js's comic reader (used by formats/load.ts for comics inside RAR, 7-Zip, TAR and zip).
import type { Book } from '../../src/reader/foliate'
export function makeComicBook(
  loader: { entries: { filename: string }[]; loadBlob(name: string): Promise<Blob>; getSize(name: string): number },
  file: File,
): Book
