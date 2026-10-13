import { fireEvent, render, screen, within } from '@testing-library/react'
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
  id, code: `A${id}`, title: `Capitolul ${id}`, area: 'ALGEBRA', position: id, photoCount: 0, exampleCount: 0, ...over,
})

const page = (over: Partial<EnChapterPageDto> = {}): EnChapterPageDto => ({
  id: 1, code: 'A1', title: 'Ordinea efectuării operațiilor', area: 'ALGEBRA', notes: null, photos: [], examples: [],
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
})
