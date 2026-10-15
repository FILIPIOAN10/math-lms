import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { ItemDialog } from '@/pages/AdminQuizzesPage'
import type { ItemInput, QuizItemDto } from '@/lib/api'

const openItem: QuizItemDto = {
  id: 9, position: 2, type: 'OPEN', statement: 'Rezolvă ecuația', points: 10, solution: 'x = 2', options: [],
  hints: ['Mută termenii', 'Împarte la coeficient'], chapterId: null,
}

function renderDialog(initial: QuizItemDto | null, onSubmit = vi.fn().mockResolvedValue(undefined)) {
  render(<ItemDialog open onClose={vi.fn()} initial={initial} onSubmit={onSubmit} />)
  return onSubmit
}

async function submitted(onSubmit: ReturnType<typeof vi.fn>): Promise<ItemInput> {
  await waitFor(() => expect(onSubmit).toHaveBeenCalled())
  return onSubmit.mock.calls[0][0]
}

describe('ItemDialog picture under the statement', () => {
  const png = () => new File([new Uint8Array([137, 80, 78, 71])], 'figura.png', { type: 'image/png' })

  it('takes a chosen file and hands it over with the item', async () => {
    const onSubmit = renderDialog({ ...openItem })
    await userEvent.upload(screen.getByLabelText('Fișierul imaginii'), png())

    expect(screen.getByRole('img', { name: 'Figura întrebării' })).toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: 'Salvează' }))

    await waitFor(() => expect(onSubmit).toHaveBeenCalled())
    expect(onSubmit.mock.calls[0][1]).toBeInstanceOf(File)
  })

  it('takes a screenshot pasted anywhere in the dialog, but leaves text pastes alone', async () => {
    const onSubmit = renderDialog({ ...openItem })
    const statement = screen.getByLabelText('Enunț')

    fireEvent.paste(statement, { clipboardData: { files: [png()], getData: () => '' } })
    expect(await screen.findByRole('img', { name: 'Figura întrebării' })).toBeInTheDocument()

    await userEvent.click(screen.getByRole('button', { name: 'Salvează' }))
    await waitFor(() => expect(onSubmit).toHaveBeenCalled())
    expect((onSubmit.mock.calls[0][1] as File).name).toBe('figura.png')
  })

  it('shows the stored picture and can take it away', async () => {
    const onSubmit = renderDialog({ ...openItem, image: 'abc.png' })

    expect(screen.getByRole('img', { name: 'Figura întrebării' })).toHaveAttribute('src', '/api/quiz/items/9/image?v=abc.png')
    await userEvent.click(screen.getByRole('button', { name: 'Scoate imaginea' }))
    expect(screen.queryByRole('img', { name: 'Figura întrebării' })).not.toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: 'Salvează' }))

    await waitFor(() => expect(onSubmit).toHaveBeenCalled())
    expect(onSubmit.mock.calls[0][1]).toBe('remove')
  })

  it('refuses a file that is not a picture', async () => {
    renderDialog({ ...openItem })
    await userEvent.upload(screen.getByLabelText('Fișierul imaginii'),
      new File(['x'], 'nota.txt', { type: 'text/plain' }), { applyAccept: false })

    expect(screen.getByText('Alege o imagine (PNG, JPG, WEBP sau GIF).')).toBeInTheDocument()
  })
})

describe('ItemDialog section', () => {
  it('sends the heading the question sits under, and nothing when left empty', async () => {
    const onSubmit = renderDialog({ ...openItem, section: 'SUBIECTUL al III-lea' })
    expect(screen.getByLabelText('Secțiune (opțional)')).toHaveValue('SUBIECTUL al III-lea')

    await userEvent.clear(screen.getByLabelText('Secțiune (opțional)'))
    await userEvent.click(screen.getByRole('button', { name: 'Salvează' }))

    expect((await submitted(onSubmit)).section).toBeNull()
  })

  it("starts a new question under the previous one's heading", () => {
    render(<ItemDialog open onClose={vi.fn()} initial={null} onSubmit={vi.fn()} defaultSection="SUBIECTUL al II-lea" />)
    expect(screen.getByLabelText('Secțiune (opțional)')).toHaveValue('SUBIECTUL al II-lea')
  })
})

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

