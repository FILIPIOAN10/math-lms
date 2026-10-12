import { useState } from 'react'
import { PhotoViewer } from '@/components/PhotoViewer'
import { cn } from '@/lib/utils'

/**
 * S2: the pages of a photographed solution, in the order they were taken; a click opens them large in the viewer on
 * the same page (turn, zoom, page to page). `stacked` lays them one under another at reading size (the teacher
 * grading); otherwise they sit side by side.
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
        {urls.map((url, index) => (
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
              className={cn('rounded-lg border bg-background object-contain', stacked ? 'max-h-[28rem]' : 'max-h-64')}
            />
          </button>
        ))}
      </div>
      <PhotoViewer urls={urls} alt={alt} index={viewing} onIndexChange={setViewing} onClose={() => setViewing(null)} />
    </>
  )
}
