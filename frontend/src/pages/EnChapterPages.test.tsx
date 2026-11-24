import { fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { EnChaptersPage } from '@/pages/EnChaptersPage'
import { EnChapterPage } from '@/pages/EnChapterPage'
import { type EnChapterPage as EnChapterPageDto, type EnChapterSummary } from '@/lib/api'

const api = vi.hoisted(() => ({
  listEnChapters: vi.fn(),
  getEnChapter: vi.fn(),
  getMyEnMastery: vi.fn(),
  getEnChapterLesson: vi.fn(),
  showEnChapterLesson: vi.fn(),
  markEnLessonPageReviewed: vi.fn(),
  getWrittenLesson: vi.fn(),
}))
vi.mock('@/lib/api', async (original) => ({ ...(await original<typeof import('@/lib/api')>()), ...api }))

const auth = vi.hoisted(() => ({ value: { user: { email: 'ana@x.ro', fullName: 'Ana', role: 'STUDENT', status: 'ACTIVE' } } }))
vi.mock('@/context/AuthContext', () => ({ useAuth: () => auth.value }))

const chapter = (id: number, over: Partial<EnChapterSummary> = {}): EnChapterSummary => ({
  id, code: `A${id}`, title: `Capitolul ${id}`, area: 'ALGEBRA', position: id, parentId: null, bookPage: null,
  photoCount: 0, exampleCount: 0, ...over,
})

const page = (over: Partial<EnChapterPageDto> = {}): EnChapterPageDto => ({
  id: 1, code: 'A1', title: 'Ordinea efectuării operațiilor', area: 'ALGEBRA', notes: null, parentId: null, rootId: 1,
  bookPage: null, sections: [], photos: [], examples: [], revisionTopic: null,
  ...over,
})

function renderAt(url: string) {
  return render(
    <MemoryRouter initialEntries={[url]}>
      <Routes>
        <Route path="/capitole" element={<EnChaptersPage />} />
        <Route path="/capitole/:id" element={<EnChapterPage />} />
        <Route path="/quizzes/:id/take" element={<p>Pagina testului</p>} />
      </Routes>
    </MemoryRouter>,
  )
}

describe('EnChaptersPage', () => {
  beforeEach(() => {
    Object.values(api).forEach((fn) => fn.mockReset())
    api.getMyEnMastery.mockResolvedValue([])
    auth.value.user.role = 'STUDENT'
  })

  const mastered = (chapterId: number, code: string, percent: number | null) => ({
    chapterId, code, title: `Capitolul ${chapterId}`, area: 'ALGEBRA', earned: 2, possible: 10, percent, questions: 2, ready: true,
  })

  it("marks the student's own chapters to revise, and names them first", async () => {
    api.listEnChapters.mockResolvedValue([chapter(1, { exampleCount: 1 }), chapter(2, { exampleCount: 1 }), chapter(3, { exampleCount: 1 })])
    api.getMyEnMastery.mockResolvedValue([mastered(1, 'A1', 85), mastered(2, 'A2', 30), mastered(3, 'A3', null)])
    renderAt('/capitole')

    const toRevise = await screen.findByTestId('en-to-revise')
    expect(toRevise).toHaveTextContent('De recapitulat acum:')
    expect(within(toRevise).getByRole('link', { name: 'A2 · Capitolul 2' })).toHaveAttribute('href', '/capitole/2')
    const algebra = screen.getByRole('region', { name: 'Algebră' })
    expect(within(algebra).getByTestId('en-level-2')).toHaveTextContent('De recapitulat')
    expect(within(algebra).getByTestId('en-level-1')).toHaveTextContent('Stăpânit')
    expect(within(algebra).queryByTestId('en-level-3')).not.toBeInTheDocument() // no answers there yet: no label
  })

  it('asks for nothing personal when a parent or teacher browses the chapters', async () => {
    auth.value.user.role = 'PARENT'
    api.listEnChapters.mockResolvedValue([chapter(1, { exampleCount: 1 })])
    renderAt('/capitole')

    await screen.findByRole('region', { name: 'Algebră' })
    expect(api.getMyEnMastery).not.toHaveBeenCalled()
    expect(screen.queryByTestId('en-to-revise')).not.toBeInTheDocument()
  })

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
    expect(within(algebra).getByText('2 pagini de teorie · 3 modele rezolvate')).toBeInTheDocument()
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
  beforeEach(() => {
    Object.values(api).forEach((fn) => fn.mockReset())
    api.listEnChapters.mockResolvedValue([])
    api.getEnChapterLesson.mockResolvedValue({ published: false, photoCount: 0, needsReview: 0, pages: [] })
    api.getWrittenLesson.mockResolvedValue({ exists: false })
    auth.value.user.role = 'STUDENT'
  })

  describe('the whole lesson, written out', () => {
    const lesson = (published: boolean) => ({
      published,
      photoCount: 2,
      needsReview: 0,
      pages: [
        { photoId: 8, position: 2, bookPage: 143, markdown: '## Teorema lui Pitagora\n\n$a^2 + b^2 = c^2$\n\n![Triunghiul ABC](desen:41)', needsReview: false, reviewNote: null },
        { photoId: 7, position: 1, bookPage: 144, markdown: 'A doua pagină.', needsReview: false, reviewNote: null },
      ],
    })

    it('tells the teacher how much is ready and keeps the button off until every page is written and checked', async () => {
      auth.value.user.role = 'ADMIN'
      api.getEnChapter.mockResolvedValue(page({ photos: [{ id: 7 }, { id: 8 }, { id: 9 }] }))
      api.getEnChapterLesson.mockResolvedValue({
        published: false,
        photoCount: 3,
        needsReview: 1,
        pages: [
          { photoId: 8, position: 2, bookPage: 143, markdown: 'Bună.', needsReview: false, reviewNote: null },
          { photoId: 7, position: 1, bookPage: 144, markdown: 'Cu text necitit.', needsReview: true, reviewNote: 'text necitit: [?]' },
        ],
      })
      api.markEnLessonPageReviewed.mockResolvedValue(undefined)
      renderAt('/capitole/1')

      expect(await screen.findByText('2 din 3 pagini scrise, 1 de revizuit.')).toBeInTheDocument()
      expect(screen.getByRole('button', { name: 'Arată-o elevilor' })).toBeDisabled()

      fireEvent.click(screen.getByRole('button', { name: /Citește lecția completă/ }))
      const flagged = screen.getByTestId('lesson-page-7')
      expect(within(flagged).getByText('De revizuit: text necitit: [?]')).toBeInTheDocument()
      fireEvent.click(within(flagged).getByRole('button', { name: 'Am verificat pagina' }))

      await waitFor(() => expect(api.markEnLessonPageReviewed).toHaveBeenCalledWith(1, 7))
      expect(await screen.findByText('2 din 3 pagini scrise.')).toBeInTheDocument()
      expect(within(screen.getByTestId('lesson-page-7')).queryByRole('button', { name: 'Am verificat pagina' }))
        .not.toBeInTheDocument()
    })

    it('opens the lesson at the page a link names ("Deschide lecția G4, pagina 144")', async () => {
      api.getEnChapter.mockResolvedValue(page({ notes: 'Rezumat.', photos: [{ id: 7 }, { id: 8 }] }))
      api.getEnChapterLesson.mockResolvedValue(lesson(true))
      renderAt('/capitole/1?pagina=144')

      const linked = await screen.findByTestId('lesson-page-7')
      expect(linked).toHaveAttribute('data-linked', 'true')
      expect(linked).toHaveTextContent('Pagina 144 din carte')
      expect(screen.getByTestId('lesson-page-8')).not.toHaveAttribute('data-linked')
      expect(screen.getByRole('button', { name: 'Ascunde lecția completă' })).toBeInTheDocument()
    })

    it('goes to the section as before when the page is not written out for the student', async () => {
      api.getEnChapter.mockResolvedValue(page({
        notes: 'Rezumat.', photos: [{ id: 7 }],
        sections: [{ id: 5, code: 'A1S1', title: 'Paranteze', parentId: 1, bookPage: 144, notes: 'Întâi parantezele.' }],
      }))
      api.getEnChapterLesson.mockResolvedValue({ published: false, photoCount: 0, needsReview: 0, pages: [] })
      renderAt('/capitole/1?sectiune=5&pagina=144')

      expect(await screen.findByText('Întâi parantezele.')).toBeInTheDocument()
      expect(screen.queryByRole('heading', { name: 'Lecția completă' })).not.toBeInTheDocument()
    })

    it('lets a student read the book pages as text with the drawings, and open each page of the book', async () => {
      api.getEnChapter.mockResolvedValue(page({ notes: 'Rezumat.', photos: [{ id: 7 }, { id: 8 }] }))
      api.getEnChapterLesson.mockResolvedValue(lesson(true))
      renderAt('/capitole/1')

      fireEvent.click(await screen.findByRole('button', { name: 'Citește lecția completă (2 pagini)' }))

      expect(screen.getByRole('heading', { name: 'Teorema lui Pitagora' })).toBeInTheDocument()
      expect(screen.getByRole('img', { name: 'Triunghiul ABC' })).toHaveAttribute('src', '/api/en-chapters/1/drawings/41')
      expect(screen.queryByRole('button', { name: /elevilor/ })).not.toBeInTheDocument()
      fireEvent.click(screen.getAllByRole('button', { name: 'Vezi pagina din carte' })[0])
      expect(await screen.findByText('Pagina 2 din 2')).toBeInTheDocument()  // the photo of that page (id 8, second)
    })

    it('shows nothing to a student while the teacher has not shown it', async () => {
      api.getEnChapter.mockResolvedValue(page({ notes: 'Rezumat.', photos: [{ id: 7 }] }))
      renderAt('/capitole/1')

      await screen.findByText('Rezumat.')
      expect(screen.queryByRole('heading', { name: 'Lecția completă' })).not.toBeInTheDocument()
    })

    it('lets the teacher read it first and show it to the students', async () => {
      auth.value.user.role = 'ADMIN'
      api.getEnChapter.mockResolvedValue(page({ notes: 'Rezumat.', photos: [{ id: 7 }, { id: 8 }] }))
      api.getEnChapterLesson.mockResolvedValue(lesson(false))
      api.showEnChapterLesson.mockResolvedValue(undefined)
      renderAt('/capitole/1')

      expect(await screen.findByText(/Elevii n-o văd încă/)).toBeInTheDocument()
      fireEvent.click(screen.getByRole('button', { name: 'Arată-o elevilor' }))

      await waitFor(() => expect(api.showEnChapterLesson).toHaveBeenCalledWith(1, true))
      expect(await screen.findByRole('button', { name: 'Ascunde-o elevilor' })).toBeInTheDocument()
      expect(screen.queryByText(/Elevii n-o văd încă/)).not.toBeInTheDocument()
    })
  })

  it('does not repeat the title at the top of the text, and sets its sub-headings as headings over lists', async () => {
    api.getEnChapter.mockResolvedValue(page({
      notes: 'Ordinea efectuării operațiilor\n\nParanteze\n• Întâi parantezele rotunde.\n• Apoi cele drepte.',
      examples: [{ id: 3, statement: '2 + 3', solution: '5', image: null }],
    }))
    renderAt('/capitole/1')

    expect(await screen.findByRole('heading', { level: 2, name: 'Paranteze' })).toBeInTheDocument()
    expect(screen.queryByText('Ordinea efectuării operațiilor', { exact: true })).not.toBeInTheDocument()
    expect(screen.getAllByRole('listitem').map((li) => li.textContent)).toContain('Întâi parantezele rotunde.')
  })

  it('ends with where to go next: back, the next chapter, all the chapters', async () => {
    api.getEnChapter.mockResolvedValue(page({ examples: [{ id: 3, statement: '2 + 3', solution: '5', image: null }] }))
    api.listEnChapters.mockResolvedValue([
      chapter(1, { title: 'Ordinea efectuării operațiilor', exampleCount: 1 }),
      chapter(2, { title: 'Rapoarte și proporții', exampleCount: 0 }),
      chapter(3, { title: 'Procente', exampleCount: 2 }),
    ])
    renderAt('/capitole/1')

    const next = await screen.findByRole('navigation', { name: 'Mai departe' })
    expect(await within(next).findByRole('link', { name: 'Capitolul următor: A3 · Procente →' })).toHaveAttribute('href', '/capitole/3')
    expect(within(next).getByRole('link', { name: 'Toate capitolele EN' })).toHaveAttribute('href', '/capitole')
    // the first chapter has none before it
    expect(within(next).queryByRole('link', { name: /Capitolul anterior/ })).not.toBeInTheDocument()
  })

  it('goes back to the previous chapter that has something to read', async () => {
    api.getEnChapter.mockResolvedValue(page({ id: 3, rootId: 3, code: 'A3', examples: [{ id: 3, statement: '2 + 3', solution: '5', image: null }] }))
    api.listEnChapters.mockResolvedValue([
      chapter(1, { title: 'Ordinea efectuării operațiilor', exampleCount: 1 }),
      chapter(2, { title: 'Rapoarte și proporții', exampleCount: 0 }),
      chapter(3, { title: 'Procente', exampleCount: 2 }),
    ])
    renderAt('/capitole/3')

    const nav = await screen.findByRole('navigation', { name: 'Mai departe' })
    expect(await within(nav).findByRole('link', { name: '← Capitolul anterior: A1 · Ordinea efectuării operațiilor' }))
      .toHaveAttribute('href', '/capitole/1')
  })

  describe('opened from a test', () => {
    const fromTest = '/capitole/1?test=%2Fquizzes%2F7%2Ftake%3Fmode%3Dpractice'

    beforeEach(() => {
      sessionStorage.clear()
      vi.restoreAllMocks()
      api.getEnChapter.mockResolvedValue(page({ examples: [{ id: 3, statement: '2 + 3', solution: '5', image: null }] }))
      api.listEnChapters.mockResolvedValue([chapter(1, { exampleCount: 1 }), chapter(2, { exampleCount: 1 })])
    })

    it('keeps "Înapoi la test" through the next chapter and the practice', async () => {
      renderAt(fromTest)

      expect(await screen.findByTestId('back-to-test')).toHaveTextContent('← Înapoi la test')
      const nav = await screen.findByRole('navigation', { name: 'Mai departe' })
      expect(within(nav).getByRole('button', { name: '← Înapoi la test' })).toBeInTheDocument()
      expect(await within(nav).findByRole('link', { name: /Capitolul următor: A2/ }))
        .toHaveAttribute('href', '/capitole/2?test=%2Fquizzes%2F7%2Ftake%3Fmode%3Dpractice')
      expect(within(nav).getByRole('link', { name: 'Exersează capitolul' }))
        .toHaveAttribute('href', '/capitole/1/exersare?test=%2Fquizzes%2F7%2Ftake%3Fmode%3Dpractice')
    })

    it('takes the student back to the test', async () => {
      const close = vi.spyOn(window, 'close').mockImplementation(() => undefined)
      renderAt(fromTest)

      fireEvent.click(await screen.findByTestId('back-to-test'))
      expect(await screen.findByText('Pagina testului')).toBeInTheDocument()
      expect(close).not.toHaveBeenCalled()
    })

    it('closes a tab opened only for the lesson, so the test is not opened twice', async () => {
      const close = vi.spyOn(window, 'close').mockImplementation(() => undefined)
      renderAt(`${fromTest}&fila=noua`)

      fireEvent.click(await screen.findByTestId('back-to-test'))
      expect(close).toHaveBeenCalledTimes(1)
    })

    it('ignores a way back that leaves the app', async () => {
      renderAt('/capitole/1?test=%2F%2Fevil.example')

      await screen.findByRole('navigation', { name: 'Mai departe' })
      expect(screen.queryByTestId('back-to-test')).not.toBeInTheDocument()
    })
  })

  it('offers a student to practise the chapter, as the main step after the theory', async () => {
    api.getEnChapter.mockResolvedValue(page({ examples: [{ id: 3, statement: '2 + 3', solution: '5', image: null }] }))
    api.listEnChapters.mockResolvedValue([chapter(1, { exampleCount: 1 })])
    renderAt('/capitole/1')

    const next = await screen.findByRole('navigation', { name: 'Mai departe' })
    expect(within(next).getByRole('link', { name: 'Exersează capitolul' })).toHaveAttribute('href', '/capitole/1/exersare')
  })

  it('does not offer the practice to a teacher reading the page', async () => {
    auth.value.user.role = 'TEACHER'
    api.getEnChapter.mockResolvedValue(page({ examples: [{ id: 3, statement: '2 + 3', solution: '5', image: null }] }))
    api.listEnChapters.mockResolvedValue([chapter(1, { exampleCount: 1 })])
    renderAt('/capitole/1')

    await screen.findByRole('navigation', { name: 'Mai departe' })
    expect(screen.queryByRole('link', { name: 'Exersează capitolul' })).not.toBeInTheDocument()
  })

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
    // the book's pages wait under "Vezi mai mult", even before the theory is written out
    expect(screen.queryByRole('img', { name: /Materia, pagina/ })).not.toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: 'Vezi mai mult: paginile cărții (2)' }))
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
        fireEvent.click(screen.getByRole('button', { name: /Vezi mai mult: paginile cărții \(13\)/ }))

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
