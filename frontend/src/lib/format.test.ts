import { describe, expect, it } from 'vitest'
import { formatMinutes, optionLetter, roCount } from './format'

describe('formatMinutes', () => {
  it('uses the singular for exactly one minute', () => {
    expect(formatMinutes(1)).toBe('1 minut')
  })

  it('uses the plural otherwise, with "de" from 20 on, as Romanian says it', () => {
    expect(formatMinutes(2)).toBe('2 minute')
    expect(formatMinutes(19)).toBe('19 minute')
    expect(formatMinutes(20)).toBe('20 de minute')
    expect(formatMinutes(45)).toBe('45 de minute')
    expect(formatMinutes(120)).toBe('120 de minute')
    expect(formatMinutes(105)).toBe('105 minute')
    expect(formatMinutes(600)).toBe('600 de minute')
  })
})

describe('roCount', () => {
  it('uses the singular for one, the plural below twenty and "de" from twenty', () => {
    expect(roCount(1, 'subiect', 'subiecte')).toBe('1 subiect')
    expect(roCount(4, 'subiect', 'subiecte')).toBe('4 subiecte')
    expect(roCount(0, 'grilă', 'grile')).toBe('0 grile')
    expect(roCount(20, 'subiect', 'subiecte')).toBe('20 de subiecte')
    expect(roCount(101, 'subiect', 'subiecte')).toBe('101 subiecte')
  })
})

describe('optionLetter', () => {
  it('letters options as a paper test does: a), b), c), d)', () => {
    expect([0, 1, 2, 3].map(optionLetter)).toEqual(['a', 'b', 'c', 'd'])
  })

  it('has no letter for an option that is not there', () => {
    expect(optionLetter(-1)).toBe('')
  })
})
