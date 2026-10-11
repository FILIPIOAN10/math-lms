import { useEffect, useId, useState, type FormEvent } from 'react'
import { Button } from '@/components/ui/button'
import { Card, CardContent } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Textarea } from '@/components/ui/textarea'
import { ConfirmDialog } from '@/components/ConfirmDialog'
import { MathContent } from '@/components/MathContent'
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

/** Same limit as the server (V20 columns). */
const MAX_COMMENT = 2000

interface Props {
  quizTitle: string
  /** The quiz's papers waiting for a grade, in queue order. */
  attemptIds: number[]
  onExit: () => void
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

  function chooseItem(id: number) {
    setItemId(id)
    // Open the first paper still missing points for this question.
    const todo = papers!.findIndex((p) => answerOf(p, id).awardedPoints === null)
    setIndex(todo === -1 ? 0 : todo)
  }

  async function saved(attemptId: number) {
    const fresh = await getAttemptForGrading(attemptId)
    setPapers((prev) => prev!.map((p) => (p.attemptId === attemptId ? fresh : p)))
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
            onClick={() => chooseItem(item.itemId)}
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
            photoUrl={attemptPhotoUrl(paper.attemptId, current.item.itemId)}
            onSave={async (points, comment) => {
              await gradeOpenItem(paper.attemptId, current.item.itemId, points, comment)
              await saved(paper.attemptId)
              if (index < papers.length - 1) setIndex(index + 1)
            }}
            onPrevious={index > 0 ? () => setIndex(index - 1) : undefined}
            onNext={index < papers.length - 1 ? () => setIndex(index + 1) : undefined}
          />

          <div className="flex flex-wrap items-center justify-between gap-3">
            <Button variant="outline" onClick={onExit}>Înapoi la lucrări</Button>
            <Button onClick={() => setConfirming(true)} disabled={ready.length === 0 || finalizing} data-testid="finalize-ready">
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

/** One student's answer to the chosen question: photo, points, comment — "Salvează și următoarea". */
function AnswerGrader({
  paper,
  answer,
  position,
  total,
  photoUrl,
  onSave,
  onPrevious,
  onNext,
}: {
  paper: AdminAttemptDetail
  answer: AdminItemReview
  position: number
  total: number
  photoUrl: string
  onSave: (points: number, comment: string | null) => Promise<void>
  onPrevious?: () => void
  onNext?: () => void
}) {
  const [points, setPoints] = useState(answer.awardedPoints === null ? '' : String(answer.awardedPoints))
  const [comment, setComment] = useState(answer.teacherComment ?? '')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const commentId = useId()

  async function submit(e: FormEvent) {
    e.preventDefault()
    const value = Number(points)
    if (points.trim() === '' || !Number.isInteger(value) || value < 0 || value > answer.points) {
      setError(`Punctajul trebuie să fie un număr întreg între 0 și ${answer.points}.`)
      return
    }
    setBusy(true)
    setError(null)
    try {
      await onSave(value, comment.trim() === '' ? null : comment.trim())
    } catch (err) {
      setError(errorMessage(err))
      setBusy(false)
    }
  }

  return (
    <Card data-testid="answer-grader">
      <CardContent className="space-y-3 py-4">
        <div className="flex flex-wrap items-center justify-between gap-2">
          <p className="font-medium">
            {paper.studentName} <span className="font-normal text-muted-foreground">· lucrarea {position} din {total}</span>
          </p>
          {answer.awardedPoints !== null && (
            <span className="text-sm text-emerald-700 dark:text-emerald-300">✓ notat cu {answer.awardedPoints} p</span>
          )}
        </div>

        {answer.photoUploaded ? (
          <a href={photoUrl} target="_blank" rel="noreferrer">
            <img src={photoUrl} alt={`Rezolvarea lui ${paper.studentName}`} className="max-h-[28rem] rounded-lg border object-contain" />
          </a>
        ) : (
          <p className="text-sm text-muted-foreground">Elevul nu a trimis nicio poză.</p>
        )}

        <form className="space-y-3" onSubmit={submit} noValidate>
          <div className="space-y-1">
            <label htmlFor={commentId} className="text-sm font-medium">Comentariu pentru elev (opțional)</label>
            <Textarea
              id={commentId}
              value={comment}
              maxLength={MAX_COMMENT}
              onChange={(e) => setComment(e.target.value)}
              disabled={busy}
              className="min-h-16"
            />
          </div>
          <div className="flex flex-wrap items-center gap-2">
            <Input
              type="number"
              min={0}
              max={answer.points}
              value={points}
              onChange={(e) => setPoints(e.target.value)}
              disabled={busy}
              className="w-24"
              aria-label="Puncte acordate"
              autoFocus
            />
            <span className="text-sm text-muted-foreground">/ {answer.points} p</span>
            <Button type="submit" size="sm" disabled={busy}>
              {busy ? 'Se salvează...' : position < total ? 'Salvează și următoarea' : 'Salvează'}
            </Button>
          </div>
          {error && <p className="text-sm text-destructive">{error}</p>}
        </form>

        <div className={cn('flex justify-between gap-2', !onPrevious && 'justify-end')}>
          {onPrevious && <Button size="sm" variant="ghost" onClick={onPrevious}>← Lucrarea anterioară</Button>}
          {onNext && <Button size="sm" variant="ghost" onClick={onNext}>Lucrarea următoare →</Button>}
        </div>
      </CardContent>
    </Card>
  )
}
