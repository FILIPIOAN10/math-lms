import { type QuizAttemptStatus } from '@/lib/api'

const LABELS: Record<QuizAttemptStatus, { text: string; className: string }> = {
  IN_PROGRESS: { text: 'În lucru', className: 'bg-amber-500/15 text-amber-700 dark:text-amber-300' },
  SUBMITTED: { text: 'Trimis — în corectare', className: 'bg-sky-500/15 text-sky-700 dark:text-sky-300' },
  GRADED: { text: 'Notat', className: 'bg-emerald-500/15 text-emerald-700 dark:text-emerald-300' },
}

/** Status pill shared by the student's attempt list, the result page and the grading screen. */
export function AttemptStatusBadge({ status }: { status: QuizAttemptStatus }) {
  const { text, className } = LABELS[status]
  return <span className={`rounded-md px-2 py-0.5 text-xs font-medium ${className}`}>{text}</span>
}
