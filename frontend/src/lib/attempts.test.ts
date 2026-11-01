import { describe, expect, it } from 'vitest'
import { attemptRule, attemptsLabel } from '@/lib/attempts'

describe('attemptsLabel', () => {
  it('names the limit, or says there is none', () => {
    expect(attemptsLabel(1)).toBe('1 încercare')
    expect(attemptsLabel(3)).toBe('3 încercări')
    expect(attemptsLabel(null)).toBe('încercări nelimitate')
  })
})

describe('attemptRule', () => {
  it('says plainly when there is only one try', () => {
    expect(attemptRule(1, 0)).toBe('Ai o singură încercare.')
  })

  it('says which try this is and which grade counts', () => {
    expect(attemptRule(3, 1)).toBe('Încercarea 2 din 3. Contează cea mai mare notă.')
  })

  it('says which grade counts on a test without a limit', () => {
    expect(attemptRule(null, 4)).toBe('Poți da testul de mai multe ori. Contează cea mai mare notă.')
  })
})
