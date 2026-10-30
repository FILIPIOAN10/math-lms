import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { AdminQuizzesPage } from '@/pages/AdminQuizzesPage'
import type { QuizDetail, QuizSummary } from '@/lib/api'

const api = vi.hoisted(() => ({
  listQuizzes: vi.fn(),
  listQuizSummaries: vi.fn(),
  listClasses: vi.fn(),
  getQuiz: vi.fn(),
  setQuizPublished: vi.fn(),
  copyQuiz: vi.fn(),
  deleteQuiz: vi.fn(),
  deleteQuizItem: vi.fn(),
  listChapterOptions: vi.fn(),
  createQuiz: vi.fn(),
  getAiImportSettings: vi.fn(),
  getAiGradingSettings: vi.fn(),
}))
vi.mock('@/lib/api', async (original) => ({ ...(await original<typeof import('@/lib/api')>()), ...api }))

const summary = (id: number, title: string, status: QuizSummary['status'] = 'PUBLISHED'): QuizSummary => ({
  id, title, description: null, status, schoolClassId: null, schoolClassName: null, timeLimitMinutes: null,
  practiceAllowed: false, chapterId: null,
})
const detail = (over: Partial<QuizDetail> = {}): QuizDetail => ({
  ...summary(7, 'Simulare EN'),
  items: [{
    id: 70, position: 0, type: 'SINGLE_CHOICE', statement: 'Cât face 2+2?', points: 2, solution: null, hints: [],
    options: [{ id: 700, position: 0, text: '4', correct: true }, { id: 701, position: 1, text: '5', correct: false }],
  }],
  ...over,
} as QuizDetail)

function Where() {
  return <p data-testid="where">{useLocation().pathname}</p>
}

function renderAt(path: string) {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <Routes>
        <Route path="/admin/quizzes" element={<><AdminQuizzesPage /><Where /></>} />
        <Route path="/admin/quizzes/:quizId" element={<><AdminQuizzesPage /><Where /></>} />
        <Route path="/admin/quizzes/:quizId/stats" element={<p>statistici</p>} />
        <Route path="/admin/assignments" element={<p>teme</p>} />
      </Routes>
    </MemoryRouter>,
  )
}

const row = async (title: string) =>
  (await screen.findAllByTestId('quiz-row')).find((r) => within(r).queryByText(title) !== null)!

