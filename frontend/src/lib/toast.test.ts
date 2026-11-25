import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { clearToasts, dismissToast, readToasts, subscribeToasts, toast } from '@/lib/toast'

describe('toast', () => {
  beforeEach(() => vi.useFakeTimers())
  afterEach(() => {
    clearToasts()
    vi.useRealTimers()
  })

  it('shows a message and takes it away after its time', () => {
    toast({ message: 'Salvat' })
    expect(readToasts().map((t) => t.message)).toEqual(['Salvat'])
    vi.advanceTimersByTime(4999)
    expect(readToasts()).toHaveLength(1)
    vi.advanceTimersByTime(1)
    expect(readToasts()).toHaveLength(0)
  })

  it('keeps one with an action longer, so there is time to press "Anulează"', () => {
    toast({ message: 'Ascuns', action: { label: 'Anulează', onClick: () => undefined } })
    vi.advanceTimersByTime(5000)
    expect(readToasts()).toHaveLength(1)
    vi.advanceTimersByTime(3000)
    expect(readToasts()).toHaveLength(0)
  })

  it('replaces a message with the same key instead of stacking it', () => {
    toast({ message: 'Mai ai 10 minute', key: 'clock' })
    toast({ message: 'Mai ai un minut', key: 'clock' })
    expect(readToasts().map((t) => t.message)).toEqual(['Mai ai un minut'])
  })

  it('shows at most three at once, the oldest leaving first', () => {
    for (const m of ['a', 'b', 'c', 'd']) toast({ message: m })
    expect(readToasts().map((t) => t.message)).toEqual(['b', 'c', 'd'])
  })

  it('tells the subscribers and can be closed early', () => {
    const seen = vi.fn()
    const stop = subscribeToasts(seen)
    const id = toast({ message: 'x' })
    dismissToast(id)
    expect(seen).toHaveBeenCalledTimes(2)
    expect(readToasts()).toHaveLength(0)
    stop()
  })
})
