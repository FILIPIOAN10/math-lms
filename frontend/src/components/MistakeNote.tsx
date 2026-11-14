import { Link } from 'react-router-dom'
import { MathContent } from '@/components/MathContent'
import { buttonVariants } from '@/components/ui/button'
import { type TutorLessonLink } from '@/lib/api'
import { lessonHref } from '@/lib/lessons'
import { cn } from '@/lib/utils'

/**
 * What the teacher wrote about the wrong option a student chose: their mistake, not the general solution. With the
 * lesson it comes from („De ce am greșit?”), a link to it at its page.
 */
export function MistakeNote({ text, lesson = null }: { text: string; lesson?: TutorLessonLink | null }) {
  return (
    <div data-testid="item-mistake" className="space-y-2 rounded-lg border-l-4 border-amber-500 bg-amber-500/10 p-3 text-sm">
      <p className="text-xs font-medium text-amber-800 dark:text-amber-200">De ce e greșit</p>
      <MathContent>{text}</MathContent>
      {lesson && (
        <Link
          to={lessonHref(lesson)}
          className={cn(buttonVariants({ size: 'sm', variant: 'outline' }), 'h-auto py-1.5 text-left whitespace-normal')}
        >
          Deschide lecția {lesson.chapterCode}, pagina {lesson.bookPage}
          <span className="sr-only"> ({lesson.title})</span>
        </Link>
      )}
    </div>
  )
}
