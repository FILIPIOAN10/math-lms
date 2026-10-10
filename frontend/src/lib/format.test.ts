import { describe, expect, it } from 'vitest'
import { formatMinutes, roCount } from './format'

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

describe('roCount', () => {
  it('uses the singular for one, the plural below twenty and "de" from twenty', () => {
    expect(roCount(1, 'subiect', 'subiecte')).toBe('1 subiect')
    expect(roCount(4, 'subiect', 'subiecte')).toBe('4 subiecte')
    expect(roCount(0, 'grilă', 'grile')).toBe('0 grile')
    expect(roCount(20, 'subiect', 'subiecte')).toBe('20 de subiecte')
    expect(roCount(101, 'subiect', 'subiecte')).toBe('101 subiecte')
  })
})
