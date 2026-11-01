import { render, screen, within } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { AdminQuizPreviewPage } from '@/pages/AdminQuizPreviewPage'
import type { QuizDetail } from '@/lib/api'

const api = vi.hoisted(() => ({ getQuiz: vi.fn() }))
vi.mock('@/lib/api', async (original) => ({ ...(await original<typeof import('@/lib/api')>()), ...api }))

const quiz = (over: Partial<QuizDetail> = {}): QuizDetail => ({
  id: 7, title: 'Simulare EN', description: 'Model oficial', status: 'DRAFT', chapterId: null, taken: false,
  items: [
    {
      id: 70, position: 0, type: 'SINGLE_CHOICE', statement: 'Cât face 2+2?', points: 5, solution: 'Se adună.', hints: ['Numără.'],
      options: [{ id: 700, position: 0, text: 'patru', correct: true }, { id: 701, position: 1, text: 'cinci', correct: false }],
    },
    {
      id: 71, position: 1, type: 'OPEN', statement: 'Rezolvă problema.', points: 5, solution: 'Barem secret.', hints: [], options: [],
      parts: [{ id: 1, label: 'a', statement: 'Arată că x = 2.', points: 5, solution: 'x = 2 (5p)' }],
    },
  ],
  ...over,
} as QuizDetail)

function renderPage() {
  return render(
    <MemoryRouter initialEntries={['/admin/quizzes/7/preview']}>
      <Routes>
        <Route path="/admin/quizzes/:quizId/preview" element={<AdminQuizPreviewPage />} />
      </Routes>
    </MemoryRouter>,
  )
}

describe('AdminQuizPreviewPage', () => {
  beforeEach(() => {
    api.getQuiz.mockReset()
    api.getQuiz.mockResolvedValue(quiz())
  })

  it('shows the test as the student reads it, with what it adds up to', async () => {
    renderPage()

    expect(await screen.findByTestId('preview-note')).toHaveTextContent('2 întrebări, 10 p')
    expect(api.getQuiz).toHaveBeenCalledWith(7)
    expect(screen.getByText('Întrebarea 1 · 5 p')).toBeInTheDocument()
    expect(screen.getByText('Model oficial')).toBeInTheDocument()
    expect(screen.getAllByTestId('preview-item')).toHaveLength(2)
    expect(screen.getByTestId('item-parts')).toHaveTextContent('Arată că x = 2.')
  })

  it('hides everything a student must not see before handing in', async () => {
    renderPage()
    await screen.findByTestId('preview-note')

    expect(document.body).not.toHaveTextContent('Se adună.')
    expect(document.body).not.toHaveTextContent('Barem secret.')
    expect(document.body).not.toHaveTextContent('x = 2 (5p)')
    expect(document.body).not.toHaveTextContent('Numără.')
    expect(document.body).not.toHaveTextContent('✓')
  })

  it('lets nobody answer: it is only to look at', async () => {
    renderPage()
    await screen.findByTestId('preview-note')

    const radios = within(screen.getAllByTestId('preview-item')[0]).getAllByRole('radio')
    expect(radios).toHaveLength(2)
    radios.forEach((radio) => expect(radio).toBeDisabled())
  })

  it('keeps the numbers and headings of a test with sections', async () => {
    const q = quiz()
    q.items = q.items.map((item) => ({ ...item, section: 'SUBIECTUL I' }))
    api.getQuiz.mockResolvedValue(q)
    renderPage()

    expect(await screen.findByText('SUBIECTUL I')).toBeInTheDocument()
    expect(screen.getByText('1. (5 p)')).toBeInTheDocument()
  })

  it('offers to try again when the test could not load', async () => {
    api.getQuiz.mockRejectedValue(new TypeError('Failed to fetch'))
    renderPage()

    expect(await screen.findByTestId('error-state')).toBeInTheDocument()
  })
})
