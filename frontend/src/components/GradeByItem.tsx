import { useCallback, useEffect, useRef, useState, type FormEvent } from 'react'
import { Button } from '@/components/ui/button'
import { Card, CardContent } from '@/components/ui/card'
import { ConfirmDialog } from '@/components/ConfirmDialog'
import { MathContent } from '@/components/MathContent'
import { PhotoPages } from '@/components/PhotoPages'
import { QuestionImage } from '@/components/QuestionImage'
import { ItemGradeForm } from '@/components/grading/ItemGradeForm'
import {
  attemptPhotoUrl as attemptPhotoUrlDefault,
  finalizeGrading as finalizeGradingRequest,
  getAttemptForGrading as getAttemptForGradingRequest,
  gradeOpenItem as gradeOpenItemRequest,
  type AdminAttemptDetail,
  type AdminItemReview,
} from '@/lib/api'
import { errorMessage } from '@/lib/errors'
import { roCount } from '@/lib/format'
import { numberItems, parseSection } from '@/lib/sections'
import { cn } from '@/lib/utils'
import { EMPTY_BANK, type SnippetBank } from '@/lib/useCommentSnippets'

interface Props {
  quizTitle: string
  /** The quiz's papers waiting for a grade, in queue order. */
  attemptIds: number[]
  onExit: () => void
  /** The teacher's saved comments (loaded once by the grading page). */
  bank?: SnippetBank
  /** Injected in tests; default to the real API calls. */
  getAttemptForGrading?: typeof getAttemptForGradingRequest
  gradeOpenItem?: typeof gradeOpenItemRequest
  finalizeGrading?: typeof finalizeGradingRequest
  attemptPhotoUrl?: typeof attemptPhotoUrlDefault
}

/** Every paper fully scored (all open questions have points) can be finalised. */
const complete = (d: AdminAttemptDetail) => d.items.every((i) => i.type !== 'OPEN' || i.awardedPoints !== null)

/**
 * T4 "Corectează pe subiect": one open question across the whole stack of papers — the same barem, one photo after
 * another — which is the fastest way to grade consistently. Points and comments are saved per answer exactly as on the
 * one-paper screen; at the end the papers whose every open question has points can be finalised together.
 */
