import { afterEach, describe, expect, it, vi } from 'vitest'
import { safeFileName, saveBlob } from '@/lib/download'

describe('safeFileName', () => {
  it('replaces what a file name may not hold', () => {
    expect(safeFileName('note-8A/B: sem. I?.csv')).toBe('note-8A-B- sem. I-.csv')
    expect(safeFileName('note-Clasa a VIII-a.csv')).toBe('note-Clasa a VIII-a.csv')
  })
})

describe('saveBlob', () => {
  afterEach(() => vi.restoreAllMocks())

  it('clicks a temporary download link and releases the memory', () => {
    // jsdom has no object URLs: stand in for both ends.
    URL.createObjectURL = vi.fn(() => 'blob:fake')
    URL.revokeObjectURL = vi.fn()
    const click = vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => undefined)

    saveBlob(new Blob(['Elev;Test']), 'note-8A.csv')

    const link = click.mock.contexts[0] as HTMLAnchorElement // the element that was clicked
    expect(link.download).toBe('note-8A.csv')
    expect(link.getAttribute('href')).toBe('blob:fake')
    expect(URL.revokeObjectURL).toHaveBeenCalledWith('blob:fake')
    expect(document.querySelector('a[download]')).toBeNull()
  })
})
