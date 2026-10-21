import { useRef, useState, type KeyboardEvent, type PointerEvent } from 'react'
import { Button } from '@/components/ui/button'
import { PhotoCrop, type PhotoBox } from '@/components/PhotoCrop'
import { boxFromDrag, moveBox, resizeBox, type BoxHandle } from '@/lib/aiImport'

const percent = (value: number) => `${(value * 100).toFixed(2)}%`

/** One arrow-key press moves or resizes the box by this much of the photo (half a percent). */
const KEY_STEP = 0.005

type Point = { x: number; y: number }

/** What the pointer is doing since it went down: drawing a new box, moving the box, or pulling one of its grips. */
type Drag =
  | { kind: 'draw'; start: Point }
  | { kind: 'move'; start: Point; from: PhotoBox }
  | { kind: 'resize'; handle: BoxHandle; from: PhotoBox }

/** The eight grips, placed on the box's corners and the middle of its sides. */
const HANDLES: { handle: BoxHandle; left: string; top: string; cursor: string }[] = [
  { handle: 'nw', left: '0%', top: '0%', cursor: 'cursor-nwse-resize' },
  { handle: 'n', left: '50%', top: '0%', cursor: 'cursor-ns-resize' },
  { handle: 'ne', left: '100%', top: '0%', cursor: 'cursor-nesw-resize' },
  { handle: 'e', left: '100%', top: '50%', cursor: 'cursor-ew-resize' },
  { handle: 'se', left: '100%', top: '100%', cursor: 'cursor-nwse-resize' },
  { handle: 's', left: '50%', top: '100%', cursor: 'cursor-ns-resize' },
  { handle: 'sw', left: '0%', top: '100%', cursor: 'cursor-nesw-resize' },
  { handle: 'w', left: '0%', top: '50%', cursor: 'cursor-ew-resize' },
]

/**
 * The whole page photo with the drawing's box on it. The teacher pulls a corner or a side to resize the box, drags it
 * from the middle to move it, or drags outside it to draw a new one (mouse or finger); with the box focused, the
 * arrows move it and Shift + arrows resize it. Below, the cut-out as the student will see it; "Gata" keeps it.
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
  // The preview is redrawn only when a drag ends: redrawing the canvas on every pointer move would be wasted work.
  const [preview, setPreview] = useState<PhotoBox>(box)
  const [drag, setDrag] = useState<Drag | null>(null)

  function at(e: PointerEvent<HTMLDivElement>): Point {
    const rect = area.current!.getBoundingClientRect()
    return { x: (e.clientX - rect.left) / rect.width, y: (e.clientY - rect.top) / rect.height }
  }

  function down(e: PointerEvent<HTMLDivElement>) {
    e.currentTarget.setPointerCapture?.(e.pointerId)
    const target = e.target as HTMLElement
    const handle = target.closest<HTMLElement>('[data-handle]')?.dataset.handle as BoxHandle | undefined
    if (handle) setDrag({ kind: 'resize', handle, from: current })
    else if (target.closest('[data-box]')) setDrag({ kind: 'move', start: at(e), from: current })
    else setDrag({ kind: 'draw', start: at(e) })
  }

  function move(e: PointerEvent<HTMLDivElement>) {
    if (!drag) return
    const point = at(e)
    if (drag.kind === 'resize') setCurrent(resizeBox(drag.from, drag.handle, point))
    else if (drag.kind === 'move') setCurrent(moveBox(drag.from, point.x - drag.start.x, point.y - drag.start.y))
    else {
      const drawn = boxFromDrag(drag.start, point)
      if (drawn) setCurrent(drawn)
    }
  }

  function up() {
    setDrag(null)
    setPreview(current)
  }

  function key(e: KeyboardEvent<HTMLDivElement>) {
    const dx = e.key === 'ArrowRight' ? KEY_STEP : e.key === 'ArrowLeft' ? -KEY_STEP : 0
    const dy = e.key === 'ArrowDown' ? KEY_STEP : e.key === 'ArrowUp' ? -KEY_STEP : 0
    if (dx === 0 && dy === 0) return
    e.preventDefault()
    const next = e.shiftKey
      ? resizeBox(current, dx !== 0 ? 'e' : 's',
        { x: current.x + current.w + dx, y: current.y + current.h + dy })
      : moveBox(current, dx, dy)
    setCurrent(next)
    setPreview(next)
  }

  const placed = { left: percent(current.x), top: percent(current.y), width: percent(current.w), height: percent(current.h) }

  return (
    <div className="space-y-3" data-testid="figure-editor">
      <p className="text-sm text-muted-foreground">
        Trage de colțuri sau de laturi ca să schimbi mărimea, trage din mijloc ca să muți chenarul, sau trage în afara lui
        un chenar nou. Cu chenarul selectat, săgețile îl mută, iar Shift + săgețile îi schimbă mărimea.
      </p>
      <div ref={area} className="relative max-w-full cursor-crosshair touch-none select-none"
           onPointerDown={down} onPointerMove={move} onPointerUp={up} onPointerCancel={up}>
        <img src={src} alt={alt} draggable={false} className="block h-auto w-full rounded border" />
        {/* Everything outside the box is dimmed, so the part that will be kept stands out. */}
        <div aria-hidden="true" className="pointer-events-none absolute inset-0 overflow-hidden rounded">
          <div className="absolute shadow-[0_0_0_9999px_rgb(0_0_0/0.45)]" style={placed} />
        </div>
        <div data-box="" data-testid="figure-box" tabIndex={0} role="group" onKeyDown={key}
             aria-label="Chenarul desenului: săgețile îl mută, Shift și săgețile îi schimbă mărimea"
             className="absolute cursor-move border-2 border-primary outline-none focus-visible:ring-3 focus-visible:ring-ring/60"
             style={placed}>
          {HANDLES.map(({ handle, left, top, cursor }) => (
            <span key={handle} data-handle={handle} data-testid={`figure-handle-${handle}`} aria-hidden="true"
                  className={`absolute size-3.5 -translate-x-1/2 -translate-y-1/2 rounded-sm border-2 border-primary bg-background ${cursor} after:absolute after:-inset-2.5`}
                  style={{ left, top }} />
          ))}
        </div>
      </div>
      <div className="space-y-1">
        <p className="text-sm font-medium">Așa va arăta</p>
        <PhotoCrop src={src} box={preview} alt={alt} className="max-h-48 rounded border" />
      </div>
      <div className="flex gap-2">
        <Button type="button" size="sm" onClick={() => onDone(current)}>Gata</Button>
        <Button type="button" size="sm" variant="outline" onClick={onCancel}>Anulează</Button>
      </div>
    </div>
  )
}
