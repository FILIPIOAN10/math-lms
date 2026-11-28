import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { type StudyPlan } from '@/lib/api'
import { StudyPlanPage } from './StudyPlanPage'

const api = vi.hoisted(() => ({ getMyStudyPlan: vi.fn(), setMyEnGoal: vi.fn() }))
vi.mock('@/lib/api', async (original) => ({ ...(await original<typeof import('@/lib/api')>()), ...api }))

const plan = (over: Partial<StudyPlan> = {}): StudyPlan => ({
  examDate: '2027-03-02',
  daysLeft: 94,
  target: 9,
  grade: 7.4,
  low: 7.1,
  high: 7.7,
  answers: 60,
  answersNeeded: 20,
  mode: 'CATCH_UP',
  behind: false,
  expectedToday: 7.6,
  weeks: [
    {
      number: 1, start: '2026-11-28', end: '2026-12-04', finalWeek: false, items: [
        { type: 'LESSON', chapterId: 3, code: 'G5', title: 'Arii' },
        { type: 'PRACTICE', chapterId: 3, code: 'G5', title: 'Arii' },
        { type: 'MINI_TEST', chapterId: 3, code: 'G5', title: 'Arii' },
        { type: 'PRACTICE', chapterId: 5, code: 'A3', title: 'Procente' },
        { type: 'MINI_TEST', chapterId: 5, code: 'A3', title: 'Procente' },
      ],
    },
    {
      number: 2, start: '2026-12-05', end: '2026-12-11', finalWeek: false, items: [
        { type: 'REVIEW', chapterId: null, code: null, title: null },
      ],
    },
    {
      number: 3, start: '2026-12-12', end: '2026-12-18', finalWeek: true, items: [
        { type: 'MOCK', chapterId: null, code: null, title: null },
        { type: 'REVIEW', chapterId: null, code: null, title: null },
      ],
    },
  ],
  history: [
    { day: '2026-11-20', grade: 7.1, low: 6.6, high: 7.6 },
    { day: '2026-11-28', grade: 7.4, low: 7.1, high: 7.7 },
  ],
  pins: [{ chapterId: 5, code: 'A3', title: 'Procente' }],
  practiceQuestions: 10,
  ...over,
})

function renderPage() {
  return render(<MemoryRouter><StudyPlanPage /></MemoryRouter>)
}

describe('StudyPlanPage', () => {
  beforeEach(() => {
    api.getMyStudyPlan.mockReset()
    api.setMyEnGoal.mockReset()
  })

  it('shows where the student stands, what to do today and the weeks up to the exam', async () => {
    api.getMyStudyPlan.mockResolvedValue(plan())

    renderPage()

    expect(await screen.findByTestId('study-plan-goal-line')).toHaveTextContent('Acum ~7,40 → ținta 9,00 · mai ai 94 de zile')
    const today = screen.getByTestId('study-plan-today')
    expect(within(today).getByRole('link', { name: 'Recapitularea zilnică' })).toHaveAttribute('href', '/review')
    expect(within(today).getByRole('link', { name: 'Exersează G5 · Arii' })).toHaveAttribute('href', '/capitole/3/exersare')

    const weeks = screen.getAllByTestId('study-plan-week')
    expect(weeks).toHaveLength(3)
    expect(weeks[0]).toHaveTextContent('Următoarele 7 zile')
    expect(within(weeks[0]).getByRole('link', { name: 'Citește lecția' })).toHaveAttribute('href', '/capitole/3')
    expect(within(weeks[0]).getAllByRole('link', { name: 'Exersează 10 întrebări' })[0]).toHaveAttribute('href', '/capitole/3/exersare')
    // A chapter the teacher pinned says so.
    expect(within(weeks[0]).getByText('pus de profesor')).toBeInTheDocument()
    expect(weeks[2]).toHaveTextContent('Simulări')
    expect(within(weeks[2]).getByRole('link', { name: 'Rezolvă un test complet de EN' })).toHaveAttribute('href', '/quizzes')

    expect(screen.getByTestId('en-goal-chart')).toBeInTheDocument()
    expect(screen.getAllByTestId('en-goal-chart-point')).toHaveLength(2)
  })

  it('marks the current target and changes it with one tap', async () => {
    api.getMyStudyPlan.mockResolvedValue(plan())
    api.setMyEnGoal.mockResolvedValue(plan({ target: 9.5 }))

    renderPage()

    expect(await screen.findByRole('button', { name: '9,00' })).toHaveAttribute('aria-pressed', 'true')
    await userEvent.click(screen.getByRole('button', { name: '9,50' }))

    expect(api.setMyEnGoal).toHaveBeenCalledWith(9.5)
    expect(await screen.findByTestId('study-plan-goal-line')).toHaveTextContent('ținta 9,50')
    expect(screen.getByRole('button', { name: '9,50' })).toHaveAttribute('aria-pressed', 'true')
  })

  it('takes a target typed by hand and refuses one off the scale', async () => {
    api.getMyStudyPlan.mockResolvedValue(plan({ target: null, mode: 'NO_TARGET', weeks: [] }))
    api.setMyEnGoal.mockResolvedValue(plan({ target: 8.75 }))

    renderPage()

    expect(await screen.findByText(/Alege nota pe care o vrei/)).toBeInTheDocument()
    const input = screen.getByLabelText('Altă notă')
    await userEvent.type(input, '10,5')
    await userEvent.click(screen.getByRole('button', { name: 'Salvează ținta' }))
    expect(screen.getByRole('alert')).toHaveTextContent('o notă de la 1 la 10')
    expect(api.setMyEnGoal).not.toHaveBeenCalled()

    await userEvent.clear(input)
    await userEvent.type(input, '8,75')
    await userEvent.click(screen.getByRole('button', { name: 'Salvează ținta' }))
    expect(api.setMyEnGoal).toHaveBeenCalledWith(8.75)
  })

  it('tells a student who is falling behind, gently', async () => {
    api.getMyStudyPlan.mockResolvedValue(plan({ behind: true, expectedToday: 8.1 }))

    renderPage()

    expect(await screen.findByTestId('study-plan-behind')).toHaveTextContent('~8,10')
  })

  it('says why there is no plan yet when the exam date is missing', async () => {
    api.getMyStudyPlan.mockResolvedValue(plan({ mode: 'NO_EXAM_DATE', examDate: null, daysLeft: null, weeks: [] }))

    renderPage()

    expect(await screen.findByText(/nu a stabilit încă data EN/)).toBeInTheDocument()
    expect(screen.queryByTestId('study-plan-today')).not.toBeInTheDocument()
  })

  it('shows the error when the plan cannot be read', async () => {
    api.getMyStudyPlan.mockRejectedValue({ status: 500, body: 'Planul nu a putut fi citit.' })

    renderPage()

    expect(await screen.findByTestId('error-state')).toHaveTextContent('Planul nu a putut fi citit.')
  })
})
