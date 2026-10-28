// Every kind of file Quiet can open, in one place: the file picker, book folders and
// "Open with Quiet" all use this list. (The Android app's own list is in QuietNativePlugin.java
// and AndroidManifest.xml.)

export const BOOK_FORMATS = {
  books: ['epub', 'mobi', 'azw3', 'azw', 'prc', 'pdb', 'fb2', 'fbz', 'pdf', 'docx', 'odt', 'rtf', 'md', 'markdown', 'txt', 'txtz', 'html', 'htm', 'xhtml', 'htmlz'],
  comics: ['cbz', 'cbr', 'cb7', 'cbt', 'zip', 'rar', '7z', 'tar'],
}
export const EXTENSIONS = [...BOOK_FORMATS.books, ...BOOK_FORMATS.comics]

export const extensionOf = (name: string) => name.toLowerCase().match(/\.([a-z0-9]+)$/)?.[1] ?? ''

// Ends with ".fb2.zip" counts as FB2 too.
export const isBookFile = (name: string) => EXTENSIONS.includes(extensionOf(name)) || /\.fb2\.zip$/i.test(name)

const MIME_TYPES = [
  'application/epub+zip', 'application/x-mobipocket-ebook', 'application/x-fictionbook+xml',
  'application/vnd.comicbook+zip', 'application/vnd.comicbook-rar', 'application/x-cbr', 'application/x-cb7',
  'application/pdf', 'text/plain', 'text/html', 'text/markdown', 'application/rtf', 'text/rtf',
  'application/vnd.openxmlformats-officedocument.wordprocessingml.document', 'application/vnd.oasis.opendocument.text',
  'application/zip', 'application/x-rar-compressed', 'application/vnd.rar', 'application/x-7z-compressed', 'application/x-tar',
]

// For the file picker.
export const ACCEPTED = [...EXTENSIONS.map(e => `.${e}`), ...MIME_TYPES].join(',')

// For the welcome text and error messages.
export const FORMAT_NAMES = 'EPUB, MOBI, AZW3, FB2, PDF, Word (DOCX), OpenDocument (ODT), RTF, Markdown, TXT, HTML and comics (CBZ, CBR, CB7, CBT)'