export function GradeByItem({
  quizTitle,
  attemptIds,
  onExit,
  bank = EMPTY_BANK,
  getAttemptForGrading = getAttemptForGradingRequest,
  gradeOpenItem = gradeOpenItemRequest,
  finalizeGrading = finalizeGradingRequest,
  attemptPhotoUrl = attemptPhotoUrlDefault,
}: Props) {
  const [papers, setPapers] = useState<AdminAttemptDetail[] | null>(null)
  // The quiz's open questions with the number students saw (their place in the quiz), fixed at load: papers leave
  // the list as they are finalised, the questions stay.
  const [questions, setQuestions] = useState<{ item: AdminItemReview; number: string }[]>([])
  const [itemId, setItemId] = useState<number | null>(null)
  const [index, setIndex] = useState(0)
  const [error, setError] = useState<string | null>(null)
  const [confirming, setConfirming] = useState(false)
  const [finalizing, setFinalizing] = useState(false)
  const [finalized, setFinalized] = useState<number | null>(null)
  // The answer on screen: send what it still holds before moving to another one or finalising.
  const flushCurrent = useRef<(() => Promise<boolean>) | null>(null)
  const registerFlush = useCallback((flush: (() => Promise<boolean>) | null) => {
    flushCurrent.current = flush
  }, [])

  useEffect(() => {
    let cancelled = false
    Promise.all(attemptIds.map((id) => getAttemptForGrading(id)))
      .then((list) => {
        if (cancelled) return
        setPapers(list)
        // "3", or "III.1" in a test with EN sections - the number the students saw.
        const open = numberItems(list[0]?.items ?? [])
          .map((n) => ({ item: n.item, number: n.label }))
          .filter((q) => q.item.type === 'OPEN')
        setQuestions(open)
        // Start on the first open question that still has an answer without points, at the first such paper.
        const missing = (p: AdminAttemptDetail, id: number) => p.items.find((x) => x.itemId === id)?.awardedPoints === null
        const start = (open.find((q) => list.some((p) => missing(p, q.item.itemId))) ?? open[0])?.item.itemId ?? null
        setItemId(start)
        setIndex(start === null ? 0 : Math.max(0, list.findIndex((p) => missing(p, start))))
      })
      .catch((e) => !cancelled && setError(errorMessage(e)))
    return () => {
      cancelled = true
    }
  }, [attemptIds, getAttemptForGrading])

  if (error && !papers) return <p className="text-sm text-destructive">{error}</p>
  if (!papers) return <p className="text-muted-foreground">Se încarcă lucrările...</p>

  if (questions.length === 0) {
    return (
      <div className="space-y-3">
        <p className="text-sm text-muted-foreground">Quiz-ul „{quizTitle}” nu are subiecte cu rezolvare de corectat.</p>
        <Button variant="outline" onClick={onExit}>Înapoi la lucrări</Button>
      </div>
    )
  }

  const answerOf = (paper: AdminAttemptDetail, id: number) => paper.items.find((i) => i.itemId === id)!
  const gradedCount = (id: number) => papers.filter((p) => answerOf(p, id).awardedPoints !== null).length
  const current = questions.find((q) => q.item.itemId === itemId) ?? null
  const paper = papers[Math.min(index, papers.length - 1)]
  const ready = papers.filter(complete)

  /** Moves on only once the answer on screen is saved (a failed save stays on screen with its error). */
  async function leaveCurrent(): Promise<boolean> {
    return flushCurrent.current ? flushCurrent.current() : true
  }

  async function goTo(to: number) {
    if (await leaveCurrent()) setIndex(to)
  }

  async function chooseItem(id: number) {
    if (!(await leaveCurrent())) return
    setItemId(id)
    // Open the first paper still missing points for this question.
    const todo = papers!.findIndex((p) => answerOf(p, id).awardedPoints === null)
    setIndex(todo === -1 ? 0 : todo)
  }

  /** A grade the server accepted: shown without fetching the paper again. */
  function graded(attemptId: number, id: number, points: number, comment: string | null) {
    setPapers((prev) => prev!.map((p) => (p.attemptId !== attemptId ? p : {
      ...p,
      items: p.items.map((i) => (i.itemId === id ? { ...i, awardedPoints: points, teacherComment: comment } : i)),
    })))
  }

  async function askToFinalize() {
    setError(null)
    if (await leaveCurrent()) setConfirming(true)
  }

  async function finalizeReady() {
    setConfirming(false)
    setFinalizing(true)
    setError(null)
    let done = 0
    try {
      for (const p of ready) {
        await finalizeGrading(p.attemptId)
        done++
      }
      setPapers((prev) => prev!.filter((p) => !ready.some((r) => r.attemptId === p.attemptId)))
      setIndex(0)
      setFinalized(done)
    } catch (e) {
      setError(`S-au finalizat ${done} din ${ready.length}. ${errorMessage(e)}`)
    } finally {
      setFinalizing(false)
    }
  }

  return (
    <div className="space-y-4">
      <nav aria-label="Subiecte" className="flex flex-wrap gap-2">
        {questions.map(({ item, number }) => (
          <Button
            key={item.itemId}
            size="sm"
            variant={item.itemId === itemId ? 'default' : 'outline'}
            aria-pressed={item.itemId === itemId}
            onClick={() => void chooseItem(item.itemId)}
          >
            Subiectul {number} ({gradedCount(item.itemId)}/{papers.length})
          </Button>
        ))}
      </nav>

      {finalized !== null && (
        <p role="status" className="text-sm text-emerald-700 dark:text-emerald-300">
          ✓ {roCount(finalized, 'lucrare finalizată', 'lucrări finalizate')}.
        </p>
      )}
      {error && <p className="text-sm text-destructive">{error}</p>}

      {papers.length === 0 ? (
        <div className="space-y-3">
          <p className="text-muted-foreground">Toate lucrările acestui quiz sunt notate. 🎉</p>
          <Button variant="outline" onClick={onExit}>Înapoi la lucrări</Button>
        </div>
      ) : current && paper ? (
        <>
          <Card>
            <CardContent className="space-y-2 py-4">
              {current.item.section && (
                <p className="text-xs font-semibold tracking-wide text-muted-foreground uppercase">
                  {parseSection(current.item.section).title}
                </p>
              )}
              <p className="text-sm font-medium">Subiectul {current.number} · {current.item.points} p</p>
              <MathContent className="text-sm">{current.item.statement}</MathContent>
              <QuestionImage itemId={current.item.itemId} image={current.item.image} />
              {current.item.barem && (
                <div className="rounded-lg bg-muted p-3 text-sm">
                  <p className="mb-1 font-medium">Barem</p>
                  <MathContent>{current.item.barem}</MathContent>
                </div>
              )}
            </CardContent>
          </Card>

          <AnswerGrader
            key={`${paper.attemptId}-${current.item.itemId}`}
            paper={paper}
            answer={answerOf(paper, current.item.itemId)}
            position={index + 1}
            total={papers.length}
            photoUrl={(photoId) => attemptPhotoUrl(paper.attemptId, current.item.itemId, photoId)}
            bank={bank}
            registerFlush={registerFlush}
            save={async (points, comment) => {
              await gradeOpenItem(paper.attemptId, current.item.itemId, points, comment)
              graded(paper.attemptId, current.item.itemId, points, comment)
            }}
            onPrevious={index > 0 ? () => void goTo(index - 1) : undefined}
            onNext={index < papers.length - 1 ? () => void goTo(index + 1) : undefined}
          />

          <div className="flex flex-wrap items-center justify-between gap-3">
            <Button variant="outline" onClick={onExit}>Înapoi la lucrări</Button>
            <Button onClick={() => void askToFinalize()} disabled={ready.length === 0 || finalizing} data-testid="finalize-ready">
              {finalizing ? 'Se finalizează...' : `Finalizează lucrările complete (${ready.length})`}
            </Button>
          </div>
        </>
      ) : null}

      <ConfirmDialog
        open={confirming}
        title={`Finalizezi ${roCount(ready.length, 'lucrare', 'lucrări')}?`}
        confirmLabel="Finalizează"
        onConfirm={() => void finalizeReady()}
        onCancel={() => setConfirming(false)}
      >
        Doar lucrările cu toate subiectele notate. După asta punctajele lor nu se mai pot modifica.
      </ConfirmDialog>
    </div>
  )
}

