import { Fragment, useCallback, useEffect, useId, useLayoutEffect, useRef, useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import { Button } from '@/components/ui/button'
import { Card, CardContent } from '@/components/ui/card'
import { Textarea } from '@/components/ui/textarea'
import { ConfirmDialog } from '@/components/ConfirmDialog'
import { AttemptStatusBadge } from '@/components/AttemptStatusBadge'
import { MathContent } from '@/components/MathContent'
import { PhotoPages } from '@/components/PhotoPages'
import { QuestionImage } from '@/components/QuestionImage'
import { GradeByItem } from '@/components/GradeByItem'
import { ItemGradeForm } from '@/components/grading/ItemGradeForm'
import { SectionHeadingView } from '@/components/SectionHeadingView'
import { numberItems } from '@/lib/sections'
import {
  attemptPhotoUrl,
  commentOnAttempt,
  finalizeGrading,
  getAttemptForGrading,
  gradeOpenItem,
  gradeOpenItemParts,
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
import { useAutosave, type SaveStatus } from '@/lib/useAutosave'
import { gradedItem, hasParts, partsPayload } from '@/lib/grading'
import { useCommentSnippets, type SnippetBank } from '@/lib/useCommentSnippets'

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
  bank,
  onGraded,
  registerFlush,
  onStatus,
}: {
  attempt: AdminAttemptDetail
  item: AdminItemReview
  bank: SnippetBank
  /** The answer as graded once the server accepted it, to update the page without fetching the paper again. */
  onGraded: (item: AdminItemReview) => void
  registerFlush: (flush: (() => Promise<boolean>) | null) => void
  onStatus: (status: SaveStatus) => void
}) {
  return (
    <div className="space-y-3">
      {item.photoIds.length > 0 ? (
        <PhotoPages
          stacked
          urls={item.photoIds.map((photoId) => attemptPhotoUrl(attempt.attemptId, item.itemId, photoId))}
          alt={(page) => `Rezolvarea trimisă de elev, pagina ${page} din ${item.photoIds.length}`}
        />
      ) : (
        <p className="text-sm text-muted-foreground">Elevul nu a trimis nicio poză.</p>
      )}

      {item.barem && (
        <div className="rounded-lg bg-muted p-3 text-sm">
          <p className="mb-1 font-medium">Barem</p>
          <MathContent>{item.barem}</MathContent>
        </div>
      )}

      <ItemGradeForm
        item={item}
        readOnly={attempt.status !== 'SUBMITTED'}
        bank={bank}
        registerFlush={registerFlush}
        onStatus={onStatus}
        save={async (grade) => {
          const comment = grade.comment.trim() === '' ? null : grade.comment.trim()
          if (hasParts(item)) {
            await gradeOpenItemParts(attempt.attemptId, item.itemId, partsPayload(item, grade), comment)
          } else {
            await gradeOpenItem(attempt.attemptId, item.itemId, grade.points!, comment)
          }
          onGraded(gradedItem(item, grade))
        }}
      />
    </div>
  )
}

// ---------- The comment on the whole paper ----------

/**
 * One comment for the whole paper, saved when the teacher leaves the box. Open while grading AND after: an all-grilă
 * test is graded the moment it is handed in, and the teacher may still want to say something about it.
 */
function OverallComment({
  attempt,
  registerFlush,
  onStatus,
}: {
  attempt: AdminAttemptDetail
  registerFlush: (flush: (() => Promise<boolean>) | null) => void
  onStatus: (status: SaveStatus) => void
}) {
  const id = useId()
  const { value, setValue, status, error, flush } = useAutosave<string>(
    attempt.teacherComment ?? '',
    (comment) => commentOnAttempt(attempt.attemptId, comment.trim()),
    { equals: (a, b) => a.trim() === b.trim(), isReady: () => true },
  )
  const register = useRef(registerFlush)
  const report = useRef(onStatus)
  useLayoutEffect(() => {
    register.current = registerFlush
    report.current = onStatus
  })
  useEffect(() => {
    register.current(flush)
    return () => register.current(null)
  }, [flush])
  useEffect(() => report.current(status), [status])

  return (
    <Card>
      <CardContent className="space-y-2 py-4">
        <label htmlFor={id} className="text-sm font-medium">Comentariu pentru toată lucrarea (opțional)</label>
        <Textarea
          id={id}
          value={value}
          maxLength={MAX_COMMENT}
          onChange={(e) => setValue(e.target.value)}
          onBlur={() => void flush()}
          placeholder="Îl văd elevul și părintele, împreună cu rezultatul. Se salvează singur."
        />
        <p role="status" className="text-sm">
          {status === 'saving' && <span className="text-muted-foreground">Se salvează…</span>}
          {status === 'saved' && <span className="text-emerald-700 dark:text-emerald-300">✓ Comentariu salvat</span>}
          {status === 'error' && (
            <span className="text-destructive">
              Nesalvat: {errorMessage(error)}{' '}
              <Button type="button" size="xs" variant="outline" onClick={() => void flush()}>Reîncearcă</Button>
            </span>
          )}
        </p>
      </CardContent>
    </Card>
  )
}

// ---------- The page ----------

