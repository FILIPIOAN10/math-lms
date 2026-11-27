import * as pdfjs from 'pdfjs-dist'
import workerUrl from 'pdfjs-dist/build/pdf.worker.min.mjs?url'

pdfjs.GlobalWorkerOptions.workerSrc = workerUrl

const MAX_PDF_BYTES = 20 * 1024 * 1024
const MAX_PAGES = 40
const MAX_PROCESSING_MS = 30_000

/** A rejected PDF input or a PDF operation that exceeded its resource bounds. */
export class PdfProcessingError extends Error {
  override name = 'PdfProcessingError'
}

/** One page of a PDF, as an image file ready to upload. */
export interface PdfPageImage {
  file: File
  /** 1-based position in the PDF. */
  page: number
  total: number
}

function deadlineFromNow(): number {
  return Date.now() + MAX_PROCESSING_MS
}

function within<T>(promise: Promise<T>, deadline: number, onTimeout: () => void): Promise<T> {
  const remaining = deadline - Date.now()
  if (remaining <= 0) {
    onTimeout()
    return Promise.reject(new PdfProcessingError('Procesarea PDF-ului a depășit 30 de secunde.'))
  }
  let timer: ReturnType<typeof setTimeout> | undefined
  return Promise.race([
    promise,
    new Promise<T>((_, reject) => {
      timer = setTimeout(() => {
        onTimeout()
        reject(new PdfProcessingError('Procesarea PDF-ului a depășit 30 de secunde.'))
      }, remaining)
    }),
  ]).finally(() => { if (timer) clearTimeout(timer) })
}

async function pdfBytes(file: File, deadline: number): Promise<Uint8Array> {
  if (file.size <= 0 || file.size > MAX_PDF_BYTES) {
    throw new PdfProcessingError('PDF-ul trebuie să aibă cel mult 20 MB.')
  }
  const header = new Uint8Array(await within(file.slice(0, 5).arrayBuffer(), deadline, () => {}))
  if (header.length !== 5 || header[0] !== 0x25 || header[1] !== 0x50 || header[2] !== 0x44
      || header[3] !== 0x46 || header[4] !== 0x2d) {
    throw new PdfProcessingError('Fișierul nu este un PDF valid.')
  }
  return new Uint8Array(await within(file.arrayBuffer(), deadline, () => {}))
}

async function openPdf(file: File, maxPages: number, deadline: number) {
  if (maxPages < 1 || maxPages > MAX_PAGES) {
    throw new PdfProcessingError(`Poți procesa cel mult ${MAX_PAGES} pagini PDF odată.`)
  }
  const data = await pdfBytes(file, deadline)
  const task = pdfjs.getDocument({ data })
  try {
    const pdf = await within(task.promise, deadline, () => { void task.destroy().catch(() => {}) })
    if (pdf.numPages < 1) throw new PdfProcessingError('PDF-ul nu are nicio pagină.')
    if (pdf.numPages > maxPages) {
      throw new PdfProcessingError(`PDF-ul are ${pdf.numPages} pagini, dar poți trimite cel mult ${maxPages}.`)
    }
    return { task, pdf }
  } catch (error) {
    await task.destroy().catch(() => {})
    throw error
  }
}

/**
 * Renders PDF pages one at a time as JPEGs. Input bytes, page count and total time are capped; each page's long edge
 * is at most `maxSide` pixels so an unusually large page cannot allocate an unbounded canvas.
 */
export async function* pdfPageImages(file: File, maxSide = 2000, quality = 0.85,
  maxPages = MAX_PAGES): AsyncGenerator<PdfPageImage> {
  const deadline = deadlineFromNow()
  const { task, pdf } = await openPdf(file, maxPages, deadline)
  const stem = file.name.replace(/\.pdf$/i, '') || 'pagina'
  try {
    for (let number = 1; number <= pdf.numPages; number++) {
      const page = await within(pdf.getPage(number), deadline, () => { void task.destroy().catch(() => {}) })
      try {
        const natural = page.getViewport({ scale: 1 })
        const viewport = page.getViewport({ scale: maxSide / Math.max(natural.width, natural.height) })
        const canvas = document.createElement('canvas')
        canvas.width = Math.max(1, Math.ceil(viewport.width))
        canvas.height = Math.max(1, Math.ceil(viewport.height))
        if (canvas.width > maxSide + 1 || canvas.height > maxSide + 1
            || canvas.width * canvas.height > maxSide * maxSide) {
          throw new PdfProcessingError('PDF-ul conține o pagină prea mare pentru procesare.')
        }
        const context = canvas.getContext('2d')
        if (!context) throw new PdfProcessingError('Browserul nu poate desena pagina PDF-ului.')
        const rendering = page.render({ canvas, canvasContext: context, viewport })
        await within(rendering.promise, deadline, () => {
          rendering.cancel()
          void task.destroy().catch(() => {})
        })
        const blob = await within(new Promise<Blob | null>((resolve) => canvas.toBlob(resolve, 'image/jpeg', quality)),
          deadline, () => { void task.destroy().catch(() => {}) })
        if (!blob) throw new PdfProcessingError(`Pagina ${number} din PDF nu a putut fi transformată în imagine.`)
        yield {
          file: new File([blob], `${stem}-p${String(number).padStart(2, '0')}.jpg`, { type: 'image/jpeg' }),
          page: number,
          total: pdf.numPages,
        }
      } finally {
        page.cleanup()
      }
    }
  } finally {
    await task.destroy().catch(() => {})
  }
}

/** How many pages a PDF has, without rendering any. */
export async function pdfPageCount(file: File, maxPages = MAX_PAGES): Promise<number> {
  const deadline = deadlineFromNow()
  const { task, pdf } = await openPdf(file, maxPages, deadline)
  try {
    return pdf.numPages
  } finally {
    await task.destroy().catch(() => {})
  }
}
