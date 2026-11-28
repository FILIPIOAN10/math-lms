import { render, screen, within } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { type StudyPlan } from '@/lib/api'
import { ParentStudyPlanPage } from './ParentStudyPlanPage'

const api = vi.hoisted(() => ({ getChildStudyPlan: vi.fn(), listMyChildren: vi.fn() }))
vi.mock('@/lib/api', async (original) => ({ ...(await original<typeof import('@/lib/api')>()), ...api }))

const plan: StudyPlan = {
  examDate: '2027-03-02', daysLeft: 94, target: 9, grade: 7.4, low: 7.1, high: 7.7, answers: 60, answersNeeded: 20,
  mode: 'CATCH_UP', behind: true, expectedToday: 8.1,
  weeks: [{
    number: 1, start: '2026-11-28', end: '2026-12-04', finalWeek: false, items: [
      { type: 'LESSON', chapterId: 3, code: 'G5', title: 'Arii' },
      { type: 'PRACTICE', chapterId: 3, code: 'G5', title: 'Arii' },
    ],
  }],
  history: [{ day: '2026-11-28', grade: 7.4, low: 7.1, high: 7.7 }],
  pins: [], practiceQuestions: 10,
}

function renderPage() {
  return render(
    <MemoryRouter initialEntries={['/parent/children/4/plan']}>
      <Routes>
        <Route path="/parent/children/:childId/plan" element={<ParentStudyPlanPage />} />
      </Routes>
    </MemoryRouter>,
  )
}

describe('ParentStudyPlanPage', () => {
  beforeEach(() => {
    api.getChildStudyPlan.mockReset()
    api.listMyChildren.mockReset().mockResolvedValue([{ id: 4, fullName: 'Ana Pop', email: 'ana@scoala.ro' }])
  })

  it("shows the child's plan to read, without the student's buttons or links", async () => {
    api.getChildStudyPlan.mockResolvedValue(plan)

    renderPage()

    expect(await screen.findByTestId('study-plan-goal-line')).toHaveTextContent('Acum ~7,40 → ținta 9,00 · mai ai 94 de zile')
    expect(api.getChildStudyPlan).toHaveBeenCalledWith(4)
    expect(await screen.findByRole('heading', { name: 'Planul EN — Ana Pop' })).toBeInTheDocument()
    expect(screen.getByTestId('study-plan-behind')).toHaveTextContent('În urmă')
    expect(screen.queryByRole('group', { name: 'Alege ținta' })).not.toBeInTheDocument()
    expect(screen.queryByTestId('study-plan-today')).not.toBeInTheDocument()
    const week = screen.getByTestId('study-plan-week')
    expect(within(week).getByText('Citește lecția')).toBeInTheDocument()
    expect(within(week).queryByRole('link')).not.toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Ana Pop' })).toHaveAttribute('href', '/parent/children/4')
  })

  it('shows the error when the plan cannot be read', async () => {
    api.getChildStudyPlan.mockRejectedValue({ status: 403, body: 'Acesta nu e copilul tău.' })

    renderPage()

    expect(await screen.findByTestId('error-state')).toHaveTextContent('Acesta nu e copilul tău.')
  })
})