/** The quizzes in the queue with their waiting papers, in queue order — the way into "Corectează pe întrebare" (T4). */
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
 * papers of a quiz ("Corectează pe întrebare").
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
  const bank = useCommentSnippets()
  // Every form on the open paper: how it stands, and how to send what it still holds (before finalising).
  const flushes = useRef(new Map<string, () => Promise<boolean>>())
  const [statuses, setStatuses] = useState<Record<string, SaveStatus>>({})
  const registerFlush = useCallback((key: string, flush: (() => Promise<boolean>) | null) => {
    if (flush) flushes.current.set(key, flush)
    else flushes.current.delete(key)
  }, [])
  const reportStatus = useCallback((key: string, status: SaveStatus) => {
    setStatuses((all) => (all[key] === status ? all : { ...all, [key]: status }))
  }, [])
  const unsaved = Object.values(statuses).some((st) => st === 'dirty' || st === 'saving' || st === 'error' || st === 'incomplete')

  // Closing the tab with something not yet on the server: the browser asks first.
  useEffect(() => {
    if (!unsaved) return
    const warn = (e: BeforeUnloadEvent) => e.preventDefault()
    window.addEventListener('beforeunload', warn)
    return () => window.removeEventListener('beforeunload', warn)
  }, [unsaved])

  /** Sends everything still pending on the open paper; false if something could not be saved. */
  async function flushAll(): Promise<boolean> {
    const results = await Promise.all([...flushes.current.values()].map((f) => f()))
    return results.every(Boolean)
  }

  /** "Finalizează": first whatever is still being typed, then the confirmation. */
  async function askToFinalize(then: 'list' | 'next') {
    setError(null)
    if (!(await flushAll())) {
      setError('Unele punctaje sau comentarii nu s-au salvat. Verifică întrebările marcate, apoi finalizează.')
      return
    }
    setConfirmingFinal(then)
  }

  /** A grade the server accepted: shown on the page without fetching the paper again. */
  const graded = useCallback((updated: AdminItemReview) => {
    setDetail((d) => d && { ...d, items: d.items.map((i) => (i.itemId === updated.itemId ? updated : i)) })
  }, [])
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
      const paper = await getAttemptForGrading(attemptId)
      setStatuses({}) // the forms of the paper before are gone
      setDetail(paper)
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
          title={byItem ? `${byItem.quizTitle} — pe întrebare` : detail ? `${detail.studentName} — ${detail.quizTitle}` : 'Corectură'}
          description={byItem || detail ? undefined : 'Lucrările predate, cu întrebările care așteaptă nota ta.'}
          breadcrumbs={byItem ? [
            { label: 'Corectură', onClick: () => { setByItem(null); void reloadList() } },
            { label: `${byItem.quizTitle} — pe întrebare` },
          ] : detail ? [
            { label: 'Corectură', onClick: closeDetail },
            { label: `${detail.studentName} — ${detail.quizTitle}` },
          ] : undefined}
        />

        {error && <p className="text-sm text-destructive">{error}</p>}
        {notice && !detail && !byItem && <p role="status" className="text-sm text-emerald-700 dark:text-emerald-300">{notice}</p>}

        {byItem ? (
          <GradeByItem
            bank={bank}
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
                  <QuestionImage itemId={item.itemId} image={item.image} />

                  {item.type === 'SINGLE_CHOICE' ? (
                    <div className="space-y-1 text-sm">
                      <p className={item.correct ? 'text-emerald-700 dark:text-emerald-300' : 'text-destructive'}>
                        {item.correct ? '✓ ' : '✗ '}Ales:{' '}
                        {item.selectedOptionText === null ? (
                          <em>fără răspuns</em>
                        ) : (
                          <>
                            {item.selectedOptionLetter && `${item.selectedOptionLetter}) `}
                            <MathContent inline>{item.selectedOptionText}</MathContent>
                          </>
                        )}{' '}
                        ({item.awardedPoints ?? 0} p, corectat automat)
                      </p>
                      {!item.correct && item.correctOptionText !== null && (
                        <p>
                          Corect: {item.correctOptionLetter && `${item.correctOptionLetter}) `}
                          <MathContent inline>{item.correctOptionText}</MathContent>
                        </p>
                      )}
                    </div>
                  ) : (
                    <OpenItemGrader
                      key={`${detail.attemptId}-${item.itemId}`}
                      attempt={detail}
                      item={item}
                      bank={bank}
                      onGraded={graded}
                      registerFlush={(f) => registerFlush(`item-${item.itemId}`, f)}
                      onStatus={(st) => reportStatus(`item-${item.itemId}`, st)}
                    />
                  )}
                </CardContent>
              </Card>
              </Fragment>
            ))}

            <OverallComment
              key={detail.attemptId}
              attempt={detail}
              registerFlush={(f) => registerFlush('overall', f)}
              onStatus={(st) => reportStatus('overall', st)}
            />

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
                  <p className="text-sm text-muted-foreground">Notează toate întrebările cu rezolvare ca să poți finaliza.</p>
                )}
                <Button
                  variant={othersWaiting > 0 ? 'outline' : 'default'}
                  onClick={() => void askToFinalize('list')}
                  disabled={!allOpenGraded || finalizing}
                  data-testid="grade-finalize"
                >
                  {finalizing ? 'Se finalizează...' : 'Finalizează nota'}
                </Button>
                {othersWaiting > 0 && (
                  <Button onClick={() => void askToFinalize('next')} disabled={!allOpenGraded || finalizing} data-testid="grade-finalize-next">
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
              <section aria-label="Pe întrebare" className="space-y-2">
                <p className="text-sm text-muted-foreground">
                  Mai repede și mai unitar: aceeași întrebare la toate lucrările unui test, una după alta.
                </p>
                {groupByQuiz(attempts).map((g) => (
                  <Card key={g.quizId}>
                    <CardContent data-testid="quiz-group" className="flex flex-wrap items-center justify-between gap-3 py-3">
                      <p className="font-medium">
                        {g.quizTitle}{' '}
                        <span className="font-normal text-muted-foreground">· {roCount(g.attemptIds.length, 'lucrare', 'lucrări')}</span>
                      </p>
                      <Button size="sm" variant="secondary" onClick={() => setByItem({ quizTitle: g.quizTitle, attemptIds: g.attemptIds })}>
                        Corectează pe întrebare
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
