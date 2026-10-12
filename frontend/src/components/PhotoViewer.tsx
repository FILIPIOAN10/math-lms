import { useEffect, useState, type CSSProperties } from 'react'
import { Dialog as DialogPrimitive } from '@base-ui/react/dialog'
import { Button } from '@/components/ui/button'
import { cn } from '@/lib/utils'

/**
 * The pages of a photographed solution, large, on the same page: turn a sideways photo (⟳, or R), switch between
 * "fits the screen" and real size (to read small writing), go page to page (← →). Turning is only for viewing - the
 * stored photo never changes.
 */
export function PhotoViewer({
  urls,
  alt,
  index,
  onIndexChange,
  onClose,
}: {
  urls: string[]
  alt: (page: number) => string
  /** The page shown (0-based); null = closed. */
  index: number | null
  onIndexChange: (index: number) => void
  onClose: () => void
}) {
  // Per page: a photo taken sideways stays turned while going back and forth.
  const [turns, setTurns] = useState<Record<number, number>>({})
  const [actualSize, setActualSize] = useState(false)
  const open = index !== null
  const page = index ?? 0
  const turn = turns[page] ?? 0
  const sideways = turn % 180 !== 0

  const rotate = () => setTurns((t) => ({ ...t, [page]: ((t[page] ?? 0) + 90) % 360 }))
  const go = (to: number) => {
    if (to >= 0 && to < urls.length) onIndexChange(to)
  }

  // On the window, not the dialog: after "→" on the last page that button is disabled and focus falls to the page.
  useEffect(() => {
    if (!open) return
    const onKeyDown = (e: KeyboardEvent) => {
      if (e.key === 'ArrowLeft' && page > 0) onIndexChange(page - 1)
      else if (e.key === 'ArrowRight' && page < urls.length - 1) onIndexChange(page + 1)
      else if (e.key === 'r' || e.key === 'R') setTurns((t) => ({ ...t, [page]: ((t[page] ?? 0) + 90) % 360 }))
      else return
      e.preventDefault()
    }
    window.addEventListener('keydown', onKeyDown)
    return () => window.removeEventListener('keydown', onKeyDown)
  }, [open, page, urls.length, onIndexChange])

  // Turned a quarter, the photo's width runs along the screen's height: swap the limits so it still fits.
  const fit: CSSProperties = sideways
    ? { maxWidth: '72vh', maxHeight: 'calc(100vw - 3rem)' }
    : { maxWidth: '100%', maxHeight: '72vh' }

  return (
    <DialogPrimitive.Root open={open} onOpenChange={(o) => !o && onClose()}>
      <DialogPrimitive.Portal>
        <DialogPrimitive.Backdrop className="fixed inset-0 z-40 bg-black/70" />
        <DialogPrimitive.Popup
          className="fixed inset-2 z-50 flex flex-col rounded-xl bg-card text-card-foreground shadow-lg ring-1 ring-foreground/10 outline-none sm:inset-6"
        >
          <div className="flex flex-wrap items-center gap-2 border-b px-3 py-2">
            <DialogPrimitive.Title className="mr-auto text-sm font-medium">
              Pagina {page + 1} din {urls.length}
            </DialogPrimitive.Title>
            <Button size="sm" variant="outline" onClick={() => go(page - 1)} disabled={page === 0} aria-label="Pagina anterioară">←</Button>
            <Button size="sm" variant="outline" onClick={() => go(page + 1)} disabled={page >= urls.length - 1} aria-label="Pagina următoare">→</Button>
            <Button size="sm" variant="outline" onClick={rotate} aria-label="Rotește 90°">⟳ 90°</Button>
            <Button size="sm" variant="outline" onClick={() => setActualSize((v) => !v)} aria-pressed={actualSize}>
              {actualSize ? 'Potrivește' : '100%'}
            </Button>
            {open && (
              <a href={urls[page]} target="_blank" rel="noreferrer" className="px-2 text-sm underline">Tab nou</a>
            )}
            <DialogPrimitive.Close render={<Button size="sm">Închide</Button>} />
          </div>
          <div className={cn('flex flex-1 overflow-auto p-3', !actualSize && 'items-center justify-center')}>
            {open && (
              <img
                src={urls[page]}
                alt={alt(page + 1)}
                data-testid="photo-viewer-image"
                style={{ ...(actualSize ? {} : fit), transform: `rotate(${turn}deg)` }}
                className={cn('rounded-lg bg-background object-contain transition-transform', actualSize && 'max-w-none')}
              />
            )}
          </div>
        </DialogPrimitive.Popup>
      </DialogPrimitive.Portal>
    </DialogPrimitive.Root>
  )
}
