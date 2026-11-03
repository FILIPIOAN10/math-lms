import { describe, expect, it } from 'vitest'
import { baremLessons, revisionLinks, revisionSentence, enPageForStudents } from '@/lib/enChapters'
import type { EnChapterLink, GradedPart, ItemResultDto } from '@/lib/api'

const A1: EnChapterLink = { id: 1, code: 'A1', title: 'Ordinea efectuării operațiilor', ready: true, revisionTopic: null }
const G8: EnChapterLink = { id: 19, code: 'G8', title: 'Corpuri geometrice: arii și volume', ready: true, revisionTopic: null }
const G9: EnChapterLink = { id: 20, code: 'G9', title: 'Drepte și plane', ready: true, revisionTopic: null }

const grila = (over: Partial<ItemResultDto> = {}): ItemResultDto => ({
  itemId: 1, position: 0, type: 'SINGLE_CHOICE', statement: '36 : 4 - 4 · 2', points: 5, awardedPoints: 0, correct: false,
  selectedOptionText: '17', correctOptionText: '1', barem: null, photoUploaded: false, photoIds: [], hintsUsed: 0,
  hintsAvailable: 0, teacherComment: null, enChapter: A1, ...over,
})

const part = (label: string, points: number, awardedPoints: number | null, enChapter: EnChapterLink): GradedPart => ({
  id: label.charCodeAt(0), label, statement: label, points, barem: null, awardedPoints, enChapter,
})

describe('enPageForStudents', () => {
  it('names what there is to read, and leaves out what there is not', () => {
    expect(enPageForStudents({ photoCount: 2, exampleCount: 3 })).toBe('2 pagini de teorie · 3 modele rezolvate')
    expect(enPageForStudents({ photoCount: 0, exampleCount: 5 })).toBe('5 modele rezolvate')
    expect(enPageForStudents({ photoCount: 1, exampleCount: 0 })).toBe('1 pagină de teorie')
  })
})

describe('revisionSentence', () => {
  const G1: EnChapterLink = { id: 16, code: 'G1', title: 'Segmente', ready: true, revisionTopic: 'simetrie și segmente' }

  it('says what to revise', () => {
    expect(revisionSentence(G1)).toBe('Dacă vrei să recapitulezi noțiunile despre simetrie și segmente:')
  })

  it('says which requirements it is about', () => {
    expect(revisionSentence(G1, ['a', 'b'])).toBe('La a), b), dacă vrei să recapitulezi noțiunile despre simetrie și segmente:')
  })

  it('stands without a phrase, on a page a teacher added and did not describe', () => {
    expect(revisionSentence({ ...G1, revisionTopic: null })).toBe('Dacă vrei să recapitulezi noțiunile din acest capitol:')
  })
})

describe('baremLessons', () => {
  const barem = { correct: null, correctOptionId: null, solution: 'Barem' }

  it('gives each requirement its lesson, and requirements sharing one share it', () => {
    expect(baremLessons({ ...barem, partLessons: [{ label: 'a', enChapter: G8 }, { label: 'b', enChapter: G9 }] }))
      .toEqual([{ chapter: G8, parts: ['a'] }, { chapter: G9, parts: ['b'] }])
    expect(baremLessons({ ...barem, partLessons: [{ label: 'a', enChapter: G9 }, { label: 'b', enChapter: G9 }] }))
      .toEqual([{ chapter: G9, parts: ['a', 'b'] }])
  })

  it("uses the problem's own lesson when it has no requirements", () => {
    expect(baremLessons({ ...barem, enChapter: A1, partLessons: [] })).toEqual([{ chapter: A1, parts: [] }])
  })

  it('leaves out a lesson with nothing on its page', () => {
    expect(baremLessons({ ...barem, partLessons: [{ label: 'a', enChapter: { ...G8, ready: false } }, { label: 'b', enChapter: null }] }))
      .toEqual([])
    expect(baremLessons(barem)).toEqual([])
  })
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
