import { type ReviewStatus } from '@/lib/api'
import { roCount } from '@/lib/format'

/**
 * One line about a student's daily review (E4): done today / how many are left / nothing due, plus the streak.
 * Nothing is shown before the student has any question in review.
 */
export function ReviewStatusLine({ review, testId }: { review: ReviewStatus | null; testId?: string }) {
  if (!review || review.totalCards === 0) return null
  return (
    <p data-testid={testId} className="text-sm">
      {review.remainingToday === 0 && review.doneToday > 0 ? (
        <span className="font-medium text-emerald-700 dark:text-emerald-300">Recapitulare: făcută azi ✓</span>
      ) : review.remainingToday > 0 ? (
        <span className="font-medium">Recapitulare: mai are {review.remainingToday} azi</span>
      ) : (
        <span className="text-muted-foreground">Recapitulare: nimic de făcut azi</span>
      )}
      {review.streak > 0 && <span className="text-muted-foreground"> · seria {roCount(review.streak, 'zi', 'zile')}</span>}
    </p>
  )
}
