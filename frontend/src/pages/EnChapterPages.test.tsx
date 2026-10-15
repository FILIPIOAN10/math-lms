import { fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { EnChaptersPage } from '@/pages/EnChaptersPage'
import { EnChapterPage } from '@/pages/EnChapterPage'
import { type EnChapterPage as EnChapterPageDto, type EnChapterSummary } from '@/lib/api'

const api = vi.hoisted(() => ({
  listEnChapters: vi.fn(),
  getEnChapter: vi.fn(),
}))
vi.mock('@/lib/api', async (original) => ({ ...(await original<typeof import('@/lib/api')>()), ...api }))

const chapter = (id: number, over: Partial<EnChapterSummary> = {}): EnChapterSummary => ({
  id, code: `A${id}`, title: `Capitolul ${id}`, area: 'ALGEBRA', position: id, parentId: null, bookPage: null,
  photoCount: 0, exampleCount: 0, ...over,
})

const page = (over: Partial<EnChapterPageDto> = {}): EnChapterPageDto => ({
  id: 1, code: 'A1', title: 'Ordinea efectuării operațiilor', area: 'ALGEBRA', notes: null, parentId: null, rootId: 1,
  bookPage: null, sections: [], photos: [], examples: [],
  ...over,
})

function renderAt(url: string) {
  return render(
    <MemoryRouter initialEntries={[url]}>
      <Routes>
        <Route path="/capitole" element={<EnChaptersPage />} />
        <Route path="/capitole/:id" element={<EnChapterPage />} />
      </Routes>
    </MemoryRouter>,
  )
}

describe('EnChaptersPage', () => {
  beforeEach(() => Object.values(api).forEach((fn) => fn.mockReset()))

  it('lists algebra and geometry apart, and only a page with something on it opens', async () => {
    api.listEnChapters.mockResolvedValue([
      chapter(1, { title: 'Ordinea efectuării operațiilor', photoCount: 2, exampleCount: 3 }),
      chapter(2, { title: 'Rapoarte și proporții' }),
      chapter(12, { code: 'G1', title: 'Segmente', area: 'GEOMETRY', exampleCount: 1 }),
    ])
    renderAt('/capitole')

    const algebra = await screen.findByRole('region', { name: 'Algebră' })
    const geometry = screen.getByRole('region', { name: 'Geometrie' })
    expect(within(algebra).getByRole('link', { name: /A1 Ordinea efectuării operațiilor/ })).toHaveAttribute('href', '/capitole/1')
    expect(within(algebra).getByText('2 poze · 3 modele')).toBeInTheDocument()
    // Nothing on the page yet: shown, but not a dead end.
    expect(within(algebra).queryByRole('link', { name: /Rapoarte/ })).not.toBeInTheDocument()
    expect(within(algebra).getByText('în pregătire')).toBeInTheDocument()
    expect(within(geometry).getByRole('link', { name: /G1 Segmente/ })).toHaveAttribute('href', '/capitole/12')
  })

  it('lists the chapters only: their sections are reached from inside the chapter', async () => {
    api.listEnChapters.mockResolvedValue([
      chapter(1, { code: 'AI', title: 'Mulțimea numerelor naturale', photoCount: 13, bookPage: 9 }),
      chapter(2, { code: 'AIS5', title: 'Puterea unui număr natural', parentId: 1, bookPage: 12 }),
    ])
    renderAt('/capitole')

    expect(await screen.findByRole('link', { name: /Mulțimea numerelor naturale/ })).toBeInTheDocument()
    expect(screen.queryByText(/Puterea unui număr natural/)).not.toBeInTheDocument()
  })
})

describe('EnChapterPage', () => {
  beforeEach(() => Object.values(api).forEach((fn) => fn.mockReset()))

  it('shows the theory photos, then each worked example with its solution hidden until asked for', async () => {
    api.getEnChapter.mockResolvedValue(page({
      notes: 'Întâi parantezele, apoi puterile.',
      photos: [{ id: 7 }, { id: 8 }],
      examples: [
        { id: 3, statement: 'Calculează 30 - 18 : 3', solution: 'Întâi împărțirea: 18 : 3 = 6, apoi 30 - 6 = 24.', image: null },
        { id: 4, statement: 'Lungimea segmentului AC', solution: 'AC = 12 cm', image: 'fig.png' },
      ],
    }))
    renderAt('/capitole/1')

    expect(await screen.findByRole('heading', { level: 1, name: 'A1 · Ordinea efectuării operațiilor' })).toBeInTheDocument()
    expect(screen.getByText('Întâi parantezele, apoi puterile.')).toBeInTheDocument()
    const photos = screen.getAllByRole('img', { name: /Materia, pagina/ })
    expect(photos.map((p) => p.getAttribute('src'))).toEqual(['/api/en-chapters/1/photos/7', '/api/en-chapters/1/photos/8'])
    expect(screen.getByRole('img', { name: 'Figura modelului 2' })).toHaveAttribute('src', '/api/en-chapters/1/examples/4/image?v=fig.png')

    const first = screen.getByTestId('en-example-3')
    expect(within(first).getByText('Calculează 30 - 18 : 3')).toBeInTheDocument()
    expect(within(first).queryByText(/apoi 30 - 6 = 24/)).not.toBeInTheDocument()
    fireEvent.click(within(first).getByRole('button', { name: 'Arată rezolvarea' }))
    expect(within(first).getByText(/apoi 30 - 6 = 24/)).toBeInTheDocument()
    fireEvent.click(within(first).getByRole('button', { name: 'Ascunde rezolvarea' }))
    expect(within(first).queryByText(/apoi 30 - 6 = 24/)).not.toBeInTheDocument()
  })

  it('says so when the teacher has not filled the page yet', async () => {
    api.getEnChapter.mockResolvedValue(page())
    renderAt('/capitole/1')

    expect(await screen.findByText('Profesorul n-a pus încă materia pentru acest capitol.')).toBeInTheDocument()
  })

  describe('a chapter with sections', () => {
    // pages 9..21 of the book are photos 101..113, in order
    const photos = Array.from({ length: 13 }, (_, i) => ({ id: 101 + i }))
    const chapterPage = () => page({
      id: 1, code: 'AI', title: 'Mulțimea numerelor naturale', bookPage: 9, photos,
      sections: [
        { id: 2, code: 'AIS5', title: 'Puterea unui număr natural', parentId: 1, bookPage: 12, notes: null },
        { id: 3, code: 'AIS5P5', title: 'Puterea unui număr natural: Ordinea efectuării operațiilor', parentId: 2, bookPage: 14, notes: null },
        { id: 4, code: 'AIS9', title: 'Capitol neîncărcat încă', parentId: 1, bookPage: 40, notes: null },
      ],
    })

    it('shows a table of contents whose entries open the page of the book where the section starts', async () => {
      api.getEnChapter.mockResolvedValue(chapterPage())
      renderAt('/capitole/1')

      const contents = await screen.findByRole('region', { name: 'Cuprins' })
      expect(within(contents).getByText('pag. 12')).toBeInTheDocument()
      // The point is shown under its section, without repeating the section's name.
      fireEvent.click(within(contents).getByRole('button', { name: /Ordinea efectuării operațiilor/ }))

      // book page 14 = the sixth photo of a chapter that starts on page 9
      expect(screen.getByTestId('photo-viewer-image')).toHaveAttribute('src', '/api/en-chapters/1/photos/106')
      expect(screen.getByText('Pagina 14 din carte')).toBeInTheDocument()
    })

    it('does not offer a section whose page is not uploaded', async () => {
      api.getEnChapter.mockResolvedValue(chapterPage())
      renderAt('/capitole/1')

      const contents = await screen.findByRole('region', { name: 'Cuprins' })
      expect(within(contents).queryByRole('button', { name: /Capitol neîncărcat încă/ })).not.toBeInTheDocument()
      expect(within(contents).getByText(/Capitol neîncărcat încă/)).toBeInTheDocument()
    })

    describe('with the theory written out', () => {
      const written = () => page({
        id: 1, code: 'AI', title: 'Mulțimea numerelor naturale', bookPage: 9, photos,
        sections: [
          { id: 2, code: 'AIS2', title: 'Adunarea și scăderea', parentId: 1, bookPage: 9,
            notes: '**Adunarea:** dacă $a+b=s$, atunci $s$ e suma.' },
          { id: 3, code: 'AIS2P2', title: 'Adunarea și scăderea: Scăderea', parentId: 2, bookPage: 10,
            notes: 'Scăderea **nu** e comutativă.' },
          { id: 4, code: 'AIS3', title: 'Înmulțirea', parentId: 1, bookPage: 30, notes: null },
        ],
      })

      it('shows each section as text under its own numbered heading, with the formulas typeset', async () => {
        api.getEnChapter.mockResolvedValue(written())
        const { container } = renderAt('/capitole/1')

        const materia = await screen.findByRole('region', { name: 'Materia' })
        expect(within(materia).getByRole('heading', { name: '2. Adunarea și scăderea' })).toBeInTheDocument()
        // a point is titled by its own name (not "Section: Point"), numbered as in the book
        expect(within(materia).getByRole('heading', { name: '2.2. Scăderea' })).toBeInTheDocument()
        expect(within(materia).getByText('Adunarea:').closest('strong')).not.toBeNull()
        expect(container.querySelector('.katex')).not.toBeNull()
        // a section with no text yet is not in the written part
        expect(within(materia).queryByText(/Înmulțirea/)).not.toBeInTheDocument()
      })

      it('sets a chapter heading apart: green, Times New Roman 16, with room above it', async () => {
        api.getEnChapter.mockResolvedValue(written())
        renderAt('/capitole/1')

        const heading = await screen.findByRole('heading', { name: '2. Adunarea și scăderea' })

        expect(heading.className).toContain('text-green-700')
        expect(heading.style.fontFamily).toContain('Times New Roman')
        expect(heading.style.fontSize).toBe('16pt')
        expect(heading.closest('section')?.className).toContain('pt-8') // space between chapters
      })

      it('shows a chapter heading even when only the points under it have text', async () => {
        api.getEnChapter.mockResolvedValue(page({
          id: 1, code: 'AI', bookPage: 9, photos,
          sections: [
            { id: 2, code: 'AIS2', title: 'Adunarea și scăderea', parentId: 1, bookPage: 9, notes: null },
            { id: 3, code: 'AIS2P1', title: 'Adunarea și scăderea: Adunarea', parentId: 2, bookPage: 9, notes: 'Suma.' },
          ],
        }))
        renderAt('/capitole/1')

        const materia = await screen.findByRole('region', { name: 'Materia' })
        expect(within(materia).getByRole('heading', { name: '2. Adunarea și scăderea' })).toBeInTheDocument()
        expect(within(materia).getByRole('heading', { name: '2.1. Adunarea' })).toBeInTheDocument()
      })

      it('draws a figure in its place in the text, from the photo of its page', async () => {
        api.getEnChapter.mockResolvedValue(page({
          id: 1, code: 'AI', bookPage: 9, photos,
          sections: [
            { id: 2, code: 'AIS1', title: 'Introducere', parentId: 1, bookPage: 9, notes: null },
            { id: 3, code: 'AIS1P1', title: 'Introducere: Axa numerelor', parentId: 2, bookPage: 9,
              notes: 'În figura de mai jos, lui $0$ îi corespunde $O$.\n\n![Axa numerelor](poza:101?x=0.078&y=0.396&w=0.844&h=0.061)' },
          ],
        }))
        renderAt('/capitole/1')

        const axis = await screen.findByRole('img', { name: 'Axa numerelor' })

        expect(axis.tagName).toBe('CANVAS')
        expect(screen.queryByText(/poza:101/)).not.toBeInTheDocument() // the address is never shown as text
      })

      it('keeps the photos of the pages behind a button once there is text', async () => {
        api.getEnChapter.mockResolvedValue(written())
        renderAt('/capitole/1')
        await screen.findByRole('region', { name: 'Materia' })

        expect(screen.queryByRole('img', { name: /Materia, pagina 9/ })).not.toBeInTheDocument()
        fireEvent.click(screen.getByRole('button', { name: /Arată pozele cu paginile cărții \(13\)/ }))

        expect(screen.getByRole('img', { name: 'Materia, pagina 9' })).toBeInTheDocument()
      })

      it('jumps from the table of contents to the written section, folding the contents away first', async () => {
        api.getEnChapter.mockResolvedValue(written())
        const scrolled = vi.fn()
        Element.prototype.scrollIntoView = scrolled
        renderAt('/capitole/1')

        const contents = await screen.findByRole('region', { name: 'Cuprins' })
        // once there is text the contents wait behind a button, so the text is right below it
        expect(within(contents).queryByRole('button', { name: /Scăderea/ })).not.toBeInTheDocument()
        fireEvent.click(within(contents).getByRole('button', { name: /Arată cuprinsul \(3\)/ }))
        fireEvent.click(within(contents).getByRole('button', { name: /Scăderea/ }))

        await waitFor(() => expect(scrolled).toHaveBeenCalledTimes(1))
        expect(scrolled.mock.contexts[0]).toBe(document.getElementById('sectiune-3'))
        // folded again, so the text is not a screen of entries further down
        expect(within(contents).queryByRole('button', { name: /Scăderea/ })).not.toBeInTheDocument()
      })

      it('offers the photo of the page for a section that has no text but whose page is uploaded', async () => {
        api.getEnChapter.mockResolvedValue({
          ...written(),
          sections: [...written().sections.slice(0, 2),
            { id: 4, code: 'AIS3', title: 'Înmulțirea', parentId: 1, bookPage: 11, notes: null }],
        })
        renderAt('/capitole/1')

        const contents = await screen.findByRole('region', { name: 'Cuprins' })
        fireEvent.click(within(contents).getByRole('button', { name: /Arată cuprinsul/ }))
        fireEvent.click(within(contents).getByRole('button', { name: /Înmulțirea/ }))

        expect(screen.getByTestId('photo-viewer-image')).toHaveAttribute('src', '/api/en-chapters/1/photos/103')
      })
    })

    it('opens a section straight at its page when it is reached by its own address', async () => {
      api.getEnChapter.mockImplementation(async (id: number) =>
        id === 3
          ? page({ id: 3, code: 'AIS5P5', parentId: 2, rootId: 1, bookPage: 14, title: 'Ordinea efectuării operațiilor' })
          : chapterPage())
      renderAt('/capitole/3')

      // redirected to the chapter, with the viewer already on the section's page
      expect(await screen.findByTestId('photo-viewer-image')).toHaveAttribute('src', '/api/en-chapters/1/photos/106')
      expect(api.getEnChapter).toHaveBeenCalledWith(1)
    })
  })
})
