import { beforeEach, describe, expect, it, vi } from 'vitest'
import { clearFlags, loadFlags, saveFlags, toggled } from './flags'

describe('flags', () => {
  beforeEach(() => localStorage.clear())

  it('keeps the marks of each attempt apart and forgets them once handed in', () => {
    saveFlags(50, new Set([100, 102]))
    saveFlags(51, new Set([7]))

    expect([...loadFlags(50)]).toEqual([100, 102])
    expect([...loadFlags(51)]).toEqual([7])
    clearFlags(50)
    expect(loadFlags(50).size).toBe(0)
    expect(loadFlags(51).size).toBe(1)
  })

  it('toggles one question on and off without touching the set it was given', () => {
    const start = new Set([1])
    const on = toggled(start, 2)
    expect([...on]).toEqual([1, 2])
    expect([...toggled(on, 1)]).toEqual([2])
    expect([...start]).toEqual([1])
  })

  it('survives storage that throws or holds rubbish', () => {
    localStorage.setItem('mathlms-flags-9', 'not json')
    expect(loadFlags(9).size).toBe(0)
    const spy = vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => { throw new Error('quota') })
    expect(() => saveFlags(9, new Set([1]))).not.toThrow()
    spy.mockRestore()
  })
})
