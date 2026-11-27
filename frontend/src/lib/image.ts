const MAX_INPUT_SIDE = 12_000
const MAX_INPUT_PIXELS = 24_000_000
const PAGE_UPLOAD_LIMIT = 4 * 1024 * 1024

function checkDimensions(width: number, height: number) {
  if (width < 1 || height < 1 || width > MAX_INPUT_SIDE || height > MAX_INPUT_SIDE
      || width * height > MAX_INPUT_PIXELS) {
    throw new Error('Imaginea are dimensiuni prea mari. Alege o poză mai mică.')
  }
}

function canvasBlob(canvas: HTMLCanvasElement, type: string, quality?: number): Promise<Blob> {
  return new Promise((resolve, reject) => {
    canvas.toBlob((blob) => blob ? resolve(blob) : reject(new Error('Browserul nu a putut prelucra imaginea.')),
      type, quality)
  })
}

function jpegName(name: string, suffix = ''): string {
  return name.replace(/\.[^.]+$/, '') + suffix + '.jpg'
}

/**
 * Shrinks and re-encodes a photo before upload. Canvas encoding strips EXIF/GPS metadata even for small files; the
 * backend repeats the validation and sanitization, so an unsupported or undecodable original is never sent raw.
 */
export async function shrinkImage(file: File, maxSide = 2000, quality = 0.85): Promise<File> {
  const type = file.type.toLowerCase()
  if (!['image/jpeg', 'image/jpg', 'image/png', 'image/webp'].includes(type)) {
    throw new Error('Alege o imagine JPG, PNG sau WebP.')
  }

  let bitmap: ImageBitmap
  try {
    bitmap = await createImageBitmap(file)
  } catch {
    throw new Error('Imaginea nu poate fi deschisă. Salveaz-o ca JPG sau PNG și încearcă din nou.')
  }
  try {
    checkDimensions(bitmap.width, bitmap.height)
    const scale = Math.min(1, maxSide / Math.max(bitmap.width, bitmap.height))
    const canvas = document.createElement('canvas')
    canvas.width = Math.max(1, Math.round(bitmap.width * scale))
    canvas.height = Math.max(1, Math.round(bitmap.height * scale))
    const context = canvas.getContext('2d')
    if (!context) throw new Error('Browserul nu poate desena imaginea.')
    context.drawImage(bitmap, 0, 0, canvas.width, canvas.height)

    if (type === 'image/png') {
      const png = await canvasBlob(canvas, 'image/png')
      if (png.size <= PAGE_UPLOAD_LIMIT) {
        return new File([png], file.name.replace(/\.[^.]+$/, '') + '.png', { type: 'image/png' })
      }
    }
    const jpeg = await canvasBlob(canvas, 'image/jpeg', quality)
    return new File([jpeg], jpegName(file.name), { type: 'image/jpeg' })
  } finally {
    bitmap.close()
  }
}

/**
 * The part of a photo inside `box` (fractions 0-1 of its width and height) as a clean JPEG. A failed crop raises a
 * visible error instead of falling back to uploading the unprocessed original.
 */
export async function cropImage(file: File, box: { x: number; y: number; w: number; h: number }, maxSide = 2000,
  quality = 0.9): Promise<File> {
  const type = file.type.toLowerCase()
  if (!['image/jpeg', 'image/jpg', 'image/png', 'image/webp'].includes(type)) {
    throw new Error('Alege o imagine JPG, PNG sau WebP.')
  }
  let bitmap: ImageBitmap
  try {
    bitmap = await createImageBitmap(file)
  } catch {
    throw new Error('Imaginea nu poate fi deschisă. Salveaz-o ca JPG sau PNG și încearcă din nou.')
  }
  try {
    checkDimensions(bitmap.width, bitmap.height)
    const x = Math.min(1, Math.max(0, box.x))
    const y = Math.min(1, Math.max(0, box.y))
    const w = Math.min(1 - x, Math.max(0, box.w))
    const h = Math.min(1 - y, Math.max(0, box.h))
    const sx = Math.round(x * bitmap.width)
    const sy = Math.round(y * bitmap.height)
    const sw = Math.max(1, Math.round(w * bitmap.width))
    const sh = Math.max(1, Math.round(h * bitmap.height))
    const scale = Math.min(1, maxSide / Math.max(sw, sh))
    const canvas = document.createElement('canvas')
    canvas.width = Math.max(1, Math.round(sw * scale))
    canvas.height = Math.max(1, Math.round(sh * scale))
    const context = canvas.getContext('2d')
    if (!context) throw new Error('Browserul nu poate desena decupajul.')
    context.drawImage(bitmap, sx, sy, sw, sh, 0, 0, canvas.width, canvas.height)
    const blob = await canvasBlob(canvas, 'image/jpeg', quality)
    return new File([blob], jpegName(file.name, '-barem'), { type: 'image/jpeg' })
  } finally {
    bitmap.close()
  }
}
