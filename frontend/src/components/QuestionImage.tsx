import { questionImageUrl } from '@/lib/api'
import { cn } from '@/lib/utils'

/**
 * The picture under a question's statement (EN Subiectul II figures), wherever the question is shown. Nothing when the
 * question has none. A click opens it full size in a new tab, to read small details.
 */
export function QuestionImage({ itemId, image, className }: { itemId: number; image?: string | null; className?: string }) {
  if (!image) return null
  const src = questionImageUrl(itemId, image)
  return (
    <a href={src} target="_blank" rel="noreferrer" className={cn('block w-fit', className)}>
      <img src={src} alt="Figura întrebării" data-testid="question-image" className="max-h-80 rounded-lg border bg-white object-contain" />
    </a>
  )
}
