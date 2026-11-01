import { describe, expect, it } from 'vitest'
import { masteryLevel, MASTERY_LABELS, chaptersToRevise } from '@/lib/mastery'

describe('chaptersToRevise', () => {
  const chapter = (chapterTitle: string, percent: number | null) => ({ chapterTitle, percent })
  const en = (title: string, percent: number | null) => ({ title, percent })

  it('names the chapters under 50%, weakest first, from both maps', () => {
    expect(chaptersToRevise([chapter('Fracții', 40), chapter('Ecuații', 90)], [en('Numere întregi', 10)]))
      .toEqual(['Numere întregi', 'Fracții'])
  })

  it('leaves out a chapter with no answers yet: nothing says it is weak', () => {
    expect(chaptersToRevise([chapter('Geometrie', null)], [en('Arii', null)])).toEqual([])
  })

  it('names a chapter once when both maps have it', () => {
    expect(chaptersToRevise([chapter('Procente', 30)], [en('Procente', 20)])).toEqual(['Procente'])
  })

  it('stops at three by default', () => {
    expect(chaptersToRevise([chapter('a', 1), chapter('b', 2), chapter('c', 3), chapter('d', 4)], [])).toEqual(['a', 'b', 'c'])
  })
})

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
