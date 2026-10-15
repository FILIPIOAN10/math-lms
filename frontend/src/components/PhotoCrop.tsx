import { useEffect, useRef, useState } from 'react'
import { cn } from '@/lib/utils'

/** A rectangle of a photo, as fractions (0 to 1) of its width and height: top-left corner and size. */
export interface PhotoBox {
  x: number
  y: number
  w: number
  h: number
}

/** The widest a figure is drawn: a phone photo is far larger than a figure needs, and a canvas costs memory. */
const MAX_DRAW_WIDTH = 1000

/**
 * One figure of a book page: the part of the page photo that shows it, drawn on its own so the student sees the
 * drawing where it belongs in the text, not the whole photo. If the photo cannot be loaded the description is shown.
 */
export function PhotoCrop({ src, box, alt, className }: { src: string; box: PhotoBox; alt: string; className?: string }) {
  const canvasRef = useRef<HTMLCanvasElement | null>(null)
  const [failed, setFailed] = useState(false)

  useEffect(() => {
    setFailed(false)
    const image = new Image()
    image.onload = () => {
      const canvas = canvasRef.current
      const context = canvas?.getContext('2d')
      if (!canvas || !context) return
      const sourceW = Math.max(1, box.w * image.naturalWidth)
      const sourceH = Math.max(1, box.h * image.naturalHeight)
      const scale = Math.min(1, MAX_DRAW_WIDTH / sourceW)
      canvas.width = Math.round(sourceW * scale)
      canvas.height = Math.round(sourceH * scale)
      context.drawImage(
        image,
        box.x * image.naturalWidth,
        box.y * image.naturalHeight,
        sourceW,
        sourceH,
        0,
        0,
        canvas.width,
        canvas.height,
      )
    }
    image.onerror = () => setFailed(true)
    image.src = src
    return () => {
      image.onload = null
      image.onerror = null
    }
  }, [src, box.x, box.y, box.w, box.h])

  if (failed) {
    return <p className="text-sm italic text-muted-foreground">[figură: {alt}]</p>
  }
  return (
    <canvas
      ref={canvasRef}
      role="img"
      aria-label={alt}
      className={cn('h-auto max-w-full rounded border bg-white', className)}
    />
  )
}
