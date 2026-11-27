import { describe, expect, it } from 'vitest'
import type { RecapExercise } from '@/lib/api'
import { recapMatches, recapStatusLabel, weakEnStudentIds } from '@/lib/recap'

const base: RecapExercise = {
  id: 1, key: 'T8-19', tema: 8, number: 19, statement: 'Pătratul $ABCD$ are latura 7 cm.', solution: null,
  answer: '7', result: '7', aiStatus: 'AGREE', verified: true, hasImage: true, figureMissing: false, enChapterId: 5,
  enChapterCode: 'G5', enChapterTitle: 'Patrulatere', review: 'DRAFT', usedIn: 0,
  parts: [{ label: 'a', statement: 'Aflați distanța.', solution: null, answer: '7', result: '7', verified: true }],
}

describe('recapMatches', () => {
  const all = { review: '' as const, chapter: '', ai: '' as const, search: '' }

  it('filters by review state, chapter, AI check and words', () => {
    expect(recapMatches(base, { ...all, review: 'DRAFT' })).toBe(true)
    expect(recapMatches(base, { ...all, review: 'APPROVED' })).toBe(false)
    expect(recapMatches(base, { ...all, chapter: '5' })).toBe(true)
    expect(recapMatches(base, { ...all, chapter: '6' })).toBe(false)
    expect(recapMatches(base, { ...all, ai: 'agree' })).toBe(true)
    expect(recapMatches(base, { ...all, ai: 'check' })).toBe(false)
    expect(recapMatches({ ...base, aiStatus: 'PARTIAL' }, { ...all, ai: 'check' })).toBe(true)
  })

  it('finds by key or by words of the statement and requirements, ignoring case and diacritics', () => {
    expect(recapMatches(base, { ...all, search: 't8-19' })).toBe(true)
    expect(recapMatches(base, { ...all, search: 'patratul' })).toBe(true)
    expect(recapMatches(base, { ...all, search: 'distanța' })).toBe(true)
    expect(recapMatches(base, { ...all, search: 'cerc' })).toBe(false)
  })
})

describe('recapStatusLabel', () => {
  it('says AI-checked only when both runs agreed', () => {
    expect(recapStatusLabel(base)).toEqual({ label: 'Verificat de AI', tone: 'success' })
    expect(recapStatusLabel({ ...base, aiStatus: 'PROOF' }).label).toBe('Demonstrație: verifică tu')
    expect(recapStatusLabel({ ...base, aiStatus: 'PARTIAL' }).tone).toBe('warning')
    expect(recapStatusLabel({ ...base, aiStatus: 'SOMETHING' }).label).toBe('De verificat')
  })
})

describe('weakEnStudentIds', () => {
  it('ticks the students below 50% in that EN column, not those without data', () => {
    const grid = {
      chapters: [{ id: 5, code: 'G5', title: 'Patrulatere', area: 'GEOMETRY' as const }],
      students: [
        { studentId: 1, fullName: 'Ana', cells: [{ chapterId: 5, percent: 30, questions: 2 }] },
        { studentId: 2, fullName: 'Bob', cells: [{ chapterId: 5, percent: 80, questions: 2 }] },
        { studentId: 3, fullName: 'Cris', cells: [{ chapterId: 5, percent: null, questions: 0 }] },
      ],
    }
    expect(weakEnStudentIds(grid, 0)).toEqual([1])
  })
})
