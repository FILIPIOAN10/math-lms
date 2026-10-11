import { Fragment, useEffect, useId, useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import { Button } from '@/components/ui/button'
import { Card, CardContent } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Textarea } from '@/components/ui/textarea'
import { ConfirmDialog } from '@/components/ConfirmDialog'
import { AttemptStatusBadge } from '@/components/AttemptStatusBadge'
import { MathContent } from '@/components/MathContent'
import { GradeByItem } from '@/components/GradeByItem'
import { SectionHeadingView } from '@/components/SectionHeadingView'
import { numberItems } from '@/lib/sections'
import {
  attemptPhotoUrl,
  commentOnAttempt,
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
import { scoreLine } from '@/lib/grades'
import { roCount } from '@/lib/format'
import { PageHeader } from '@/components/PageHeader'

/** Same limit as the server (V20 columns). */
const MAX_COMMENT = 2000

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
  const [comment, setComment] = useState(item.teacherComment ?? '')
  const commentId = useId()
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
      await gradeOpenItem(attempt.attemptId, item.itemId, value, comment.trim() === '' ? null : comment.trim())
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

      <div className="space-y-1">
        <label htmlFor={commentId} className="text-sm font-medium">Comentariu pentru elev (opțional)</label>
        <Textarea
          id={commentId}
          value={comment}
          maxLength={MAX_COMMENT}
          onChange={(e) => setComment(e.target.value)}
          disabled={readOnly || busy}
          placeholder="Ex.: Raționament corect, dar ai uitat unitatea de măsură."
          className="min-h-16"
        />
      </div>

      <div className="flex flex-wrap items-center gap-2">
        <Input
          type="number"
          data-testid="grade-points"
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
          <Button size="sm" onClick={save} disabled={busy} data-testid="grade-save">
            {busy ? 'Se salvează...' : 'Salvează nota și comentariul'}
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

// ---------- The comment on the whole paper ----------

/**
 * One comment for the whole paper. Open while grading AND after: an all-grilă test is graded the moment it is handed
 * in, and the teacher may still want to say something about it.
 */
function OverallComment({ attempt, onSaved }: { attempt: AdminAttemptDetail; onSaved: () => Promise<void> }) {
  const [comment, setComment] = useState(attempt.teacherComment ?? '')
  const [busy, setBusy] = useState(false)
  const [saved, setSaved] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const id = useId()

  async function save() {
    setBusy(true)
    setSaved(false)
    setError(null)
    try {
      await commentOnAttempt(attempt.attemptId, comment.trim())
      setSaved(true)
      await onSaved()
    } catch (e) {
      setError(errorMessage(e))
    } finally {
      setBusy(false)
    }
  }

  return (
    <Card>
      <CardContent className="space-y-2 py-4">
        <label htmlFor={id} className="text-sm font-medium">Comentariu pentru toată lucrarea (opțional)</label>
        <Textarea
          id={id}
          value={comment}
          maxLength={MAX_COMMENT}
          onChange={(e) => {
            setComment(e.target.value)
            setSaved(false)
          }}
          disabled={busy}
          placeholder="Îl văd elevul și părintele, împreună cu rezultatul."
        />
        <div className="flex flex-wrap items-center gap-3">
          <Button size="sm" variant="outline" onClick={save} disabled={busy}>
            {busy ? 'Se salvează...' : 'Salvează comentariul'}
          </Button>
          {saved && <span className="text-sm text-emerald-700 dark:text-emerald-300">✓ Comentariu salvat</span>}
        </div>
        {error && <p className="text-sm text-destructive">{error}</p>}
      </CardContent>
    </Card>
  )
}

// ---------- The page ----------

/** The quizzes in the queue with their waiting papers, in queue order — the way into "Corectează pe subiect" (T4). */
function groupByQuiz(attempts: AdminAttemptSummary[]): { quizId: number; quizTitle: string; attemptIds: number[] }[] {
  const groups = new Map<number, { quizId: number; quizTitle: string; attemptIds: number[] }>()
  for (const a of attempts) {
    const group = groups.get(a.quizId) ?? { quizId: a.quizId, quizTitle: a.quizTitle, attemptIds: [] }
    group.attemptIds.push(a.attemptId)
    groups.set(a.quizId, group)
  }
  return [...groups.values()]
}

/**
 * Teacher grading (Q10): the queue of submitted attempts, then one attempt at a time — with "Finalizează și treci la
 * următoarea" (T4) so a stack of papers is graded without going back to the list — or one question across all the
 * papers of a quiz ("Corectează pe subiect").
 */
export function AdminGradingPage() {
  const [filter, setFilter] = useState<QuizAttemptStatus>('SUBMITTED')
  const [attempts, setAttempts] = useState<AdminAttemptSummary[]>([])
  const [detail, setDetail] = useState<AdminAttemptDetail | null>(null)
  const [loading, setLoading] = useState(true)
  const [finalizing, setFinalizing] = useState(false)
  // Which finalise button opened the confirmation: stay on the list, or go on to the next paper.
  const [confirmingFinal, setConfirmingFinal] = useState<false | 'list' | 'next'>(false)
  const [byItem, setByItem] = useState<{ quizTitle: string; attemptIds: number[] } | null>(null)
  const [notice, setNotice] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)
  // `?attempt=12` opens that paper straight away (links from a student's page).
  const [searchParams, setSearchParams] = useSearchParams()
  const linkedAttempt = Number(searchParams.get('attempt')) || null

  async function reloadList(status: QuizAttemptStatus = filter): Promise<AdminAttemptSummary[]> {
    setLoading(true)
    try {
      const list = await listAttemptsForGrading(status)
      setAttempts(list)
      return list
    } catch (e) {
      setError(errorMessage(e))
      return []
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

  useEffect(() => {
    if (linkedAttempt !== null) void open(linkedAttempt)
    // eslint-disable-next-line react-hooks/exhaustive-deps -- only when the address names another paper
  }, [linkedAttempt])

  /** Back to the queue; also forgets a paper named in the address, so a refresh does not reopen it. */
  function closeDetail() {
    setDetail(null)
    if (linkedAttempt !== null) setSearchParams({}, { replace: true })
  }

  async function open(attemptId: number) {
    setError(null)
    setNotice(null)
    try {
      setDetail(await getAttemptForGrading(attemptId))
    } catch (e) {
      setError(errorMessage(e))
    }
  }

  async function finalize(then: 'list' | 'next') {
    if (!detail) return
    setConfirmingFinal(false)
    setFinalizing(true)
    setError(null)
    try {
      await finalizeGrading(detail.attemptId)
      const before = attempts.map((a) => a.attemptId)
      const remaining = filter === 'SUBMITTED' ? await reloadList() : await listAttemptsForGrading('SUBMITTED')
      // The paper after this one in the queue the teacher was looking at, else the first one still waiting.
      const after = before.slice(before.indexOf(detail.attemptId) + 1)
      const next = remaining.find((a) => after.includes(a.attemptId)) ?? remaining[0]
      if (then === 'next' && next) {
        await open(next.attemptId)
      } else {
        closeDetail()
        if (then === 'next') setNotice('Gata — nu mai e nicio lucrare de corectat. 🎉')
      }
    } catch (e) {
      setError(errorMessage(e))
    } finally {
      setFinalizing(false)
    }
  }

  const allOpenGraded = detail?.items.every((i) => i.type !== 'OPEN' || i.awardedPoints !== null) ?? false
  const othersWaiting = detail ? attempts.filter((a) => a.status === 'SUBMITTED' && a.attemptId !== detail.attemptId).length : 0
  const pointsSoFar = detail?.items.reduce((sum, i) => sum + (i.awardedPoints ?? 0), 0) ?? 0

  return (
    <div className="p-4">
      <div className="mx-auto max-w-3xl space-y-4">
        <PageHeader
          title={byItem ? `${byItem.quizTitle} — pe subiect` : detail ? `${detail.studentName} — ${detail.quizTitle}` : 'Corectură'}
          description={byItem || detail ? undefined : 'Lucrările predate, cu subiectele care așteaptă nota ta.'}
          breadcrumbs={byItem ? [
            { label: 'Corectură', onClick: () => { setByItem(null); void reloadList() } },
            { label: `${byItem.quizTitle} — pe subiect` },
          ] : detail ? [
            { label: 'Corectură', onClick: closeDetail },
            { label: `${detail.studentName} — ${detail.quizTitle}` },
          ] : undefined}
        />

        {error && <p className="text-sm text-destructive">{error}</p>}
        {notice && !detail && !byItem && <p role="status" className="text-sm text-emerald-700 dark:text-emerald-300">{notice}</p>}

        {byItem ? (
          <GradeByItem
            quizTitle={byItem.quizTitle}
            attemptIds={byItem.attemptIds}
            onExit={() => {
              setByItem(null)
              void reloadList()
            }}
          />
        ) : detail ? (
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
                    {detail.status === 'GRADED' && detail.score !== null
                      ? scoreLine(detail.score, detail.maxScore)
                      : `${pointsSoFar} / ${detail.maxScore} p`}
                  </span>
                </div>
              </CardContent>
            </Card>

            {numberItems(detail.items).map(({ item, local, heading }) => (
              <Fragment key={item.itemId}>
              {heading && <SectionHeadingView heading={heading} />}
              <Card>
                <CardContent className="space-y-3 py-4">
                  <p className="text-sm font-medium">
                    {local}. {item.type === 'SINGLE_CHOICE' ? 'Grilă' : 'Rezolvare'} · {item.points} p
                  </p>
                  <MathContent className="text-sm">{item.statement}</MathContent>

                  {item.type === 'SINGLE_CHOICE' ? (
                    <div className="space-y-1 text-sm">
                      <p className={item.correct ? 'text-emerald-700 dark:text-emerald-300' : 'text-destructive'}>
                        {item.correct ? '✓ ' : '✗ '}Ales:{' '}
                        {item.selectedOptionText === null ? (
                          <em>fără răspuns</em>
                        ) : (
                          <MathContent inline>{item.selectedOptionText}</MathContent>
                        )}{' '}
                        ({item.awardedPoints ?? 0} p, corectat automat)
                      </p>
                      {!item.correct && item.correctOptionText !== null && (
                        <p>
                          Corect: <MathContent inline>{item.correctOptionText}</MathContent>
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
              </Fragment>
            ))}

            <OverallComment key={detail.attemptId} attempt={detail} onSaved={() => open(detail.attemptId)} />

            <ConfirmDialog
              open={confirmingFinal !== false}
              title="Finalizezi nota?"
              confirmLabel={confirmingFinal === 'next' ? 'Finalizează și treci mai departe' : 'Finalizează'}
              onConfirm={() => void finalize(confirmingFinal === 'next' ? 'next' : 'list')}
              onCancel={() => setConfirmingFinal(false)}
            >
              După asta punctajele nu se mai pot modifica. Comentariul pentru toată lucrarea îl poți schimba și după.
            </ConfirmDialog>

            {detail.status === 'SUBMITTED' && (
              <div className="flex flex-wrap items-center justify-end gap-3">
                {!allOpenGraded && (
                  <p className="text-sm text-muted-foreground">Notează toate subiectele cu rezolvare ca să poți finaliza.</p>
                )}
                <Button
                  variant={othersWaiting > 0 ? 'outline' : 'default'}
                  onClick={() => setConfirmingFinal('list')}
                  disabled={!allOpenGraded || finalizing}
                  data-testid="grade-finalize"
                >
                  {finalizing ? 'Se finalizează...' : 'Finalizează nota'}
                </Button>
                {othersWaiting > 0 && (
                  <Button onClick={() => setConfirmingFinal('next')} disabled={!allOpenGraded || finalizing} data-testid="grade-finalize-next">
                    Finalizează și treci la următoarea ({othersWaiting} {othersWaiting === 1 ? 'rămasă' : 'rămase'})
                  </Button>
                )}
              </div>
            )}
          </div>
        ) : (
          /* ----- The queue ----- */
          <>
            <div className="flex items-center justify-between gap-2">
              <h2 className="font-medium">Lucrări</h2>
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
            {!loading && filter === 'SUBMITTED' && attempts.length > 0 && (
              <section aria-label="Pe subiect" className="space-y-2">
                <p className="text-sm text-muted-foreground">
                  Mai repede și mai unitar: același subiect la toate lucrările unui test, una după alta.
                </p>
                {groupByQuiz(attempts).map((g) => (
                  <Card key={g.quizId}>
                    <CardContent data-testid="quiz-group" className="flex flex-wrap items-center justify-between gap-3 py-3">
                      <p className="font-medium">
                        {g.quizTitle}{' '}
                        <span className="font-normal text-muted-foreground">· {roCount(g.attemptIds.length, 'lucrare', 'lucrări')}</span>
                      </p>
                      <Button size="sm" variant="secondary" onClick={() => setByItem({ quizTitle: g.quizTitle, attemptIds: g.attemptIds })}>
                        Corectează pe subiect
                      </Button>
                    </CardContent>
                  </Card>
                ))}
                <h3 className="pt-2 text-sm font-medium">Sau lucrare cu lucrare</h3>
              </section>
            )}
            {!loading && attempts.length === 0 && (
              <p className="text-muted-foreground">
                {filter === 'SUBMITTED' ? 'Nicio lucrare de corectat. 🎉' : 'Nicio lucrare notată încă.'}
              </p>
            )}
            <div className="space-y-2">
              {attempts.map((a) => (
                <Card key={a.attemptId}>
                  <CardContent data-testid="attempt-row" className="flex items-center justify-between gap-3 py-3">
                    <div className="min-w-0 space-y-1">
                      <p className="truncate font-medium">{a.studentName} — {a.quizTitle}</p>
                      <p className="flex flex-wrap items-center gap-2 text-sm text-muted-foreground">
                        <AttemptStatusBadge status={a.status} />
                        <span>{formatDate(a.submittedAt)}</span>
                        {a.score !== null && <span className="text-foreground">{scoreLine(a.score, a.maxScore)}</span>}
                      </p>
                    </div>
                    <Button size="sm" variant={a.status === 'SUBMITTED' ? 'default' : 'outline'} onClick={() => open(a.attemptId)} data-testid="attempt-open">
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
