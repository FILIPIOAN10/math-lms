import { type RetakeBlock } from '@/lib/api'
import { roCount } from '@/lib/format'

/** Why another attempt cannot start, in the student's words. */
export const RETAKE_BLOCK_TEXT: Record<RetakeBlock, string> = {
  NO_ATTEMPTS_LEFT: 'Ai folosit toate încercările la acest test.',
  WAITING_FOR_GRADE: 'Lucrarea ta se corectează. Poți da testul din nou după ce primești nota.',
}

/** "1 încercare", "3 încercări", "încercări nelimitate": the limit on a teacher's list. */
export function attemptsLabel(maxAttempts: number | null): string {
  return maxAttempts === null ? 'încercări nelimitate' : roCount(maxAttempts, 'încercare', 'încercări')
}

/** What the student is told before starting: how many tries they have and which grade counts. */
export function attemptRule(maxAttempts: number | null, used: number): string {
  if (maxAttempts === 1) return 'Ai o singură încercare.'
  const tries = maxAttempts === null ? 'Poți da testul de mai multe ori.' : `Încercarea ${used + 1} din ${maxAttempts}.`
  return `${tries} Contează cea mai mare notă.`
}
