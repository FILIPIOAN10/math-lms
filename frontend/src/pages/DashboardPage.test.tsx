import { render, screen, waitFor, within } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { DashboardPage } from '@/pages/DashboardPage'
import { ApiError } from '@/lib/api'
import type {
  AdminAttemptSummary,
  AssignmentStudentStatus,
  AssignmentSummary,
  MyAttemptDto,
  ProgressPointDto,
  StudentAssignment,
  User,
} from '@/lib/api'

const auth = vi.hoisted(() => ({ value: { user: null as User | null, loading: false } }))
vi.mock('@/context/AuthContext', () => ({ useAuth: () => auth.value }))

const api = vi.hoisted(() => ({
  getMyAssignments: vi.fn(),
  getMyAttempts: vi.fn(),
  getMyProgress: vi.fn(),
  getReviewToday: vi.fn(),
  getChildReview: vi.fn(),
  listAttemptsForGrading: vi.fn(),
  listPendingUsers: vi.fn(),
  listAssignments: vi.fn(),
  getAssignmentStatus: vi.fn(),
  listMyChildren: vi.fn(),
  listClasses: vi.fn(),
  listActiveUsers: vi.fn(),
  listQuizzes: vi.fn(),
  getChildAttempts: vi.fn(),
  getChildProgress: vi.fn(),
  getChildAssignments: vi.fn(),
}))
vi.mock('@/lib/api', async (original) => ({ ...(await original<typeof import('@/lib/api')>()), ...api }))

const DAY = 86_400_000
const inDays = (n: number) => new Date(Date.now() + n * DAY).toISOString()

const user = (over: Partial<User>): User => ({
  email: 'x@scoala.ro', fullName: 'Ana Student', role: 'STUDENT', status: 'ACTIVE', ...over,
})
const homework = (id: number, title: string, state: StudentAssignment['state']): StudentAssignment => ({
  assignmentId: id, quizId: id, quizTitle: title, schoolClassName: 'Clasa a 8-a', dueAt: inDays(1), state,
  late: false, overdue: false, attemptId: state === 'NOT_STARTED' ? null : 100 + id, timeLimitMinutes: null,
})
const attempt = (over: Partial<MyAttemptDto>): MyAttemptDto => ({
  attemptId: 1, quizId: 1, quizTitle: 'Test', status: 'GRADED', startedAt: inDays(-1), submittedAt: inDays(-1),
  score: 8, mode: 'TEST', maxScore: 11, newResult: false, ...over,
})
const point = (over: Partial<ProgressPointDto>): ProgressPointDto => ({
  attemptId: 7, quizId: 3, quizTitle: 'Simulare EN', submittedAt: inDays(-1), score: 8, maxScore: 11, percent: 73, ...over,
})

function renderHome() {
  return render(
    <MemoryRouter>
      <DashboardPage />
    </MemoryRouter>,
  )
}

beforeEach(() => {
  Object.values(api).forEach((fn) => fn.mockReset())
})

