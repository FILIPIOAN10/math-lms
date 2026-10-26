import { fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { BaremDialog } from '@/components/BaremDialog'
import type { BaremProposal, QuizItemDto } from '@/lib/api'

const api = vi.hoisted(() => ({
  createBaremRead: vi.fn(),
  getBaremRead: vi.fn(),
  applyBarem: vi.fn(),
}))
vi.mock('@/lib/api', async (original) => ({ ...(await original<typeof import('@/lib/api')>()), ...api }))
vi.mock('@/lib/image', () => ({ shrinkImage: (f: File) => Promise.resolve(f) }))

const option = (id: number, position: number, text: string, correct: boolean) => ({ id, position, text, correct })
const items: QuizItemDto[] = [
  { id: 10, position: 0, type: 'SINGLE_CHOICE', statement: 'Cât face $6 - 6 : 2$?', points: 5, solution: null,
    options: [option(100, 0, '0', true), option(101, 1, '3', false), option(102, 2, '6', false)],
    hints: [], chapterId: null, section: 'SUBIECTUL I' },
  { id: 12, position: 1, type: 'OPEN', statement: 'Un teren', points: 5, solution: null, options: [], hints: [],
    chapterId: null, section: 'SUBIECTUL al III-lea', parts: [
      { id: 7, position: 0, label: 'a', statement: 'Perimetrul', points: 2, solution: null },
      { id: 8, position: 1, label: 'b', statement: 'Aria', points: 3, solution: 'vechi' },
    ] },
]
const proposal = (over: Partial<BaremProposal> = {}): BaremProposal => ({
  taken: false,
  unmatched: ['III.7'],
  items: [
    { itemId: 10, label: 'I.1', type: 'SINGLE_CHOICE', statement: 'Cât face $6 - 6 : 2$?', points: 5,
      currentLetter: 'a', proposedLetter: 'b', proposedOptionId: 101, currentBarem: null, proposedBarem: null,
      parts: [], warnings: [] },
    { itemId: 12, label: 'III.1', type: 'OPEN', statement: 'Un teren', points: 5, currentLetter: null,
      proposedLetter: null, proposedOptionId: null, currentBarem: null, proposedBarem: null, warnings: ['Litera b) nu se citește clar.'],
      parts: [
        { partId: 7, label: 'a', points: 2, statement: 'Perimetrul', currentBarem: null, proposedBarem: '$P = 130$ ... 2p' },
        { partId: 8, label: 'b', points: 3, statement: 'Aria', currentBarem: 'vechi', proposedBarem: '$A = 1000$ ... 3p' },
      ] },
  ],
  ...over,
})

async function readBarem() {
  render(<BaremDialog quizId={5} items={items} onClose={vi.fn()} onApplied={vi.fn()} pollMs={0} />)
  await userEvent.upload(screen.getByLabelText('Pozele cu baremul'), [new File(['x'], 'barem.jpg', { type: 'image/jpeg' })])
  await userEvent.click(screen.getByRole('button', { name: 'Citește baremul' }))
}

describe('BaremDialog', () => {
  beforeEach(() => {
    Object.values(api).forEach((fn) => fn.mockReset())
    api.createBaremRead.mockResolvedValue({ id: 3, status: 'QUEUED', error: null, proposal: null })
  })

  it('sends the photos, waits for the reading and shows each question with what changes', async () => {
    api.getBaremRead
      .mockResolvedValueOnce({ id: 3, status: 'RUNNING', error: null, proposal: null })
      .mockResolvedValue({ id: 3, status: 'DONE', error: null, proposal: proposal() })
    await readBarem()

    const rows = await screen.findAllByTestId('barem-row')
    expect(api.createBaremRead).toHaveBeenCalledWith(5, [expect.any(File)])
    expect(rows[0]).toHaveTextContent('I.1')
    expect(rows[0]).toHaveTextContent('Acum: a)')
    expect(within(rows[0]).getByRole('combobox', { name: 'Varianta corectă la I.1' })).toHaveValue('b')
    expect(within(rows[1]).getByRole('textbox', { name: 'Barem III.1 a)' })).toHaveValue('$P = 130$ ... 2p')
    expect(rows[1]).toHaveTextContent('Litera b) nu se citește clar.')
    expect(screen.getByText(/III\.7/)).toBeInTheDocument() // in the barem, not in the test
  })

  it('applies what the teacher kept, with their edits, and nothing they unticked', async () => {
    api.getBaremRead.mockResolvedValue({ id: 3, status: 'DONE', error: null, proposal: proposal() })
    api.applyBarem.mockResolvedValue(undefined)
    const onApplied = vi.fn()
    render(<BaremDialog quizId={5} items={items} onClose={vi.fn()} onApplied={onApplied} pollMs={0} />)
    await userEvent.upload(screen.getByLabelText('Pozele cu baremul'), [new File(['x'], 'b.jpg', { type: 'image/jpeg' })])
    await userEvent.click(screen.getByRole('button', { name: 'Citește baremul' }))
    await screen.findAllByTestId('barem-row')

    fireEvent.change(screen.getByRole('combobox', { name: 'Varianta corectă la I.1' }), { target: { value: 'c' } })
    fireEvent.change(screen.getByRole('textbox', { name: 'Barem III.1 b)' }), { target: { value: '$A = 1000 > 900$ ... 3p' } })
    await userEvent.click(screen.getByRole('button', { name: 'Aplică baremul (2)' }))

    await waitFor(() => expect(api.applyBarem).toHaveBeenCalledWith(5, [
      { itemId: 10, correctOptionId: 102 },
      { itemId: 12, parts: [{ partId: 7, barem: '$P = 130$ ... 2p' }, { partId: 8, barem: '$A = 1000 > 900$ ... 3p' }] },
    ]))
    expect(onApplied).toHaveBeenCalled()
  })

  it('leaves a question out when unticked', async () => {
    api.getBaremRead.mockResolvedValue({ id: 3, status: 'DONE', error: null, proposal: proposal() })
    api.applyBarem.mockResolvedValue(undefined)
    await readBarem()
    await screen.findAllByTestId('barem-row')

    await userEvent.click(screen.getByRole('checkbox', { name: 'Aplic baremul la I.1' }))
    await userEvent.click(screen.getByRole('button', { name: 'Aplică baremul (1)' }))

    await waitFor(() => expect(api.applyBarem).toHaveBeenCalledWith(5, [expect.objectContaining({ itemId: 12 })]))
  })

  it('does not offer to change the right answer of a test students already took', async () => {
    api.getBaremRead.mockResolvedValue({ id: 3, status: 'DONE', error: null, proposal: proposal({ taken: true }) })
    await readBarem()
    const rows = await screen.findAllByTestId('barem-row')

    expect(within(rows[0]).getByRole('combobox', { name: 'Varianta corectă la I.1' })).toBeDisabled()
    expect(rows[0]).toHaveTextContent('Testul a fost dat deja')
  })

  it('says why a reading failed and lets the teacher try other photos', async () => {
    api.getBaremRead.mockResolvedValue({ id: 3, status: 'FAILED', error: 'Nu am găsit în poze baremul acestui test.', proposal: null })
    await readBarem()

    expect(await screen.findByRole('alert')).toHaveTextContent('Nu am găsit în poze baremul')
    expect(screen.getByLabelText('Pozele cu baremul')).toBeInTheDocument()
  })
})
