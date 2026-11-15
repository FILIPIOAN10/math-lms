import { useEffect, useRef, useState, type CSSProperties } from 'react'
import { Button } from '@/components/ui/button'
import { PhotoViewer } from '@/components/PhotoViewer'
import { cn } from '@/lib/utils'

/**
 * S2: the pages of a photographed solution, in the order they were taken; a click opens them large in the viewer on
 * the same page (turn, zoom, page to page). `stacked` (the teacher grading) puts each page in a box beside the points
 * with its own controls - turn a sideways photo (⟳), real size to read small writing (100%), a new tab - so the
 * handwriting can be read while the points are given, without opening a window that covers the form. Otherwise the
 * pages sit side by side as thumbnails.
 */
export function PhotoPages({
  urls,
  alt,
  stacked = false,
}: {
  urls: string[]
  /** The alt text of page `page` (1-based), e.g. "Rezolvarea lui Ana, pagina 2". */
  alt: (page: number) => string
  stacked?: boolean
}) {
  const [viewing, setViewing] = useState<number | null>(null)
  if (urls.length === 0) return null
  return (
    <>
      <div data-testid="photo-pages" className={cn('flex gap-3', stacked ? 'flex-col' : 'flex-wrap')}>
        {urls.map((url, index) =>
          stacked ? (
            <StackedPage key={url} url={url} label={alt(index + 1)} onOpen={() => setViewing(index)} />
          ) : (
            <button
              key={url}
              type="button"
              onClick={() => setViewing(index)}
              className="block w-fit cursor-zoom-in rounded-lg outline-none focus-visible:ring-3 focus-visible:ring-ring/50"
              aria-label={`${alt(index + 1)} — deschide mare`}
            >
              <img
                src={url}
                alt={alt(index + 1)}
                // A fixed box, not max-h: an <img> is a few pixels tall until its photo arrives, and a photo that lands
                // late would push everything below it down under the user's click.
                className="h-64 w-48 rounded-lg border bg-background object-contain"
              />
            </button>
          ),
        )}
      </div>
      <PhotoViewer urls={urls} alt={alt} index={viewing} onIndexChange={setViewing} onClose={() => setViewing(null)} />
    </>
  )
}

/** The size of an element, kept up to date; null until it is known (and always in a browser without ResizeObserver). */
function useBoxSize(ref: React.RefObject<HTMLElement | null>) {
  const [size, setSize] = useState<{ width: number; height: number } | null>(null)
  useEffect(() => {
    const el = ref.current
    if (!el || typeof ResizeObserver === 'undefined') return
    const observer = new ResizeObserver(() => setSize({ width: el.clientWidth, height: el.clientHeight }))
    observer.observe(el)
    return () => observer.disconnect()
  }, [ref])
  return size
}

function StackedPage({ url, label, onOpen }: { url: string; label: string; onOpen: () => void }) {
  const box = useRef<HTMLDivElement>(null)
  const size = useBoxSize(box)
  const [turn, setTurn] = useState(0)
  const [actual, setActual] = useState(false)
  const sideways = turn % 180 !== 0

  // A photo turned a quarter has its width along the box's height: swap the limits so it still fits the box.
  let fit: CSSProperties = { maxWidth: '100%', maxHeight: '100%' }
  if (size) {
    fit = sideways
      ? { maxWidth: size.height - 8, maxHeight: size.width - 8 }
      : { maxWidth: size.width - 8, maxHeight: size.height - 8 }
  }

  return (
    <div className="space-y-1">
      <div className="flex flex-wrap items-center gap-1">
        <Button type="button" size="sm" variant="outline" onClick={() => setTurn((t) => (t + 90) % 360)} aria-label="Rotește poza 90°">
          ⟳ 90°
        </Button>
        <Button type="button" size="sm" variant="outline" onClick={() => setActual((v) => !v)} aria-pressed={actual}>
          {actual ? 'Potrivește' : '100%'}
        </Button>
        <a href={url} target="_blank" rel="noreferrer" className="px-2 text-sm underline">Tab nou</a>
      </div>
      <div
        ref={box}
        className={cn('h-[min(70vh,44rem)] overflow-auto rounded-lg border bg-background', !actual && 'flex items-center justify-center')}
      >
        <button
          type="button"
          onClick={onOpen}
          className="block cursor-zoom-in outline-none focus-visible:ring-3 focus-visible:ring-ring/50"
          aria-label={`${label} — deschide mare`}
        >
          <img
            src={url}
            alt={label}
            style={{ ...(actual ? {} : fit), transform: `rotate(${turn}deg)` }}
            className={cn('object-contain transition-transform', actual && 'max-w-none')}
          />
        </button>
      </div>
    </div>
  )
}
