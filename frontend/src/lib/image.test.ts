import { describe, expect, it } from 'vitest'
import { shrinkImage } from './image'

// jsdom has no image decoder: what matters here is that nothing unprocessed is ever sent as it came.
describe('shrinkImage', () => {
  it('refuses a type it cannot clean (HEIC, SVG, GIF...) instead of sending the original', async () => {
    const svg = new File(['<svg onload="x()"/>'], 'poza.svg', { type: 'image/svg+xml' })
    await expect(shrinkImage(svg)).rejects.toThrow(/JPG, PNG sau WebP/)
  })

  it('refuses a photo the browser cannot open instead of sending it raw (EXIF/GPS and all)', async () => {
    const broken = new File(['not really a jpeg'], 'poza.jpg', { type: 'image/jpeg' })
    await expect(shrinkImage(broken)).rejects.toThrow(/nu poate fi deschisă/)
  })
})
