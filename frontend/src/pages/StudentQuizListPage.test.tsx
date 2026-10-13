import { render, screen, within } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { StudentQuizListPage } from '@/pages/StudentQuizListPage'
import type { MyAttemptDto, QuizSummary, StudentAssignment } from '@/lib/api'

const api = vi.hoisted(() => ({
  getStudentQuizzes: vi.fn(),
  getMyAttempts: vi.fn(),
  getMyAssignments: vi.fn(),
}))
vi.mock('@/lib/api', async (original) => ({ ...(await original<typeof import('@/lib/api')>()), ...api }))

const quiz = (id: number, title: string, over: Partial<QuizSummary> = {}): QuizSummary => ({
  id, title, description: null, status: 'PUBLISHED', schoolClassId: null, schoolClassName: null,
  timeLimitMinutes: null, practiceAllowed: false, chapterId: null, ...over,
})
const attempt = (over: Partial<MyAttemptDto>): MyAttemptDto => ({
  attemptId: 1, quizId: 1, quizTitle: 'Q', status: 'GRADED', startedAt: '2026-10-01T10:00:00Z',
  submittedAt: '2026-10-01T10:30:00Z', score: 8, mode: 'TEST', maxScore: 11, newResult: false, ...over,
})
const homework = (quizId: number, state: StudentAssignment['state']): StudentAssignment => ({
  assignmentId: quizId, quizId, quizTitle: `Tema ${quizId}`, schoolClassName: 'Clasa a 8-a',
  dueAt: new Date(Date.now() + 86_400_000).toISOString(), state, late: false, overdue: false,
  attemptId: null, timeLimitMinutes: null,
})

function renderPage() {
  return render(
    <MemoryRouter>
      <StudentQuizListPage />
    </MemoryRouter>,
  )
}

const card = async (title: string) =>
  (await screen.findAllByTestId('quiz-card')).find((c) => within(c).queryByText(title) !== null)!

describe('StudentQuizListPage', () => {
  beforeEach(() => {
    Object.values(api).forEach((fn) => fn.mockReset())
    api.getMyAssignments.mockResolvedValue([])
  })

  it('offers a test never taken with a plain "Începe"', async () => {
    api.getStudentQuizzes.mockResolvedValue([quiz(1, 'Fracții')])
    api.getMyAttempts.mockResolvedValue([])
    renderPage()

    const c = await card('Fracții')
    expect(within(c).getByRole('link', { name: 'Începe' })).toHaveAttribute('href', '/quizzes/1/take')
    expect(within(c).queryByTestId('quiz-done')).not.toBeInTheDocument()
  })

  it('shows a test already taken as done, with its latest grade, the result first and a retake second', async () => {
    api.getStudentQuizzes.mockResolvedValue([quiz(1, 'Fracții')])
    api.getMyAttempts.mockResolvedValue([ // newest first
      attempt({ attemptId: 9, quizId: 1, score: 8, maxScore: 11 }),
      attempt({ attemptId: 3, quizId: 1, score: 2, maxScore: 11 }),
    ])
    renderPage()

    const c = await card('Fracții')
    expect(within(c).getByTestId('quiz-done')).toHaveTextContent('Dat · 8 / 11 p · nota 7,55')
    expect(within(c).getByRole('link', { name: 'Vezi rezultatul' })).toHaveAttribute('href', '/quizzes/attempts/9/result')
    expect(within(c).getByRole('link', { name: 'Dă din nou' })).toHaveAttribute('href', '/quizzes/1/take')
  })

  it('says a handed-in test waits for the teacher', async () => {
    api.getStudentQuizzes.mockResolvedValue([quiz(1, 'Fracții')])
    api.getMyAttempts.mockResolvedValue([attempt({ attemptId: 9, quizId: 1, status: 'SUBMITTED', score: null })])
    renderPage()

    expect(within(await card('Fracții')).getByTestId('quiz-done')).toHaveTextContent('Predat — în corectare')
  })

  it('offers "Continuă" for a test in progress, ignoring practice sessions for the done state', async () => {
    api.getStudentQuizzes.mockResolvedValue([quiz(1, 'Fracții', { practiceAllowed: true })])
    api.getMyAttempts.mockResolvedValue([
      attempt({ attemptId: 10, quizId: 1, status: 'IN_PROGRESS', score: null, submittedAt: null }),
      attempt({ attemptId: 8, quizId: 1, mode: 'PRACTICE', score: null }),
    ])
    renderPage()

    const c = await card('Fracții')
    expect(within(c).getByRole('link', { name: 'Continuă' })).toHaveAttribute('href', '/quizzes/1/take')
    expect(within(c).queryByTestId('quiz-done')).not.toBeInTheDocument()
  })

  it('lists homework still to do only under "Teme", not again under the tests', async () => {
    api.getStudentQuizzes.mockResolvedValue([quiz(1, 'Fracții'), quiz(2, 'Ecuații')])
    api.getMyAttempts.mockResolvedValue([])
    api.getMyAssignments.mockResolvedValue([homework(1, 'NOT_STARTED')])
    renderPage()

    const cards = await screen.findAllByTestId('quiz-card')
    expect(cards.map((c) => c.textContent)).toEqual([expect.stringContaining('Ecuații')])
  })

  it('shows "x / max p · nota" in the attempt history', async () => {
    api.getStudentQuizzes.mockResolvedValue([])
    api.getMyAttempts.mockResolvedValue([attempt({ quizTitle: 'Fracții', score: 8, maxScore: 11 })])
    renderPage()

    expect(await screen.findByText('8 / 11 p · nota 7,55')).toBeInTheDocument()
  })

  it('S3: marks a grade not yet opened as "Nou" and makes its result the main button', async () => {
    api.getStudentQuizzes.mockResolvedValue([])
    api.getMyAttempts.mockResolvedValue([
      attempt({ attemptId: 1, quizTitle: 'Nouă', newResult: true }),
      attempt({ attemptId: 2, quizTitle: 'Văzută', newResult: false }),
    ])
    renderPage()

    expect(await screen.findAllByTestId('new-result')).toHaveLength(1)
    const links = screen.getAllByRole('link', { name: 'Vezi rezultatul' })
    expect(links[0]).toHaveAttribute('href', '/quizzes/attempts/1/result')
    expect(links[0]).not.toHaveClass('border-border') // the filled button, not the outline one
  })

  it('after "Salvează și ieși", says the work is saved and where to continue it', async () => {
    api.getStudentQuizzes.mockResolvedValue([])
    api.getMyAttempts.mockResolvedValue([])
    render(
      <MemoryRouter initialEntries={[{ pathname: '/quizzes', state: { savedQuizTitle: 'Simulare EN' } }]}>
        <StudentQuizListPage />
      </MemoryRouter>,
    )

    expect(await screen.findByTestId('saved-notice')).toHaveTextContent(
      'Am salvat ce ai lucrat la „Simulare EN”. Continui oricând cu „Continuă”.',
    )
  })
})
