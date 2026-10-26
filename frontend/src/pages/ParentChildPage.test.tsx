import { render, screen, waitFor, within } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ParentChildPage } from '@/pages/ParentChildPage'
import { ApiError, type StudentAssignment } from '@/lib/api'

const api = vi.hoisted(() => ({
  getChildAttempts: vi.fn(),
  getChildProgress: vi.fn(),
  getChildAssignments: vi.fn(),
  listMyChildren: vi.fn(),
  getChildMastery: vi.fn(),
}))
vi.mock('@/lib/api', async (original) => ({ ...(await original<typeof import('@/lib/api')>()), ...api }))

const homework = (id: number, title: string, over: Partial<StudentAssignment> = {}): StudentAssignment => ({
  assignmentId: id, quizId: id, quizTitle: title, schoolClassName: 'Clasa a 8-a',
  dueAt: new Date(Date.now() + 2 * 86_400_000).toISOString(), state: 'NOT_STARTED', late: false, overdue: false,
  attemptId: null, timeLimitMinutes: null, ...over,
})

function renderPage() {
  return render(
    <MemoryRouter initialEntries={['/parent/children/4']}>
      <Routes>
        <Route path="/parent/children/:childId" element={<ParentChildPage />} />
      </Routes>
    </MemoryRouter>,
  )
}

describe('ParentChildPage', () => {
  beforeEach(() => {
    Object.values(api).forEach((fn) => fn.mockReset())
    api.getChildAttempts.mockResolvedValue([])
    api.getChildProgress.mockResolvedValue([])
    api.getChildAssignments.mockResolvedValue([])
    api.listMyChildren.mockResolvedValue([{ id: 4, fullName: 'Ana Student', email: 'ana@x.ro' }])
    api.getChildMastery.mockResolvedValue([])
  })

  it("shows the child's level per chapter", async () => {
    api.getChildMastery.mockResolvedValue([{
      chapterId: 1, chapterTitle: 'Fracții', bookTitle: 'Algebra', schoolClassName: 'Clasa a 8-a',
      earned: 9, possible: 10, percent: 90, questions: 4,
    }])
    renderPage()

    const section = await screen.findByRole('region', { name: 'Pe capitole' })
    expect(section).toHaveTextContent('Fracții')
    expect(section).toHaveTextContent('90%')
    expect(api.getChildMastery).toHaveBeenCalledWith(4)
  })

  it("is titled with the child's name", async () => {
    renderPage()
    expect(await screen.findByRole('heading', { level: 1, name: 'Ana Student' })).toBeInTheDocument()
    await waitFor(() => expect(document.title).toBe('Ana Student · Math LMS')) // set by an effect after the render
  })

  it("lists the child's homework with its state and deadline, without buttons to take it", async () => {
    api.getChildAssignments.mockResolvedValue([
      homework(1, 'Fracții', { overdue: true }),
      homework(2, 'Ecuații', { state: 'IN_PROGRESS' }),
      homework(3, 'Simulare EN', { state: 'GRADED', attemptId: 101 }),
    ])
    api.getChildAttempts.mockResolvedValue([{
      attemptId: 101, quizId: 3, quizTitle: 'Simulare EN', status: 'GRADED', startedAt: '2026-10-04T08:00:00Z',
      submittedAt: '2026-10-04T09:00:00Z', score: 8, mode: 'TEST', maxScore: 11, newResult: false,
    }])
    renderPage()

    const section = await screen.findByRole('region', { name: 'Teme' })
    const cards = within(section).getAllByTestId('assignment-card')
    expect(cards).toHaveLength(3)
    expect(cards[0]).toHaveTextContent('Termen depășit')
    expect(cards[1]).toHaveTextContent('În lucru')
    expect(within(section).queryByRole('link', { name: /Începe|Continuă/ })).not.toBeInTheDocument()
    expect(within(cards[2]).getByRole('link', { name: 'Vezi rezultatul' }))
      .toHaveAttribute('href', '/parent/children/4/attempts/101')
    await waitFor(() => expect(within(cards[2]).getByTestId('assignment-grade')).toHaveTextContent('nota 7,55'))
    expect(api.getChildAssignments).toHaveBeenCalledWith(4)
  })

  it('says so when the child has no homework', async () => {
    renderPage()
    expect(await screen.findByText('Nicio temă dată clasei copilului.')).toBeInTheDocument()
  })

  it("shows the server's refusal for a child that is not yours", async () => {
    api.getChildAssignments.mockRejectedValue(new ApiError(403, 'Not your child'))
    api.getChildAttempts.mockRejectedValue(new ApiError(403, 'Not your child'))
    renderPage()
    expect(await screen.findByText('Not your child')).toBeInTheDocument()
  })
})
