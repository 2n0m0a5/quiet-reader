// The parts of zip.js that Quiet uses (formats/office.ts, formats/load.ts).
export function configure(options: { useWebWorkers?: boolean }): void
export class BlobReader { constructor(blob: Blob) }
export class TextWriter { constructor() }
export class BlobWriter { constructor(type?: string) }
export class ZipReader {
  constructor(reader: BlobReader)
  getEntries(): Promise<{ filename: string; uncompressedSize: number; directory: boolean; getData(writer: unknown): Promise<any> }[]>
}