describe('DashboardPage — student', () => {
  beforeEach(() => {
    auth.value = { user: user({}), loading: false }
    api.getMyAssignments.mockResolvedValue([])
    api.getMyAttempts.mockResolvedValue([])
    api.getMyProgress.mockResolvedValue([])
    api.getReviewToday.mockResolvedValue({ limit: 10, doneToday: 0, remainingToday: 0, streak: 0, totalCards: 0, mastered: 0, questions: [] })
  })

  it('greets the student by name', async () => {
    renderHome()
    expect(screen.getByTestId('welcome')).toHaveTextContent('Bine ai venit, Ana Student')
    await screen.findByText(/Nicio temă de făcut/)
  })

  it('lists only the homework still to do, each with its next step', async () => {
    api.getMyAssignments.mockResolvedValue([
      homework(1, 'Fracții', 'IN_PROGRESS'),
      homework(2, 'Ecuații', 'NOT_STARTED'),
      homework(3, 'Predată deja', 'GRADED'),
    ])
    renderHome()

    const todo = await screen.findByRole('region', { name: 'De făcut' })
    const cards = within(todo).getAllByTestId('assignment-card')
    expect(cards).toHaveLength(2)
    expect(within(cards[0]).getByRole('link', { name: 'Continuă' })).toHaveAttribute('href', '/quizzes/1/take')
    expect(within(cards[1]).getByRole('link', { name: 'Începe' })).toHaveAttribute('href', '/quizzes/2/take')
    expect(screen.queryByText('Predată deja')).not.toBeInTheDocument()
  })

  it('says so when there is no homework left, and points to the other tests', async () => {
    api.getMyAssignments.mockResolvedValue([homework(3, 'Predată deja', 'SUBMITTED')])
    renderHome()
    expect(await screen.findByText(/Nicio temă de făcut/)).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Vezi toate testele' })).toHaveAttribute('href', '/quizzes')
  })

  it('offers to continue a started test that is not homework (homework already has its own button)', async () => {
    api.getMyAssignments.mockResolvedValue([homework(1, 'Fracții', 'IN_PROGRESS')])
    api.getMyAttempts.mockResolvedValue([
      attempt({ attemptId: 101, quizId: 1, quizTitle: 'Fracții', status: 'IN_PROGRESS', score: null }),
      attempt({ attemptId: 9, quizId: 9, quizTitle: 'Calcul rapid', status: 'IN_PROGRESS', score: null }),
      attempt({ attemptId: 10, quizId: 10, quizTitle: 'Exersare', status: 'IN_PROGRESS', mode: 'PRACTICE', score: null }),
    ])
    renderHome()

    const started = await screen.findByRole('region', { name: 'Teste începute' })
    expect(within(started).getByText('Calcul rapid')).toBeInTheDocument()
    expect(within(started).queryByText('Fracții')).not.toBeInTheDocument()
    expect(within(started).queryByText('Exersare')).not.toBeInTheDocument()
    expect(within(started).getByRole('link', { name: 'Continuă' })).toHaveAttribute('href', '/quizzes/9/take')
  })

  it('shows the latest grade out of the maximum, and how many tests wait for the teacher', async () => {
    api.getMyProgress.mockResolvedValue([
      point({ attemptId: 5, quizTitle: 'Vechi', submittedAt: inDays(-10), score: 3, maxScore: 10, percent: 30 }),
      point({}),
    ])
    api.getMyAttempts.mockResolvedValue([
      attempt({ attemptId: 20, status: 'SUBMITTED', score: null }),
      attempt({ attemptId: 21, status: 'SUBMITTED', score: null, mode: 'PRACTICE' }),
    ])
    renderHome()

    const grade = await screen.findByTestId('latest-grade')
    expect(grade).toHaveTextContent('8 / 11')
    expect(grade).toHaveTextContent('nota 7,55')
    expect(grade).toHaveTextContent('Simulare EN')
    expect(within(grade).getByRole('link', { name: 'Vezi rezultatul' })).toHaveAttribute('href', '/quizzes/attempts/7/result')
    expect(screen.getByTestId('awaiting-grading')).toHaveTextContent('1')
  })

  it('S3: puts a grade not yet opened at the top, with its result one click away', async () => {
    api.getMyAttempts.mockResolvedValue([
      attempt({ attemptId: 30, quizTitle: 'Teză', score: 8, maxScore: 11, newResult: true }),
      attempt({ attemptId: 31, quizTitle: 'Veche', newResult: false }),
    ])
    renderHome()

    expect(await screen.findByRole('heading', { name: 'Ai o notă nouă' })).toBeInTheDocument()
    const rows = screen.getAllByTestId('home-new-result')
    expect(rows).toHaveLength(1)
    expect(rows[0]).toHaveTextContent('NouTeză')
    expect(rows[0]).toHaveTextContent('8 / 11 p · nota 7,55')
    expect(within(rows[0]).getByRole('link', { name: 'Vezi rezultatul' })).toHaveAttribute('href', '/quizzes/attempts/30/result')
  })

  it('shows no "new grade" section once every result was opened', async () => {
    api.getMyAttempts.mockResolvedValue([attempt({ newResult: false })])
    renderHome()

    await screen.findByTestId('latest-grade')
    expect(screen.queryByTestId('home-new-result')).not.toBeInTheDocument()
  })

  it('shows the error instead of an empty home when the data cannot be loaded', async () => {
    api.getMyAssignments.mockRejectedValue(new ApiError(503, 'Serverul nu răspunde.'))
    renderHome()
    expect(await screen.findByText('Serverul nu răspunde.')).toBeInTheDocument()
  })
})

