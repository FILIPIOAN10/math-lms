import { type TutorLessonLink } from '@/lib/api'

/**
 * The chapter page of a lesson link, scrolled to its section when it is one, and naming the page of the book: where the
 * chapter's lesson is written out (V73/V74) the page opens at that written-out page; otherwise the section, as before.
 */
export function lessonHref(lesson: TutorLessonLink): string {
  const query = new URLSearchParams()
  if (lesson.sectionId !== null) query.set('sectiune', String(lesson.sectionId))
  if (lesson.bookPage > 0) query.set('pagina', String(lesson.bookPage))
  const search = query.toString()
  return search ? `/capitole/${lesson.chapterId}?${search}` : `/capitole/${lesson.chapterId}`
}
