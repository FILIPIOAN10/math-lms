import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { type EnChapterSummary, type StudyPlan } from '@/lib/api'
import { StudentStudyPlanSection } from './StudentStudyPlanSection'

const api = vi.hoisted(() => ({
  getStudentStudyPlan: vi.fn(),
  listEnChapters: vi.fn(),
  pinStudyPlanChapter: vi.fn(),
  unpinStudyPlanChapter: vi.fn(),
}))
vi.mock('@/lib/api', async (original) => ({ ...(await original<typeof import('@/lib/api')>()), ...api }))

const chapter = (id: number, code: string, title: string, parentId: number | null = null): EnChapterSummary => ({
  id, code, title, area: 'ALGEBRA', position: id, parentId, bookPage: null, photoCount: 0, exampleCount: 0,
})

const plan = (over: Partial<StudyPlan> = {}): StudyPlan => ({
  examDate: '2027-03-02', daysLeft: 94, target: 9, grade: 7.4, low: 7.1, high: 7.7, answers: 60, answersNeeded: 20,
  mode: 'CATCH_UP', behind: false, expectedToday: 7.6,
  weeks: [{
    number: 1, start: '2026-11-28', end: '2026-12-04', finalWeek: false, items: [
      { type: 'PRACTICE', chapterId: 5, code: 'A3', title: 'Procente' },
      { type: 'PRACTICE', chapterId: 3, code: 'G5', title: 'Arii' },
    ],
  }],
  history: [], pins: [{ chapterId: 5, code: 'A3', title: 'Procente' }], practiceQuestions: 10,
  ...over,
})

function renderSection() {
  return render(<MemoryRouter><StudentStudyPlanSection studentId={7} /></MemoryRouter>)
}

describe('StudentStudyPlanSection', () => {
  beforeEach(() => {
    Object.values(api).forEach((fn) => fn.mockReset())
    api.listEnChapters.mockResolvedValue([
      chapter(3, 'G5', 'Arii'), chapter(5, 'A3', 'Procente'), chapter(6, 'A4', 'Ecuații'), chapter(9, 'A4.1', 'Secțiune', 6),
    ])
  })

  it("shows the student's plan with the chapters the teacher pinned", async () => {
    api.getStudentStudyPlan.mockResolvedValue(plan())

    renderSection()

    expect(await screen.findByRole('heading', { name: 'Planul până la EN' })).toBeInTheDocument()
    expect(api.getStudentStudyPlan).toHaveBeenCalledWith(7)
    const pins = screen.getByTestId('study-plan-pins')
    expect(within(pins).getByText('A3')).toBeInTheDocument()
    expect(screen.getByTestId('study-plan-week')).toHaveTextContent('pus de profesor')
  })

  it('pins a chapter into the plan; only whole chapters not pinned yet are offered', async () => {
    api.getStudentStudyPlan.mockResolvedValue(plan())
    api.pinStudyPlanChapter.mockResolvedValue(plan({ pins: [
      { chapterId: 5, code: 'A3', title: 'Procente' }, { chapterId: 6, code: 'A4', title: 'Ecuații' },
    ] }))

    renderSection()

    const select = await screen.findByLabelText('Capitol de pus în plan')
    const offered = within(select).getAllByRole('option').map((o) => o.textContent)
    expect(offered).toContain('A4 · Ecuații')
    expect(offered).not.toContain('A3 · Procente') // already pinned
    expect(offered).not.toContain('A4.1 · Secțiune') // a section, not a chapter
    await userEvent.selectOptions(select, '6')
    await userEvent.click(screen.getByRole('button', { name: 'Pune în plan' }))

    expect(api.pinStudyPlanChapter).toHaveBeenCalledWith(7, 6)
    expect(await within(screen.getByTestId('study-plan-pins')).findByText('A4')).toBeInTheDocument()
  })

  it('takes a pin out', async () => {
    api.getStudentStudyPlan.mockResolvedValue(plan())
    api.unpinStudyPlanChapter.mockResolvedValue(plan({ pins: [] }))

    renderSection()

    await userEvent.click(await screen.findByRole('button', { name: 'Scoate A3 din plan' }))

    expect(api.unpinStudyPlanChapter).toHaveBeenCalledWith(7, 5)
    expect(await screen.findByText('Niciun capitol pus de tine.')).toBeInTheDocument()
  })

  it('shows the error of a pin that failed', async () => {
    api.getStudentStudyPlan.mockResolvedValue(plan())
    api.pinStudyPlanChapter.mockRejectedValue({ status: 404, body: 'Capitolul nu mai există.' })

    renderSection()

    await userEvent.selectOptions(await screen.findByLabelText('Capitol de pus în plan'), '6')
    await userEvent.click(screen.getByRole('button', { name: 'Pune în plan' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('Capitolul nu mai există.')
  })
})
