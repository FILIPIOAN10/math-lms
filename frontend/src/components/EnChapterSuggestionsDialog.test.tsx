import { StrictMode } from 'react'
import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { EnChapterSuggestionsDialog } from '@/components/EnChapterSuggestionsDialog'
import type { EnChapterSummary, QuizItemDto } from '@/lib/api'

const api = vi.hoisted(() => ({
  suggestEnChapters: vi.fn(),
  assignEnChapters: vi.fn(),
}))
vi.mock('@/lib/api', async (original) => ({ ...(await original<typeof import('@/lib/api')>()), ...api }))

const chapter = (id: number, code: string, title: string): EnChapterSummary => ({
  id, code, title, area: code.startsWith('A') ? 'ALGEBRA' : 'GEOMETRY', position: id, photoCount: 0, exampleCount: 0,
})
const enChapters = [chapter(1, 'A1', 'Ordinea operațiilor'), chapter(2, 'A2', 'Proporții'), chapter(3, 'A3', 'Procente'),
  chapter(19, 'G8', 'Corpuri'), chapter(20, 'G9', 'Drepte și plane')]

const items: QuizItemDto[] = [
  { id: 10, position: 0, type: 'SINGLE_CHOICE', statement: '$36 : 4 - 4 \\cdot 2$', points: 5, solution: null, options: [],
    hints: [], chapterId: null, section: 'SUBIECTUL I', enChapterId: null, enChapterAuto: 1 },
  { id: 11, position: 1, type: 'SINGLE_CHOICE', statement: '40% din 250', points: 5, solution: null, options: [],
    hints: [], chapterId: null, section: 'SUBIECTUL I', enChapterId: null, enChapterAuto: 2 },
  { id: 12, position: 2, type: 'OPEN', statement: 'Cubul', points: 5, solution: null, options: [], hints: [],
    chapterId: null, section: 'SUBIECTUL al III-lea', enChapterId: 20, enChapterAuto: 20,
    parts: [
      { id: 7, position: 0, label: 'a', statement: 'Volumul', points: 2, solution: null, enChapterId: null, enChapterAuto: 19 },
      { id: 8, position: 1, label: 'b', statement: 'Distanța', points: 3, solution: null, enChapterId: null, enChapterAuto: 20 },
    ] },
]

describe('EnChapterSuggestionsDialog', () => {
  beforeEach(() => Object.values(api).forEach((fn) => fn.mockReset()))

  it('ticks the proposals that change something and applies only the ones kept', async () => {
    api.suggestEnChapters.mockResolvedValue([
      { itemId: 10, chapterId: 1, code: 'A1', reason: 'Un calcul.', parts: [] },          // same as now
      { itemId: 11, chapterId: 3, code: 'A3', reason: 'Apare un procent.', parts: [] },   // A2 → A3
      { itemId: 12, chapterId: 19, code: 'G8', reason: 'Un cub.', parts: [
        { partId: 7, chapterId: 19, code: 'G8', reason: 'Volumul.' },                     // same as automatic
        { partId: 8, chapterId: 19, code: 'G8', reason: 'Tot cubul.' },                   // G9 → G8
      ] },
    ])
    api.assignEnChapters.mockResolvedValue(undefined)
    const onApplied = vi.fn()
    render(<EnChapterSuggestionsDialog quizId={5} items={items} enChapters={enChapters} onClose={vi.fn()} onApplied={onApplied} />)

    const rows = await screen.findAllByTestId('en-suggestion')
    expect(rows.map((r) => r.querySelector('p')?.textContent)).toEqual(['I.1', 'I.2', 'III.1 a)', 'III.1 b)'])
    expect(screen.getByText('Apare un procent.')).toBeInTheDocument()
    expect(screen.getByLabelText('Păstrez propunerea pentru I.1')).toBeDisabled() // nothing to change
    expect(screen.getByLabelText('Păstrez propunerea pentru I.2')).toBeChecked()
    expect(screen.getByLabelText('Păstrez propunerea pentru III.1 b)')).toBeChecked()

    fireEvent.click(screen.getByLabelText('Păstrez propunerea pentru III.1 b)')) // the teacher keeps G9 for b)
    fireEvent.click(screen.getByRole('button', { name: 'Aplică (1)' }))

    await waitFor(() => expect(api.assignEnChapters).toHaveBeenCalledWith(5, [{ itemId: 11, enChapterId: 3, parts: [] }]))
    expect(onApplied).toHaveBeenCalled()
  })

  it('asks the AI once per opening, even when StrictMode runs the effect twice', async () => {
    api.suggestEnChapters.mockResolvedValue([{ itemId: 11, chapterId: 3, code: 'A3', reason: 'Un procent.', parts: [] }])
    render(
      <StrictMode>
        <EnChapterSuggestionsDialog quizId={5} items={items} enChapters={enChapters} onClose={vi.fn()} onApplied={vi.fn()} />
      </StrictMode>,
    )

    expect(await screen.findByText('Un procent.')).toBeInTheDocument()
    expect(api.suggestEnChapters).toHaveBeenCalledTimes(1)
  })

  it('says why the AI could not answer', async () => {
    api.suggestEnChapters.mockRejectedValue(Object.assign(new Error('x'), { status: 503, body: 'OpenAI nu răspunde acum.' }))
    render(<EnChapterSuggestionsDialog quizId={5} items={items} enChapters={enChapters} onClose={vi.fn()} onApplied={vi.fn()} />)

    expect(await screen.findByRole('alert')).toHaveTextContent('OpenAI nu răspunde acum.')
  })
})
