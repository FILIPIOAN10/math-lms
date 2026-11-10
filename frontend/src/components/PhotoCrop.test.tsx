import { act, render, screen } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { PhotoCrop } from '@/components/PhotoCrop'

// jsdom never loads an image: this one loads when the test says so, with the size the test gives.
const pending: FakeImage[] = []
const loads: string[] = []
class FakeImage {
  onload: (() => void) | null = null
  onerror: (() => void) | null = null
  naturalWidth = 0
  naturalHeight = 0
  url = ''
  set src(url: string) {
    this.url = url
    loads.push(url)
    pending.push(this)
  }
}
async function finish(url: string, width: number, height: number) {
  await act(async () => {
    for (const image of pending.filter((p) => p.url === url)) {
      image.naturalWidth = width
      image.naturalHeight = height
      image.onload?.()
    }
  })
}

beforeEach(() => {
  pending.length = 0
  loads.length = 0
  vi.stubGlobal('Image', FakeImage)
  vi.spyOn(HTMLCanvasElement.prototype, 'getContext').mockReturnValue(null)
})
afterEach(() => {
  vi.unstubAllGlobals()
  vi.restoreAllMocks()
})

const size = (el: HTMLElement) => [Number(el.getAttribute('width')), Number(el.getAttribute('height'))]

describe('PhotoCrop', () => {
  // A figure whose photo lands late must not push the page under the user's click: before the photo arrives the
  // canvas already has the shape it will most likely have (an upright phone page, 1500 x 2000 after shrinkImage).
  it('holds the likely shape of the figure before the photo arrives', async () => {
    render(<PhotoCrop src="/page/a" box={{ x: 0.1, y: 0.2, w: 0.5, h: 0.25 }} alt="Triunghiul" />)
    expect(size(screen.getByRole('img', { name: 'Triunghiul' }))).toEqual([750, 500])

    await finish('/page/a', 1500, 2000)
    expect(size(screen.getByRole('img', { name: 'Triunghiul' }))).toEqual([750, 500])
  })

  it('takes the real shape once the photo is in, and a second figure of the same page knows it at once', async () => {
    render(<PhotoCrop src="/page/b" box={{ x: 0, y: 0, w: 0.5, h: 0.5 }} alt="Primul" />)
    await finish('/page/b', 2000, 688)
    expect(size(screen.getByRole('img', { name: 'Primul' }))).toEqual([1000, 344])

    render(<PhotoCrop src="/page/b" box={{ x: 0.5, y: 0.5, w: 0.25, h: 0.5 }} alt="Al doilea" />)
    expect(size(screen.getByRole('img', { name: 'Al doilea' }))).toEqual([500, 344])
    // One download of the page for both figures.
    expect(loads.filter((url) => url === '/page/b')).toHaveLength(1)
  })

  it('shows the description when the photo cannot be loaded', async () => {
    render(<PhotoCrop src="/page/c" box={{ x: 0, y: 0, w: 1, h: 1 }} alt="Cercul" />)
    await act(async () => pending.find((p) => p.url === '/page/c')?.onerror?.())
    expect(screen.getByText('[figură: Cercul]')).toBeInTheDocument()
  })
})
