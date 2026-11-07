import { render, screen, waitFor, within } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { StudentQuizListPage } from '@/pages/StudentQuizListPage'
import type { MyAttemptDto, OfferedQuiz, StudentAssignment } from '@/lib/api'

const api = vi.hoisted(() => ({
  getStudentQuizzes: vi.fn(),
  getMyAttempts: vi.fn(),
  getMyAssignments: vi.fn(),
}))
vi.mock('@/lib/api', async (original) => ({ ...(await original<typeof import('@/lib/api')>()), ...api }))

const quiz = (id: number, title: string, over: Partial<OfferedQuiz> = {}): OfferedQuiz => ({
  id, title, description: null, status: 'PUBLISHED', schoolClassId: null, schoolClassName: null,
  timeLimitMinutes: null, practiceAllowed: false, chapterId: null, maxAttempts: null, publisherId: null, publisherName: null, bookId: null, bookTitle: null, attemptsUsed: 0, blockedBy: null, ...over,
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

  it('says a class only to a student who has more than one', async () => {
    api.getStudentQuizzes.mockResolvedValue([quiz(1, 'Fracții')])
    api.getMyAttempts.mockResolvedValue([])
    api.getMyAssignments.mockResolvedValue([homework(1, 'NOT_STARTED')])
    const { unmount } = renderPage()
    expect(await card('Fracții')).not.toHaveTextContent('Clasa a 8-a')
    unmount()

    api.getMyAssignments.mockResolvedValue([homework(1, 'NOT_STARTED'), { ...homework(2, 'NOT_STARTED'), schoolClassName: 'Mate avansați' }])
    renderPage()
    expect(await card('Fracții')).toHaveTextContent('Clasa a 8-a')
  })

  it('keeps a graded card to its title, one line and the grade', async () => {
    api.getStudentQuizzes.mockResolvedValue([quiz(1, 'Fracții', { description: 'Adunare de fracții.' })])
    api.getMyAttempts.mockResolvedValue([attempt({ attemptId: 9, quizId: 1, score: 8, maxScore: 11 })])
    renderPage()

    const c = await card('Fracții')
    expect(c).not.toHaveTextContent('Adunare de fracții.')
    expect(c).not.toHaveTextContent('8 / 11 p')
    expect(c).not.toHaveTextContent(/\d\d:\d\d/) // the day, without the hour
    expect(c).toHaveTextContent('2026')
  })

  it('still says what a test is about while it is to do', async () => {
    api.getStudentQuizzes.mockResolvedValue([quiz(1, 'Fracții', { description: 'Adunare de fracții.' })])
    api.getMyAttempts.mockResolvedValue([])
    renderPage()

    expect(await card('Fracții')).toHaveTextContent('Adunare de fracții.')
  })

  it('shows a retake of homework already handed in as a test in progress, nothing else', async () => {
    api.getStudentQuizzes.mockResolvedValue([quiz(1, 'Fracții')])
    api.getMyAssignments.mockResolvedValue([{ ...homework(1, 'GRADED'), overdue: false, attemptId: 9,
      dueAt: new Date(Date.now() - 6 * 86_400_000).toISOString() }])
    api.getMyAttempts.mockResolvedValue([
      attempt({ attemptId: 10, quizId: 1, status: 'IN_PROGRESS', score: null, submittedAt: null }),
      attempt({ attemptId: 9, quizId: 1 }),
    ])
    renderPage()

    const c = await card('Fracții')
    expect(within(c).getByTestId('quiz-state')).toHaveTextContent('În lucru')
    expect(c).not.toHaveTextContent('Predată')
    expect(c).not.toHaveTextContent('depășit')
    expect(within(c).getByRole('link', { name: 'Continuă' })).toBeInTheDocument()
  })

  it('offers to try again when the list could not load', async () => {
    api.getStudentQuizzes.mockRejectedValue(new TypeError('Failed to fetch'))
    api.getMyAttempts.mockResolvedValue([])
    renderPage()

    expect(await screen.findByTestId('error-state')).toHaveTextContent('Nu ne putem conecta la server')
    expect(screen.getByRole('button', { name: 'Reîncearcă' })).toBeInTheDocument()
  })

  it('lets the actions of a card wrap on a phone, instead of overflowing past its edge', async () => {
    api.getStudentQuizzes.mockResolvedValue([quiz(1, 'Fracții', { practiceAllowed: true })])
    api.getMyAttempts.mockResolvedValue([attempt({ attemptId: 9, quizId: 1 })])
    renderPage()

    const actions = within(await card('Fracții')).getByTestId('quiz-actions')
    expect(within(actions).getByRole('link', { name: 'Vezi rezultatul' })).toBeInTheDocument()
    expect(actions).not.toHaveClass('shrink-0') // a row that cannot shrink never reaches its wrap point
    expect(actions).toHaveClass('flex-wrap', 'max-w-full')
  })

  it('says a handed-in test waits for the teacher, and offers no retake until it has its grade', async () => {
    api.getStudentQuizzes.mockResolvedValue([quiz(1, 'Fracții', { blockedBy: 'WAITING_FOR_GRADE' })])
    api.getMyAttempts.mockResolvedValue([attempt({ attemptId: 9, quizId: 1, status: 'SUBMITTED', score: null })])
    renderPage()

    const c = await card('Fracții')
    expect(within(c).getByTestId('quiz-state')).toHaveTextContent('În corectare')
    expect(within(c).queryByTestId('quiz-grade')).not.toBeInTheDocument()
    expect(within(c).queryByRole('link', { name: 'Dă din nou' })).not.toBeInTheDocument()
    expect(within(c).getByRole('link', { name: 'Vezi rezultatul' })).toBeInTheDocument()
  })

  it('offers no retake once the attempts are used up', async () => {
    api.getStudentQuizzes.mockResolvedValue([quiz(1, 'Fracții', { maxAttempts: 1, attemptsUsed: 1, blockedBy: 'NO_ATTEMPTS_LEFT' })])
    api.getMyAttempts.mockResolvedValue([attempt({ attemptId: 9, quizId: 1 })])
    renderPage()

    const c = await card('Fracții')
    expect(within(c).queryByRole('link', { name: 'Dă din nou' })).not.toBeInTheDocument()
    expect(within(c).queryByTestId('quiz-attempts')).not.toBeInTheDocument() // one attempt: nothing to count
  })

  it('counts the attempts of a test with a limit above one', async () => {
    api.getStudentQuizzes.mockResolvedValue([quiz(1, 'Fracții', { maxAttempts: 3, attemptsUsed: 1 })])
    api.getMyAttempts.mockResolvedValue([attempt({ attemptId: 9, quizId: 1 })])
    renderPage()

    const c = await card('Fracții')
    expect(within(c).getByTestId('quiz-attempts')).toHaveTextContent('1 din 3 încercări')
    expect(within(c).getByRole('link', { name: 'Dă din nou' })).toBeInTheDocument()
  })

  it('shows the highest grade, not the latest, and says that is the one that counts', async () => {
    api.getStudentQuizzes.mockResolvedValue([quiz(1, 'Fracții')])
    api.getMyAttempts.mockResolvedValue([ // newest first
      attempt({ attemptId: 9, quizId: 1, score: 2, maxScore: 11 }),
      attempt({ attemptId: 3, quizId: 1, score: 8, maxScore: 11 }),
    ])
    renderPage()

    const c = await card('Fracții')
    expect(within(c).getByTestId('quiz-grade')).toHaveTextContent('nota 7,55')
    expect(within(c).getByTestId('quiz-grade')).toHaveTextContent('cea mai mare din 2')
  })

  it('says nothing about "the highest" with a single grade', async () => {
    api.getStudentQuizzes.mockResolvedValue([quiz(1, 'Fracții')])
    api.getMyAttempts.mockResolvedValue([attempt({ attemptId: 9, quizId: 1 })])
    renderPage()

    expect(within(await card('Fracții')).getByTestId('quiz-grade')).not.toHaveTextContent('cea mai mare')
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
    expect(within(c).getByRole('link', { name: 'Continuă exersarea' })).toHaveAttribute('href', '/quizzes/1/take?mode=practice')
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

  // 95 minutes left on the server's clock when the list was made
  const running = { status: 'IN_PROGRESS' as const, score: null, submittedAt: null, deadlineAt: '2026-11-06T13:45:00Z', serverNow: '2026-11-06T12:10:00Z' }

  it('counts down a timed test the student left, instead of its full time and the homework deadline', async () => {
    api.getStudentQuizzes.mockResolvedValue([quiz(1, 'Simulare EN', { timeLimitMinutes: 120 })])
    api.getMyAssignments.mockResolvedValue([
      { ...homework(1, 'IN_PROGRESS'), quizTitle: 'Simulare EN', attemptId: 10, timeLimitMinutes: 120 },
    ])
    api.getMyAttempts.mockResolvedValue([attempt({ attemptId: 10, quizId: 1, quizTitle: 'Simulare EN', ...running })])
    renderPage()

    const c = await card('Simulare EN')
    expect(within(c).getByTestId('quiz-time-left')).toHaveTextContent('Mai ai 1 h 35 min')
    expect(within(c).queryByTestId('quiz-time-limit')).not.toBeInTheDocument()
    expect(c).not.toHaveTextContent('Termen:') // the paper is handed in when its clock runs out, days before that
    expect(within(c).getByRole('link', { name: 'Continuă' })).toHaveAttribute('href', '/quizzes/1/take')
  })

  it('says a timed test whose time ran out hands itself in, and offers no "Continuă"', async () => {
    api.getStudentQuizzes.mockResolvedValue([quiz(1, 'Simulare EN', { timeLimitMinutes: 120 })])
    api.getMyAttempts.mockResolvedValue([
      attempt({ attemptId: 10, quizId: 1, quizTitle: 'Simulare EN', ...running, deadlineAt: '2026-11-06T12:09:00Z' }),
    ])
    renderPage()

    const c = await card('Simulare EN')
    expect(within(c).getByTestId('quiz-time-left')).toHaveTextContent('Timpul a expirat: lucrarea se predă singură')
    expect(within(c).queryByRole('link', { name: 'Continuă' })).not.toBeInTheDocument()
  })

  it('still shows the full time of a timed test not started yet', async () => {
    api.getStudentQuizzes.mockResolvedValue([quiz(1, 'Simulare EN', { timeLimitMinutes: 120 })])
    api.getMyAttempts.mockResolvedValue([])
    renderPage()

    expect(within(await card('Simulare EN')).getByTestId('quiz-time-limit')).toHaveTextContent('⏱ 120 de minute')
  })

  it('after "Salvează și ieși" from a timed test, says the clock keeps running and how long is left, not "oricând"', async () => {
    api.getStudentQuizzes.mockResolvedValue([quiz(1, 'Simulare EN', { timeLimitMinutes: 120 })])
    api.getMyAttempts.mockResolvedValue([attempt({ attemptId: 10, quizId: 1, quizTitle: 'Simulare EN', ...running })])
    render(
      <MemoryRouter
        initialEntries={[{ pathname: '/quizzes', state: { savedQuizTitle: 'Simulare EN', savedAttemptId: 10, savedTimed: true } }]}
      >
        <StudentQuizListPage />
      </MemoryRouter>,
    )

    const notice = await screen.findByTestId('saved-notice')
    await waitFor(() => expect(notice).toHaveTextContent('Cronometrul merge în continuare: mai ai 1 h 35 min.'))
    expect(notice).toHaveTextContent('Revino cu „Continuă” înainte să expire timpul; atunci lucrarea se predă singură')
    expect(notice).not.toHaveTextContent('oricând')
  })
})