describe('ItemDialog quick entry', () => {
  it('starts a new multiple-choice item with four options', () => {
    renderDialog(null)

    expect(screen.getAllByPlaceholderText(/^Varianta [a-z]\)$/)).toHaveLength(4)
  })

  it('moves to a new option on Enter in the last one instead of saving', async () => {
    const onSubmit = renderDialog(null)

    await userEvent.type(screen.getByPlaceholderText('Varianta d)'), 'ultima{Enter}')

    expect(screen.getAllByPlaceholderText(/^Varianta [a-z]\)$/)).toHaveLength(5)
    await waitFor(() => expect(screen.getByPlaceholderText('Varianta e)')).toHaveFocus())
    expect(onSubmit).not.toHaveBeenCalled()
  })

  it('saves and empties the form for the next item, keeping its type and points', async () => {
    const onClose = vi.fn()
    const onSubmit = vi.fn().mockResolvedValue(undefined)
    render(<ItemDialog open onClose={onClose} initial={null} onSubmit={onSubmit} />)
    await userEvent.selectOptions(screen.getByLabelText('Tip'), 'OPEN')
    await userEvent.type(screen.getByLabelText('Enunț'), 'Demonstrează')
    await userEvent.clear(screen.getByLabelText('Punctaj'))
    await userEvent.type(screen.getByLabelText('Punctaj'), '3')

    await userEvent.click(screen.getByRole('button', { name: 'Salvează și adaugă încă unul' }))

    expect((await submitted(onSubmit)).statement).toBe('Demonstrează')
    expect(screen.getByLabelText('Enunț')).toHaveValue('')
    expect(screen.getByLabelText('Tip')).toHaveValue('OPEN')
    expect(screen.getByLabelText('Punctaj')).toHaveValue(3)
    expect(screen.getByRole('status')).toHaveTextContent('1 întrebare salvată')
    expect(onClose).not.toHaveBeenCalled()
  })

  it('offers save-and-next only for a new item', () => {
    renderDialog(openItem)

    expect(screen.queryByRole('button', { name: 'Salvează și adaugă încă unul' })).not.toBeInTheDocument()
  })
})

describe('ItemDialog chapter', () => {
  const chapters = [
    { id: 1, title: 'Fracții', bookId: 1, bookTitle: 'Algebra', schoolClassId: 8, schoolClassName: 'Clasa a 8-a' },
    { id: 2, title: 'Ecuații', bookId: 1, bookTitle: 'Algebra', schoolClassId: 8, schoolClassName: 'Clasa a 8-a' },
  ]

  it("inherits the quiz's chapter unless another one is picked", async () => {
    const onSubmit = vi.fn().mockResolvedValue(undefined)
    render(<ItemDialog open onClose={vi.fn()} initial={null} onSubmit={onSubmit} chapters={chapters} quizChapterTitle="Fracții" />)

    const select = screen.getByLabelText('Capitol')
    expect(select).toHaveValue('')
    expect(screen.getByRole('option', { name: 'Ca la test (Fracții)' })).toBeInTheDocument()

    await userEvent.type(screen.getByLabelText('Enunț'), 'Rezolvă $x+1=2$')
    await userEvent.selectOptions(screen.getByLabelText('Tip'), 'OPEN')
    await userEvent.selectOptions(select, '2')
    await userEvent.click(screen.getByRole('button', { name: 'Salvează' }))

    expect(onSubmit).toHaveBeenCalledWith(expect.objectContaining({ chapterId: 2 }), 'keep')
  })

  it('has no chapter choice when no chapters are offered', () => {
    render(<ItemDialog open onClose={vi.fn()} initial={null} onSubmit={vi.fn()} />)
    expect(screen.queryByLabelText('Capitol')).not.toBeInTheDocument()
  })
})

