import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { ChapterQuizDialog } from '@/components/ChapterQuizDialog'
import { ApiError, type Chapter, type ChapterQuizInput, type Exercise, type QuizSummary } from '@/lib/api'

const chapter = { id: 30, bookId: 2, title: 'Fracții', description: null } as Chapter
const exercise = (id: number, statement: string): Exercise =>
  ({ id, chapterId: 30, statement, solution: null, difficulty: null, version: 0 })
const exercises = [exercise(1, 'Calculează 1/2 + 1/4'), exercise(2, 'Simplifică 6/8'), exercise(3, 'Compară 2/3 cu 3/4')]
const draft = { id: 77, title: 'Fracții — test' } as QuizSummary

function renderDialog(
  create: (chapterId: number, input: ChapterQuizInput) => Promise<QuizSummary> = () => Promise.resolve(draft),
) {
  const createSpy = vi.fn(create)
  const onCreated = vi.fn()
  render(
    <ChapterQuizDialog open onClose={() => {}} chapter={chapter} exercises={exercises}
      onCreated={onCreated} createQuizFromChapter={createSpy} />,
  )
  return { createSpy, onCreated }
}

describe('ChapterQuizDialog', () => {
  it('starts with every exercise ticked and a title from the chapter', () => {
    renderDialog()

    expect(screen.getByLabelText('Titlu')).toHaveValue('Fracții — test')
    expect(screen.getAllByRole('checkbox').every((c) => (c as HTMLInputElement).checked)).toBe(true)
    expect(screen.getByRole('button', { name: 'Creează quiz (3)' })).toBeEnabled()
  })

  it('creates the quiz from the ticked exercises, in the chapter order, and hands it on', async () => {
    const user = userEvent.setup()
    const { createSpy, onCreated } = renderDialog()

    await user.click(screen.getByLabelText('Exercițiul 2'))
    const points = screen.getByLabelText('Puncte pe subiect')
    await user.clear(points)
    await user.type(points, '3')
    await user.click(screen.getByRole('button', { name: 'Creează quiz (2)' }))

    expect(createSpy).toHaveBeenCalledWith(30, { title: 'Fracții — test', exerciseIds: [1, 3], points: 3 })
    expect(onCreated).toHaveBeenCalledWith(draft)
  })

  it('cannot create a quiz with nothing ticked', async () => {
    const user = userEvent.setup()
    renderDialog()

    await user.click(screen.getByRole('button', { name: 'Deselectează tot' }))

    expect(screen.getByRole('button', { name: 'Creează quiz (0)' })).toBeDisabled()
  })

  it("shows the server's reason when creating fails", async () => {
    const user = userEvent.setup()
    const { onCreated } = renderDialog(() =>
      Promise.reject(new ApiError(400, 'Alege doar exerciții din capitolul „Fracții”.')))

    await user.click(screen.getByRole('button', { name: 'Creează quiz (3)' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('Alege doar exerciții din capitolul')
    expect(onCreated).not.toHaveBeenCalled()
  })
})
