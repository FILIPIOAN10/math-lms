import { fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { AssignmentDialog } from '@/pages/AdminAssignmentsPage'
import type { AssignmentInput, QuizSummary, SchoolClass } from '@/lib/api'
import { isoToLocalInput } from '@/lib/assignments'

const quiz = (id: number, title: string, status: QuizSummary['status'] = 'PUBLISHED'): QuizSummary => ({
  id, title, description: null, status, schoolClassId: null, schoolClassName: null, timeLimitMinutes: null, practiceAllowed: false, chapterId: null, maxAttempts: null,
})
const quizzes = [
  quiz(10, 'Simulare EN'), quiz(11, 'Ciorna', 'DRAFT'), quiz(12, 'Teza'),
  { ...quiz(13, 'Doar a 10-a'), schoolClassId: 6, schoolClassName: 'Clasa a 10-a' },
]
const classes: SchoolClass[] = [
  { id: 5, name: 'Clasa a 9-a', description: null, teacherId: 1 },
  { id: 6, name: 'Clasa a 10-a', description: null, teacherId: 1 },
]
const inDays = (n: number) => isoToLocalInput(new Date(Date.now() + n * 86_400_000).toISOString())

function renderCreate(onSubmit = vi.fn().mockResolvedValue(undefined)) {
  render(<AssignmentDialog open onClose={vi.fn()} mode="create" quizzes={quizzes} classes={classes} onSubmit={onSubmit} />)
  return onSubmit
}

describe('AssignmentDialog (create)', () => {
  it('offers only published quizzes', () => {
    renderCreate()

    const options = screen.getAllByRole('option').map((o) => o.textContent)
    expect(options).toContain('Simulare EN')
    expect(options).toContain('Teza')
    expect(options).not.toContain('Ciorna')
  })

  it('sends the chosen quiz, class and deadline as an ISO instant', async () => {
    const onSubmit = renderCreate()
    await userEvent.selectOptions(screen.getByLabelText('Test'), '12')
    await userEvent.click(screen.getByRole('checkbox', { name: 'Clasa a 10-a' }))
    const due = inDays(3)
    await userEvent.type(screen.getByLabelText('Termen'), due)

    await userEvent.click(screen.getByRole('button', { name: 'Dă tema' }))

    await waitFor(() => expect(onSubmit).toHaveBeenCalled())
    const input = onSubmit.mock.calls[0][0] as AssignmentInput
    expect(input.quizId).toBe(12)
    expect(input.schoolClassIds).toEqual([6])
    expect(input.dueAt).toBe(new Date(due).toISOString())
  })

  it('gives the same homework to several classes in one go', async () => {
    const onSubmit = renderCreate()
    await userEvent.selectOptions(screen.getByLabelText('Test'), '10')
    await userEvent.click(screen.getByRole('checkbox', { name: 'Clasa a 9-a' }))
    await userEvent.click(screen.getByRole('checkbox', { name: 'Clasa a 10-a' }))
    await userEvent.type(screen.getByLabelText('Termen'), inDays(3))

    await userEvent.click(screen.getByRole('button', { name: 'Dă tema (2 clase)' }))

    await waitFor(() => expect(onSubmit).toHaveBeenCalled())
    expect((onSubmit.mock.calls[0][0] as AssignmentInput).schoolClassIds).toEqual([5, 6])
  })

  it('offers only its own class for a quiz reserved to one, already ticked', async () => {
    renderCreate()
    await userEvent.selectOptions(screen.getByLabelText('Test'), '13')

    const boxes = within(screen.getByRole('group', { name: 'Clase' })).getAllByRole('checkbox')
    expect(boxes).toHaveLength(1)
    expect(screen.getByRole('checkbox', { name: 'Clasa a 10-a' })).toBeChecked()
  })

  it('will not submit without a quiz, a class and a deadline', async () => {
    const onSubmit = renderCreate()

    expect(screen.getByRole('button', { name: 'Dă tema' })).toBeDisabled()
    await userEvent.selectOptions(screen.getByLabelText('Test'), '10')
    await userEvent.click(screen.getByRole('checkbox', { name: 'Clasa a 9-a' }))
    expect(screen.getByRole('button', { name: 'Dă tema' })).toBeDisabled() // still no deadline
    expect(onSubmit).not.toHaveBeenCalled()
  })

  it('refuses a deadline in the past before asking the server', async () => {
    const onSubmit = renderCreate()
    await userEvent.selectOptions(screen.getByLabelText('Test'), '10')
    await userEvent.click(screen.getByRole('checkbox', { name: 'Clasa a 9-a' }))
    await userEvent.type(screen.getByLabelText('Termen'), inDays(-1))

    await userEvent.click(screen.getByRole('button', { name: 'Dă tema' }))

    expect(await screen.findByText(/trebuie să fie în viitor/)).toBeInTheDocument()
    expect(onSubmit).not.toHaveBeenCalled()
  })

  it('shows the server error and stays open', async () => {
    const onSubmit = vi.fn().mockRejectedValue({ body: 'Quiz-ul este deja dat ca temă acestei clase — modifică termenul' })
    renderCreate(onSubmit)
    await userEvent.selectOptions(screen.getByLabelText('Test'), '10')
    await userEvent.click(screen.getByRole('checkbox', { name: 'Clasa a 9-a' }))
    await userEvent.type(screen.getByLabelText('Termen'), inDays(2))

    await userEvent.click(screen.getByRole('button', { name: 'Dă tema' }))

    expect(await screen.findByText(/deja dat ca temă/)).toBeInTheDocument()
  })
})

describe('AssignmentDialog (reschedule)', () => {
  it('only asks for the new deadline and starts from the current one', async () => {
    const onSubmit = vi.fn().mockResolvedValue(undefined)
    const current = new Date(Date.now() + 2 * 86_400_000).toISOString()
    render(<AssignmentDialog open onClose={vi.fn()} mode="reschedule" currentDueAt={current} onSubmit={onSubmit} />)

    expect(screen.queryByLabelText('Test')).not.toBeInTheDocument()
    expect(screen.getByLabelText('Termen')).toHaveValue(isoToLocalInput(current))

    await userEvent.click(screen.getByRole('button', { name: 'Salvează' }))

    await waitFor(() => expect(onSubmit).toHaveBeenCalled())
    expect((onSubmit.mock.calls[0][0] as AssignmentInput).dueAt).toBe(new Date(isoToLocalInput(current)).toISOString())
  })

  it('can schedule the homework to appear later, before the deadline', async () => {
    const onSubmit = renderCreate()
    await userEvent.selectOptions(screen.getByLabelText('Test'), '12')
    await userEvent.click(screen.getByRole('checkbox', { name: 'Clasa a 10-a' }))
    await userEvent.type(screen.getByLabelText('Termen'), inDays(5))
    await userEvent.click(screen.getByRole('checkbox', { name: 'Programează: apare elevilor mai târziu' }))
    // the test is already published: only the homework waits, the test stays on the students' list
    expect(screen.getByText(/Testul, deja publicat, rămâne/)).toBeInTheDocument()
    const release = screen.getByLabelText('Apare elevilor la')
    fireEvent.change(release, { target: { value: inDays(6) } }) // after the deadline: refused here

    await userEvent.click(screen.getByRole('button', { name: 'Dă tema' }))
    expect(await screen.findByText('Tema trebuie să apară înainte de termen.')).toBeInTheDocument()
    expect(onSubmit).not.toHaveBeenCalled()

    const appears = inDays(2)
    fireEvent.change(release, { target: { value: appears } })
    await userEvent.click(screen.getByRole('button', { name: 'Dă tema' }))

    await waitFor(() => expect(onSubmit).toHaveBeenCalled())
    expect((onSubmit.mock.calls[0][0] as AssignmentInput).availableFrom).toBe(new Date(appears).toISOString())
  })

  it('sends no release when the homework is not scheduled', async () => {
    const onSubmit = renderCreate()
    await userEvent.selectOptions(screen.getByLabelText('Test'), '12')
    await userEvent.click(screen.getByRole('checkbox', { name: 'Clasa a 10-a' }))
    await userEvent.type(screen.getByLabelText('Termen'), inDays(3))
    await userEvent.click(screen.getByRole('button', { name: 'Dă tema' }))

    await waitFor(() => expect(onSubmit).toHaveBeenCalled())
    expect((onSubmit.mock.calls[0][0] as AssignmentInput).availableFrom).toBeNull()
  })
})
