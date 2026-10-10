import { render, screen, within } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { DashboardPage } from '@/pages/DashboardPage'
import { ApiError } from '@/lib/api'
import type {
  AdminAttemptSummary,
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
  listAttemptsForGrading: vi.fn(),
  listPendingUsers: vi.fn(),
  listAssignments: vi.fn(),
  listMyChildren: vi.fn(),
  getChildAttempts: vi.fn(),
  getChildProgress: vi.fn(),
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
  score: 8, mode: 'TEST', ...over,
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
    expect(grade).toHaveTextContent('Simulare EN')
    expect(within(grade).getByRole('link', { name: 'Vezi rezultatul' })).toHaveAttribute('href', '/quizzes/attempts/7/result')
    expect(screen.getByTestId('awaiting-grading')).toHaveTextContent('1')
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
    submittedAt: inDays(-1), score: null,
  })

  beforeEach(() => {
    auth.value = { user: user({ role: 'ADMIN', fullName: 'Prof Admin' }), loading: false }
    api.listAttemptsForGrading.mockResolvedValue([submitted(1, 'Ana'), submitted(2, 'Radu')])
    api.listPendingUsers.mockResolvedValue([{}])
    api.listAssignments.mockResolvedValue([summary(1, 'Fracții', 3), summary(2, 'Ecuații', -1), summary(3, 'Vechi', -40)])
  })

  it('counts what is waiting, each count linking to where it is handled', async () => {
    renderHome()

    const grading = await screen.findByTestId('tile-grading')
    expect(grading).toHaveTextContent('2')
    expect(grading).toHaveAttribute('href', '/admin/grading')
    expect(screen.getByTestId('tile-pending')).toHaveTextContent('1')
    expect(screen.getByTestId('tile-pending')).toHaveAttribute('href', '/admin/pending')
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