describe('AdminQuizzesPage', () => {
  beforeEach(() => {
    Object.values(api).forEach((fn) => fn.mockReset())
    api.listQuizzes.mockResolvedValue([summary(7, 'Simulare EN'), summary(8, 'Ciornă nouă', 'DRAFT')])
    api.listQuizSummaries.mockResolvedValue([])
    api.listClasses.mockResolvedValue([
      { id: 8, name: 'Clasa a 8-a', description: null },
      { id: 9, name: 'Clasa a 9-a', description: null },
    ])
    api.getQuiz.mockResolvedValue(detail())
    api.listChapterOptions.mockResolvedValue([
      { id: 1, title: 'Fracții', bookId: 1, bookTitle: 'Algebra', schoolClassId: 8, schoolClassName: 'Clasa a 8-a' },
      { id: 3, title: 'Funcții', bookId: 2, bookTitle: 'Analiză', schoolClassId: 9, schoolClassName: 'Clasa a 9-a' },
    ])
    api.getAiImportSettings.mockResolvedValue({ enabled: false, maxPages: 0, dailyPageLimit: 0, pagesUsedToday: 0 })
    api.getAiGradingSettings.mockResolvedValue({ enabled: false, baremMaxPages: 0, baremDailyPageLimit: 0, baremPagesUsedToday: 0 })
  })

  it('says which grile still wait for their answer, and where it comes from', async () => {
    api.getQuiz.mockResolvedValue(detail({ items: [
      { ...detail().items[0], options: [{ id: 700, position: 0, text: '4', correct: false }, { id: 701, position: 1, text: '5', correct: false }] },
    ] }))
    api.getAiGradingSettings.mockResolvedValue({ enabled: true, baremMaxPages: 5, baremDailyPageLimit: 20, baremPagesUsedToday: 0 })
    renderAt('/admin/quizzes/7')

    const banner = await screen.findByTestId('unanswered-grile')
    expect(banner).toHaveTextContent('1 grilă nu are încă răspunsul corect: 1.')
    expect(banner).toHaveTextContent('nu se poate publica')
    expect(within(banner).getByRole('button', { name: 'Barem din poze (AI)' })).toBeInTheDocument()
    expect(screen.getByTestId('no-correct-answer')).toHaveTextContent('Fără răspuns corect')
  })

  it('offers the barem from photos on a test with questions, only where the server reads it', async () => {
    renderAt('/admin/quizzes/7')
    await screen.findByText('Cât face 2+2?')
    expect(screen.queryByRole('button', { name: 'Barem din poze (AI)' })).not.toBeInTheDocument()

    api.getAiGradingSettings.mockResolvedValue({ enabled: true, baremMaxPages: 5, baremDailyPageLimit: 20, baremPagesUsedToday: 0 })
    renderAt('/admin/quizzes/7')
    await userEvent.click(await screen.findByRole('button', { name: 'Barem din poze (AI)' }))
    expect(await screen.findByRole('dialog', { name: 'Barem din poze (AI)' })).toBeInTheDocument()
  })

  it('offers a quiz from textbook photos only where the server reads them', async () => {
    renderAt('/admin/quizzes')
    await row('Simulare EN')
    expect(screen.queryByRole('link', { name: 'Din poze (AI)' })).not.toBeInTheDocument()

    api.getAiImportSettings.mockResolvedValue({ enabled: true, maxPages: 5, dailyPageLimit: 50, pagesUsedToday: 0 })
    renderAt('/admin/quizzes')
    expect(await screen.findByRole('link', { name: 'Din poze (AI)' })).toHaveAttribute('href', '/admin/quizzes/ai-import')
  })

  it('names the page and the browser tab', async () => {
    renderAt('/admin/quizzes')
    expect(await screen.findByRole('heading', { level: 1, name: 'Teste' })).toBeInTheDocument()
    expect(document.title).toBe('Teste · Math LMS')
  })

  it('shows one main action per quiz and keeps the rest in a menu', async () => {
    renderAt('/admin/quizzes')

    const published = await row('Simulare EN')
    expect(within(published).getByRole('link', { name: 'Simulare EN' })).toHaveAttribute('href', '/admin/quizzes/7')
    expect(within(published).getByRole('link', { name: 'Dă ca temă' })).toHaveAttribute('href', '/admin/assignments?quizId=7')
    expect(within(published).queryByRole('button', { name: 'Șterge' })).not.toBeInTheDocument()

    const draft = await row('Ciornă nouă')
    expect(within(draft).getByRole('button', { name: 'Publică' })).toBeInTheDocument()
    expect(within(draft).queryByRole('link', { name: 'Dă ca temă' })).not.toBeInTheDocument()
  })

  it('shows the numbers on each row and a visible link to the statistics of a published test', async () => {
    api.listQuizSummaries.mockResolvedValue([
      { quizId: 7, questions: 18, papers: 24, average: 7.42, dueAt: '2026-11-02T18:00:00Z' },
      { quizId: 8, questions: 1, papers: 0, average: null, dueAt: null },
    ])
    renderAt('/admin/quizzes')

    const published = await row('Simulare EN')
    const numbers = await within(published).findByTestId('quiz-numbers')
    expect(numbers).toHaveTextContent('18 întrebări')
    expect(numbers).toHaveTextContent('24 de lucrări')
    expect(numbers).toHaveTextContent('media 7,42')
    expect(numbers).toHaveTextContent('Termen:')
    expect(within(published).getByRole('link', { name: 'Statistici' })).toHaveAttribute('href', '/admin/quizzes/7/stats')

    const draft = await row('Ciornă nouă')
    const draftNumbers = await within(draft).findByTestId('quiz-numbers')
    expect(draftNumbers).toHaveTextContent('1 întrebare')
    expect(draftNumbers).not.toHaveTextContent('media')
    expect(draftNumbers).not.toHaveTextContent('Termen')
    expect(within(draft).queryByRole('link', { name: 'Statistici' })).not.toBeInTheDocument()
  })

  it('offers the other actions in the menu, deleting last', async () => {
    renderAt('/admin/quizzes')
    await userEvent.click(within(await row('Simulare EN')).getByRole('button', { name: 'Mai multe acțiuni pentru Simulare EN' }))

    const items = (await screen.findAllByRole('menuitem')).map((i) => i.textContent)
    expect(items).toEqual(['Editează detaliile', 'Copiază', 'Depublică', 'Șterge'])
  })

  it('asks in a dialog before deleting, and deletes only on yes', async () => {
    api.deleteQuiz.mockResolvedValue(undefined)
    renderAt('/admin/quizzes')
    const open = async () => {
      await userEvent.click(within(await row('Simulare EN')).getByRole('button', { name: 'Mai multe acțiuni pentru Simulare EN' }))
      await userEvent.click(await screen.findByRole('menuitem', { name: 'Șterge' }))
      return screen.findByRole('dialog', { name: 'Ștergi testul „Simulare EN”?' })
    }

    await userEvent.click(within(await open()).getByRole('button', { name: 'Anulează' }))
    expect(api.deleteQuiz).not.toHaveBeenCalled()

    await userEvent.click(within(await open()).getByRole('button', { name: 'Șterge' }))
    await waitFor(() => expect(api.deleteQuiz).toHaveBeenCalledWith(7))
  })

  it('opens the editor at its own address', async () => {
    renderAt('/admin/quizzes')
    await userEvent.click(within(await row('Simulare EN')).getByRole('link', { name: 'Simulare EN' }))

    expect(await screen.findByText('Cât face 2+2?')).toBeInTheDocument()
    expect(screen.getByTestId('where')).toHaveTextContent('/admin/quizzes/7')
  })

  it('loads the editor straight from the address, so refresh and Back work', async () => {
    renderAt('/admin/quizzes/7')

    expect(await screen.findByRole('heading', { level: 1, name: 'Simulare EN' })).toBeInTheDocument()
    expect(api.getQuiz).toHaveBeenCalledWith(7)
    const trail = screen.getByRole('navigation', { name: 'Unde ești' })
    expect(within(trail).getByRole('link', { name: 'Teste' })).toHaveAttribute('href', '/admin/quizzes')
  })

  it('says so when the quiz in the address does not exist', async () => {
    const { ApiError } = await import('@/lib/api')
    api.getQuiz.mockRejectedValue(new ApiError(404, 'Quiz 99 not found'))
    renderAt('/admin/quizzes/99')

    expect(await screen.findByText('Quiz 99 not found')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Înapoi la teste' })).toHaveAttribute('href', '/admin/quizzes')
  })

  it('asks in a dialog before deleting an item', async () => {
    api.deleteQuizItem.mockResolvedValue(undefined)
    renderAt('/admin/quizzes/7')
    await screen.findByText('Cât face 2+2?')

    expect(screen.queryByRole('button', { name: 'Șterge întrebarea 1' })).not.toBeInTheDocument() // not next to Editează
    await userEvent.click(screen.getByRole('button', { name: 'Mai multe acțiuni pentru întrebarea 1' }))
    await userEvent.click(await screen.findByRole('menuitem', { name: 'Șterge întrebarea' }))
    await userEvent.click(within(await screen.findByRole('dialog', { name: 'Ștergi întrebarea 1?' })).getByRole('button', { name: 'Șterge' }))

    await waitFor(() => expect(api.deleteQuizItem).toHaveBeenCalledWith(70))
  })
})

describe('AdminQuizzesPage — chapter tag', () => {
  beforeEach(() => {
    Object.values(api).forEach((fn) => fn.mockReset())
    api.listQuizzes.mockResolvedValue([])
    api.listQuizSummaries.mockResolvedValue([])
    api.listClasses.mockResolvedValue([
      { id: 8, name: 'Clasa a 8-a', description: null },
      { id: 9, name: 'Clasa a 9-a', description: null },
    ])
    api.listChapterOptions.mockResolvedValue([
      { id: 1, title: 'Fracții', bookId: 1, bookTitle: 'Algebra', schoolClassId: 8, schoolClassName: 'Clasa a 8-a' },
      { id: 3, title: 'Funcții', bookId: 2, bookTitle: 'Analiză', schoolClassId: 9, schoolClassName: 'Clasa a 9-a' },
    ])
    api.createQuiz.mockResolvedValue({ ...summary(20, 'Nou'), status: 'DRAFT' })
    api.getQuiz.mockResolvedValue(detail({ id: 20, title: 'Nou', items: [] }))
    api.getAiImportSettings.mockResolvedValue({ enabled: false, maxPages: 0, dailyPageLimit: 0, pagesUsedToday: 0 })
    api.getAiGradingSettings.mockResolvedValue({ enabled: false, baremMaxPages: 0, baremDailyPageLimit: 0, baremPagesUsedToday: 0 })
  })

  it("offers only the chosen class's chapters and sends the one picked", async () => {
    renderAt('/admin/quizzes')
    await userEvent.click(await screen.findByRole('button', { name: 'Adaugă test' }))
    const dialog = await screen.findByRole('dialog')

    await userEvent.type(within(dialog).getByLabelText('Titlu'), 'Nou')
    await userEvent.selectOptions(within(dialog).getByLabelText('Pentru clasa'), '8')
    const chapter = within(dialog).getByLabelText('Capitol (pentru harta pe capitole)')
    const offered = within(chapter).getAllByRole('option').map((o) => o.textContent)
    expect(offered).toContain('Fracții')
    expect(offered).not.toContain('Funcții')
    await userEvent.selectOptions(chapter, '1')
    await userEvent.click(within(dialog).getByRole('button', { name: 'Salvează' }))

    await waitFor(() => expect(api.createQuiz).toHaveBeenCalledWith(expect.objectContaining({ schoolClassId: 8, chapterId: 1 })))
  })
})
