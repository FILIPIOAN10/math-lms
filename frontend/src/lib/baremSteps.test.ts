import { describe, expect, it } from 'vitest'
import { parseBaremSteps, stepsTicked, sumOf } from '@/lib/baremSteps'

const D = 'R: $(12x^2 + 1) - (3x^2 + 4)$ (2p); $= 6x^2 + 8x\\sqrt{3} + 2$ (3p)'

describe('parseBaremSteps', () => {
  it('splits a barem on its (Np) marks, in order', () => {
    expect(parseBaremSteps(D, 5)).toEqual([
      { text: '$(12x^2 + 1) - (3x^2 + 4)$', points: 2 },
      { text: '$= 6x^2 + 8x\\sqrt{3} + 2$', points: 3 },
    ])
  })

  it('is null when the marks do not add up to the requirement points', () => {
    expect(parseBaremSteps(D, 6)).toBeNull()
  })

  it('is null for a single step: the points row is enough', () => {
    expect(parseBaremSteps('R: $16x^2 - 24x + 9$ (5p)', 5)).toBeNull()
  })

  it('is null without marks or without a barem', () => {
    expect(parseBaremSteps('R: $x = 3$', 5)).toBeNull()
    expect(parseBaremSteps(null, 5)).toBeNull()
    expect(parseBaremSteps('   ', 5)).toBeNull()
  })

  it('accepts "pct" and "puncte" and a trailing step without text', () => {
    expect(parseBaremSteps('Notăm $t$ (1 pct); calcul (2 puncte)', 3)).toEqual([
      { text: 'Notăm $t$', points: 1 },
      { text: 'calcul', points: 2 },
    ])
  })
})

describe('stepsTicked', () => {
  const steps = [{ text: 'a', points: 2 }, { text: 'b', points: 3 }]

  it('ticks nothing for no points, everything for the full points', () => {
    expect(stepsTicked(steps, null)).toEqual([false, false])
    expect(stepsTicked(steps, 0)).toEqual([false, false])
    expect(stepsTicked(steps, 5)).toEqual([true, true])
  })

  it('ticks the one subset that gives the points', () => {
    expect(stepsTicked(steps, 2)).toEqual([true, false])
    expect(stepsTicked(steps, 3)).toEqual([false, true])
  })

  it('ticks nothing when the points match no subset', () => {
    expect(stepsTicked(steps, 4)).toEqual([false, false])
  })

  it('ticks the earliest subset when several give the points', () => {
    const three = [{ text: 'a', points: 2 }, { text: 'b', points: 2 }, { text: 'c', points: 1 }]
    expect(stepsTicked(three, 2)).toEqual([true, false, false])
    expect(stepsTicked(three, 3)).toEqual([true, false, true])
  })
})

describe('sumOf', () => {
  it('adds the ticked steps', () => {
    expect(sumOf([{ text: 'a', points: 2 }, { text: 'b', points: 3 }], [false, true])).toBe(3)
  })
})
