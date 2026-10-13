import { describe, expect, it } from 'vitest'
import { revisionLinks } from '@/lib/enChapters'
import type { EnChapterLink, GradedPart, ItemResultDto } from '@/lib/api'

const A1: EnChapterLink = { id: 1, code: 'A1', title: 'Ordinea efectuării operațiilor', ready: true }
const G8: EnChapterLink = { id: 19, code: 'G8', title: 'Corpuri geometrice: arii și volume', ready: true }
const G9: EnChapterLink = { id: 20, code: 'G9', title: 'Drepte și plane', ready: true }

const grila = (over: Partial<ItemResultDto> = {}): ItemResultDto => ({
  itemId: 1, position: 0, type: 'SINGLE_CHOICE', statement: '36 : 4 - 4 · 2', points: 5, awardedPoints: 0, correct: false,
  selectedOptionText: '17', correctOptionText: '1', barem: null, photoUploaded: false, photoIds: [], hintsUsed: 0,
  hintsAvailable: 0, teacherComment: null, enChapter: A1, ...over,
})

const part = (label: string, points: number, awardedPoints: number | null, enChapter: EnChapterLink): GradedPart => ({
  id: label.charCodeAt(0), label, statement: label, points, barem: null, awardedPoints, enChapter,
})

describe('revisionLinks', () => {
  it('sends a wrong or unanswered grilă to its chapter, and a right one nowhere', () => {
    expect(revisionLinks(grila())).toEqual([{ chapter: A1, parts: [] }])
    expect(revisionLinks(grila({ correct: null, selectedOptionText: null }))).toEqual([{ chapter: A1, parts: [] }])
    expect(revisionLinks(grila({ correct: true, awardedPoints: 5 }))).toEqual([])
  })

  it('only offers a page that has something on it', () => {
    expect(revisionLinks(grila({ enChapter: { ...A1, ready: false } }))).toEqual([])
    expect(revisionLinks(grila({ enChapter: null }))).toEqual([])
  })

  it('sends an open answer that lost points, once the teacher has graded it', () => {
    const open = grila({ type: 'OPEN', correct: null, points: 5 })
    expect(revisionLinks({ ...open, awardedPoints: 3 })).toEqual([{ chapter: A1, parts: [] }])
    expect(revisionLinks({ ...open, awardedPoints: 5 })).toEqual([])
    expect(revisionLinks({ ...open, awardedPoints: null })).toEqual([])
  })

  it('goes requirement by requirement, one button per chapter', () => {
    const cube = grila({
      type: 'OPEN', correct: null, points: 5, awardedPoints: 2,
      parts: [part('a', 2, 2, G8), part('b', 3, 0, G9)],
    })
    expect(revisionLinks(cube)).toEqual([{ chapter: G9, parts: ['b'] }])

    const both = grila({
      type: 'OPEN', correct: null, points: 5, awardedPoints: 1,
      parts: [part('a', 2, 1, G9), part('b', 3, 0, G9), part('c', 1, null, G8)],
    })
    expect(revisionLinks(both)).toEqual([{ chapter: G9, parts: ['a', 'b'] }])
  })

  it('waits until every requirement of a problem is graded', () => {
    const halfGraded = grila({
      type: 'OPEN', correct: null, points: 5, awardedPoints: null, // the autosave kept a) only
      parts: [part('a', 2, 0, G8), part('b', 3, null, G9)],
    })
    expect(revisionLinks(halfGraded)).toEqual([])
  })
})