describe('DashboardPage — teacher', () => {
  const summary = (id: number, title: string, dueInDays: number, over: Partial<AssignmentSummary> = {}): AssignmentSummary => ({
    id, quizId: id, quizTitle: title, schoolClassId: 1, schoolClassName: 'Clasa a 9-a', dueAt: inDays(dueInDays),
    overdue: dueInDays < 0, enrolled: 20, done: 12, late: 0, inProgress: 2, notStarted: 6, ...over,
  })
  const submitted = (id: number, studentName: string): AdminAttemptSummary => ({
    attemptId: id, quizId: 1, quizTitle: 'Simulare EN', studentId: id, studentName, status: 'SUBMITTED',
    submittedAt: inDays(-1), score: null, maxScore: 11,
  })

  beforeEach(() => {
    auth.value = { user: user({ role: 'ADMIN', fullName: 'Prof Admin' }), loading: false }
    api.listAttemptsForGrading.mockResolvedValue([submitted(1, 'Ana'), submitted(2, 'Radu')])
    api.listPendingUsers.mockResolvedValue([{}])
    api.listAssignments.mockResolvedValue([summary(1, 'Fracții', 3), summary(2, 'Ecuații', -1), summary(3, 'Vechi', -40)])
    // A set-up teacher: the first-run checklist is complete, so it stays hidden.
    api.listClasses.mockResolvedValue([{ id: 1, name: 'Clasa a 9-a', description: null }])
    api.listActiveUsers.mockResolvedValue([{ id: 4 }])
    api.listQuizzes.mockResolvedValue([{ id: 1 }])
  })

  it('walks a brand-new teacher through the first steps, the next one with a button', async () => {
    api.listAttemptsForGrading.mockResolvedValue([])
    api.listPendingUsers.mockResolvedValue([])
    api.listAssignments.mockResolvedValue([])
    api.listClasses.mockResolvedValue([{ id: 1, name: 'Clasa a 9-a', description: null }])
    api.listActiveUsers.mockResolvedValue([])
    api.listQuizzes.mockResolvedValue([])
    renderHome()

    const checklist = await screen.findByRole('region', { name: 'Primii pași (1 din 4)' })
    expect(within(checklist).getByTestId('first-run-class')).toHaveAttribute('data-done', 'true')
    const next = within(checklist).getByTestId('first-run-students')
    expect(next).toHaveTextContent('Invită elevii')
    expect(within(next).getByRole('link', { name: 'Începe' })).toHaveAttribute('href', '/admin/content')
    expect(within(checklist).getAllByRole('link', { name: 'Începe' })).toHaveLength(1)
  })

  it('hides the checklist once everything is set up', async () => {
    renderHome()
    await screen.findByTestId('tile-grading')
    await waitFor(() => expect(api.listQuizzes).toHaveBeenCalled())
    expect(screen.queryByTestId('first-run')).not.toBeInTheDocument()
  })

  it('counts what is waiting, each count linking to where it is handled', async () => {
    renderHome()

    const grading = await screen.findByTestId('tile-grading')
    expect(grading).toHaveTextContent('2')
    expect(grading).toHaveAttribute('href', '/admin/grading')
    expect(screen.getByTestId('tile-pending')).toHaveTextContent('1')
    expect(screen.getByTestId('tile-pending')).toHaveAttribute('href', '/admin/students')
    expect(screen.getByTestId('tile-assignments')).toHaveTextContent('1') // only Fracții is still open
    expect(api.listAttemptsForGrading).toHaveBeenCalledWith('SUBMITTED')
  })

  it('lists the deadlines that need attention with how many students handed in', async () => {
    renderHome()

    const deadlines = await screen.findByRole('region', { name: 'Termene' })
    const rows = within(deadlines).getAllByTestId('deadline-row')
    expect(rows.map((r) => within(r).getByTestId('deadline-title').textContent)).toEqual(['Ecuații', 'Fracții'])
    expect(rows[0]).toHaveTextContent('Termen depășit')
    expect(rows[1]).toHaveTextContent('12 / 20 predate')
  })

  it('says a class has no students instead of "0 / 0 predate"', async () => {
    api.listAssignments.mockResolvedValue([summary(1, 'Logaritmi', 1, { enrolled: 0, done: 0, notStarted: 0, inProgress: 0 })])
    renderHome()
    const row = await screen.findByTestId('deadline-row')
    expect(row).toHaveTextContent('Niciun elev în clasă')
    expect(row).not.toHaveTextContent('0 / 0')
  })

  it('names the students whose work waits for grading', async () => {
    renderHome()
    const queue = await screen.findByRole('region', { name: 'De corectat' })
    expect(within(queue).getByText('Ana')).toBeInTheDocument()
    expect(within(queue).getByText('Radu')).toBeInTheDocument()
  })

  it('opens each waiting paper straight from the home page', async () => {
    renderHome()
    const queue = await screen.findByRole('region', { name: 'De corectat' })
    expect(within(queue).getByRole('link', { name: /Ana/ })).toHaveAttribute('href', '/admin/grading?attempt=1')
    expect(within(queue).getByRole('link', { name: /Radu/ })).toHaveAttribute('href', '/admin/grading?attempt=2')
  })

  it('opens who handed in from each deadline', async () => {
    renderHome()
    const deadlines = await screen.findByRole('region', { name: 'Termene' })
    expect(within(deadlines).getByRole('link', { name: /Fracții/ })).toHaveAttribute('href', '/admin/assignments?open=1')
    expect(within(deadlines).getByRole('link', { name: /Ecuații/ })).toHaveAttribute('href', '/admin/assignments?open=2')
  })

  it('names who is still missing when only a few students are left', async () => {
    const status = (studentId: number, fullName: string, state: AssignmentStudentStatus['state']): AssignmentStudentStatus => ({
      studentId, fullName, state, submittedAt: null, late: false, score: null, attemptId: null,
    })
    api.listAssignments.mockResolvedValue([
      summary(1, 'Fracții', 3, { enrolled: 6, done: 4, inProgress: 1, notStarted: 1 }),
      summary(2, 'Ecuații', 5, { enrolled: 6, done: 5, inProgress: 0, notStarted: 1 }),
    ])
    api.getAssignmentStatus.mockImplementation(async (id: number) =>
      id === 1
        ? [status(1, 'Ana', 'GRADED'), status(2, 'Radu Marin', 'IN_PROGRESS'), status(3, 'Mihai Stan', 'NOT_STARTED')]
        : [status(1, 'Ana', 'SUBMITTED'), status(4, 'Ioana', 'NOT_STARTED')],
    )
    renderHome()

    const rows = await screen.findAllByTestId('deadline-row')
    await waitFor(() => expect(rows[0]).toHaveTextContent('Lipsesc: Radu Marin, Mihai Stan'))
    expect(rows[1]).toHaveTextContent('Lipsește: Ioana')
  })

  it('does not look up the names when many students are still missing', async () => {
    renderHome() // 12 / 20 handed in on both deadlines
    await screen.findAllByTestId('deadline-row')
    await waitFor(() => expect(api.listQuizzes).toHaveBeenCalled())
    expect(api.getAssignmentStatus).not.toHaveBeenCalled()
  })

  it('still shows the deadline when the names cannot be loaded', async () => {
    api.listAssignments.mockResolvedValue([summary(1, 'Fracții', 3, { enrolled: 6, done: 5, inProgress: 1, notStarted: 0 })])
    api.getAssignmentStatus.mockRejectedValue(new Error('down'))
    renderHome()
    const row = await screen.findByTestId('deadline-row')
    await waitFor(() => expect(api.getAssignmentStatus).toHaveBeenCalled())
    expect(row).toHaveTextContent('5 / 6 predate')
    expect(row).not.toHaveTextContent('Lips')
  })

  it('suggests giving homework when no deadline is coming up', async () => {
    api.listAssignments.mockResolvedValue([])
    api.listAttemptsForGrading.mockResolvedValue([])
    renderHome()
    expect(await screen.findByText(/Nicio temă cu termen apropiat/)).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Dă o temă' })).toHaveAttribute('href', '/admin/assignments')
    expect(screen.queryByRole('region', { name: 'De corectat' })).not.toBeInTheDocument()
  })
})

