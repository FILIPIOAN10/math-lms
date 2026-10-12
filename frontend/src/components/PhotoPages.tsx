import { cn } from '@/lib/utils'

/**
 * S2: the pages of a photographed solution, in the order they were taken, each opening full size in a new tab.
 * `stacked` lays them one under another at reading size (the teacher grading); otherwise they sit side by side.
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
  if (urls.length === 0) return null
  return (
    <div data-testid="photo-pages" className={cn('flex gap-3', stacked ? 'flex-col' : 'flex-wrap')}>
      {urls.map((url, index) => (
        <a key={url} href={url} target="_blank" rel="noreferrer" className="block w-fit">
          <img
            src={url}
            alt={alt(index + 1)}
            className={cn('rounded-lg border bg-background object-contain', stacked ? 'max-h-[28rem]' : 'max-h-64')}
          />
        </a>
      ))}
    </div>
  )
}
