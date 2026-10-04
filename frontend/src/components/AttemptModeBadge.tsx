import { type AttemptMode } from '@/lib/api'

/** "Practică" pill for a practice sitting; a graded test needs no label, so it renders nothing. */
export function AttemptModeBadge({ mode }: { mode: AttemptMode }) {
  if (mode !== 'PRACTICE') return null
  return (
    <span
      data-testid="mode-badge"
      className="rounded-md bg-violet-500/15 px-2 py-0.5 text-xs font-medium text-violet-700 dark:text-violet-300"
    >
      Practică
    </span>
  )
}
