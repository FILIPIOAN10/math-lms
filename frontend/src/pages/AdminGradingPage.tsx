import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { Button, buttonVariants } from '@/components/ui/button'
import { Card, CardContent } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { AttemptStatusBadge } from '@/components/AttemptStatusBadge'
import { MathContent } from '@/components/MathContent'
import {
  attemptPhotoUrl,
  finalizeGrading,
  getAttemptForGrading,
  gradeOpenItem,
  listAttemptsForGrading,
  type AdminAttemptDetail,
  type AdminAttemptSummary,
  type AdminItemReview,
  type QuizAttemptStatus,
} from '@/lib/api'
import { errorMessage } from '@/lib/errors'

const selectClass =
  'h-8 rounded-lg border border-border bg-background px-2 text-sm outline-none focus-visible:border-ring focus-visible:ring-3 focus-visible:ring-ring/50'

function formatDate(iso: string | null): string {
  return iso ? new Date(iso).toLocaleString('ro-RO', { dateStyle: 'medium', timeStyle: 'short' }) : '—'
}

// ---------- One OPEN item: photo + barem + points ----------

function OpenItemGrader({
  attempt,
  item,
  onSaved,
}: {
  attempt: AdminAttemptDetail
  item: AdminItemReview
  onSaved: () => Promise<void>
}) {
  const [points, setPoints] = useState(item.awardedPoints === null ? '' : String(item.awardedPoints))
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const readOnly = attempt.status !== 'SUBMITTED'

  async function save() {
    const value = Number(points)
    if (points.trim() === '' || !Number.isInteger(value) || value < 0 || value > item.points) {
      setError(`Punctajul trebuie să fie un număr întreg între 0 și ${item.points}.`)
      return
    }
    setBusy(true)
    setError(null)
    try {
      await gradeOpenItem(attempt.attemptId, item.itemId, value)
      await onSaved()
    } catch (e) {
      setError(errorMessage(e))
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="space-y-3">
      {item.photoUploaded ? (
        <a href={attemptPhotoUrl(attempt.attemptId, item.itemId)} target="_blank" rel="noreferrer">
          <img
            src={attemptPhotoUrl(attempt.attemptId, item.itemId)}
            alt="Rezolvarea trimisă de elev"
            className="max-h-96 rounded-lg border object-contain"
          />
        </a>
      ) : (
        <p className="text-sm text-muted-foreground">Elevul nu a trimis nicio poză.</p>
      )}

      {item.barem && (
        <div className="rounded-lg bg-muted p-3 text-sm">
          <p className="mb-1 font-medium">Barem</p>
          <MathContent>{item.barem}</MathContent>
        </div>
      )}

      <div className="flex flex-wrap items-center gap-2">
        <Input
          type="number"
          min={0}
          max={item.points}
          value={points}
          onChange={(e) => setPoints(e.target.value)}
          disabled={readOnly || busy}
          className="w-24"
          aria-label="Puncte acordate"
        />
        <span className="text-sm text-muted-foreground">/ {item.points} p</span>
        {!readOnly && (
          <Button size="sm" onClick={save} disabled={busy}>
            {busy ? 'Se salvează...' : 'Salvează punctajul'}
          </Button>
        )}
        {item.awardedPoints !== null && (
          <span className="text-sm text-emerald-700 dark:text-emerald-300">✓ notat cu {item.awardedPoints} p</span>
        )}
      </div>
      {error && <p className="text-sm text-destructive">{error}</p>}
    </div>
  )
}

// ---------- The page ----------

/** Teacher grading (Q10): the queue of submitted attempts, then one attempt at a time. */
export function AdminGradingPage() {
  const [filter, setFilter] = useState<QuizAttemptStatus>('SUBMITTED')
  const [attempts, setAttempts] = useState<AdminAttemptSummary[]>([])
  const [detail, setDetail] = useState<AdminAttemptDetail | null>(null)
  const [loading, setLoading] = useState(true)
  const [finalizing, setFinalizing] = useState(false)
  const [error, setError] = useState<string | null>(null)

  async function reloadList(status: QuizAttemptStatus = filter) {
    setLoading(true)
    try {
      setAttempts(await listAttemptsForGrading(status))
    } catch (e) {
      setError(errorMessage(e))
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    let cancelled = false
    listAttemptsForGrading(filter)
      .then((list) => !cancelled && setAttempts(list))
      .catch((e) => !cancelled && setError(errorMessage(e)))
      .finally(() => !cancelled && setLoading(false))
    return () => {
      cancelled = true
    }
  }, [filter])

  async function open(attemptId: number) {
    setError(null)
    try {
      setDetail(await getAttemptForGrading(attemptId))
    } catch (e) {
      setError(errorMessage(e))
    }
  }

  async function finalize() {
    if (!detail || !window.confirm('Finalizezi nota? După asta punctajele nu se mai pot modifica.')) return
    setFinalizing(true)
    setError(null)
    try {
      await finalizeGrading(detail.attemptId)
      setDetail(null)
      await reloadList()
    } catch (e) {
      setError(errorMessage(e))
    } finally {
      setFinalizing(false)
    }
  }

  const allOpenGraded = detail?.items.every((i) => i.type !== 'OPEN' || i.awardedPoints !== null) ?? false
  const pointsSoFar = detail?.items.reduce((sum, i) => sum + (i.awardedPoints ?? 0), 0) ?? 0

  return (
    <div className="min-h-screen bg-muted p-4">
      <div className="mx-auto max-w-3xl space-y-4">
        <div className="flex items-center justify-between">
          <nav className="flex items-center gap-1 text-sm text-muted-foreground">
            <button className="hover:text-foreground hover:underline" onClick={() => setDetail(null)}>
              Corectură
            </button>
            {detail && (
              <>
                <span>/</span>
                <span className="text-foreground">{detail.studentName} — {detail.quizTitle}</span>
              </>
            )}
          </nav>
          <Link to="/" className={buttonVariants({ variant: 'outline', size: 'sm' })}>Acasă</Link>
        </div>

        {error && <p className="text-sm text-destructive">{error}</p>}

        {detail ? (
          /* ----- One attempt ----- */
          <div className="space-y-4">
            <Card>
              <CardContent className="flex flex-wrap items-center justify-between gap-2 py-3">
                <div>
                  <p className="font-medium">{detail.studentName}</p>
                  <p className="text-sm text-muted-foreground">Trimis: {formatDate(detail.submittedAt)}</p>
                </div>
                <div className="flex items-center gap-3">
                  <AttemptStatusBadge status={detail.status} />
                  <span className="text-sm">
                    {detail.status === 'GRADED' ? detail.score : pointsSoFar} / {detail.maxScore} p
                  </span>
                </div>
              </CardContent>
            </Card>

            {detail.items.map((item, index) => (
              <Card key={item.itemId}>
                <CardContent className="space-y-3 py-4">
                  <p className="text-sm font-medium">
                    {index + 1}. {item.type === 'SINGLE_CHOICE' ? 'Grilă' : 'Rezolvare'} · {item.points} p
                  </p>
                  <MathContent className="text-sm">{item.statement}</MathContent>

                  {item.type === 'SINGLE_CHOICE' ? (
                    <div className="space-y-1 text-sm">
                      <p className={item.correct ? 'text-emerald-700 dark:text-emerald-300' : 'text-destructive'}>
                        {item.correct ? '✓ ' : '✗ '}Ales:{' '}
                        {item.selectedOptionText === null ? (
                          <em>fără răspuns</em>
                        ) : (
                          <MathContent className="inline">{item.selectedOptionText}</MathContent>
                        )}{' '}
                        ({item.awardedPoints ?? 0} p, corectat automat)
                      </p>
                      {!item.correct && item.correctOptionText !== null && (
                        <p>
                          Corect: <MathContent className="inline">{item.correctOptionText}</MathContent>
                        </p>
                      )}
                    </div>
                  ) : (
                    <OpenItemGrader
                      key={`${detail.attemptId}-${item.itemId}`}
                      attempt={detail}
                      item={item}
                      onSaved={() => open(detail.attemptId)}
                    />
                  )}
                </CardContent>
              </Card>
            ))}

            {detail.status === 'SUBMITTED' && (
              <div className="flex items-center justify-end gap-3">
                {!allOpenGraded && (
                  <p className="text-sm text-muted-foreground">Notează toate subiectele cu rezolvare ca să poți finaliza.</p>
                )}
                <Button onClick={finalize} disabled={!allOpenGraded || finalizing}>
                  {finalizing ? 'Se finalizează...' : 'Finalizează nota'}
                </Button>
              </div>
            )}
          </div>
        ) : (
          /* ----- The queue ----- */
          <>
            <div className="flex items-center justify-between gap-2">
              <h1 className="text-xl font-semibold">Lucrări</h1>
              <select
                className={selectClass}
                value={filter}
                onChange={(e) => {
                  setLoading(true)
                  setError(null)
                  setFilter(e.target.value as QuizAttemptStatus)
                }}
              >
                <option value="SUBMITTED">De corectat</option>
                <option value="GRADED">Notate</option>
              </select>
            </div>
            {loading && <p className="text-muted-foreground">Se încarcă...</p>}
            {!loading && attempts.length === 0 && (
              <p className="text-muted-foreground">
                {filter === 'SUBMITTED' ? 'Nicio lucrare de corectat. 🎉' : 'Nicio lucrare notată încă.'}
              </p>
            )}
            <div className="space-y-2">
              {attempts.map((a) => (
                <Card key={a.attemptId}>
                  <CardContent className="flex items-center justify-between gap-3 py-3">
                    <div className="min-w-0 space-y-1">
                      <p className="truncate font-medium">{a.studentName} — {a.quizTitle}</p>
                      <p className="flex flex-wrap items-center gap-2 text-sm text-muted-foreground">
                        <AttemptStatusBadge status={a.status} />
                        <span>{formatDate(a.submittedAt)}</span>
                        {a.score !== null && <span className="text-foreground">{a.score} p</span>}
                      </p>
                    </div>
                    <Button size="sm" variant={a.status === 'SUBMITTED' ? 'default' : 'outline'} onClick={() => open(a.attemptId)}>
                      {a.status === 'SUBMITTED' ? 'Corectează' : 'Vezi'}
                    </Button>
                  </CardContent>
                </Card>
              ))}
            </div>
          </>
        )}
      </div>
    </div>
  )
}
