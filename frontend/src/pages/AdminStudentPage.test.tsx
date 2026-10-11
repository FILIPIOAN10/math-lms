import { render, screen, within } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { AdminStudentPage } from '@/pages/AdminStudentPage'
import { ApiError, type MyAttemptDto, type StudentAssignment, type StudentProfile } from '@/lib/api'

const api = vi.hoisted(() => ({
  getStudentProfile: vi.fn(),
  getStudentAttempts: vi.fn(),
  getStudentAssignments: vi.fn(),
  getStudentProgress: vi.fn(),
  getStudentMastery: vi.fn(),
  getStudentReview: vi.fn(),
}))
vi.mock('@/lib/api', async (original) => ({ ...(await original<typeof import('@/lib/api')>()), ...api }))

const ana: StudentProfile = {
  id: 4, fullName: 'Ana Student', email: 'ana@scoala.ro', status: 'ACTIVE',
  classes: [{ id: 1, name: 'Clasa a 8-a' }], parentId: 7, parentName: 'Maria Parinte', parentEmail: 'maria@scoala.ro',
}

const attempt = (id: number, over: Partial<MyAttemptDto> = {}): MyAttemptDto => ({
  attemptId: id, quizId: 2, quizTitle: 'Simulare EN', mode: 'TEST', status: 'GRADED', startedAt: '2026-10-10T08:00:00Z',
  submittedAt: '2026-10-10T08:30:00Z', score: 6, maxScore: 10, ...over,
} as MyAttemptDto)

const homework: StudentAssignment = {
  assignmentId: 3, quizId: 2, quizTitle: 'Simulare EN', schoolClassName: 'Clasa a 8-a',
  dueAt: new Date(Date.now() + 86_400_000).toISOString(), state: 'GRADED', late: false, overdue: false,
  attemptId: 11, timeLimitMinutes: 10,
}

function renderPage() {
  return render(
    <MemoryRouter initialEntries={['/admin/students/4']}>
      <Routes>
        <Route path="/admin/students/:studentId" element={<AdminStudentPage />} />
      </Routes>
    </MemoryRouter>,
  )
}

describe('AdminStudentPage', () => {
  beforeEach(() => {
    Object.values(api).forEach((fn) => fn.mockReset())
    api.getStudentProfile.mockResolvedValue(ana)
    api.getStudentAttempts.mockResolvedValue([attempt(11), attempt(12, { status: 'SUBMITTED', score: null })])
    api.getStudentAssignments.mockResolvedValue([homework])
    api.getStudentProgress.mockResolvedValue([])
    api.getStudentMastery.mockResolvedValue([])
    api.getStudentReview.mockResolvedValue({
      doneToday: 2, remainingToday: 0, streak: 3, totalCards: 5, mastered: 1, lastReviewOn: null,
    })
  })

  it('puts the class, the parent and the daily review at the top', async () => {
    renderPage()

    const about = await screen.findByTestId('student-about')
    expect(within(about).getByRole('link', { name: 'Clasa a 8-a' })).toHaveAttribute('href', '/admin/classes/1/mastery')
    expect(about).toHaveTextContent('Maria Parinte (maria@scoala.ro)')
    expect(screen.getByTestId('student-review')).toHaveTextContent('Recapitulare: făcută azi ✓ · seria 3 zile')
    expect(screen.getByRole('heading', { name: 'Ana Student' })).toBeInTheDocument()
  })

  it("opens each paper on the grading screen, and a paper still waiting says so", async () => {
    renderPage()

    const papers = await screen.findAllByTestId('student-attempt')
    expect(within(papers[0]).getByRole('link', { name: 'Vezi lucrarea' })).toHaveAttribute('href', '/admin/grading?attempt=11')
    expect(within(papers[1]).getByRole('link', { name: 'Corectează' })).toHaveAttribute('href', '/admin/grading?attempt=12')
  })

  it('shows the homework read-only, with the result on the grading screen', async () => {
    renderPage()

    const card = await screen.findByTestId('assignment-card')
    expect(within(card).getByRole('link', { name: 'Vezi rezultatul' })).toHaveAttribute('href', '/admin/grading?attempt=11')
    expect(within(card).queryByRole('link', { name: /Începe|Continuă/ })).not.toBeInTheDocument()
  })

  it('offers to invite a parent when none is linked', async () => {
    api.getStudentProfile.mockResolvedValue({ ...ana, parentId: null, parentName: null, parentEmail: null })
    renderPage()

    expect(await screen.findByRole('link', { name: 'Invită părintele' })).toHaveAttribute('href', '/admin/students')
  })

  it("shows the server's answer for an id that is not a student", async () => {
    api.getStudentProfile.mockRejectedValue(new ApiError(404, 'Nu există elevul 4.'))
    renderPage()

    expect(await screen.findByText('Nu există elevul 4.')).toBeInTheDocument()
  })
})
