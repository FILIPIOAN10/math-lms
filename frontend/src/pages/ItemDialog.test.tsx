import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { ItemDialog } from '@/pages/AdminQuizzesPage'
import type { ItemInput, QuizItemDto } from '@/lib/api'

const openItem: QuizItemDto = {
  id: 9, position: 2, type: 'OPEN', statement: 'Rezolvă ecuația', points: 10, solution: 'x = 2', options: [],
  hints: ['Mută termenii', 'Împarte la coeficient'],
}

function renderDialog(initial: QuizItemDto | null, onSubmit = vi.fn().mockResolvedValue(undefined)) {
  render(<ItemDialog open onClose={vi.fn()} initial={initial} onSubmit={onSubmit} />)
  return onSubmit
}

async function submitted(onSubmit: ReturnType<typeof vi.fn>): Promise<ItemInput> {
  await waitFor(() => expect(onSubmit).toHaveBeenCalled())
  return onSubmit.mock.calls[0][0]
}

describe('ItemDialog hints editor', () => {
  it('starts empty for a new item and adds hint boxes one at a time', async () => {
    renderDialog(null)
    expect(screen.queryByLabelText(/^Indiciul \d$/)).not.toBeInTheDocument()

    await userEvent.click(screen.getByRole('button', { name: 'Adaugă indiciu' }))
    await userEvent.click(screen.getByRole('button', { name: 'Adaugă indiciu' }))

    expect(screen.getByLabelText('Indiciul 1')).toBeInTheDocument()
    expect(screen.getByLabelText('Indiciul 2')).toBeInTheDocument()
  })

  it('loads the hints of an existing item, in order', () => {
    renderDialog(openItem)

    expect(screen.getByLabelText('Indiciul 1')).toHaveValue('Mută termenii')
    expect(screen.getByLabelText('Indiciul 2')).toHaveValue('Împarte la coeficient')
  })

  it('stops adding at five hints', async () => {
    renderDialog(null)
    const add = screen.getByRole('button', { name: 'Adaugă indiciu' })

    for (let i = 0; i < 5; i++) await userEvent.click(add)

    expect(screen.getAllByLabelText(/^Indiciul \d$/)).toHaveLength(5)
    expect(add).toBeDisabled()
  })

  it('removes a hint and renumbers the rest', async () => {
    renderDialog(openItem)

    await userEvent.click(screen.getByRole('button', { name: 'Șterge indiciul 1' }))

    expect(screen.getAllByLabelText(/^Indiciul \d$/)).toHaveLength(1)
    expect(screen.getByLabelText('Indiciul 1')).toHaveValue('Împarte la coeficient')
  })

  it('sends the hints trimmed and in order, dropping empty boxes', async () => {
    const onSubmit = renderDialog(openItem)
    await userEvent.click(screen.getByRole('button', { name: 'Adaugă indiciu' }))      // a third, left empty
    await userEvent.clear(screen.getByLabelText('Indiciul 1'))
    await userEvent.type(screen.getByLabelText('Indiciul 1'), '  Primul  ')

    await userEvent.click(screen.getByRole('button', { name: 'Salvează' }))

    expect((await submitted(onSubmit)).hints).toEqual(['Primul', 'Împarte la coeficient'])
  })

  it('sends no hints when none were written', async () => {
    const onSubmit = renderDialog({ ...openItem, hints: [] })

    await userEvent.click(screen.getByRole('button', { name: 'Salvează' }))

    expect((await submitted(onSubmit)).hints).toEqual([])
  })

  it('lets a multiple-choice item carry an explained solution too, not only an open one', async () => {
    const choice: QuizItemDto = {
      ...openItem, type: 'SINGLE_CHOICE', solution: 'Adunăm: 2+2=4', hints: [],
      options: [
        { id: 1, position: 0, text: '4', correct: true },
        { id: 2, position: 1, text: '5', correct: false },
      ],
    }
    const onSubmit = renderDialog(choice)

    expect(screen.getByLabelText('Rezolvare explicată (opțional)')).toHaveValue('Adunăm: 2+2=4')
    await userEvent.click(screen.getByRole('button', { name: 'Salvează' }))

    expect((await submitted(onSubmit)).solution).toBe('Adunăm: 2+2=4')
  })
})
