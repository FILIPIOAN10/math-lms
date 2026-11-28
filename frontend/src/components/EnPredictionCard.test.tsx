import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { type EnGoalSummary, type EnPrediction } from '@/lib/api'
import { EnPredictionCard } from './EnPredictionCard'

const api = vi.hoisted(() => ({
  getMyEnPrediction: vi.fn(),
  getChildEnPrediction: vi.fn(),
  getStudentEnPrediction: vi.fn(),
  getMyEnGoal: vi.fn(),
  getChildEnGoal: vi.fn(),
  getStudentEnGoal: vi.fn(),
}))
vi.mock('@/lib/api', async (original) => ({ ...(await original<typeof import('@/lib/api')>()), ...api }))

const prediction = (over: Partial<EnPrediction> = {}): EnPrediction => ({
  available: true, answers: 60, answersNeeded: 20, grade: 7.4, low: 7.1, high: 7.7,
  topGains: [{ chapterId: 3, code: 'G5', title: 'Arii', gradeGain: 0.6 }], ...over,
})

const goal = (over: Partial<EnGoalSummary> = {}): EnGoalSummary => ({
  target: 9, examDate: '2027-03-02', daysLeft: 94, ...over,
})

function renderCard(props: { childId?: number; studentId?: number; planTo?: string } = {}) {
  return render(<MemoryRouter><EnPredictionCard {...props} /></MemoryRouter>)
}

describe('EnPredictionCard', () => {
  beforeEach(() => {
    Object.values(api).forEach((fn) => fn.mockReset())
  })

  it("shows the student's target next to the estimate and links to their plan", async () => {
    api.getMyEnPrediction.mockResolvedValue(prediction())
    api.getMyEnGoal.mockResolvedValue(goal())

    renderCard()

    expect(await screen.findByTestId('en-goal-line')).toHaveTextContent('Acum ~7,40 → ținta 9,00 · mai ai 94 de zile')
    expect(screen.getByTestId('en-target-marker')).toHaveStyle({ left: `${((9 - 1) / 9) * 100}%` })
    expect(screen.getByRole('link', { name: 'Vezi planul' })).toHaveAttribute('href', '/plan')
  })

  it('asks the student to pick a target when they have none', async () => {
    api.getMyEnPrediction.mockResolvedValue(prediction())
    api.getMyEnGoal.mockResolvedValue(goal({ target: null }))

    renderCard()

    expect(await screen.findByRole('link', { name: 'Alege-ți ținta' })).toHaveAttribute('href', '/plan')
    expect(screen.queryByTestId('en-goal-line')).not.toBeInTheDocument()
    expect(screen.queryByTestId('en-target-marker')).not.toBeInTheDocument()
  })

  it('keeps the target in view before there is an estimate', async () => {
    api.getMyEnPrediction.mockResolvedValue(prediction({ available: false, answers: 12, grade: null, low: null, high: null, topGains: [] }))
    api.getMyEnGoal.mockResolvedValue(goal())

    renderCard()

    expect(await screen.findByTestId('en-goal-line')).toHaveTextContent('Ținta 9,00 · mai ai 94 de zile')
    expect(screen.getByRole('link', { name: 'Vezi planul' })).toBeInTheDocument()
  })

  it('still shows the estimate when the target cannot be read', async () => {
    api.getMyEnPrediction.mockResolvedValue(prediction())
    api.getMyEnGoal.mockRejectedValue(new Error('500'))

    renderCard()

    expect(await screen.findByTestId('en-prediction-range')).toHaveTextContent('7,10 - 7,70')
    expect(screen.queryByTestId('en-goal-line')).not.toBeInTheDocument()
  })

  it("shows a child's target to the parent, with a link only where the page gives one", async () => {
    api.getChildEnPrediction.mockResolvedValue(prediction())
    api.getChildEnGoal.mockResolvedValue(goal({ target: 8.5 }))

    renderCard({ childId: 4 })

    expect(await screen.findByTestId('en-goal-line')).toHaveTextContent('ținta 8,50')
    expect(api.getChildEnGoal).toHaveBeenCalledWith(4)
    expect(screen.queryByRole('link', { name: 'Vezi planul' })).not.toBeInTheDocument()
    expect(screen.queryByRole('link', { name: 'Alege-ți ținta' })).not.toBeInTheDocument()
  })

  it("links to the plan page it is given for someone else's estimate", async () => {
    api.getChildEnPrediction.mockResolvedValue(prediction())
    api.getChildEnGoal.mockResolvedValue(goal())

    renderCard({ childId: 4, planTo: '/parent/children/4/plan' })

    expect(await screen.findByRole('link', { name: 'Vezi planul' })).toHaveAttribute('href', '/parent/children/4/plan')
  })

  it("reads the teacher's student by id", async () => {
    api.getStudentEnPrediction.mockResolvedValue(prediction())
    api.getStudentEnGoal.mockResolvedValue(goal({ target: 10 }))

    renderCard({ studentId: 7 })

    expect(await screen.findByTestId('en-goal-line')).toHaveTextContent('ținta 10,00')
    expect(api.getStudentEnGoal).toHaveBeenCalledWith(7)
  })
})
