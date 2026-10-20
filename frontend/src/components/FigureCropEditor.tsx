import { useRef, useState, type PointerEvent } from 'react'
import { Button } from '@/components/ui/button'
import type { PhotoBox } from '@/components/PhotoCrop'
import { boxFromDrag } from '@/lib/aiImport'

const percent = (value: number) => `${(value * 100).toFixed(2)}%`

/**
 * The whole page photo with the drawing's box on it; the teacher drags a new box around the drawing (mouse or finger)
 * and keeps it with "Gata". A click without a drag changes nothing, so the old box stays until a real one is drawn.
 */
export function FigureCropEditor({ src, box, alt, onDone, onCancel }: {
  src: string
  box: PhotoBox
  alt: string
  onDone: (box: PhotoBox) => void
  onCancel: () => void
}) {
  const area = useRef<HTMLDivElement | null>(null)
  const [current, setCurrent] = useState<PhotoBox>(box)
  const [start, setStart] = useState<{ x: number; y: number } | null>(null)

  function at(e: PointerEvent<HTMLDivElement>) {
    const rect = area.current!.getBoundingClientRect()
    return { x: (e.clientX - rect.left) / rect.width, y: (e.clientY - rect.top) / rect.height }
  }

  function down(e: PointerEvent<HTMLDivElement>) {
    e.currentTarget.setPointerCapture?.(e.pointerId)
    setStart(at(e))
  }

  function move(e: PointerEvent<HTMLDivElement>) {
    if (!start) return
    const dragged = boxFromDrag(start, at(e))
    if (dragged) setCurrent(dragged)
  }

  return (
    <div className="space-y-2" data-testid="figure-editor">
      <p className="text-sm text-muted-foreground">
        Trage cu mouse-ul (sau cu degetul) un dreptunghi în jurul desenului, apoi apasă „Gata”.
      </p>
      <div ref={area} className="relative max-w-full cursor-crosshair touch-none select-none"
           onPointerDown={down} onPointerMove={move} onPointerUp={() => setStart(null)}
           onPointerCancel={() => setStart(null)}>
        <img src={src} alt={alt} draggable={false} className="block h-auto w-full rounded border" />
        <div aria-hidden="true" className="pointer-events-none absolute border-2 border-primary bg-primary/10"
             style={{ left: percent(current.x), top: percent(current.y), width: percent(current.w), height: percent(current.h) }} />
      </div>
      <div className="flex gap-2">
        <Button type="button" size="sm" onClick={() => onDone(current)}>Gata</Button>
        <Button type="button" size="sm" variant="outline" onClick={onCancel}>Anulează</Button>
      </div>
    </div>
  )
}