describe('DashboardPage — parent', () => {
  beforeEach(() => {
    auth.value = { user: user({ role: 'PARENT', fullName: 'Maria Parinte' }), loading: false }
    api.getChildAssignments.mockResolvedValue([])
    api.getChildReview.mockResolvedValue({ doneToday: 0, remainingToday: 0, streak: 0, totalCards: 0, mastered: 0, lastReviewOn: null })
  })

  it("says whether each child did today's review, with their streak", async () => {
    api.listMyChildren.mockResolvedValue([
      { id: 4, fullName: 'Ana Student', email: 'ana@x.ro' },
      { id: 5, fullName: 'Radu Nou', email: 'radu@x.ro' },
    ])
    api.getChildProgress.mockResolvedValue([])
    api.getChildAttempts.mockResolvedValue([])
    api.getChildReview.mockImplementation(async (id: number) =>
      id === 4
        ? { doneToday: 5, remainingToday: 0, streak: 4, totalCards: 9, mastered: 2, lastReviewOn: '2026-10-11' }
        : { doneToday: 0, remainingToday: 6, streak: 0, totalCards: 6, mastered: 0, lastReviewOn: null },
    )
    renderHome()

    const cards = await screen.findAllByTestId('child-card')
    expect(within(cards[0]).getByTestId('child-review')).toHaveTextContent('Recapitulare: făcută azi ✓ · seria 4 zile')
    expect(within(cards[1]).getByTestId('child-review')).toHaveTextContent('Recapitulare: mai are 6 azi')
  })

  it("says how much homework each child still has to hand in, and what is overdue", async () => {
    api.listMyChildren.mockResolvedValue([
      { id: 4, fullName: 'Ana Student', email: 'ana@x.ro' },
      { id: 5, fullName: 'Radu Nou', email: 'radu@x.ro' },
    ])
    api.getChildProgress.mockResolvedValue([])
    api.getChildAttempts.mockResolvedValue([])
    api.getChildAssignments.mockImplementation(async (id: number) =>
      id === 4
        ? [
            { ...homework(1, 'Fracții', 'NOT_STARTED'), overdue: true },
            homework(2, 'Ecuații', 'IN_PROGRESS'),
            homework(3, 'Predată', 'GRADED'),
          ]
        : [],
    )
    renderHome()

    const cards = await screen.findAllByTestId('child-card')
    expect(within(cards[0]).getByTestId('child-homework')).toHaveTextContent('2 teme de predat')
    expect(within(cards[0]).getByTestId('child-homework')).toHaveTextContent('1 cu termenul depășit')
    expect(within(cards[1]).getByTestId('child-homework')).toHaveTextContent('Nicio temă de predat')
    expect(api.getChildAssignments).toHaveBeenCalledWith(4)
  })

  it('shows each child with their latest grade and what waits for the teacher', async () => {
    api.listMyChildren.mockResolvedValue([
      { id: 4, fullName: 'Ana Student', email: 'ana@x.ro' },
      { id: 5, fullName: 'Radu Nou', email: 'radu@x.ro' },
    ])
    api.getChildProgress.mockImplementation(async (id: number) => (id === 4 ? [point({})] : []))
    api.getChildAttempts.mockImplementation(async (id: number) =>
      id === 4 ? [attempt({ status: 'SUBMITTED', score: null })] : [],
    )
    renderHome()

    const cards = await screen.findAllByTestId('child-card')
    expect(cards).toHaveLength(2)
    expect(cards[0]).toHaveTextContent('Ana Student')
    expect(cards[0]).toHaveTextContent('8 / 11')
    expect(cards[0]).toHaveTextContent('nota 7,55')
    expect(cards[0]).toHaveTextContent('1 test în corectare')
    expect(within(cards[0]).getByRole('link', { name: 'Vezi detalii' })).toHaveAttribute('href', '/parent/children/4')
    expect(cards[1]).toHaveTextContent('Nicio notă încă')
  })

  it('explains what to do when no child is linked yet', async () => {
    api.listMyChildren.mockResolvedValue([])
    renderHome()
    expect(await screen.findByText(/Cere profesorului să te lege de copilul tău/)).toBeInTheDocument()
  })
})

