import { useEffect, useState } from 'react'
import {
  getAutoProposals as getAutoProposalsRequest,
  setAutoProposals as setAutoProposalsRequest,
} from '@/lib/api'
import { errorMessage } from '@/lib/errors'
import { roCount } from '@/lib/format'

/**
 * "Propuneri AI automate" for one test: when on, every hand-in asks the AI for grade proposals by itself; when off
 * (the default), the teacher asks per answer with "Cere propunere AI". Each proposal is a paid call to the strongest
 * model, so it is a choice per test. Turning it on also asks for the papers already waiting.
 */
export function AiAutoProposalsSwitch({
  quizId,
  onQueued,
  getAutoProposals = getAutoProposalsRequest,
  setAutoProposals = setAutoProposalsRequest,
}: {
  quizId: number
  /** Called after turning on queued some proposals, so the open paper can show them coming. */
  onQueued?: () => void
  getAutoProposals?: typeof getAutoProposalsRequest
  setAutoProposals?: typeof setAutoProposalsRequest
}) {
  const [auto, setAuto] = useState<boolean | null>(null)
  const [busy, setBusy] = useState(false)
  const [message, setMessage] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    // One test per mount: the grading page keys it by test, so another test starts from a fresh state.
    let cancelled = false
    getAutoProposals(quizId)
      .then((r) => !cancelled && setAuto(r.auto))
      .catch(() => undefined) // no switch shown; "Cere propunere AI" still works
    return () => {
      cancelled = true
    }
  }, [quizId, getAutoProposals])

  if (auto === null) return null

  async function toggle(on: boolean) {
    setBusy(true)
    setError(null)
    setMessage(null)
    try {
      const result = await setAutoProposals(quizId, on)
      setAuto(result.auto)
      if (on && result.queued > 0) {
        setMessage(`Cerute acum pentru ${roCount(result.queued, 'răspuns', 'răspunsuri')} care așteaptă nota.`)
        onQueued?.()
      }
    } catch (e) {
      setError(errorMessage(e))
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="space-y-1 text-sm">
      <label className="flex min-h-11 items-center gap-2">
        <input type="checkbox" className="size-4" checked={auto} disabled={busy}
               onChange={(e) => void toggle(e.target.checked)} />
        Propuneri AI automate la acest test
      </label>
      <p className="text-xs text-muted-foreground">
        {auto
          ? 'Fiecare lucrare predată primește singură propuneri AI (un apel plătit pe problemă).'
          : 'Oprit: ceri propunerea doar unde vrei, cu „Cere propunere AI”. Pornit, o primește fiecare lucrare predată.'}
      </p>
      {message && <p role="status" className="text-xs text-emerald-700 dark:text-emerald-300">{message}</p>}
      {error && <p className="text-xs text-destructive">{error}</p>}
    </div>
  )
}
