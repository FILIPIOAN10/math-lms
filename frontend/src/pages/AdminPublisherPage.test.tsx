import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { AdminPublisherBookPage, AdminPublisherPage } from '@/pages/AdminPublisherPage'
import type { Publisher, QuizSummary } from '@/lib/api'

const api = vi.hoisted(() => ({
  getPublisher: vi.fn(),
  listPublishers: vi.fn(),
  createPublisherBook: vi.fn(),
  updatePublisherBook: vi.fn(),
  deletePublisherBook: vi.fn(),
  listQuizzes: vi.fn(),
  listClasses: vi.fn(),
  listChapterOptions: vi.fn(),
  createQuiz: vi.fn(),
}))
vi.mock('@/lib/api', async (original) => ({ ...(await original<typeof import('@/lib/api')>()), ...api }))

const artKlett: Publisher = {
  id: 1, name: 'Art Klett', description: null, hasLogo: false, logoVersion: 1, quizCount: 1,
  books: [
    { id: 10, publisherId: 1, title: 'Culegere EN 2026', description: 'Teste de antrenament', quizCount: 1 },
    { id: 11, publisherId: 1, title: 'Teste finale', description: null, quizCount: 0 },
  ],
}

const quiz = (id: number, title: string, bookId: number | null): QuizSummary => ({
  id, title, description: null, status: 'DRAFT', schoolClassId: null, schoolClassName: null, timeLimitMinutes: null,
  practiceAllowed: false, chapterId: null, maxAttempts: 1, publisherId: bookId ? 1 : null, publisherName: bookId ? 'Art Klett' : null,
  bookId, bookTitle: bookId === 10 ? 'Culegere EN 2026' : null,
})

function Where() {
  return <p data-testid="where">{useLocation().pathname}</p>
}

function renderAt(path: string) {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <Routes>
        <Route path="/admin/publishers/:publisherId" element={<AdminPublisherPage />} />
        <Route path="/admin/publishers/:publisherId/books/:bookId" element={<AdminPublisherBookPage />} />
        <Route path="*" element={<Where />} />
      </Routes>
    </MemoryRouter>,
  )
}

describe('AdminPublisherPage', () => {
  beforeEach(() => {
    Object.values(api).forEach((fn) => fn.mockReset())
    api.getPublisher.mockResolvedValue(artKlett)
    api.listPublishers.mockResolvedValue([artKlett])
    api.listQuizzes.mockResolvedValue([quiz(5, 'Testul 1', 10), quiz(6, 'Simulare oficială', null)])
    api.listClasses.mockResolvedValue([])
    api.listChapterOptions.mockResolvedValue([])
  })

  it("lists the publisher's books, each leading to its tests", async () => {
    renderAt('/admin/publishers/1')
    const books = await screen.findAllByTestId('publisher-book')
    expect(books).toHaveLength(2)
    expect(within(books[0]).getByRole('link', { name: 'Culegere EN 2026' }))
      .toHaveAttribute('href', '/admin/publishers/1/books/10')
    expect(within(books[0]).getByText('1 test')).toBeInTheDocument()
    expect(within(books[1]).getByText('niciun test')).toBeInTheDocument()
  })

  it('adds a book', async () => {
    const user = userEvent.setup()
    api.createPublisherBook.mockResolvedValue({ id: 12, publisherId: 1, title: 'Auxiliar', description: null, quizCount: 0 })
    renderAt('/admin/publishers/1')

    await user.click(await screen.findByRole('button', { name: 'Adaugă carte' }))
    await user.type(screen.getByLabelText('Titlul cărții'), ' Auxiliar ')
    await user.click(screen.getByRole('button', { name: 'Salvează' }))

    await waitFor(() => expect(api.createPublisherBook).toHaveBeenCalledWith(1, { title: 'Auxiliar', description: null }))
    expect(api.getPublisher).toHaveBeenCalledTimes(2)
  })

  it('deletes a book only on yes, saying its tests stay', async () => {
    const user = userEvent.setup()
    renderAt('/admin/publishers/1')

    const [book] = await screen.findAllByTestId('publisher-book')
    await user.click(within(book).getByRole('button', { name: 'Șterge' }))
    expect(await screen.findByText(/Testul ei rămâne la editură, fără carte/)).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Șterge cartea' }))

    await waitFor(() => expect(api.deletePublisherBook).toHaveBeenCalledWith(10))
  })
})

describe('AdminPublisherBookPage', () => {
  beforeEach(() => {
    Object.values(api).forEach((fn) => fn.mockReset())
    api.listPublishers.mockResolvedValue([artKlett])
    api.listQuizzes.mockResolvedValue([quiz(5, 'Testul 1', 10), quiz(6, 'Simulare oficială', null)])
    api.listClasses.mockResolvedValue([])
    api.listChapterOptions.mockResolvedValue([])
  })

  it('shows only the tests in this book', async () => {
    renderAt('/admin/publishers/1/books/10')
    const rows = await screen.findAllByTestId('book-quiz')
    expect(rows).toHaveLength(1)
    expect(within(rows[0]).getByRole('link', { name: 'Testul 1' })).toHaveAttribute('href', '/admin/quizzes/5')
  })

  it('adds a test already in this book and opens it', async () => {
    const user = userEvent.setup()
    api.createQuiz.mockResolvedValue(quiz(7, 'Testul 2', 10))
    renderAt('/admin/publishers/1/books/10')

    await user.click(await screen.findByRole('button', { name: 'Adaugă test' }))
    const dialog = await screen.findByRole('dialog', { name: 'Adaugă test' })
    expect(within(dialog).getByLabelText('Editură')).toHaveValue('1')
    expect(within(dialog).getByLabelText('Carte')).toHaveValue('10')
    await user.type(within(dialog).getByLabelText('Titlu'), 'Testul 2')
    await user.click(within(dialog).getByRole('button', { name: 'Salvează' }))

    await waitFor(() => expect(api.createQuiz).toHaveBeenCalledWith(expect.objectContaining({
      title: 'Testul 2', publisherId: 1, bookId: 10,
    })))
    await waitFor(() => expect(screen.getByTestId('where')).toHaveTextContent('/admin/quizzes/7'))
  })

  it('says so when the book is gone', async () => {
    renderAt('/admin/publishers/1/books/99')
    expect(await screen.findByText('Cartea nu mai există.')).toBeInTheDocument()
  })
})
