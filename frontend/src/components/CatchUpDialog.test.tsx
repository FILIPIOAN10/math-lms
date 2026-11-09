import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { CatchUpDialog } from '@/components/CatchUpDialog'
import { ApiError, type AssignmentSummary, type ClassMastery, type QuizSummary } from '@/lib/api'

const grid: ClassMastery = {
  chapters: [{ id: 30, title: 'Fracții', bookTitle: 'Algebra' }],
  students: [
    { studentId: 1, fullName: 'Ana Pop', cells: [{ chapterId: 30, percent: 35, questions: 4 }] },
    { studentId: 2, fullName: 'Bob Ionescu', cells: [{ chapterId: 30, percent: 85, questions: 3 }] },
    { studentId: 3, fullName: 'Cris Rus', cells: [{ chapterId: 30, percent: 40, questions: 2 }] },
  ],
}

const quiz = (id: number, title: string, chapterId: number | null): QuizSummary => ({
  id, title, description: null, status: 'PUBLISHED', schoolClassId: null, schoolClassName: null,
  timeLimitMinutes: null, practiceAllowed: true, chapterId, maxAttempts: null,
} as QuizSummary)

const quizzes = [quiz(7, 'Recapitulare generală', null), quiz(8, 'Fracții — exersare', 30)]
const created = { id: 99, quizTitle: 'Fracții — exersare', forStudents: 2 } as AssignmentSummary

function renderDialog(create: () => Promise<AssignmentSummary> = () => Promise.resolve(created)) {
  const createSpy = vi.fn(create)
  const onCreated = vi.fn()
  render(
    <CatchUpDialog open onClose={() => {}} grid={grid} column={0} classId={5} quizzes={quizzes}
      onCreated={onCreated} createCatchUpAssignment={createSpy} />,
  )
  return { createSpy, onCreated }
}

describe('CatchUpDialog', () => {
  it("starts with the chapter's weak students ticked and the chapter's quiz chosen", () => {
    renderDialog()

    expect(screen.getByRole('heading', { name: 'Temă de recuperare — Fracții' })).toBeInTheDocument()
    expect(screen.getByLabelText('Ana Pop (35%)')).toBeChecked()
    expect(screen.getByLabelText('Cris Rus (40%)')).toBeChecked()
    expect(screen.getByLabelText('Bob Ionescu (85%)')).not.toBeChecked()
    expect(screen.getByLabelText('Test')).toHaveValue('8')
    expect(screen.getByRole('button', { name: 'Dă tema (2 elevi)' })).toBeEnabled()
  })

  it('gives the homework to the ticked students only', async () => {
    const user = userEvent.setup()
    const { createSpy, onCreated } = renderDialog()

    await user.click(screen.getByLabelText('Cris Rus (40%)'))
    await user.click(screen.getByRole('button', { name: 'Dă tema (1 elev)' }))

    expect(createSpy).toHaveBeenCalledWith(expect.objectContaining({ quizId: 8, schoolClassId: 5, studentIds: [1] }))
    expect(onCreated).toHaveBeenCalledWith(created)
  })

  it('cannot give it to nobody', async () => {
    const user = userEvent.setup()
    renderDialog()

    await user.click(screen.getByLabelText('Ana Pop (35%)'))
    await user.click(screen.getByLabelText('Cris Rus (40%)'))

    expect(screen.getByRole('button', { name: 'Dă tema (0 elevi)' })).toBeDisabled()
  })

  it("shows the server's reason when it is refused", async () => {
    const user = userEvent.setup()
    const { onCreated } = renderDialog(() =>
      Promise.reject(new ApiError(409, 'Testul este deja dat ca temă acestei clase — modifică termenul')))

    await user.click(screen.getByRole('button', { name: 'Dă tema (2 elevi)' }))

    expect(await screen.findByText(/deja dat ca temă/)).toBeInTheDocument()
    expect(onCreated).not.toHaveBeenCalled()
  })

  it('offers a quiz the class already had as a redo, and says what that means', async () => {
    const user = userEvent.setup()
    render(
      <CatchUpDialog open onClose={() => {}} grid={grid} column={0} classId={5}
        quizzes={[...quizzes, quiz(9, 'Fracții — a doua', 30)]} givenQuizIds={[8]}
        onCreated={vi.fn()} createCatchUpAssignment={vi.fn()} />,
    )

    expect(screen.getByLabelText('Test')).toHaveValue('8')
    expect(screen.getByRole('option', { name: 'Fracții — exersare (deja temă clasei — o refac)' })).toBeEnabled()
    expect(screen.getByText(/elevii aleși îl refac/)).toBeInTheDocument()

    await user.selectOptions(screen.getByLabelText('Test'), '9')
    expect(screen.queryByText(/elevii aleși îl refac/)).not.toBeInTheDocument()
  })
})
