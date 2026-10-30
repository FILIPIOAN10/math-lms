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

  it('offers a test never taken under "De făcut", with a plain "Începe"', async () => {
    api.getStudentQuizzes.mockResolvedValue([quiz(1, 'Fracții')])
    api.getMyAttempts.mockResolvedValue([])
    renderPage()

    const c = await card('Fracții')
    expect(screen.getByTestId('quizzes-todo')).toContainElement(c)
    expect(within(c).getByTestId('quiz-state')).toHaveTextContent('De făcut')
    expect(within(c).getByRole('link', { name: 'Începe' })).toHaveAttribute('href', '/quizzes/1/take')
    expect(within(c).queryByTestId('quiz-history')).not.toBeInTheDocument()
  })

  it('shows one card for a quiz that is homework, on offer and already attempted', async () => {
    api.getStudentQuizzes.mockResolvedValue([quiz(1, 'Fracții')])
    api.getMyAssignments.mockResolvedValue([{ ...homework(1, 'GRADED'), quizTitle: 'Fracții', attemptId: 9 }])
    api.getMyAttempts.mockResolvedValue([attempt({ attemptId: 9, quizId: 1, quizTitle: 'Fracții' })])
    renderPage()

    const cards = await screen.findAllByTestId('quiz-card')
    expect(cards).toHaveLength(1)
    expect(within(cards[0]).getByText('Clasa a 8-a')).toBeInTheDocument()
    expect(within(cards[0]).getByTestId('quiz-grade')).toHaveTextContent('nota 7,55')
  })

  it('shows a graded test with its latest grade, the result, a retake and the older attempts folded', async () => {
    api.getStudentQuizzes.mockResolvedValue([quiz(1, 'Fracții')])
    api.getMyAttempts.mockResolvedValue([ // newest first
      attempt({ attemptId: 9, quizId: 1, score: 8, maxScore: 11 }),
      attempt({ attemptId: 3, quizId: 1, score: 2, maxScore: 11 }),
    ])
    renderPage()

    const c = await card('Fracții')
    expect(screen.getByTestId('quizzes-done')).toContainElement(c)
    expect(within(c).getByTestId('quiz-state')).toHaveTextContent('Notat')
    expect(within(c).getByTestId('quiz-grade')).toHaveTextContent('nota 7,55')
    expect(within(c).getByRole('link', { name: 'Vezi rezultatul' })).toHaveAttribute('href', '/quizzes/attempts/9/result')
    expect(within(c).getByRole('link', { name: 'Dă din nou' })).toHaveAttribute('href', '/quizzes/1/take')
    const history = within(c).getByTestId('quiz-history')
    expect(history).toHaveTextContent('Toate încercările (2)')
    expect(within(history).getAllByRole('link', { name: 'Deschide' })[1]).toHaveAttribute('href', '/quizzes/attempts/3/result')
  })

  it('says a handed-in test waits for the teacher', async () => {
    api.getStudentQuizzes.mockResolvedValue([quiz(1, 'Fracții')])
    api.getMyAttempts.mockResolvedValue([attempt({ attemptId: 9, quizId: 1, status: 'SUBMITTED', score: null })])
    renderPage()

    const c = await card('Fracții')
    expect(within(c).getByTestId('quiz-state')).toHaveTextContent('În corectare')
    expect(within(c).queryByTestId('quiz-grade')).not.toBeInTheDocument()
  })

  it('offers "Continuă" for a test in progress; a practice session changes only its own button', async () => {
    api.getStudentQuizzes.mockResolvedValue([quiz(1, 'Fracții', { practiceAllowed: true })])
    api.getMyAttempts.mockResolvedValue([
      attempt({ attemptId: 10, quizId: 1, status: 'IN_PROGRESS', score: null, submittedAt: null }),
      attempt({ attemptId: 8, quizId: 1, mode: 'PRACTICE', status: 'IN_PROGRESS', score: null, submittedAt: null }),
    ])
    renderPage()

    const c = await card('Fracții')
    expect(within(c).getByTestId('quiz-state')).toHaveTextContent('În lucru')
    expect(within(c).getByRole('link', { name: 'Continuă' })).toHaveAttribute('href', '/quizzes/1/take')
    expect(within(c).getByRole('link', { name: 'Continuă practica' })).toHaveAttribute('href', '/quizzes/1/take?mode=practice')
  })

  it('shows the deadline of homework still to do and flags it when overdue', async () => {
    api.getStudentQuizzes.mockResolvedValue([quiz(1, 'Fracții')])
    api.getMyAttempts.mockResolvedValue([])
    api.getMyAssignments.mockResolvedValue([{ ...homework(1, 'NOT_STARTED'), overdue: true }])
    renderPage()

    const c = await card('Fracții')
    expect(within(c).getByTestId('quiz-state')).toHaveTextContent('Termen depășit')
    expect(c).toHaveTextContent('Termen:')
    expect(within(c).getByRole('link', { name: 'Începe' })).toBeInTheDocument()
  })

  it('keeps the history of a test no longer on offer, without a retake', async () => {
    api.getStudentQuizzes.mockResolvedValue([])
    api.getMyAttempts.mockResolvedValue([attempt({ attemptId: 4, quizId: 7, quizTitle: 'Retras' })])
    renderPage()

    const c = await card('Retras')
    expect(within(c).getByRole('link', { name: 'Vezi rezultatul' })).toBeInTheDocument()
    expect(within(c).queryByRole('link', { name: 'Dă din nou' })).not.toBeInTheDocument()
  })

  it('S3: marks a grade not yet opened as "Nou", shows it first and opens that attempt', async () => {
    api.getStudentQuizzes.mockResolvedValue([])
    api.getMyAttempts.mockResolvedValue([
      attempt({ attemptId: 2, quizId: 2, quizTitle: 'Văzută', newResult: false }),
      attempt({ attemptId: 1, quizId: 1, quizTitle: 'Nouă', newResult: true }),
    ])
    renderPage()

    expect(await screen.findAllByTestId('new-result')).toHaveLength(1)
    expect((await screen.findAllByTestId('quiz-card'))[0]).toHaveTextContent('Nouă')
    expect(screen.getAllByRole('link', { name: 'Vezi rezultatul' })[0]).toHaveAttribute('href', '/quizzes/attempts/1/result')
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
