import { act, renderHook } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import { useAutosave } from '@/lib/useAutosave'

const opts = { equals: (a: number | null, b: number | null) => a === b, isReady: (v: number | null) => v !== null }

function deferred() {
  let resolve!: () => void
  let reject!: (e: unknown) => void
  const promise = new Promise<void>((res, rej) => {
    resolve = res
    reject = rej
  })
  return { promise, resolve, reject }
}

describe('useAutosave', () => {
  it('sends nothing when nothing changed', async () => {
    const save = vi.fn(() => Promise.resolve())
    const { result } = renderHook(() => useAutosave<number | null>(3, save, opts))

    await act(async () => expect(await result.current.flush()).toBe(true))
    expect(save).not.toHaveBeenCalled()
    expect(result.current.status).toBe('idle')
  })

  it('waits for a value that can be saved', async () => {
    const save = vi.fn(() => Promise.resolve())
    const { result } = renderHook(() => useAutosave<number | null>(3, save, opts))

    act(() => result.current.setValue(null))
    await act(async () => expect(await result.current.flush()).toBe(false))
    expect(save).not.toHaveBeenCalled()
    expect(result.current.status).toBe('incomplete')
  })

  it('never runs two saves at once and the newest value wins', async () => {
    const first = deferred()
    const save = vi.fn((v: number | null) => (v === 1 ? first.promise : Promise.resolve()))
    const { result } = renderHook(() => useAutosave<number | null>(null, save, opts))

    act(() => result.current.setValue(1))
    let one!: Promise<boolean>
    act(() => {
      one = result.current.flush()
    })
    expect(result.current.status).toBe('saving')
    act(() => result.current.setValue(2))
    let two!: Promise<boolean>
    act(() => {
      two = result.current.flush()
    })
    expect(save).toHaveBeenCalledTimes(1) // the second waits for the first

    await act(async () => {
      first.resolve()
      expect(await one).toBe(true)
      expect(await two).toBe(true)
    })
    expect(save.mock.calls.map((c) => c[0])).toEqual([1, 2])
    expect(result.current.status).toBe('saved')
  })

  it('reports a failed save and sends it again on the next flush', async () => {
    const save = vi.fn().mockRejectedValueOnce(new Error('offline')).mockResolvedValueOnce(undefined)
    const { result } = renderHook(() => useAutosave<number | null>(null, save, opts))

    act(() => result.current.setValue(4))
    await act(async () => expect(await result.current.flush()).toBe(false))
    expect(result.current.status).toBe('error')
    expect((result.current.error as Error).message).toBe('offline')

    await act(async () => expect(await result.current.flush()).toBe(true))
    expect(save).toHaveBeenCalledTimes(2)
    expect(result.current.status).toBe('saved')
  })
})
