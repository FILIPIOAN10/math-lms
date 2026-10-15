import { fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { AdminEnChaptersPage } from '@/pages/AdminEnChaptersPage'
import { AdminEnChapterPage } from '@/pages/AdminEnChapterPage'
import { ApiError, type EnChapterPage, type EnChapterSummary } from '@/lib/api'

const api = vi.hoisted(() => ({
  listEnChapters: vi.fn(),
  getEnChapter: vi.fn(),
  createEnChapter: vi.fn(),
  updateEnChapter: vi.fn(),
  deleteEnChapter: vi.fn(),
  moveEnChapter: vi.fn(),
  uploadEnChapterPhoto: vi.fn(),
  removeEnChapterPhoto: vi.fn(),
  addEnExample: vi.fn(),
  updateEnExample: vi.fn(),
  deleteEnExample: vi.fn(),
  uploadEnExampleImage: vi.fn(),
  removeEnExampleImage: vi.fn(),
}))
vi.mock('@/lib/api', async (original) => ({ ...(await original<typeof import('@/lib/api')>()), ...api }))

// pdf.js needs a real browser canvas: the PDF is faked here, its pages are plain image files.
const pdf = vi.hoisted(() => ({ pdfPageCount: vi.fn(), pdfPageImages: vi.fn() }))
vi.mock('@/lib/pdfPages', () => pdf)

const chapter = (id: number, over: Partial<EnChapterSummary> = {}): EnChapterSummary => ({
  id, code: `A${id}`, title: `Capitolul ${id}`, area: 'ALGEBRA', position: id, parentId: null, bookPage: null,
  photoCount: 0, exampleCount: 0, ...over,
})

const page = (over: Partial<EnChapterPage> = {}): EnChapterPage => ({
  id: 1, code: 'A1', title: 'Ordinea efectuării operațiilor', area: 'ALGEBRA', notes: null, parentId: null, rootId: 1,
  bookPage: null, sections: [], photos: [], examples: [],
  ...over,
})

function renderAt(url: string) {
  return render(
    <MemoryRouter initialEntries={[url]}>
      <Routes>
        <Route path="/admin/en-chapters" element={<AdminEnChaptersPage />} />
        <Route path="/admin/en-chapters/:id" element={<AdminEnChapterPage />} />
      </Routes>
    </MemoryRouter>,
  )
}

describe('AdminEnChaptersPage', () => {
  beforeEach(() => {
    Object.values(api).forEach((fn) => fn.mockReset())
    api.listEnChapters.mockResolvedValue([
      chapter(1, { title: 'Ordinea efectuării operațiilor', photoCount: 2 }),
      chapter(2, { title: 'Rapoarte și proporții' }),
    ])
  })

  it('lists the chapters with what each page holds, each one opening its editor', async () => {
    renderAt('/admin/en-chapters')

    const row = await screen.findByTestId('en-chapter-row-1')
    expect(within(row).getByRole('link', { name: /A1 Ordinea efectuării operațiilor/ })).toHaveAttribute('href', '/admin/en-chapters/1')
    expect(row).toHaveTextContent('2 poze · 0 modele')
  })

  it('adds a chapter', async () => {
    api.createEnChapter.mockResolvedValue(chapter(21, { code: 'A12' }))
    renderAt('/admin/en-chapters')
    await screen.findByTestId('en-chapter-row-1')

    fireEvent.click(screen.getByRole('button', { name: 'Adaugă capitol' }))
    fireEvent.change(screen.getByLabelText('Cod'), { target: { value: 'a12' } })
    fireEvent.change(screen.getByLabelText('Titlu'), { target: { value: 'Puteri' } })
    fireEvent.change(screen.getByLabelText('Partea'), { target: { value: 'GEOMETRY' } })
    fireEvent.click(screen.getByRole('button', { name: 'Salvează' }))

    await waitFor(() => expect(api.createEnChapter).toHaveBeenCalledWith({
      code: 'a12', title: 'Puteri', area: 'GEOMETRY', notes: null, parentId: null, bookPage: null,
    }))
    await waitFor(() => expect(api.listEnChapters).toHaveBeenCalledTimes(2))
  })

  describe('with sections', () => {
    beforeEach(() => {
      api.listEnChapters.mockResolvedValue([
        chapter(1, { code: 'AI', title: 'Mulțimea numerelor naturale', photoCount: 13, bookPage: 9 }),
        chapter(2, { code: 'AIS5', title: 'Puterea unui număr natural', parentId: 1, bookPage: 12 }),
        chapter(3, { code: 'AIS5P5', title: 'Puterea unui număr natural: Ordinea efectuării operațiilor', parentId: 2, bookPage: 14 }),
        chapter(4, { code: 'AII', title: 'Mulțimea numerelor întregi', bookPage: 22 }),
        chapter(5, { code: 'AIS9', title: 'Încă neîncărcat', parentId: 1, bookPage: 40 }),
      ])
    })

    it('lists the chapters closed and opens one into its sections and points, with their page of the book', async () => {
      renderAt('/admin/en-chapters')
      await screen.findByTestId('en-chapter-row-1')
      expect(screen.queryByTestId('en-chapter-row-2')).not.toBeInTheDocument()

      fireEvent.click(screen.getByRole('button', { name: 'Arată subcapitolele lui AI' }))

      // each one says whether its page is among the photos uploaded to the chapter
      expect(within(screen.getByTestId('en-chapter-row-2')).getByText('pag. 12 · în materie')).toBeInTheDocument()
      expect(within(screen.getByTestId('en-chapter-row-3')).getByText('pag. 14 · în materie')).toBeInTheDocument()
      expect(within(screen.getByTestId('en-chapter-row-5')).getByText('pag. 40 · pagina nu e încărcată')).toBeInTheDocument()
      // a chapter's own row says how many it has, a section's row has no photo counts
      expect(screen.getByRole('button', { name: 'Ascunde subcapitolele lui AI' })).toHaveTextContent('3 subcapitole')
    })

    it('moves a section only among the sections of its chapter', async () => {
      api.moveEnChapter.mockResolvedValue(undefined)
      renderAt('/admin/en-chapters')
      await screen.findByTestId('en-chapter-row-1')
      fireEvent.click(screen.getByRole('button', { name: 'Arată subcapitolele lui AI' }))

      // AIS5 is the first section of AI (cannot go above it) and AIS9 the last (cannot go below it)
      expect(within(screen.getByTestId('en-chapter-row-2')).getByRole('button', { name: 'Mută AIS5 mai sus' })).toBeDisabled()
      expect(within(screen.getByTestId('en-chapter-row-2')).getByRole('button', { name: 'Mută AIS5 mai jos' })).toBeEnabled()
      expect(within(screen.getByTestId('en-chapter-row-5')).getByRole('button', { name: 'Mută AIS9 mai jos' })).toBeDisabled()
    })

    it('adds a section under a chapter, with the page of the book where it starts', async () => {
      api.createEnChapter.mockResolvedValue(chapter(9, { code: 'AIS6' }))
      renderAt('/admin/en-chapters')
      await screen.findByTestId('en-chapter-row-1')

      fireEvent.click(screen.getByRole('button', { name: 'Adaugă capitol' }))
      fireEvent.change(screen.getByLabelText('Cod'), { target: { value: 'AIS6' } })
      fireEvent.change(screen.getByLabelText('Titlu'), { target: { value: 'Scrierea în baza 10' } })
      fireEvent.change(screen.getByLabelText('Sub capitolul'), { target: { value: '1' } })
      fireEvent.change(screen.getByLabelText('Pagina din carte'), { target: { value: '15' } })
      fireEvent.click(screen.getByRole('button', { name: 'Salvează' }))

      await waitFor(() => expect(api.createEnChapter).toHaveBeenCalledWith({
        code: 'AIS6', title: 'Scrierea în baza 10', area: 'ALGEBRA', notes: null, parentId: 1, bookPage: 15,
      }))
    })

    it('does not offer a point as a parent, nor the page itself', async () => {
      api.getEnChapter.mockResolvedValue(page({ id: 4, code: 'AII', title: 'Mulțimea numerelor întregi', rootId: 4, bookPage: 22 }))
      renderAt('/admin/en-chapters')
      await screen.findByTestId('en-chapter-row-4')
      fireEvent.click(screen.getByRole('button', { name: 'Arată subcapitolele lui AI' })) // so the section and the point exist

      fireEvent.click(within(screen.getByTestId('en-chapter-row-4')).getByRole('button', { name: 'Editează AII' }))
      const parent = await screen.findByLabelText('Sub capitolul')

      const offered = within(parent).getAllByRole('option').map((o) => o.textContent ?? '')
      expect(offered.some((text) => text.includes('AIS5 ·'))).toBe(true) // a section may hold points
      expect(offered.some((text) => text.includes('AIS5P5'))).toBe(false) // a point: nothing goes under it
      expect(offered.some((text) => text.includes('AII ·'))).toBe(false) // not under itself
    })

    it('keeps the place in the book when a chapter is edited', async () => {
      api.getEnChapter.mockResolvedValue(page({ id: 2, code: 'AIS5', title: 'Puterea unui număr natural', parentId: 1, rootId: 1, bookPage: 12 }))
      api.updateEnChapter.mockResolvedValue(chapter(2))
      renderAt('/admin/en-chapters')
      await screen.findByTestId('en-chapter-row-1')
      fireEvent.click(screen.getByRole('button', { name: 'Arată subcapitolele lui AI' }))

      fireEvent.click(within(screen.getByTestId('en-chapter-row-2')).getByRole('button', { name: 'Editează AIS5' }))
      fireEvent.change(await screen.findByLabelText('Titlu'), { target: { value: 'Puteri' } })
      fireEvent.click(screen.getByRole('button', { name: 'Salvează' }))

      await waitFor(() => expect(api.updateEnChapter).toHaveBeenCalledWith(2, {
        code: 'AIS5', title: 'Puteri', area: 'ALGEBRA', notes: null, parentId: 1, bookPage: 12,
      }))
    })
  })

  it('moves a chapter up and refreshes the list', async () => {
    api.moveEnChapter.mockResolvedValue(undefined)
    renderAt('/admin/en-chapters')
    const row = await screen.findByTestId('en-chapter-row-2')

    fireEvent.click(within(row).getByRole('button', { name: 'Mută A2 mai sus' }))

    await waitFor(() => expect(api.moveEnChapter).toHaveBeenCalledWith(2, 'UP'))
    await waitFor(() => expect(api.listEnChapters).toHaveBeenCalledTimes(2))
  })

  it('asks before deleting and shows why the server refused', async () => {
    api.deleteEnChapter.mockRejectedValue(new ApiError(409, 'Capitolul „A1 · Ordinea” are 2 poze și 0 modele. Șterge-le întâi, apoi capitolul.'))
    renderAt('/admin/en-chapters')
    const row = await screen.findByTestId('en-chapter-row-1')

    fireEvent.click(within(row).getByRole('button', { name: 'Șterge A1' }))
    fireEvent.click(await screen.findByRole('button', { name: 'Șterge capitolul' }))

    await waitFor(() => expect(api.deleteEnChapter).toHaveBeenCalledWith(1))
    expect(await screen.findByText(/Șterge-le întâi, apoi capitolul/)).toBeInTheDocument()
  })
})

describe('AdminEnChapterPage', () => {
  beforeEach(() => {
    Object.values(api).forEach((fn) => fn.mockReset())
  })

  it('uploads every chosen photo of the theory, in order', async () => {
    api.getEnChapter.mockResolvedValue(page())
    api.uploadEnChapterPhoto.mockResolvedValue({ id: 9 })
    renderAt('/admin/en-chapters/1')
    await screen.findByRole('heading', { level: 1, name: 'A1 · Ordinea efectuării operațiilor' })

    const files = [new File(['a'], 'p1.png', { type: 'image/png' }), new File(['b'], 'p2.png', { type: 'image/png' })]
    fireEvent.change(screen.getByLabelText('Adaugă poze cu materia'), { target: { files } })

    await waitFor(() => expect(api.uploadEnChapterPhoto).toHaveBeenCalledTimes(2))
    expect(api.uploadEnChapterPhoto.mock.calls.map((c) => [c[0], (c[1] as File).name])).toEqual([[1, 'p1.png'], [1, 'p2.png']])
    await waitFor(() => expect(api.getEnChapter).toHaveBeenCalledTimes(2))
  })

  it('turns a chapter PDF into its pages: each one is uploaded as a photo, in order', async () => {
    api.getEnChapter.mockResolvedValue(page())
    api.uploadEnChapterPhoto.mockResolvedValue({ id: 9 })
    pdf.pdfPageCount.mockResolvedValue(2)
    pdf.pdfPageImages.mockImplementation(async function* () {
      yield { file: new File(['1'], 'cap-p01.jpg', { type: 'image/jpeg' }), page: 1, total: 2 }
      yield { file: new File(['2'], 'cap-p02.jpg', { type: 'image/jpeg' }), page: 2, total: 2 }
    })
    renderAt('/admin/en-chapters/1')
    await screen.findByRole('heading', { level: 1, name: 'A1 · Ordinea efectuării operațiilor' })

    const file = new File(['%PDF'], 'capitolul-1.pdf', { type: 'application/pdf' })
    fireEvent.change(screen.getByLabelText('Adaugă PDF cu materia'), { target: { files: [file] } })

    await waitFor(() => expect(api.uploadEnChapterPhoto).toHaveBeenCalledTimes(2))
    expect(api.uploadEnChapterPhoto.mock.calls.map((c) => [c[0], (c[1] as File).name])).toEqual([[1, 'cap-p01.jpg'], [1, 'cap-p02.jpg']])
    await waitFor(() => expect(api.getEnChapter).toHaveBeenCalledTimes(2))
  })

  it('refuses a PDF with more pages than the chapter has room for, before sending anything', async () => {
    api.getEnChapter.mockResolvedValue(page({ photos: Array.from({ length: 30 }, (_, i) => ({ id: i + 1 })) }))
    pdf.pdfPageCount.mockResolvedValue(15)
    renderAt('/admin/en-chapters/1')
    await screen.findByRole('heading', { level: 1 })

    fireEvent.change(screen.getByLabelText('Adaugă PDF cu materia'), {
      target: { files: [new File(['%PDF'], 'mare.pdf', { type: 'application/pdf' })] },
    })

    expect(await screen.findByRole('alert')).toHaveTextContent('15 pagini')
    expect(screen.getByRole('alert')).toHaveTextContent('mai poate primi 10')
    expect(api.uploadEnChapterPhoto).not.toHaveBeenCalled()
  })

  it('lists the sections of a chapter with whether they have text, each one opening its own editor', async () => {
    api.getEnChapter.mockResolvedValue(page({
      id: 1, code: 'AI', title: 'Mulțimea numerelor naturale', bookPage: 9,
      sections: [
        { id: 2, code: 'AIS1', title: 'Introducere', parentId: 1, bookPage: 9, notes: 'Ceva text.' },
        { id: 3, code: 'AIS1P1', title: 'Introducere: Axa numerelor', parentId: 2, bookPage: 9, notes: null },
      ],
    }))
    renderAt('/admin/en-chapters/1')

    const first = await screen.findByTestId('en-section-2')
    expect(first).toHaveTextContent('are text')
    expect(within(first).getByRole('link', { name: 'Editează AIS1' })).toHaveAttribute('href', '/admin/en-chapters/2')
    const second = screen.getByTestId('en-section-3')
    expect(second).toHaveTextContent('fără text')
    expect(within(second).getByRole('link', { name: 'Editează AIS1P1' })).toHaveAttribute('href', '/admin/en-chapters/3')
  })

  it('shows the page text the way a student sees it while it is being edited', async () => {
    api.getEnChapter.mockResolvedValue(page({ id: 2, code: 'AIS1', parentId: 1, rootId: 1, bookPage: 9, notes: null }))
    renderAt('/admin/en-chapters/2')
    fireEvent.click(await screen.findByRole('button', { name: 'Editează capitolul' }))

    fireEvent.change(await screen.findByLabelText('Text pe pagină (opțional)'), {
      target: { value: '**Reținem!** suma e $a+b=s$.\n\n| a | b |\n|---|---|\n| 1 | 2 |' },
    })

    const preview = await screen.findByTestId('en-notes-preview')
    expect(within(preview).getByText('Reținem!').closest('strong')).not.toBeNull()
    expect(within(preview).getByRole('table')).toBeInTheDocument()
  })

  it('sends the teacher to the chapter for the photos of a section', async () => {
    api.getEnChapter.mockResolvedValue(page({ id: 2, code: 'AIS5', title: 'Puterea', parentId: 1, rootId: 1, bookPage: 12 }))
    renderAt('/admin/en-chapters/2')

    const note = await screen.findByTestId('en-section-note')
    expect(note).toHaveTextContent('pagina 12 din carte')
    expect(within(note).getByRole('link', { name: 'Pozele se încarcă la capitol' })).toHaveAttribute('href', '/admin/en-chapters/1')
    expect(screen.queryByLabelText('Adaugă poze cu materia')).not.toBeInTheDocument()
  })

  it('removes a photo after asking', async () => {
    api.getEnChapter.mockResolvedValue(page({ photos: [{ id: 7 }] }))
    api.removeEnChapterPhoto.mockResolvedValue(undefined)
    renderAt('/admin/en-chapters/1')

    fireEvent.click(await screen.findByRole('button', { name: 'Șterge pagina 1' }))
    fireEvent.click(await screen.findByRole('button', { name: 'Șterge poza' }))

    await waitFor(() => expect(api.removeEnChapterPhoto).toHaveBeenCalledWith(1, 7))
  })

  it('adds a worked example with its explained solution', async () => {
    api.getEnChapter.mockResolvedValue(page())
    api.addEnExample.mockResolvedValue({ id: 3, statement: 'x', solution: 'y', image: null })
    renderAt('/admin/en-chapters/1')
    await screen.findByRole('heading', { level: 1 })

    fireEvent.click(screen.getByRole('button', { name: 'Adaugă model' }))
    fireEvent.change(screen.getByLabelText('Enunț'), { target: { value: '$30 - 18 : 3$' } })
    fireEvent.change(screen.getByLabelText('Rezolvare explicată'), { target: { value: 'Întâi împărțirea.' } })
    fireEvent.click(screen.getByRole('button', { name: 'Salvează' }))

    await waitFor(() => expect(api.addEnExample).toHaveBeenCalledWith(1, { statement: '$30 - 18 : 3$', solution: 'Întâi împărțirea.' }))
  })

  it('edits and deletes an example, and gives it a figure', async () => {
    api.getEnChapter.mockResolvedValue(page({
      examples: [{ id: 3, statement: 'AB = ?', solution: 'AB = 4 cm', image: null }],
    }))
    api.updateEnExample.mockResolvedValue({ id: 3, statement: 'AB = ?', solution: 'AB = 5 cm', image: null })
    api.uploadEnExampleImage.mockResolvedValue(undefined)
    api.deleteEnExample.mockResolvedValue(undefined)
    renderAt('/admin/en-chapters/1')
    const example = await screen.findByTestId('en-example-3')

    fireEvent.click(within(example).getByRole('button', { name: 'Editează' }))
    fireEvent.change(screen.getByLabelText('Rezolvare explicată'), { target: { value: 'AB = 5 cm' } })
    fireEvent.click(screen.getByRole('button', { name: 'Salvează' }))
    await waitFor(() => expect(api.updateEnExample).toHaveBeenCalledWith(1, 3, { statement: 'AB = ?', solution: 'AB = 5 cm' }))

    const figure = new File(['f'], 'fig.png', { type: 'image/png' })
    fireEvent.change(within(screen.getByTestId('en-example-3')).getByLabelText('Adaugă o figură modelului 1'), { target: { files: [figure] } })
    await waitFor(() => expect(api.uploadEnExampleImage).toHaveBeenCalledWith(1, 3, figure))

    fireEvent.click(within(screen.getByTestId('en-example-3')).getByRole('button', { name: 'Șterge' }))
    fireEvent.click(await screen.findByRole('button', { name: 'Șterge modelul' }))
    await waitFor(() => expect(api.deleteEnExample).toHaveBeenCalledWith(1, 3))
  })
})
