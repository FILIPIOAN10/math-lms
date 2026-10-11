import { describe, expect, it } from 'vitest'
import { masteryLevel, MASTERY_LABELS } from '@/lib/mastery'

describe('masteryLevel', () => {
  it('is mastered from 80%, in progress from 50%, to review below that, and unknown without data', () => {
    expect(masteryLevel(100)).toBe('high')
    expect(masteryLevel(80)).toBe('high')
    expect(masteryLevel(79)).toBe('mid')
    expect(masteryLevel(50)).toBe('mid')
    expect(masteryLevel(49)).toBe('low')
    expect(masteryLevel(0)).toBe('low')
    expect(masteryLevel(null)).toBe('none')
  })

  it('has a word for every level, so colour is never the only signal', () => {
    expect(MASTERY_LABELS).toEqual({
      high: 'Stăpânit', mid: 'În lucru', low: 'De recapitulat', none: 'Fără date',
    })
  })
})
