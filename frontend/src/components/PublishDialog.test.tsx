import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { PublishDialog } from '@/components/PublishDialog'
import { ApiError, type AssignmentSummary, type QuizSummary, type SchoolClass } from '@/lib/api'

const classes: SchoolClass[] = [
  { id: 1, name: 'Clasa a 8-a', description: null },
  { id: 2, name: 'Clasa a 9-a', description: null },
]

function renderDialog(over: {
  quizClassId?: number | null
  classes?: SchoolClass[]
  publishAndAssign?: () => Promise<AssignmentSummary>
} = {}) {
  const publishAndAssign = vi.fn(over.publishAndAssign ?? (() => Promise.resolve({} as AssignmentSummary)))
  const setQuizPublished = vi.fn(() => Promise.resolve({} as QuizSummary))
  const onPublished = vi.fn()
  const onClose = vi.fn()
  render(
    <PublishDialog
      open onClose={onClose} quiz={{ id: 7, title: 'Simulare EN' }}
      quizClassId={over.quizClassId ?? null} classes={over.classes ?? classes}
      onPublished={onPublished} publishAndAssign={publishAndAssign} setQuizPublished={setQuizPublished}
    />,
  )
  return { publishAndAssign, setQuizPublished, onPublished, onClose }
}

describe('PublishDialog', () => {
  it('publishes and gives the homework in one step, with a deadline a week away pre-filled', async () => {
    const user = userEvent.setup()
    const { publishAndAssign, setQuizPublished, onPublished, onClose } = renderDialog()

    await user.selectOptions(screen.getByLabelText('Clasa'), '2')
    expect((screen.getByLabelText('Termen') as HTMLInputElement).value).toMatch(/T20:00$/)
    await user.click(screen.getByRole('button', { name: 'Publică și dă tema' }))

    expect(publishAndAssign).toHaveBeenCalledWith(7, expect.objectContaining({ schoolClassId: 2 }))
    expect(setQuizPublished).not.toHaveBeenCalled()
    expect(onPublished).toHaveBeenCalledWith({}) // the homework the server created, for the confirmation
    expect(onClose).toHaveBeenCalled()
  })

  it('only publishes when "Dă-l și ca temă" is unticked', async () => {
    const user = userEvent.setup()
    const { publishAndAssign, setQuizPublished } = renderDialog()

    await user.click(screen.getByLabelText('Dă-l și ca temă'))
    await user.click(screen.getByRole('button', { name: 'Publică' }))

    expect(setQuizPublished).toHaveBeenCalledWith(7, true)
    expect(publishAndAssign).not.toHaveBeenCalled()
  })

  it("fixes the class of a quiz reserved for one class", () => {
    renderDialog({ quizClassId: 1 })

    const select = screen.getByLabelText('Clasa') as HTMLSelectElement
    expect(select.value).toBe('1')
    expect(select).toBeDisabled()
  })

  it('waits for a class before giving homework', () => {
    renderDialog()

    expect(screen.getByRole('button', { name: 'Publică și dă tema' })).toBeDisabled()
  })

  it('refuses a deadline in the past without calling the server', async () => {
    const user = userEvent.setup()
    const { publishAndAssign } = renderDialog({ quizClassId: 1 })

    const due = screen.getByLabelText('Termen')
    await user.clear(due)
    await user.type(due, '2020-01-01T10:00')
    await user.click(screen.getByRole('button', { name: 'Publică și dă tema' }))

    expect(screen.getByRole('alert')).toHaveTextContent('Termenul trebuie să fie în viitor.')
    expect(publishAndAssign).not.toHaveBeenCalled()
  })

  it("keeps the dialog open with the server's reason when it refuses", async () => {
    const user = userEvent.setup()
    const { onClose } = renderDialog({
      quizClassId: 1,
      publishAndAssign: () => Promise.reject(new ApiError(409, 'Quiz-ul este deja dat ca temă acestei clase — modifică termenul')),
    })

    await user.click(screen.getByRole('button', { name: 'Publică și dă tema' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('deja dat ca temă')
    expect(onClose).not.toHaveBeenCalled()
  })

  it('only publishes when there is no class yet', () => {
    renderDialog({ classes: [] })

    expect(screen.getByLabelText('Dă-l și ca temă')).toBeDisabled()
    expect(screen.getByRole('button', { name: 'Publică' })).toBeEnabled()
  })
})
