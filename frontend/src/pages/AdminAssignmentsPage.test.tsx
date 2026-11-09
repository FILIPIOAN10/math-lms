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
  setAssignmentOverride: vi.fn(),
  grantExtraAttempt: vi.fn(),
}))
vi.mock('@/lib/api', async (original) => ({ ...(await original<typeof import('@/lib/api')>()), ...api }))

const summary = (id: number, quizTitle: string): AssignmentSummary => ({
  id, quizId: id, quizTitle, schoolClassId: 1, schoolClassName: 'Clasa a 8-a A', dueAt: new Date(Date.now() + 86_400_000).toISOString(),
  overdue: false, enrolled: 2, done: 1, late: 0, inProgress: 0, notStarted: 1, excused: 0,
})
const status = (studentId: number, fullName: string, state: AssignmentStudentStatus['state']): AssignmentStudentStatus => ({
  studentId, fullName, state, submittedAt: null, late: false, score: null, attemptId: null, ownDueAt: null, excused: false,
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

  it('keeps changing and deleting a homework behind the ⋯ menu, deleting last', async () => {
    renderPage('/admin/assignments')
    const row = (await screen.findAllByTestId('assignment-row'))[0]

    expect(within(row).queryByRole('button', { name: 'Șterge' })).not.toBeInTheDocument()
    expect(within(row).queryByRole('button', { name: 'Termen' })).not.toBeInTheDocument()
    await userEvent.click(within(row).getByRole('button', { name: /^Mai multe acțiuni pentru / }))

    const items = (await screen.findAllByRole('menuitem')).map((i) => i.textContent)
    expect(items).toEqual(['Schimbă termenul', 'Șterge tema'])
  })

  it('opens who handed in for the homework named in ?open= (the link from Acasă)', async () => {
    renderPage('/admin/assignments?open=2')

    const table = await screen.findByTestId('assignment-status')
    expect(api.getAssignmentStatus).toHaveBeenCalledWith(2)
    expect(within(table).getByText('Radu Marin')).toBeInTheDocument()
    const rows = screen.getAllByTestId('assignment-row')
    expect(within(rows[1]).getByTestId('assignment-status')).toBe(table)
    expect(within(rows[1]).getByRole('button', { name: 'Ascunde' })).toBeInTheDocument()
    expect(within(rows[0]).getByRole('button', { name: 'Cine a predat' })).toBeInTheDocument()
  })

  it('opens nothing without ?open=', async () => {
    renderPage('/admin/assignments')
    await screen.findAllByTestId('assignment-row')
    expect(api.getAssignmentStatus).not.toHaveBeenCalled()
    expect(screen.queryByTestId('assignment-status')).not.toBeInTheDocument()
  })

  async function openRadusMenu(user: ReturnType<typeof userEvent.setup>) {
    renderPage('/admin/assignments?open=1')
    const table = await screen.findByTestId('assignment-status')
    await user.click(within(table).getByRole('button', { name: 'Excepții pentru Radu Marin' }))
    return (await screen.findAllByRole('menuitem')).map((i) => i.textContent)
  }

  it('excuses one student from their row and shows it in the list', async () => {
    const user = userEvent.setup()
    api.setAssignmentOverride.mockResolvedValue([{ ...status(7, 'Radu Marin', 'NOT_STARTED'), excused: true }])

    expect(await openRadusMenu(user)).toEqual(['Termen doar pentru el/ea', 'Scutește de temă'])
    await user.click(screen.getByRole('menuitem', { name: 'Scutește de temă' }))

    expect(api.setAssignmentOverride).toHaveBeenCalledWith(1, 7, { dueAt: null, excused: true })
    expect(await within(screen.getByTestId('assignment-status')).findByText('Scutit')).toBeInTheDocument()
  })

  it('shows a student\'s own deadline and can put them back on the class one', async () => {
    const user = userEvent.setup()
    const own = new Date(Date.now() + 5 * 86_400_000).toISOString()
    api.getAssignmentStatus.mockResolvedValue([{ ...status(7, 'Radu Marin', 'NOT_STARTED'), ownDueAt: own }])
    api.setAssignmentOverride.mockResolvedValue([status(7, 'Radu Marin', 'NOT_STARTED')])

    const items = await openRadusMenu(user)
    expect(screen.getByTestId('own-due')).toHaveTextContent('termen propriu')
    expect(items).toContain('Înapoi la termenul clasei')
    await user.click(screen.getByRole('menuitem', { name: 'Înapoi la termenul clasei' }))

    expect(api.setAssignmentOverride).toHaveBeenCalledWith(1, 7, { dueAt: null, excused: false })
  })

  it('gives one student a deadline of their own through the deadline dialog', async () => {
    const user = userEvent.setup()
    api.setAssignmentOverride.mockResolvedValue([status(7, 'Radu Marin', 'NOT_STARTED')])

    await openRadusMenu(user)
    await user.click(screen.getByRole('menuitem', { name: 'Termen doar pentru el/ea' }))
    const dialog = await screen.findByRole('dialog', { name: 'Termen pentru Radu Marin' })
    const input = within(dialog).getByLabelText('Termen')
    await user.clear(input)
    await user.type(input, '2099-01-15T20:00')
    await user.click(within(dialog).getByRole('button', { name: 'Salvează' }))

    expect(api.setAssignmentOverride).toHaveBeenCalledWith(1, 7, {
      dueAt: new Date('2099-01-15T20:00').toISOString(), excused: false,
    })
  })

  it('counts the excused students apart', async () => {
    api.listAssignments.mockResolvedValue([{ ...summary(1, 'Fracții'), excused: 2 }])
    renderPage('/admin/assignments')

    expect(await screen.findByTestId('assignment-counts')).toHaveTextContent('scutiți 2')
  })

  it('gives a student who handed in one more attempt when the test has a limit', async () => {
    const user = userEvent.setup()
    api.listQuizzes.mockResolvedValue([{
      id: 1, title: 'Fracții', description: null, status: 'PUBLISHED', schoolClassId: null, schoolClassName: null,
      timeLimitMinutes: null, practiceAllowed: false, chapterId: null, maxAttempts: 1,
    }])
    api.getAssignmentStatus.mockResolvedValue([{ ...status(7, 'Radu Marin', 'GRADED'), attemptId: 300 }])
    api.grantExtraAttempt.mockResolvedValue({ attemptsAllowed: 2 })

    const items = await openRadusMenu(user)
    expect(items).toContain('+1 încercare')
    await user.click(screen.getByRole('menuitem', { name: '+1 încercare' }))

    expect(api.grantExtraAttempt).toHaveBeenCalledWith(300)
    expect(await screen.findByText(/Radu Marin poate da testul de 2 ori/)).toBeInTheDocument()
  })

  it('offers no extra attempt when the test has no limit or the student never handed in', async () => {
    const user = userEvent.setup()
    expect(await openRadusMenu(user)).not.toContain('+1 încercare')
  })
})
