import { describe, expect, it } from 'vitest'
import { barPercent, formatGain, formatGrade, formatRange } from './enPrediction'

describe('EN estimate', () => {
  it('writes grades with a comma and two decimals', () => {
    expect(formatGrade(7.4)).toBe('7,40')
    expect(formatGrade(10)).toBe('10,00')
  })

  it('writes the range, or one grade when both ends meet', () => {
    expect(formatRange(7.4, 8.1)).toBe('7,40 - 8,10')
    expect(formatRange(10, 10)).toBe('10,00')
  })

  it('writes a gain with its sign and one decimal', () => {
    expect(formatGain(0.6)).toBe('+0,6')
  })

  it('places 1 at the start of the bar, 10 at the end and 5,5 in the middle', () => {
    expect(barPercent(1)).toBe(0)
    expect(barPercent(10)).toBe(100)
    expect(barPercent(5.5)).toBe(50)
    expect(barPercent(12)).toBe(100)
  })
})