describe('DashboardPage — student review', () => {
  beforeEach(() => {
    auth.value = { user: user({}), loading: false }
    api.getMyAssignments.mockResolvedValue([])
    api.getMyAttempts.mockResolvedValue([])
    api.getMyProgress.mockResolvedValue([])
  })

  it("invites the student to today's review", async () => {
    api.getReviewToday.mockResolvedValue({ limit: 10, doneToday: 0, remainingToday: 6, streak: 2, totalCards: 9, mastered: 0, questions: [] })
    renderHome()

    const card = await screen.findByTestId('home-review')
    expect(card).toHaveTextContent('6 întrebări de recapitulat azi')
    expect(card).toHaveTextContent('seria 2 zile')
    expect(within(card).getByRole('link', { name: 'Începe recapitularea' })).toHaveAttribute('href', '/review')
  })

  it('says when today is done', async () => {
    api.getReviewToday.mockResolvedValue({ limit: 10, doneToday: 4, remainingToday: 0, streak: 5, totalCards: 9, mastered: 0, questions: [] })
    renderHome()
    expect(await screen.findByTestId('home-review')).toHaveTextContent('Gata pe azi ✓')
  })

  it('shows nothing before the student has any question to review', async () => {
    api.getReviewToday.mockResolvedValue({ limit: 10, doneToday: 0, remainingToday: 0, streak: 0, totalCards: 0, mastered: 0, questions: [] })
    renderHome()
    await screen.findByText(/Nicio temă de făcut/)
    expect(screen.queryByTestId('home-review')).not.toBeInTheDocument()
  })
})
