import { describe, expect, it } from 'vitest'
import { weakSpotReason } from './weakSpots'
import { type WeakSpot } from './api'

const spot = (over: Partial<WeakSpot> = {}): WeakSpot => ({
  chapterId: 5, code: 'A3', title: 'Fracții', percent: 33, questions: 3, recentWrong: 0, recentAnswered: 0,
  sureWrong: 0, lastAnsweredAt: '2026-11-10T10:00:00Z', lessonChapterId: 5, lessonSectionId: null, ready: true, ...over,
})
const NOW = new Date('2026-11-14T10:00:00Z')

describe('weakSpotReason', () => {
  it('says the level and what it rests on', () => {
    expect(weakSpotReason(spot(), NOW)).toBe('Ai 33% din puncte, din 3 întrebări.')
  })

  it('adds the mistakes of the last two weeks', () => {
    expect(weakSpotReason(spot({ recentWrong: 4, recentAnswered: 6 }), NOW))
      .toBe('Ai 33% din puncte, din 3 întrebări. În ultimele 2 săptămâni ai greșit 4 din 6.')
  })

  it('names the confident mistakes, which point at a misunderstanding', () => {
    expect(weakSpotReason(spot({ sureWrong: 2 }), NOW))
      .toBe('Ai 33% din puncte, din 3 întrebări. De 2 ori ai zis „Știu sigur” și era greșit.')
    expect(weakSpotReason(spot({ sureWrong: 1 }), NOW))
      .toBe('Ai 33% din puncte, din 3 întrebări. O dată ai zis „Știu sigur” și era greșit.')
  })

  it('says when it was not worked on for a month or more', () => {
    expect(weakSpotReason(spot({ lastAnsweredAt: '2026-10-01T10:00:00Z' }), NOW))
      .toBe('Ai 33% din puncte, din 3 întrebări. Nu ai mai lucrat la el de 44 de zile.')
  })
})
