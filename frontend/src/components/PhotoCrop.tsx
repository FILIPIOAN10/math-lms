import { useEffect, useRef, useState } from 'react'
import { cn } from '@/lib/utils'

/** A rectangle of a photo, as fractions (0 to 1) of its width and height: top-left corner and size. */
export interface PhotoBox {
  x: number
  y: number
  w: number
  h: number
}

interface Size {
  width: number
  height: number
}

/** The widest a figure is drawn: a phone photo is far larger than a figure needs, and a canvas costs memory. */
const MAX_DRAW_WIDTH = 1000

/**
 * The page a figure most likely comes from, until its photo is in: shrinkImage caps the long edge at 2000 px and a
 * book page is photographed upright, about 3:4. Sizing the canvas for it before the photo arrives keeps the text
 * below in place when it does (otherwise every figure grows from the browser's 300 x 150 and pushes the page down
 * under the user's click).
 */
const LIKELY_PAGE: Size = { width: 1500, height: 2000 }

/** Page photos already asked for, so the figures of one page share one download (the server sends them no-store). */
const photos = new Map<string, Promise<HTMLImageElement>>()
/** The real size of every page photo seen, so a figure mounted later is drawn at its final size from the start. */
const pageSizes = new Map<string, Size>()
/** A few decoded pages are enough: a test has at most five or six, and each one holds megabytes of memory. */
const KEEP_PHOTOS = 8

function loadPhoto(src: string): Promise<HTMLImageElement> {
  const known = photos.get(src)
  if (known) {
    photos.delete(src)
    photos.set(src, known)
    return known
  }
  const loading = new Promise<HTMLImageElement>((resolve, reject) => {
    const image = new Image()
    image.onload = () => {
      pageSizes.set(src, { width: image.naturalWidth, height: image.naturalHeight })
      resolve(image)
    }
    image.onerror = () => reject(new Error(`photo ${src} could not be loaded`))
    image.src = src
  })
  // A failed photo is asked for again next time (the network may be back).
  loading.catch(() => photos.delete(src))
  photos.set(src, loading)
  if (photos.size > KEEP_PHOTOS) photos.delete(photos.keys().next().value as string)
  return loading
}

/** The canvas size for `box` of a page of size `page`: the framed pixels, scaled down to MAX_DRAW_WIDTH. */
function cropSize(page: Size, box: PhotoBox): Size {
  const sourceW = Math.max(1, box.w * page.width)
  const sourceH = Math.max(1, box.h * page.height)
  const scale = Math.min(1, MAX_DRAW_WIDTH / sourceW)
  return { width: Math.max(1, Math.round(sourceW * scale)), height: Math.max(1, Math.round(sourceH * scale)) }
}

/**
 * One figure of a book page: the part of the page photo that shows it, drawn on its own so the student sees the
 * drawing where it belongs in the text, not the whole photo. If the photo cannot be loaded the description is shown.
 */
export function PhotoCrop({ src, box, alt, className }: { src: string; box: PhotoBox; alt: string; className?: string }) {
  const canvasRef = useRef<HTMLCanvasElement | null>(null)
  const [loaded, setLoaded] = useState<{ src: string; image: HTMLImageElement } | null>(null)
  const [failed, setFailed] = useState<string | null>(null)
  const image = loaded?.src === src ? loaded.image : null
  const page = image ? { width: image.naturalWidth, height: image.naturalHeight } : pageSizes.get(src) ?? LIKELY_PAGE
  const { width, height } = cropSize(page, box)

  useEffect(() => {
    let stopped = false
    loadPhoto(src)
      .then((next) => { if (!stopped) setLoaded({ src, image: next }) })
      .catch(() => { if (!stopped) setFailed(src) })
    return () => { stopped = true }
  }, [src])

  // After React has set the canvas size (which clears it): draw the framed part of the page into it.
  useEffect(() => {
    const context = image && canvasRef.current?.getContext('2d')
    if (!image || !context) return
    context.drawImage(
      image,
      box.x * image.naturalWidth,
      box.y * image.naturalHeight,
      Math.max(1, box.w * image.naturalWidth),
      Math.max(1, box.h * image.naturalHeight),
      0,
      0,
      width,
      height,
    )
  }, [image, box.x, box.y, box.w, box.h, width, height])

  if (failed === src) {
    return <p className="text-sm italic text-muted-foreground">[figură: {alt}]</p>
  }
  return (
    <canvas
      ref={canvasRef}
      width={width}
      height={height}
      role="img"
      aria-label={alt}
      className={cn('h-auto max-w-full rounded border bg-white', className)}
    />
  )
}
