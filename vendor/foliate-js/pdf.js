// Quiet: PDF support is planned for v1.x. The upstream pdf.js adapter (and its
// 13 MB PDF.js bundle) is left out until then, so opening a PDF fails cleanly.
export const makePDF = async () => {
    throw new Error('PDF support is coming in a later version of Quiet.')
}
