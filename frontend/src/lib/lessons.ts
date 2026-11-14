import { type TutorLessonLink } from '@/lib/api'

/** The chapter page of a lesson link, scrolled to its section when it is one. */
export function lessonHref(lesson: TutorLessonLink): string {
  return lesson.sectionId === null ? `/capitole/${lesson.chapterId}` : `/capitole/${lesson.chapterId}?sectiune=${lesson.sectionId}`
}
