import { beforeEach, describe, expect, it, vi } from 'vitest'
import { celebrateFirstTen } from '@/lib/celebrate'

const motion = (allowed: boolean) =>
  vi.stubGlobal('matchMedia', vi.fn().mockReturnValue({ matches: allowed }))

describe('celebrateFirstTen', () => {
  beforeEach(() => {
    localStorage.clear()
    vi.unstubAllGlobals()
  })

  it('celebrates the first 10 once', () => {
    motion(true)
    expect(celebrateFirstTen()).toBe(true)
    expect(celebrateFirstTen()).toBe(false)
  })

  it('stays still for someone who asked for less motion, and keeps the first 10 for later', () => {
    motion(false)
    expect(celebrateFirstTen()).toBe(false)
    motion(true)
    expect(celebrateFirstTen()).toBe(true)
  })
})