/**
 * One student's answer to the chosen question: photo, points, comment. Each grade saves itself (see ItemGradeForm);
 * "Următoarea" (or Enter in a points box) moves on once it is saved.
 */
function AnswerGrader({
  paper,
  answer,
  position,
  total,
  photoUrl,
  bank,
  save,
  registerFlush,
  onPrevious,
  onNext,
}: {
  paper: AdminAttemptDetail
  answer: AdminItemReview
  position: number
  total: number
  /** The address of one page of this answer. */
  photoUrl: (photoId: number) => string
  bank: SnippetBank
  save: (points: number, comment: string | null) => Promise<void>
  registerFlush: (flush: (() => Promise<boolean>) | null) => void
  onPrevious?: () => void
  onNext?: () => void
}) {
  function submit(e: FormEvent) {
    e.preventDefault()
    onNext?.()
  }

  return (
    <Card data-testid="answer-grader">
      <CardContent className="space-y-3 py-4">
        <div className="flex flex-wrap items-center justify-between gap-2">
          <p className="font-medium">
            {paper.studentName} <span className="font-normal text-muted-foreground">· lucrarea {position} din {total}</span>
          </p>
        </div>

        {answer.photoIds.length > 0 ? (
          <PhotoPages
            stacked
            urls={answer.photoIds.map(photoUrl)}
            alt={(page) => `Rezolvarea lui ${paper.studentName}, pagina ${page} din ${answer.photoIds.length}`}
          />
        ) : (
          <p className="text-sm text-muted-foreground">Elevul nu a trimis nicio poză.</p>
        )}

        <form onSubmit={submit} noValidate>
          <ItemGradeForm item={answer} save={save} bank={bank} autoFocus registerFlush={registerFlush} />
        </form>

        <div className={cn('flex justify-between gap-2', !onPrevious && 'justify-end')}>
          {onPrevious && <Button size="sm" variant="ghost" onClick={onPrevious}>← Lucrarea anterioară</Button>}
          {onNext && <Button size="sm" onClick={onNext}>Următoarea lucrare →</Button>}
        </div>
      </CardContent>
    </Card>
  )
}
