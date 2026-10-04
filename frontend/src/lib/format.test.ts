import { describe, expect, it } from 'vitest'
import { formatMinutes } from './format'

describe('formatMinutes', () => {
  it('uses the singular for exactly one minute', () => {
    expect(formatMinutes(1)).toBe('1 minut')
  })

  it('uses the plural otherwise', () => {
    expect(formatMinutes(2)).toBe('2 minute')
    expect(formatMinutes(45)).toBe('45 minute')
    expect(formatMinutes(600)).toBe('600 minute')
  })
})
