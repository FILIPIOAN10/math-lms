import { describe, expect, it } from 'vitest'
import { type ItemResultDto } from '@/lib/api'
import { mistakesOf, parseOnly, practiceMistakesHref } from '@/lib/mistakes'

const choice = (itemId: number, correct: boolean | null, section: string | null = null): ItemResultDto => ({
  itemId, position: itemId, type: 'SINGLE_CHOICE', statement: 's', points: 5, awardedPoints: correct ? 5 : 0, correct,
  selectedOptionText: correct === null ? null : 'x', correctOptionText: 'y', barem: null, photoUploaded: false,
  hintsUsed: 0, hintsAvailable: 0, teacherComment: null, section,
})
const open = (itemId: number, awarded: number | null, section: string | null = null): ItemResultDto => ({
  ...choice(itemId, null, section), type: 'OPEN', points: 10, awardedPoints: awarded, correct: null,
})

describe('mistakes', () => {
  it('counts a wrong or empty grilă and an open question short of full points, in the numbers of the screen', () => {
    const m = mistakesOf([
      choice(1, true, 'SUBIECTUL I'), choice(2, false, 'SUBIECTUL I'), choice(3, null, 'SUBIECTUL I'),
      open(4, 10, 'SUBIECTUL al II-lea'), open(5, 6, 'SUBIECTUL al II-lea'),
    ])

    expect(m.labels).toEqual(['I.2', 'I.3', 'II.2'])
    expect(m.itemIds).toEqual([2, 3, 5])
    expect(m.wrongChoices).toBe(1) // the empty one has no answer to bring back in the review
  })

  it('finds nothing on a perfect paper', () => {
    expect(mistakesOf([choice(1, true), open(2, 10)]).labels).toEqual([])
  })

  it('builds the practice address and reads it back, ignoring junk', () => {
    expect(practiceMistakesHref(7, [2, 3])).toBe('/quizzes/7/take?mode=practice&only=2,3')
    expect(parseOnly('2,3')).toEqual(new Set([2, 3]))
    expect(parseOnly('abc,-1,')).toBeNull()
    expect(parseOnly(null)).toBeNull()
  })
})