describe('ItemDialog requirements a), b) (EN Subiectul III)', () => {
  it('splits an open problem into requirements with their own points; the problem is worth their sum', async () => {
    const onSubmit = renderDialog({ ...openItem, hints: [] })

    await userEvent.click(screen.getByRole('button', { name: 'Adaugă cerința a)' }))
    await userEvent.type(screen.getByLabelText('Cerința a)'), 'Arată că x = 25.')
    await userEvent.click(screen.getByRole('button', { name: 'Adaugă cerința b)' }))
    await userEvent.type(screen.getByLabelText('Cerința b)'), 'Calculează x la pătrat.')
    await userEvent.type(screen.getByLabelText('Barem cerința b)'), '625')

    expect(screen.getByLabelText('Punctaj')).toHaveValue(5) // 2 + 3, filled in
    expect(screen.getByLabelText('Punctaj')).toBeDisabled()
    await userEvent.click(screen.getByRole('button', { name: 'Salvează' }))

    const input = await submitted(onSubmit)
    expect(input.points).toBe(5)
    expect(input.parts).toEqual([
      { statement: 'Arată că x = 25.', points: 2, solution: null, enChapterId: null },
      { statement: 'Calculează x la pătrat.', points: 3, solution: '625', enChapterId: null },
    ])
  })

  it('shows the requirements a stored problem has, and a grilă never has any', async () => {
    renderDialog({
      ...openItem,
      points: 5,
      parts: [
        { id: 1, position: 0, label: 'a', statement: 'Arată că x = 25.', points: 2, solution: null },
        { id: 2, position: 1, label: 'b', statement: 'Calculează.', points: 3, solution: '-1' },
      ],
    })

    expect(screen.getByLabelText('Cerința a)')).toHaveValue('Arată că x = 25.')
    expect(screen.getByLabelText('Barem cerința b)')).toHaveValue('-1')
    expect(screen.getByRole('button', { name: 'Adaugă cerința c)' })).toBeInTheDocument()
  })
})

describe('ItemDialog EN chapter', () => {
  const enChapters = [
    { id: 1, code: 'A1', title: 'Ordinea efectuării operațiilor', area: 'ALGEBRA' as const, position: 1, parentId: null, bookPage: null, photoCount: 1, exampleCount: 0 },
    { id: 19, code: 'G8', title: 'Corpuri geometrice', area: 'GEOMETRY' as const, position: 19, parentId: null, bookPage: null, photoCount: 0, exampleCount: 0 },
    { id: 20, code: 'G9', title: 'Drepte și plane', area: 'GEOMETRY' as const, position: 20, parentId: null, bookPage: null, photoCount: 0, exampleCount: 0 },
  ]

  it('says where "automatic" sends the student, and sends a chapter picked for the question and a requirement', async () => {
    const onSubmit = vi.fn().mockResolvedValue(undefined)
    const cube: QuizItemDto = {
      ...openItem, section: 'SUBIECTUL al III-lea', enChapterId: null, enChapterAuto: 20,
      parts: [
        { id: 1, position: 0, label: 'a', statement: 'Volumul', points: 2, solution: null, enChapterId: null, enChapterAuto: 19 },
        { id: 2, position: 1, label: 'b', statement: 'FO ⊥ DE', points: 3, solution: null, enChapterId: null, enChapterAuto: 20 },
      ],
    }
    render(<ItemDialog open onClose={vi.fn()} initial={cube} onSubmit={onSubmit} enChapters={enChapters} />)

    const select = screen.getByLabelText('Capitol EN (unde ajunge elevul dacă greșește)')
    expect(select).toHaveDisplayValue('Automat: G9 Drepte și plane')
    expect(screen.getByLabelText('Capitol EN cerința a)')).toHaveDisplayValue('Automat: G8 Corpuri geometrice')

    await userEvent.selectOptions(select, '1')
    await userEvent.selectOptions(screen.getByLabelText('Capitol EN cerința b)'), '19')
    await userEvent.click(screen.getByRole('button', { name: 'Salvează' }))

    const input = await submitted(onSubmit)
    expect(input.enChapterId).toBe(1)
    expect(input.parts?.map((p) => p.enChapterId)).toEqual([null, 19])
  })

  it('shows no EN choice without the chapter list', () => {
    renderDialog({ ...openItem })
    expect(screen.queryByLabelText('Capitol EN (unde ajunge elevul dacă greșește)')).not.toBeInTheDocument()
  })
})

