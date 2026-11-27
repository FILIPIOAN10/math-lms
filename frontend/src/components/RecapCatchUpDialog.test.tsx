import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { RecapCatchUpDialog } from '@/components/RecapCatchUpDialog'
import { ApiError, type EnClassMastery } from '@/lib/api'

const grid: EnClassMastery = {
  chapters: [{ id: 12, code: 'A3', title: 'Procente', area: 'ALGEBRA' }],
  students: [
    { studentId: 1, fullName: 'Ana Pop', cells: [{ chapterId: 12, percent: 30, questions: 3 }] },
    { studentId: 2, fullName: 'Bob Rus', cells: [{ chapterId: 12, percent: 90, questions: 3 }] },
  ],
}

function renderDialog(approved = 5, create = () => Promise.resolve({ quizId: 7, assignmentId: 8, exerciseCount: 3 })) {
  const createSpy = vi.fn(create)
  const onCreated = vi.fn()
  render(<RecapCatchUpDialog grid={grid} column={0} classId={4} approved={approved} onClose={() => {}}
                             onCreated={onCreated} createRecapCatchUp={createSpy} />)
  return { createSpy, onCreated }
}

describe('RecapCatchUpDialog', () => {
  it('ticks the weak students and gives them exercises of the chapter from the collection', async () => {
    const user = userEvent.setup()
    const { createSpy, onCreated } = renderDialog()

    expect(screen.getByRole('heading', { name: 'Temă din culegere — A3 Procente' })).toBeInTheDocument()
    expect(screen.getByLabelText('Ana Pop (30%)')).toBeChecked()
    expect(screen.getByLabelText('Bob Rus (90%)')).not.toBeChecked()
    expect(screen.getByLabelText('Câte exerciții')).toHaveValue(3)

    await user.click(screen.getByRole('button', { name: 'Dă tema (1 elev)' }))

    expect(createSpy).toHaveBeenCalledWith(expect.objectContaining({
      schoolClassId: 4, enChapterId: 12, studentIds: [1], count: 3,
    }))
    expect(onCreated).toHaveBeenCalledWith(expect.stringContaining('3 exerciții'))
  })

  it('says so and gives nothing when the chapter has no approved exercise', () => {
    renderDialog(0)

    expect(screen.getByText(/Niciun exercițiu aprobat/)).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Dă tema (1 elev)' })).toBeDisabled()
  })

  it("shows the server's refusal", async () => {
    const user = userEvent.setup()
    renderDialog(5, () => Promise.reject(new ApiError(409, 'Unii dintre elevii aleși nu sunt în clasă', new Headers())))

    await user.click(screen.getByRole('button', { name: 'Dă tema (1 elev)' }))

    expect(await screen.findByText('Unii dintre elevii aleși nu sunt în clasă')).toBeInTheDocument()
  })
})
