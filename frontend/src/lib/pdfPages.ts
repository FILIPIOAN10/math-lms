import * as pdfjs from 'pdfjs-dist'
import workerUrl from 'pdfjs-dist/build/pdf.worker.min.mjs?url'

pdfjs.GlobalWorkerOptions.workerSrc = workerUrl

/** One page of a PDF, as an image file ready to upload. */
export interface PdfPageImage {
  file: File
  /** 1-based position in the PDF. */
  page: number
  total: number
}

/**
 * Renders the pages of a PDF one at a time as JPEG files whose long edge is at most `maxSide` px - the same size the
 * photo upload shrinks to - so a chapter scanned into one PDF goes up as the chapter's pages, in order. The PDF stays
 * in the browser: only the page images are sent.
 */
export async function* pdfPageImages(file: File, maxSide = 2000, quality = 0.85): AsyncGenerator<PdfPageImage> {
  const task = pdfjs.getDocument({ data: new Uint8Array(await file.arrayBuffer()) })
  const pdf = await task.promise
  const stem = file.name.replace(/\.pdf$/i, '') || 'pagina'
  try {
    for (let number = 1; number <= pdf.numPages; number++) {
      const page = await pdf.getPage(number)
      const natural = page.getViewport({ scale: 1 })
      const viewport = page.getViewport({ scale: maxSide / Math.max(natural.width, natural.height) })
      const canvas = document.createElement('canvas')
      canvas.width = Math.round(viewport.width)
      canvas.height = Math.round(viewport.height)
      const context = canvas.getContext('2d')
      if (!context) throw new Error('Browserul nu poate desena pagina PDF-ului.')
      await page.render({ canvas, canvasContext: context, viewport }).promise
      const blob = await new Promise<Blob | null>((resolve) => canvas.toBlob(resolve, 'image/jpeg', quality))
      if (!blob) throw new Error(`Pagina ${number} din PDF nu a putut fi transformată în imagine.`)
      page.cleanup()
      yield {
        file: new File([blob], `${stem}-p${String(number).padStart(2, '0')}.jpg`, { type: 'image/jpeg' }),
        page: number,
        total: pdf.numPages,
      }
    }
  } finally {
    await task.destroy()
  }
}

/** How many pages a PDF has, without rendering any. */
export async function pdfPageCount(file: File): Promise<number> {
  const task = pdfjs.getDocument({ data: new Uint8Array(await file.arrayBuffer()) })
  try {
    return (await task.promise).numPages
  } finally {
    await task.destroy()
  }
}
