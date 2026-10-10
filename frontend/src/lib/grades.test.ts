import { describe, expect, it } from 'vitest'
import { formatGrade, romanianGrade, scoreLine } from '@/lib/grades'

describe('romanianGrade (1 + 9 × procent)', () => {
  it('gives 1 for nothing, 10 for everything and 5.50 for half', () => {
    expect(romanianGrade(0, 10)).toBe(1)
    expect(romanianGrade(10, 10)).toBe(10)
    expect(romanianGrade(5, 10)).toBe(5.5)
  })

  it('rounds to two decimals', () => {
    expect(romanianGrade(8, 11)).toBe(7.55) // 7.5454...
    expect(romanianGrade(1, 3)).toBe(4) // 4.0
  })

  it('has no grade for a quiz worth no points', () => {
    expect(romanianGrade(0, 0)).toBeNull()
  })
})

describe('formatGrade', () => {
  it('writes a whole grade plainly and any other with two decimals and a comma', () => {
    expect(formatGrade(10)).toBe('10')
    expect(formatGrade(5.5)).toBe('5,50')
    expect(formatGrade(7.55)).toBe('7,55')
  })
})

describe('scoreLine', () => {
  it('reads "x / max p · nota n"', () => {
    expect(scoreLine(8, 11)).toBe('8 / 11 p · nota 7,55')
  })

  it('leaves the grade out when the quiz is worth nothing', () => {
    expect(scoreLine(0, 0)).toBe('0 / 0 p')
  })
})
