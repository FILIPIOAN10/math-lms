import { MistakeEditor } from '@/components/MistakeEditor'
import { type AdminItemReview, type TutorLessonLink } from '@/lib/api'

/**
 * „De ce e greșit” under a wrong grilă of the paper being marked: what the student reads about the option they chose,
 * and the place to write it when it is missing - most of all when students asked „De ce am greșit?” about it. The
 * explanation belongs to the option, so it reaches every student who chose it, on this test and later ones.
 */
export function GradingMistake({ item, aiOn, tutorOn, onSaved }: {
  item: AdminItemReview
  aiOn: boolean
  tutorOn: boolean
  onSaved: (mistake: string, lesson: TutorLessonLink | null) => void
}) {
  if (item.selectedOptionId == null) return null
  const requests = item.explanationRequests ?? 0
  return (
    <div
      data-testid={`grading-mistake-${item.itemId}`}
      className={
        requests > 0 && !item.mistake
          ? 'space-y-1.5 rounded-lg border border-amber-500/50 bg-amber-500/10 px-3 py-2'
          : 'space-y-1.5 rounded-lg border px-3 py-2'
      }
    >
      <p className="text-xs font-medium text-muted-foreground">
        De ce e greșit „{item.selectedOptionLetter})”
        {/* the editor itself says how many asked */}
        {item.mistake ? ' — elevii care l-au ales văd:' : requests > 0 ? '' : ' — încă fără explicație'}
      </p>
      <MistakeEditor
        // a new paper, or a saved text, starts the editor again from what the server has
        key={`${item.selectedOptionId}-${item.mistake ?? ''}`}
        option={{
          id: item.selectedOptionId,
          letter: item.selectedOptionLetter ?? '?',
          text: item.selectedOptionText ?? '',
          correct: false,
          chosen: 0,
          mistake: item.mistake ?? null,
          mistakeLesson: item.mistakeLesson ?? null,
          requests,
        }}
        aiOn={aiOn}
        tutorOn={tutorOn}
        onSaved={onSaved}
      />
    </div>
  )
}
