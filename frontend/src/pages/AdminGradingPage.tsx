import { Fragment, useCallback, useEffect, useId, useLayoutEffect, useRef, useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import { Button } from '@/components/ui/button'
import { ErrorState } from '@/components/ErrorState'
import { Card, CardContent } from '@/components/ui/card'
import { Textarea } from '@/components/ui/textarea'
import { AttemptStatusBadge } from '@/components/AttemptStatusBadge'
import { MathContent } from '@/components/MathContent'
import { PhotoPages } from '@/components/PhotoPages'
import { QuestionImage } from '@/components/QuestionImage'
import { FilterBar, FilterSelect, NoMatches } from '@/components/FilterBar'
import { GradeByItem } from '@/components/GradeByItem'
import { AiAutoProposalsSwitch } from '@/components/grading/AiAutoProposalsSwitch'
import { useConfirm } from '@/components/useConfirm'
import { ItemGradeForm } from '@/components/grading/ItemGradeForm'
import { CommentPreview } from '@/components/grading/CommentPreview'
import { SectionHeadingView } from '@/components/SectionHeadingView'
import { numberItems } from '@/lib/sections'
import { itemTypeLabel } from '@/lib/numericAnswer'
import {
  attemptPhotoUrl,
  commentOnAttempt,
  finalizeGrading,
  getAiGradingSettings,
  getAiProposals,
  getAttemptForGrading,
  grantExtraAttempt,
  gradeOpenItem,
  gradeOpenItemParts,
  GRADED_PAGE,
  listAttemptsForGrading,
  reopenGrading,
  requestAiProposal,
  type AdminAttemptDetail,
  type AiProposal,
  type AdminAttemptSummary,
  type AdminItemReview,
  type QuizAttemptStatus,
} from '@/lib/api'
import { errorMessage } from '@/lib/errors'
import { scoreLine } from '@/lib/grades'
import { roCount } from '@/lib/format'
import { FILTER_FROM, matchesQuery } from '@/lib/search'
import { NAV_COUNTS_CHANGED } from '@/lib/roles'
import { PageHeader } from '@/components/PageHeader'
import { useAutosave, type SaveStatus } from '@/lib/useAutosave'
import { gradedItem, hasParts, partsPayload } from '@/lib/grading'
import { useCommentSnippets, type SnippetBank } from '@/lib/useCommentSnippets'
import { cn } from '@/lib/utils'
import { LoadingCards } from '@/components/LoadingCards'
import { selectClass } from '@/lib/formClasses'

/** Same limit as the server (V20 columns). */
const MAX_COMMENT = 2000
/** How often the AI's proposals are asked for while some are still being made. */
const AI_POLL_MS = 5000

function formatDate(iso: string | null): string {
  return iso ? new Date(iso).toLocaleString('ro-RO', { dateStyle: 'medium', timeStyle: 'short' }) : '—'
}

// ---------- One OPEN item: photo beside barem + points ----------

/**
 * The student's photo on the left, barem and points on the right, on a wide screen: the teacher reads the solution and
 * scores it without scrolling between the two. The photo stays in view while the right column scrolls. On a phone
 * they stack, photo first.
 */
function OpenItemGrader({
  attempt,
  item,
  bank,
  autoFocus,
  onGraded,
  registerFlush,
  onStatus,
  aiOn,
  aiProposal,
  onRequestAiProposal,
}: {
  attempt: AdminAttemptDetail
  item: AdminItemReview
  bank: SnippetBank
  /** The first answer still without points: the keyboard starts here. */
  autoFocus: boolean
  aiOn: boolean
  aiProposal: AiProposal | null
  onRequestAiProposal: () => void
  /** The answer as graded once the server accepted it, to update the page without fetching the paper again. */
  onGraded: (item: AdminItemReview) => void
  registerFlush: (flush: (() => Promise<boolean>) | null) => void
  onStatus: (status: SaveStatus) => void
}) {
  const hasPhotos = item.photoIds.length > 0
  return (
    <div data-testid="grade-split" className={cn('space-y-3', hasPhotos && 'lg:grid lg:grid-cols-2 lg:items-start lg:gap-6 lg:space-y-0')}>
      <div className={cn(hasPhotos && 'lg:sticky lg:top-4 lg:max-h-[calc(100vh-2rem)] lg:overflow-y-auto')}>
        {hasPhotos ? (
          <PhotoPages
            stacked
            urls={item.photoIds.map((photoId) => attemptPhotoUrl(attempt.attemptId, item.itemId, photoId))}
            alt={(page) => `Rezolvarea trimisă de elev, pagina ${page} din ${item.photoIds.length}`}
          />
        ) : (
          <p className="text-sm text-muted-foreground">Elevul nu a trimis nicio poză.</p>
        )}
      </div>

      <div className="min-w-0 space-y-3">
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
          autoFocus={autoFocus}
          registerFlush={registerFlush}
          onStatus={onStatus}
          aiOn={aiOn}
          aiProposal={aiProposal}
          onRequestAiProposal={onRequestAiProposal}
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
        <CommentPreview text={value} />
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
export function AdminGradingPage({ aiPollMs = AI_POLL_MS }: { aiPollMs?: number } = {}) {
  const [filter, setFilter] = useState<QuizAttemptStatus>('SUBMITTED')
  const [attempts, setAttempts] = useState<AdminAttemptSummary[]>([])
  const [query, setQuery] = useState('')
  const [classFilter, setClassFilter] = useState('')
  const [detail, setDetail] = useState<AdminAttemptDetail | null>(null)
  const [granting, setGranting] = useState(false)
  const [granted, setGranted] = useState<string | null>(null) // "Ana mai poate da testul o dată." after "+1 încercare"
  const [loading, setLoading] = useState(true)
  const [finalizing, setFinalizing] = useState(false)
  // The paper finalised last: "Anulează" stays on screen while the teacher moves on, instead of a question per paper.
  const [undoable, setUndoable] = useState<{ attemptId: number; studentName: string } | null>(null)
  const { confirm, dialog: confirmDialog } = useConfirm()
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

  /** "Finalizează": first whatever is still being typed, then the grade itself. */
  async function finish(then: 'list' | 'next') {
    setError(null)
    if (!(await flushAll())) {
      setError('Unele punctaje sau comentarii nu s-au salvat. Verifică întrebările marcate, apoi finalizează.')
      return
    }
    await finalize(then)
  }

  /** A grade the server accepted: shown on the page without fetching the paper again. */
  const graded = useCallback((updated: AdminItemReview) => {
    setDetail((d) => d && { ...d, items: d.items.map((i) => (i.itemId === updated.itemId ? updated : i)) })
  }, [])
  const [error, setError] = useState<string | null>(null)
  const [loadError, setLoadError] = useState<string | null>(null)
  // `?attempt=12` opens that paper straight away (links from a student's page).
  const [searchParams, setSearchParams] = useSearchParams()
  const linkedAttempt = Number(searchParams.get('attempt')) || null
  // A paper already on screen when the address catches up with it: not fetched a second time.
  const shownAlready = useRef<number | undefined>(undefined)
  const loadTurn = useRef(0)

  /** Whether "Mai vechi" may bring more graded papers (the last page came back full). */
  const [more, setMore] = useState(false)
  const [loadingMore, setLoadingMore] = useState(false)

  async function reloadList(status: QuizAttemptStatus = filter): Promise<AdminAttemptSummary[]> {
    setLoading(true)
    try {
      const list = await listAttemptsForGrading(status)
      setAttempts(list)
      setMore(status === 'GRADED' && list.length === GRADED_PAGE)
      return list
    } catch (e) {
      setLoadError(errorMessage(e))
      return []
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    let cancelled = false
    listAttemptsForGrading(filter)
      .then((list) => {
        if (cancelled) return
        setAttempts(list)
        setMore(filter === 'GRADED' && list.length === GRADED_PAGE)
      })
      .catch((e) => !cancelled && setLoadError(errorMessage(e)))
      .finally(() => !cancelled && setLoading(false))
    return () => {
      cancelled = true
    }
  }, [filter])

  // The address says which paper is open, so Back returns to the queue and a refresh stays on the paper.
  useEffect(() => {
    if (shownAlready.current === linkedAttempt) {
      shownAlready.current = undefined
      return
    }
    if (linkedAttempt === null) setDetail(null)
    else void loadPaper(linkedAttempt)
    // eslint-disable-next-line react-hooks/exhaustive-deps -- only when the address names another paper
  }, [linkedAttempt])

  /** The next page of graded papers, below the last one shown. */
  async function loadOlder() {
    const last = attempts[attempts.length - 1]
    if (!last) return
    setLoadingMore(true)
    try {
      const page = await listAttemptsForGrading('GRADED', last.attemptId)
      setAttempts((prev) => [...prev, ...page])
      setMore(page.length === GRADED_PAGE)
    } catch (e) {
      setError(errorMessage(e))
    } finally {
      setLoadingMore(false)
    }
  }

  /** Back to the queue. `replace`: the paper is finished with, so Back does not lead to it again. */
  function closeDetail(replace = false) {
    setSearchParams({}, { replace })
  }

  async function loadPaper(attemptId: number) {
    setError(null)
    setLoadError(null)
    setNotice(null)
    const turn = ++loadTurn.current
    try {
      const paper = await getAttemptForGrading(attemptId)
      if (turn !== loadTurn.current) return // the address moved on while this one was on its way
      setStatuses({}) // the forms of the paper before are gone
      setDetail(paper)
    } catch (e) {
      setLoadError(errorMessage(e))
    }
  }

  /** Opens a paper from the list: the address changes, and the effect above loads it. */
  function open(attemptId: number) {
    setSearchParams({ attempt: String(attemptId) })
  }

  /** The next paper, or one taken back: loaded first, so the queue never flashes up and the buttons stay busy. */
  async function goToPaper(attemptId: number, replace = false) {
    await loadPaper(attemptId)
    if (attemptId === linkedAttempt) return
    shownAlready.current = attemptId
    setSearchParams({ attempt: String(attemptId) }, { replace })
  }

  async function finalize(then: 'list' | 'next') {
    if (!detail) return
    setFinalizing(true)
    setError(null)
    try {
      await finalizeGrading(detail.attemptId)
      setUndoable({ attemptId: detail.attemptId, studentName: detail.studentName })
      window.dispatchEvent(new Event(NAV_COUNTS_CHANGED))
      const before = attempts.map((a) => a.attemptId)
      const remaining = filter === 'SUBMITTED' ? await reloadList() : await listAttemptsForGrading('SUBMITTED')
      // The paper after this one in the queue the teacher was looking at, else the first one still waiting.
      const after = before.slice(before.indexOf(detail.attemptId) + 1)
      const next = remaining.find((a) => after.includes(a.attemptId)) ?? remaining[0]
      if (then === 'next' && next) {
        await goToPaper(next.attemptId, true)
      } else {
        closeDetail(true)
        if (then === 'next') setNotice('Gata — nu mai e nicio lucrare de corectat. 🎉')
      }
    } catch (e) {
      setError(errorMessage(e))
    } finally {
      setFinalizing(false)
    }
  }

  /** Takes a final grade back: the paper opens again to correct it, and waits in the queue until it is finalised. */
  async function reopen(attemptId: number) {
    setError(null)
    if (!(await flushAll())) {
      setError('Unele punctaje sau comentarii nu s-au salvat. Verifică întrebările marcate, apoi redeschide lucrarea.')
      return
    }
    try {
      await reopenGrading(attemptId)
      setUndoable(null)
      window.dispatchEvent(new Event(NAV_COUNTS_CHANGED))
      await reloadList()
      await goToPaper(attemptId)
    } catch (e) {
      setError(errorMessage(e))
    }
  }

  /** One more graded attempt for this student only (a dropped connection, a wrong photo): the class keeps its limit. */
  async function grantOne(paper: AdminAttemptDetail) {
    setGranting(true)
    setError(null)
    try {
      const { attemptsAllowed } = await grantExtraAttempt(paper.attemptId)
      setDetail((d) => d && d.attemptId === paper.attemptId ? { ...d, attemptsAllowed } : d)
      setGranted(`${paper.studentName} mai poate da testul o dată.`)
    } catch (e) {
      setError(errorMessage(e))
    } finally {
      setGranting(false)
    }
  }

  async function askToReopen(attemptId: number) {
    const yes = await confirm({
      title: 'Redeschizi lucrarea?',
      body: 'Nota dispare pentru elev până o finalizezi din nou, iar lucrarea revine la „De corectat”, cu punctajele de '
        + 'acum. Rămâne scris în Jurnal.',
      confirmLabel: 'Redeschide',
    })
    if (yes) await reopen(attemptId)
  }

  const filtering = query.trim() !== '' || classFilter !== ''
  const classNames = [...new Set(attempts.map((a) => a.schoolClassName).filter((n): n is string => n !== null))]
    .sort((a, b) => a.localeCompare(b, 'ro', { numeric: true }))
  const visibleAttempts = attempts.filter((a) =>
    matchesQuery(query, a.studentName, a.quizTitle, a.schoolClassName)
    && (classFilter === '' || a.schoolClassName === classFilter))
  const clearFilters = () => {
    setQuery('')
    setClassFilter('')
  }

  const allOpenGraded = detail?.items.every((i) => i.type !== 'OPEN' || i.awardedPoints !== null) ?? false
  const othersWaiting = detail ? attempts.filter((a) => a.status === 'SUBMITTED' && a.attemptId !== detail.attemptId).length : 0
  const pointsSoFar = detail?.items.reduce((sum, i) => sum + (i.awardedPoints ?? 0), 0) ?? 0
  // The AI's proposals for the open paper (only when the server has them switched on), by question.
  const [aiOn, setAiOn] = useState(false)
  const [proposals, setProposals] = useState<Record<number, AiProposal>>({})
  useEffect(() => {
    getAiGradingSettings()
      .then((s) => setAiOn(s.enabled))
      .catch(() => undefined) // no proposals shown, grading works as before
  }, [])
  const paperId = detail?.attemptId ?? null
  // The test of the open paper (the detail itself does not name it; the list it was opened from does).
  const detailQuizId = attempts.find((a) => a.attemptId === paperId)?.quizId ?? null
  const pending = Object.values(proposals).some((p) => p.status === 'QUEUED' || p.status === 'RUNNING')
  // Once per paper opened...
  useEffect(() => {
    if (!aiOn || paperId === null) return
    let cancelled = false
    getAiProposals(paperId)
      .then((list) => !cancelled && setProposals(Object.fromEntries(list.map((p) => [p.itemId, p]))))
      .catch(() => undefined) // no proposals shown, grading works as before
    return () => {
      cancelled = true
    }
  }, [aiOn, paperId])
  // ...then again every aiPollMs while the AI is still working on some (each answer re-arms the timer).
  useEffect(() => {
    if (!aiOn || paperId === null || !pending) return
    let cancelled = false
    const timer = setTimeout(() => {
      getAiProposals(paperId)
        .then((list) => !cancelled && setProposals(Object.fromEntries(list.map((p) => [p.itemId, p]))))
        .catch(() => undefined) // a missed poll is retried by the next one
    }, aiPollMs)
    return () => {
      cancelled = true
      clearTimeout(timer)
    }
  }, [aiOn, paperId, pending, aiPollMs, proposals])
  async function askAi(itemId: number) {
    if (paperId === null) return
    try {
      const queued = await requestAiProposal(paperId, itemId)
      setProposals((all) => ({ ...all, [itemId]: queued }))
    } catch (e) {
      setError(errorMessage(e))
    }
  }

  // Where the keyboard starts on a paper still to grade; fixed when the paper opens, not moved by each grade.
  const [firstUngraded, setFirstUngraded] = useState<number | null>(null)
  const [openedId, setOpenedId] = useState<number | null>(null)
  if (detail && detail.attemptId !== openedId) {
    setOpenedId(detail.attemptId)
    setProposals({}) // the paper before's proposals are not this one's
    setFirstUngraded(
      detail.status === 'SUBMITTED' ? detail.items.find((i) => i.type === 'OPEN' && i.awardedPoints === null)?.itemId ?? null : null,
    )
  }

  return (
    <div className="p-4">
      <div className={cn('mx-auto space-y-4', detail || byItem ? 'max-w-6xl' : 'max-w-3xl')}>
        <PageHeader
          title={byItem ? `${byItem.quizTitle} — pe întrebare` : detail ? `${detail.studentName} — ${detail.quizTitle}` : 'Corectură'}
          description={byItem || detail ? undefined : 'Lucrările predate, cu întrebările care așteaptă nota ta.'}
          breadcrumbs={byItem ? [
            { label: 'Corectură', onClick: () => { setByItem(null); void reloadList() } },
            { label: `${byItem.quizTitle} — pe întrebare` },
          ] : detail ? [
            { label: 'Corectură', onClick: () => closeDetail() },
            { label: `${detail.studentName} — ${detail.quizTitle}` },
          ] : undefined}
        />

        {loadError && <ErrorState message={loadError} />}
        {error && <p className="text-sm text-destructive">{error}</p>}
        {notice && !detail && !byItem && <p role="status" className="text-sm text-emerald-700 dark:text-emerald-300">{notice}</p>}
        {undoable && !byItem && (
          <div
            role="status"
            data-testid="grade-undo"
            className="flex flex-wrap items-center justify-between gap-2 rounded-lg border border-emerald-500/40 bg-emerald-500/10 px-3 py-2 text-sm text-emerald-800 dark:text-emerald-200"
          >
            <span>✓ {undoable.studentName}: nota e finalizată, elevul o vede.</span>
            <Button size="sm" variant="outline" onClick={() => void reopen(undoable.attemptId)}>Anulează</Button>
          </div>
        )}

        {byItem ? (
          <GradeByItem
            bank={bank}
            aiOn={aiOn}
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
                  {detail.attemptsAllowed !== null && (
                    <p data-testid="attempts-line" className="flex flex-wrap items-center gap-2 text-sm text-muted-foreground">
                      Încercări: {detail.attemptsUsed} din {detail.attemptsAllowed}
                      <Button
                        size="sm"
                        variant="outline"
                        aria-label={`+1 încercare pentru ${detail.studentName}`}
                        disabled={granting}
                        onClick={() => void grantOne(detail)}
                      >
                        +1 încercare
                      </Button>
                    </p>
                  )}
                  {granted && <p role="status" className="text-sm text-emerald-700 dark:text-emerald-300">{granted}</p>}
                  {detail.status === 'SUBMITTED' && (
                    <p className="text-xs text-muted-foreground">
                      Din tastatură: <kbd className="rounded border px-1 font-mono">0–9</kbd> dă punctajul și trece la
                      următorul, săgețile îl schimbă{aiOn && <>, <kbd className="rounded border px-1 font-mono">Enter</kbd> ia
                      propunerea AI</>}.
                    </p>
                  )}
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

            {aiOn && detailQuizId !== null && detail.status === 'SUBMITTED' && detail.items.some((i) => i.type === 'OPEN') && (
              <Card>
                <CardContent className="py-2">
                  <AiAutoProposalsSwitch
                    key={detailQuizId}
                    quizId={detailQuizId}
                    onQueued={() => {
                      getAiProposals(detail.attemptId)
                        .then((list) => setProposals(Object.fromEntries(list.map((p) => [p.itemId, p]))))
                        .catch(() => undefined)
                    }}
                  />
                </CardContent>
              </Card>
            )}

            {numberItems(detail.items).map(({ item, local, heading }) => (
              <Fragment key={item.itemId}>
              {heading && <SectionHeadingView heading={heading} />}
              <Card>
                <CardContent className="space-y-3 py-4">
                  <p className="text-sm font-medium">
                    {local}. {item.type === 'OPEN' ? 'Rezolvare' : itemTypeLabel(item.type)} · {item.points} p
                  </p>
                  <MathContent className="text-sm">{item.statement}</MathContent>
                  <QuestionImage itemId={item.itemId} image={item.image} />

                  {item.type !== 'OPEN' ? (
                    <div className="space-y-1 text-sm">
                      <p className={item.correct ? 'text-emerald-700 dark:text-emerald-300' : 'text-destructive'}>
                        {item.correct ? '✓ ' : '✗ '}{item.type === 'NUMERIC' ? 'Scris' : 'Ales'}:{' '}
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
                      autoFocus={item.itemId === firstUngraded}
                      aiOn={aiOn}
                      aiProposal={proposals[item.itemId] ?? null}
                      onRequestAiProposal={() => void askAi(item.itemId)}
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

            {/* A test of grile only has nothing to correct: the server marked it. */}
            {detail.status === 'GRADED' && detail.items.some((i) => i.type === 'OPEN') && (
              <div className="flex justify-end">
                <Button variant="outline" onClick={() => void askToReopen(detail.attemptId)} data-testid="grade-reopen">
                  Redeschide lucrarea
                </Button>
              </div>
            )}

            {detail.status === 'SUBMITTED' && (
              <div className="space-y-2">
                {!allOpenGraded && (
                  <p data-testid="grade-finalize-why" className="text-right text-sm text-muted-foreground">
                    Notează toate întrebările cu rezolvare ca să poți finaliza.
                  </p>
                )}
                {/* One main button: with papers still waiting it is "finish and go on"; finishing alone is the quiet one. */}
                <div className="flex flex-wrap items-center justify-end gap-2">
                  <Button
                    variant={othersWaiting > 0 ? 'ghost' : 'default'}
                    onClick={() => void finish('list')}
                    disabled={!allOpenGraded || finalizing}
                    data-testid="grade-finalize"
                  >
                    {finalizing ? 'Se finalizează...' : othersWaiting > 0 ? 'Doar finalizează' : 'Finalizează nota'}
                  </Button>
                  {othersWaiting > 0 && (
                    <Button onClick={() => void finish('next')} disabled={!allOpenGraded || finalizing} data-testid="grade-finalize-next">
                      Finalizează și treci la următoarea ({othersWaiting} {othersWaiting === 1 ? 'rămasă' : 'rămase'})
                    </Button>
                  )}
                </div>
              </div>
            )}
          </div>
        ) : (
          /* ----- The queue ----- */
          <>
            <div className="flex items-center justify-between gap-2">
              <h2 className="font-medium">Lucrări</h2>
              <select
                aria-label="Ce lucrări arăți"
                className={selectClass}
                value={filter}
                onChange={(e) => {
                  setLoading(true)
                  setError(null)
                  setLoadError(null)
                  setFilter(e.target.value as QuizAttemptStatus)
                }}
              >
                <option value="SUBMITTED">De corectat</option>
                <option value="GRADED">Notate</option>
              </select>
            </div>
            {loading && <LoadingCards />}
            {!loading && attempts.length >= FILTER_FROM && (
              <FilterBar
                label="Caută elev, test sau clasă"
                search={query}
                onSearch={setQuery}
                active={filtering}
                onClear={clearFilters}
                shown={visibleAttempts.length}
                total={attempts.length}
              >
                {classNames.length > 1 && (
                  <FilterSelect
                    label="Clasa"
                    value={classFilter}
                    onChange={setClassFilter}
                    options={[{ value: '', label: 'Toate clasele' }, ...classNames.map((name) => ({ value: name, label: name }))]}
                  />
                )}
              </FilterBar>
            )}
            {!loading && attempts.length > 0 && visibleAttempts.length === 0 && <NoMatches onClear={clearFilters} />}
            {!loading && filter === 'SUBMITTED' && visibleAttempts.length > 0 && (
              <section aria-label="Pe întrebare" className="space-y-2">
                <p className="text-sm text-muted-foreground">
                  Mai repede și mai unitar: aceeași întrebare la toate lucrările unui test, una după alta.
                </p>
                {groupByQuiz(visibleAttempts).map((g) => (
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
            {!loading && !loadError && attempts.length === 0 && (
              <p className="text-muted-foreground">
                {filter === 'SUBMITTED' ? 'Nicio lucrare de corectat. 🎉' : 'Nicio lucrare notată încă.'}
              </p>
            )}
            <div className="space-y-2">
              {visibleAttempts.map((a) => (
                <Card key={a.attemptId}>
                  <CardContent data-testid="attempt-row" className="flex items-center justify-between gap-3 py-3">
                    <div className="min-w-0 space-y-1">
                      <p className="truncate font-medium">{a.studentName} — {a.quizTitle}</p>
                      <p className="flex flex-wrap items-center gap-2 text-sm text-muted-foreground">
                        <AttemptStatusBadge status={a.status} />
                        {a.schoolClassName && <span data-testid="attempt-class">{a.schoolClassName}</span>}
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
            {!loading && filter === 'GRADED' && more && (
              <Button variant="outline" onClick={() => void loadOlder()} disabled={loadingMore} data-testid="graded-older">
                {loadingMore ? 'Se încarcă...' : 'Mai vechi'}
              </Button>
            )}
          </>
        )}
      </div>
      {confirmDialog}
    </div>
  )
}
