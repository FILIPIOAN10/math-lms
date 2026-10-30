import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { AdminAssignmentsPage } from '@/pages/AdminAssignmentsPage'
import type { AssignmentStudentStatus, AssignmentSummary } from '@/lib/api'

const api = vi.hoisted(() => ({
  listAssignments: vi.fn(),
  listQuizzes: vi.fn(),
  listClasses: vi.fn(),
  getAssignmentStatus: vi.fn(),
}))
vi.mock('@/lib/api', async (original) => ({ ...(await original<typeof import('@/lib/api')>()), ...api }))

const summary = (id: number, quizTitle: string): AssignmentSummary => ({
  id, quizId: id, quizTitle, schoolClassId: 1, schoolClassName: 'Clasa a 8-a A', dueAt: new Date(Date.now() + 86_400_000).toISOString(),
  overdue: false, enrolled: 2, done: 1, late: 0, inProgress: 0, notStarted: 1,
})
const status = (studentId: number, fullName: string, state: AssignmentStudentStatus['state']): AssignmentStudentStatus => ({
  studentId, fullName, state, submittedAt: null, late: false, score: null, attemptId: null,
})

function renderPage(path: string) {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <Routes>
        <Route path="/admin/assignments" element={<AdminAssignmentsPage />} />
      </Routes>
    </MemoryRouter>,
  )
}

describe('AdminAssignmentsPage', () => {
  beforeEach(() => {
    Object.values(api).forEach((fn) => fn.mockReset())
    api.listAssignments.mockResolvedValue([summary(1, 'Fracții'), summary(2, 'Ecuații')])
    api.listQuizzes.mockResolvedValue([])
    api.listClasses.mockResolvedValue([])
    api.getAssignmentStatus.mockResolvedValue([status(7, 'Radu Marin', 'NOT_STARTED'), status(8, 'Ana', 'GRADED')])
  })

  it('searches and filters the homework once there are five', async () => {
    const user = userEvent.setup()
    api.listAssignments.mockResolvedValue([
      summary(1, 'Fracții'),
      summary(2, 'Ecuații'),
      { ...summary(3, 'Procente'), overdue: true },
      { ...summary(4, 'Geometrie'), schoolClassName: 'Clasa a 9-a B', overdue: true },
      summary(5, 'Simulare EN'),
    ])
    renderPage('/admin/assignments')
    await screen.findByText('Procente')

    await user.selectOptions(screen.getByRole('combobox', { name: 'Termen' }), 'overdue')
    expect(screen.getAllByTestId('assignment-row')).toHaveLength(2)

    await user.selectOptions(screen.getByRole('combobox', { name: 'Clasa' }), 'Clasa a 9-a B')
    expect(screen.getAllByTestId('assignment-row')).toHaveLength(1)

    await user.click(screen.getByRole('button', { name: 'Șterge filtrele' }))
    await user.type(screen.getByRole('searchbox', { name: /Caută temă/ }), 'fractii')
    expect(screen.getAllByTestId('assignment-row')).toHaveLength(1)
  })

  it('opens who handed in for the homework named in ?open= (the link from Acasă)', async () => {
    renderPage('/admin/assignments?open=2')

    const table = await screen.findByTestId('assignment-status')
    expect(api.getAssignmentStatus).toHaveBeenCalledWith(2)
    expect(within(table).getByText('Radu Marin')).toBeInTheDocument()
    const rows = screen.getAllByTestId('assignment-row')
    expect(within(rows[1]).getByTestId('assignment-status')).toBe(table)
    expect(within(rows[1]).getByRole('button', { name: 'Ascunde' })).toBeInTheDocument()
  })

  it('opens nothing without ?open=', async () => {
    renderPage('/admin/assignments')
    await screen.findAllByTestId('assignment-row')
    expect(api.getAssignmentStatus).not.toHaveBeenCalled()
    expect(screen.queryByTestId('assignment-status')).not.toBeInTheDocument()
  })
})
