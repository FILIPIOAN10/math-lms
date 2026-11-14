import type { ResultCheck } from '@/lib/api'

/**
 * What the final result typed for one requirement says against the barem's (V72) - compared on the server by value,
 * with no AI: for the teacher next to the points (a right result only proposes them), and for the student on the
 * result page once the barem is theirs to see. Nothing is shown for a requirement that asks for no result. The typed
 * text is the student's own and is shown as plain text, never typeset.
 */
export function ResultCheckLine({ check, typed, expected, audience, className }: {
  check?: ResultCheck | null
  typed?: string | null
  expected?: string | null
  audience: 'teacher' | 'student'
  className?: string
}) {
  const teacher = audience === 'teacher'
  if (!check) {
    // The student's page while the test can still be handed in again: what they typed, with no verdict.
    if (teacher || !typed) return null
    return (
      <p className={className} data-testid="result-check">
        Rezultatul tău: <span className="font-medium tabular-nums">{typed}</span>
      </p>
    )
  }
  if (check === 'MISSING') {
    return (
      <p className={className} data-testid="result-check" data-check="MISSING">
        <span className="text-muted-foreground">
          {teacher ? 'Elevul nu a scris rezultatul final' : 'Nu ai scris rezultatul final'}
        </span>
        {expected && <span className="text-muted-foreground"> · în barem: <span className="tabular-nums">{expected}</span></span>}
      </p>
    )
  }
  const right = check === 'RIGHT'
  return (
    <p className={className} data-testid="result-check" data-check={check}>
      {teacher ? 'Rezultat scris de elev: ' : 'Rezultatul tău: '}
      <span className="font-medium tabular-nums">{typed}</span>{' '}
      <span className={right ? 'font-medium text-emerald-700 dark:text-emerald-300' : 'font-medium text-destructive'}>
        {right ? '✓ corect' : '✗ greșit'}
      </span>
      {!right && expected && <span className="text-muted-foreground"> · în barem: <span className="tabular-nums">{expected}</span></span>}
      {right && teacher && <span className="text-muted-foreground"> · verifică rezolvarea din poză</span>}
    </p>
  )
}
