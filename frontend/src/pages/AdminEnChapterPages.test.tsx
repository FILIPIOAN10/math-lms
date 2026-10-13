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

const chapter = (id: number, over: Partial<EnChapterSummary> = {}): EnChapterSummary => ({
  id, code: `A${id}`, title: `Capitolul ${id}`, area: 'ALGEBRA', position: id, photoCount: 0, exampleCount: 0, ...over,
})

const page = (over: Partial<EnChapterPage> = {}): EnChapterPage => ({
  id: 1, code: 'A1', title: 'Ordinea efectuării operațiilor', area: 'ALGEBRA', notes: null, photos: [], examples: [],
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

    await waitFor(() => expect(api.createEnChapter).toHaveBeenCalledWith({ code: 'a12', title: 'Puteri', area: 'GEOMETRY', notes: null }))
    await waitFor(() => expect(api.listEnChapters).toHaveBeenCalledTimes(2))
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
