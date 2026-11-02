import { Link } from 'react-router-dom'
import { buttonVariants } from '@/components/ui/button'
import { type EnChapterLink } from '@/lib/api'
import { revisionSentence } from '@/lib/enChapters'
import { cn } from '@/lib/utils'

/**
 * After a wrong answer: what to revise, in a sentence, and the lesson. `newTab` is for a practice or a review, where the
 * student goes back to the question they were reading; the button wraps, a chapter's title can be long.
 */
export function RevisionNote({
  chapter,
  parts = [],
  newTab = false,
}: {
  chapter: EnChapterLink
  parts?: string[]
  newTab?: boolean
}) {
  return (
    <div data-testid="revision-note" className="space-y-2 text-sm max-sm:text-base">
      <p>{revisionSentence(chapter, parts)}</p>
      <Link
        to={`/capitole/${chapter.id}`}
        {...(newTab && { target: '_blank', rel: 'noopener' })}
        className={cn(
          buttonVariants({ size: 'sm', variant: 'outline' }),
          'h-auto py-1.5 text-left whitespace-normal max-sm:h-auto max-sm:min-h-11',
        )}
      >
        Vezi lecția: {chapter.code} {chapter.title} {newTab ? '↗' : '→'}
        {newTab && <span className="sr-only"> (se deschide în altă filă)</span>}
      </Link>
    </div>
  )
}
