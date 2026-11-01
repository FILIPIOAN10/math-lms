import { describe, expect, it } from 'vitest'
import { commonMistakes } from '@/lib/quizStats'
import type { OptionStat, QuizItemStat } from '@/lib/api'

const option = (id: number, chosen: number, correct = false): OptionStat => ({
  id, letter: 'b', text: 'x', correct, chosen, mistake: null,
})

const item = (options: OptionStat[]) => ({ options } as QuizItemStat)

describe('commonMistakes', () => {
  it('lists the wrong options most students chose, most chosen first', () => {
    const numbered = [
      { label: '1', item: item([option(1, 9, true), option(2, 3)]) },
      { label: '2', item: item([option(3, 2, true), option(4, 8)]) },
    ]

    expect(commonMistakes(numbered, 12).map((m) => [m.label, m.option.id])).toEqual([['2', 4], ['1', 2]])
  })

  it('leaves out the right answer, a single slip and what a small share chose', () => {
    const numbered = [{ label: '1', item: item([option(1, 20, true), option(2, 1), option(3, 3)]) }]

    expect(commonMistakes(numbered, 24)).toEqual([])
  })

  it('shows at most three', () => {
    const numbered = [{ label: '1', item: item([option(1, 5), option(2, 5), option(3, 5), option(4, 5)]) }]

    expect(commonMistakes(numbered, 20)).toHaveLength(3)
  })

  it('has nothing to say about an open question', () => {
    expect(commonMistakes([{ label: '1', item: item([]) }], 10)).toEqual([])
  })
})
