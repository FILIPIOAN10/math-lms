import { Fragment, useEffect, useRef, useState, type ReactNode } from 'react'
import { Link, useNavigate, useParams, useSearchParams } from 'react-router-dom'
import { Button, buttonVariants } from '@/components/ui/button'
import { Card, CardContent } from '@/components/ui/card'
import { ConfirmDialog } from '@/components/ConfirmDialog'
import { MathContent } from '@/components/MathContent'
import { QuestionImage } from '@/components/QuestionImage'
import { SectionHeadingView } from '@/components/SectionHeadingView'
import { parseOnly } from '@/lib/mistakes'
import { numberItems, parseSection } from '@/lib/sections'
import {
  ApiError,
  getMyAssignments,
  getQuizPreview,
  ownPhotoUrl,
  revealQuizHint,
  saveQuizAnswer,
  startQuizAttempt,
  submitQuizAttempt,
  uploadQuizPhoto,
  deleteQuizPhoto,
  MAX_PHOTOS,
  type AnswerFeedback,
  type AttemptMode,
  type QuizPreview,
  type StartedAttemptDto,
  type StudentItemDto,
} from '@/lib/api'
import { dueCountdown } from '@/lib/assignments'
import { formatClock, secondsLeft } from '@/lib/countdown'
import { errorMessage } from '@/lib/errors'
import { formatDate, formatMinutes, roCount } from '@/lib/format'
import { todoAssignments } from '@/lib/home'
import { shrinkImage } from '@/lib/image'
import { cn } from '@/lib/utils'
import { usePageTitle } from '@/lib/usePageTitle'

const MAX_UPLOAD_BYTES = 8 * 1024 * 1024 // matches spring.servlet.multipart.max-file-size

type TimerTone = 'normal' | 'warning' | 'danger'

/** Calm, then amber in the last five minutes, then red in the last one. */
function timerTone(left: number): TimerTone {
  if (left <= 60) return 'danger'
  if (left <= 5 * 60) return 'warning'
  return 'normal'
}

const TIMER_CLASSES: Record<TimerTone, string> = {
  normal: 'bg-background',
  warning: 'bg-amber-100 text-amber-900 dark:bg-amber-500/20 dark:text-amber-100',
  danger: 'bg-destructive text-destructive-foreground',
}

/**
 * Said once to a screen reader when the clock crosses into a new tone (a polite live region is only read when its text
 * changes, so the per-second countdown itself is never read out).
 */
const TIMER_ANNOUNCEMENTS: Record<TimerTone, string> = {
  normal: '',
  warning: 'Mai ai mai puțin de 5 minute.',
  danger: 'Mai ai mai puțin de un minut.',
}

const headingId = (itemId: number) => `item-heading-${itemId}`

/**
 * A button that opens the file picker - with `capture`, the phone's camera directly (S2). The input sits inside the
 * label (visually hidden but focusable), so the whole button is the click target and the keyboard focus ring.
 */
function PhotoPicker({
  testId,
  capture = false,
  primary,
  disabled,
  onPick,
  children,
}: {
  testId: string
  capture?: boolean
  primary: boolean
  disabled: boolean
  onPick: (file: File) => void
  children: ReactNode
}) {
  return (
    <label
      className={cn(
        buttonVariants({ variant: primary ? 'default' : 'outline' }),
        'h-10 cursor-pointer px-4 has-[:focus-visible]:ring-3 has-[:focus-visible]:ring-ring/50',
        disabled && 'pointer-events-none opacity-50',
      )}
    >
      <input
        type="file"
        className="sr-only"
        data-testid={testId}
        accept="image/*"
        capture={capture ? 'environment' : undefined}
        disabled={disabled}
        onChange={(e) => {
          const file = e.target.files?.[0]
          if (file) onPick(file)
          e.target.value = '' // lets the student pick the same file again after an error
        }}
      />
      {children}
    </label>
  )
}

/**
 * Taking one quiz. Nothing is created until the student presses "Începe" (the start call creates
 * the attempt, or resumes the one in progress). Before that the page shows what the student is walking into: the
 * title, how many items and points, the clock and, for homework, the deadline. Every answer is saved to the server
 * as soon as it is given, so a closed tab or a dead battery loses nothing — reopening resumes with the saved
 * answers restored.
 *
 * A timed quiz shows a countdown, but only as a display: the SERVER holds the deadline and rejects answers
 * after it (HTTP 409). When the countdown reaches zero — or the server says time is up — the page hands the
 * attempt in with whatever was saved in time.
 *
 * With {@code ?mode=practice} (only for quizzes the teacher opened for it) the same screen becomes a practice: no
 * clock, and each answer comes back from the server with its verdict, the right option and the barem. It is never
 * graded. The server alone decides what to reveal — a graded test returns no feedback at all.
 *
 * The bottom bar numbers every item (answered ones filled) and jumps to it; handing in goes through a dialog that
 * lists what is still unanswered and can take the student to the first such item.
 */
export function TakeQuizPage() {
  const { id } = useParams<{ id: string }>()
  const quizId = Number(id)
  const navigate = useNavigate()
  const [searchParams] = useSearchParams()
  const mode: AttemptMode = searchParams.get('mode') === 'practice' ? 'PRACTICE' : 'TEST'
  const practice = mode === 'PRACTICE'
  // S4 "Exersează doar ce ai greșit": a practice may show just some questions (never a test, which is graded whole).
  const only = practice ? parseOnly(searchParams.get('only')) : null

  const [attempt, setAttempt] = useState<StartedAttemptDto | null>(null)
  const [selected, setSelected] = useState<Record<number, number>>({})
  const [photos, setPhotos] = useState<Record<number, number[]>>({}) // itemId -> the pages sent (photo ids), oldest first
  const [feedback, setFeedback] = useState<Record<number, AnswerFeedback | undefined>>({}) // practice only
  const [hints, setHints] = useState<Record<number, string[]>>({}) // itemId -> hints revealed so far (practice only)
  const [hintBusy, setHintBusy] = useState<Record<number, boolean>>({})
  const [saving, setSaving] = useState<Record<number, boolean>>({})
  const [itemErrors, setItemErrors] = useState<Record<number, string | undefined>>({})
  const [starting, setStarting] = useState(false)
  const [submitting, setSubmitting] = useState(false)
  const [confirming, setConfirming] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [preview, setPreview] = useState<QuizPreview | null>(null) // shown before start, so nothing is a surprise
  const [homeworkDue, setHomeworkDue] = useState<string | null>(null)
  const [receivedAt, setReceivedAt] = useState(0)
  const [now, setNow] = useState(() => Date.now())
  const autoSubmitted = useRef(false)
  usePageTitle(attempt?.quiz.title ?? preview?.title ?? (practice ? 'Exersare' : 'Test'))
  const jumpTarget = useRef<HTMLElement | null>(null) // where "Mergi la subiectul N" sends focus once the dialog closes

  useEffect(() => {
    let cancelled = false
    // Both purely informative: the quiz still starts without them.
    getQuizPreview(quizId)
      .then((p) => !cancelled && setPreview(p))
      .catch(() => undefined)
    if (!practice) {
      getMyAssignments()
        .then((list) => !cancelled && setHomeworkDue(todoAssignments(list).find((a) => a.quizId === quizId)?.dueAt ?? null))
        .catch(() => undefined)
    }
    return () => {
      cancelled = true
    }
  }, [quizId, practice])

  const deadlineAt = attempt?.deadlineAt ?? null
  useEffect(() => {
    if (!deadlineAt) return
    const timer = window.setInterval(() => setNow(Date.now()), 1000)
    return () => window.clearInterval(timer)
  }, [deadlineAt])

  const timeLeft = attempt && attempt.deadlineAt
    ? secondsLeft(attempt.deadlineAt, attempt.serverNow, receivedAt, now)
    : null
  const timeUp = timeLeft === 0

  /**
   * A 409 on an answer means "time is up" only for a timed attempt whose countdown has run out. Any other conflict
   * (e.g. the same answer saved twice at once from two tabs) is shown on the item and the student keeps working.
   */
  function isTimeUpConflict(e: unknown): boolean {
    return e instanceof ApiError && e.status === 409 && attempt?.deadlineAt != null
      && secondsLeft(attempt.deadlineAt, attempt.serverNow, receivedAt, Date.now()) === 0
  }

  /** Hands in what was saved, without asking: time is up. Tried once; if it fails the manual button remains. */
  async function submitBecauseTimeIsUp() {
    if (!attempt || autoSubmitted.current) return
    autoSubmitted.current = true
    setConfirming(false)
    setSubmitting(true)
    setError(null)
    try {
      await submitQuizAttempt(attempt.attemptId)
      navigate(`/quizzes/attempts/${attempt.attemptId}/result`, { replace: true })
    } catch (e) {
      if (e instanceof ApiError && e.status === 400) {
        // "no longer in progress": the server already handed it in when the time ran out — just show the result.
        navigate(`/quizzes/attempts/${attempt.attemptId}/result`, { replace: true })
        return
      }
      setError(`${errorMessage(e)} Apasă „Trimite lucrarea” ca să încerci din nou.`)
      setSubmitting(false)
    }
  }

  useEffect(() => {
    if (timeUp) void submitBecauseTimeIsUp()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [timeUp])

  function setItemBusy(itemId: number, busy: boolean) {
    setSaving((s) => ({ ...s, [itemId]: busy }))
  }
  function setItemError(itemId: number, message: string | undefined) {
    setItemErrors((s) => ({ ...s, [itemId]: message }))
  }

  /** The pages already sent for an open question (photo ids, oldest first). */
  const pagesOf = (itemId: number): number[] => photos[itemId] ?? []

  function isAnswered(item: StudentItemDto): boolean {
    return item.type === 'SINGLE_CHOICE' ? selected[item.id] !== undefined : (photos[item.id]?.length ?? 0) > 0
  }

  /** Scrolls an item into view and moves focus to its heading, so keyboard and screen-reader users land there too. */
  function goToItem(itemId: number) {
    const heading = document.getElementById(headingId(itemId))
    if (!heading) return
    const reduceMotion = window.matchMedia?.('(prefers-reduced-motion: reduce)').matches
    heading.scrollIntoView?.({ behavior: reduceMotion ? 'auto' : 'smooth', block: 'start' })
    heading.focus({ preventScroll: true })
  }

  async function start() {
    setStarting(true)
    setError(null)
    try {
      let data: StartedAttemptDto
      try {
        data = await startQuizAttempt(quizId, mode)
      } catch (e) {
        // 409 = a parallel start (double click, second tab) created the attempt first (Q11) — resume it.
        if (e instanceof ApiError && e.status === 409) {
          data = await startQuizAttempt(quizId, mode)
        } else {
          throw e
        }
      }
      const restoredChoices: Record<number, number> = {}
      const restoredPhotos: Record<number, number[]> = {}
      const restoredFeedback: Record<number, AnswerFeedback | undefined> = {}
      for (const answer of data.answers) {
        if (answer.selectedOptionId !== null) restoredChoices[answer.itemId] = answer.selectedOptionId
        if (answer.photoIds.length > 0) restoredPhotos[answer.itemId] = answer.photoIds
        if (answer.feedback) restoredFeedback[answer.itemId] = answer.feedback
      }
      setSelected(restoredChoices)
      setPhotos(restoredPhotos)
      setFeedback(restoredFeedback)
      setHints(Object.fromEntries(data.revealedHints.map((r) => [r.itemId, r.hints])))
      setReceivedAt(Date.now())
      setNow(Date.now())
      setAttempt(data)
    } catch (e) {
      setError(errorMessage(e))
    } finally {
      setStarting(false)
    }
  }

  /** Asks the server for hint `number` of an item (practice only); it keeps the order and records the usage. */
  async function askHint(itemId: number, number: number) {
    if (!attempt) return
    setItemError(itemId, undefined)
    setHintBusy((s) => ({ ...s, [itemId]: true }))
    try {
      const hint = await revealQuizHint(attempt.attemptId, itemId, number)
      setHints((s) => {
        const next = [...(s[itemId] ?? [])]
        next[hint.number - 1] = hint.text
        return { ...s, [itemId]: next }
      })
    } catch (e) {
      setItemError(itemId, errorMessage(e))
    } finally {
      setHintBusy((s) => ({ ...s, [itemId]: false }))
    }
  }

  async function choose(itemId: number, optionId: number) {
    if (!attempt) return
    const previous = selected[itemId]
    setSelected((s) => ({ ...s, [itemId]: optionId }))
    setItemError(itemId, undefined)
    setItemBusy(itemId, true)
    try {
      const result = await saveQuizAnswer(attempt.attemptId, itemId, optionId)
      setFeedback((s) => ({ ...s, [itemId]: result ?? undefined })) // null in a graded test: nothing is revealed
    } catch (e) {
      // Roll the radio back so the screen never shows an answer the server does not have.
      setSelected((s) => {
        const next = { ...s }
        if (previous === undefined) delete next[itemId]
        else next[itemId] = previous
        return next
      })
      setItemError(itemId, errorMessage(e))
      if (isTimeUpConflict(e)) void submitBecauseTimeIsUp() // the server says time is up
    } finally {
      setItemBusy(itemId, false)
    }
  }

  async function upload(itemId: number, file: File) {
    if (!attempt) return
    setItemError(itemId, undefined)
    setItemBusy(itemId, true)
    try {
      const toSend = await shrinkImage(file)
      if (toSend.size > MAX_UPLOAD_BYTES) {
        setItemError(itemId, 'Poza e prea mare (maxim 8 MB).')
        return
      }
      const result = await uploadQuizPhoto(attempt.attemptId, itemId, toSend)
      setPhotos((s) => ({ ...s, [itemId]: result.photoIds }))
      setFeedback((s) => ({ ...s, [itemId]: result.feedback ?? undefined }))
    } catch (e) {
      setItemError(itemId, errorMessage(e))
      if (isTimeUpConflict(e)) void submitBecauseTimeIsUp()
    } finally {
      setItemBusy(itemId, false)
    }
  }

  /** S2: takes one page off an open answer (the attempt is still open, so it can change). */
  async function removePhoto(itemId: number, photoId: number) {
    if (!attempt) return
    setItemError(itemId, undefined)
    setItemBusy(itemId, true)
    try {
      const result = await deleteQuizPhoto(attempt.attemptId, itemId, photoId)
      setPhotos((s) => ({ ...s, [itemId]: result.photoIds }))
    } catch (e) {
      setItemError(itemId, errorMessage(e))
      if (isTimeUpConflict(e)) void submitBecauseTimeIsUp()
    } finally {
      setItemBusy(itemId, false)
    }
  }

  async function submit() {
    if (!attempt) return
    jumpTarget.current = null
    setConfirming(false)
    setSubmitting(true)
    setError(null)
    try {
      await submitQuizAttempt(attempt.attemptId)
      navigate(`/quizzes/attempts/${attempt.attemptId}/result`, { replace: true })
    } catch (e) {
      setError(errorMessage(e))
      setSubmitting(false)
    }
  }

  // ----- Before start -----
  if (!attempt) {
    const limitMinutes = preview?.timeLimitMinutes ?? null
    return (
      <div className="min-h-screen bg-muted p-4">
        <div className="mx-auto max-w-xl space-y-4 pt-12">
          <Card>
            <CardContent className="space-y-4 py-6 text-center">
              <div className="space-y-1">
                <p className="text-xs font-medium tracking-wide text-muted-foreground uppercase">
                  {practice ? 'Exersare' : 'Test'}
                </p>
                <h1 className="text-xl font-semibold">
                  {preview?.title ?? (practice ? 'Gata de exersat?' : 'Ești gata să începi?')}
                </h1>
                {preview?.description && <p className="text-sm text-muted-foreground">{preview.description}</p>}
              </div>
              {preview && (
                <p data-testid="quiz-facts" className="text-sm font-medium">
                  {roCount(preview.itemCount, 'subiect', 'subiecte')} · {roCount(preview.maxScore, 'punct', 'puncte')}
                  {!practice && ` · ${limitMinutes !== null ? `⏱ ${formatMinutes(limitMinutes)}` : 'Fără limită de timp'}`}
                </p>
              )}
              {homeworkDue && (
                <p data-testid="quiz-homework-due" className="text-sm">
                  Temă — termen: {formatDate(homeworkDue)} ({dueCountdown(homeworkDue, Date.now())})
                </p>
              )}
              <p className="text-sm text-muted-foreground">
                Răspunsurile se salvează automat pe măsură ce lucrezi. Dacă închizi pagina, poți continua
                de unde ai rămas din „Testele mele”.
              </p>
              {practice && (
                <p className="text-sm font-medium" data-testid="quiz-practice-note">
                  Mod practică: vezi imediat dacă ai răspuns corect și cum se rezolvă. Nu se acordă notă, nu există
                  cronometru și nu afectează progresul — poți relua cât vrei.
                </p>
              )}
              {!practice && limitMinutes !== null && (
                <p className="text-sm font-medium" data-testid="quiz-limit-note">
                  ⏱ Testul are limită de timp: {formatMinutes(limitMinutes)}. Cronometrul pornește când apeși „Începe testul”
                  și nu se oprește dacă închizi pagina.
                </p>
              )}
              {error && <p className="text-sm text-destructive">{error}</p>}
              <div className="flex justify-center gap-2">
                <Link to="/quizzes" className={buttonVariants({ variant: 'outline' })}>Înapoi</Link>
                <Button onClick={start} disabled={starting} data-testid="quiz-start">
                  {starting ? 'Se pregătește...' : practice ? 'Începe practica' : 'Începe testul'}
                </Button>
              </div>
            </CardContent>
          </Card>
        </div>
      </div>
    )
  }

  // ----- Taking the quiz -----
  // Numbered over the whole quiz, so a filtered practice keeps the numbers of the test ("I.3"), headings re-shown
  // above the first visible question of each section.
  const numberedAll = numberItems(attempt.quiz.items)
  const focused = only ? numberedAll.filter((n) => only.has(n.item.id)) : []
  const filtered = focused.length > 0
  const numbered = filtered
    ? focused.map((n, i) => ({
        ...n,
        heading:
          n.item.section && (i === 0 || focused[i - 1].item.section !== n.item.section) ? parseSection(n.item.section) : null,
      }))
    : numberedAll
  const items = numbered.map((n) => n.item)
  const answeredCount = items.filter(isAnswered).length
  const labelOf = new Map(numbered.map((n) => [n.item.id, n.label]))
  const unanswered = numbered.flatMap((n) => (isAnswered(n.item) ? [] : [{ item: n.item, number: n.label }]))
  const askAboutUnanswered = !practice && !timeUp && unanswered.length > 0
  const tone = timeLeft !== null ? timerTone(timeLeft) : 'normal'

  const dialogTitle = practice
    ? 'Închei sesiunea de practică?'
    : timeUp
      ? 'Timpul a expirat'
      : askAboutUnanswered
        ? `Ai ${roCount(unanswered.length, 'subiect', 'subiecte')} fără răspuns`
        : 'Trimiți lucrarea?'
  const dialogBody = practice
    ? 'Poți relua practica oricând.'
    : timeUp
      ? 'Se trimit răspunsurile salvate la timp.'
      : askAboutUnanswered
        ? `${unanswered.length === 1 ? 'Subiectul' : 'Subiectele'} ${unanswered.map((u) => u.number).join(', ')} ${
            unanswered.length === 1 ? 'nu are' : 'nu au'
          } răspuns. Le poți completa acum sau poți trimite lucrarea așa. După trimitere nu mai poți modifica nimic.`
        : 'După trimitere nu mai poți modifica răspunsurile.'

  return (
    <div className="min-h-screen bg-muted p-4 pb-40">
      {timeLeft !== null && (
        <>
          <div
            role="timer"
            data-testid="quiz-timer"
            data-tone={tone}
            className={`fixed inset-x-0 top-0 z-10 border-b p-2 text-center text-sm font-semibold transition-colors ${TIMER_CLASSES[tone]}`}
          >
            {timeUp ? 'Timpul a expirat — se trimite lucrarea...' : `⏱ Timp rămas: ${formatClock(timeLeft)}`}
          </div>
          <div aria-live="polite" className="sr-only" data-testid="quiz-timer-announcement">
            {timeUp ? '' : TIMER_ANNOUNCEMENTS[tone]}
          </div>
        </>
      )}
      {practice && (
        <div
          data-testid="quiz-practice-banner"
          className="fixed inset-x-0 top-0 z-10 border-b bg-violet-500/15 p-2 text-center text-sm font-semibold text-violet-800 dark:text-violet-200"
        >
          Mod practică — răspunsurile se verifică pe loc, fără notă
        </div>
      )}
      <div className={`mx-auto max-w-3xl space-y-4 ${timeLeft !== null || practice ? 'pt-10' : ''}`}>
        <div>
          <h1 className="text-2xl font-semibold">{attempt.quiz.title}</h1>
          {attempt.quiz.description && (
            <p className="text-sm text-muted-foreground">{attempt.quiz.description}</p>
          )}
          {filtered && (
            <p data-testid="practice-only" className="mt-1 text-sm">
              Exersezi doar ce ai greșit: {roCount(items.length, 'subiect', 'subiecte')} ·{' '}
              <Link to={`/quizzes/${quizId}/take?mode=practice`} replace className="text-primary hover:underline">
                Exersează tot testul
              </Link>
            </p>
          )}
        </div>

        {numbered.map(({ item, label, local, heading }) => (
          <Fragment key={item.id}>
          {heading && <SectionHeadingView heading={heading} />}
          <Card className="scroll-mt-14">
            <CardContent className="space-y-3 py-4">
              <h2 id={headingId(item.id)} tabIndex={-1} className="scroll-mt-14 text-sm font-medium outline-none">
                {label.includes('.') ? `${local}.` : `Subiectul ${label}`} · {item.points} p
              </h2>
              <MathContent>{item.statement}</MathContent>
              <QuestionImage itemId={item.id} image={item.image} />

              {item.type === 'SINGLE_CHOICE' ? (
                <div className="space-y-2">
                  {item.options.map((option) => {
                    const fb = feedback[item.id]
                    const isRight = fb?.correctOptionId === option.id
                    const isWrongPick = fb?.correct === false && selected[item.id] === option.id
                    return (
                    <label
                      key={option.id}
                      className={`flex cursor-pointer items-center gap-3 rounded-lg border p-2 text-sm ${
                        isRight
                          ? 'border-emerald-500 bg-emerald-500/10'
                          : isWrongPick
                            ? 'border-destructive bg-destructive/10'
                            : selected[item.id] === option.id
                              ? 'border-primary bg-primary/5'
                              : 'border-border hover:bg-muted'
                      }`}
                    >
                      <input
                        type="radio"
                        name={`item-${item.id}`}
                        className="size-4 shrink-0"
                        checked={selected[item.id] === option.id}
                        disabled={saving[item.id] || timeUp}
                        onChange={() => choose(item.id, option.id)}
                      />
                      <MathContent inline>{option.text}</MathContent>
                      {/* A sign as well as a colour: the verdict must not depend on telling green from red. */}
                      {isRight && (
                        <span className="ml-auto font-semibold text-emerald-700 dark:text-emerald-300">
                          <span aria-hidden="true">✓</span>
                          <span className="sr-only"> (răspunsul corect)</span>
                        </span>
                      )}
                      {isWrongPick && (
                        <span className="ml-auto font-semibold text-destructive">
                          <span aria-hidden="true">✗</span>
                          <span className="sr-only"> (alegerea ta, greșită)</span>
                        </span>
                      )}
                    </label>
                    )
                  })}
                  {feedback[item.id] && (
                    <div data-testid="item-feedback" className="space-y-2 rounded-lg bg-muted p-3 text-sm">
                      <p
                        className={`font-medium ${
                          feedback[item.id]?.correct ? 'text-emerald-700 dark:text-emerald-300' : 'text-destructive'
                        }`}
                      >
                        {feedback[item.id]?.correct ? '✓ Corect!' : '✗ Greșit — răspunsul corect e marcat cu ✓.'}
                      </p>
                      {feedback[item.id]?.solution && (
                        <div>
                          <p className="text-xs font-medium text-muted-foreground">Rezolvare</p>
                          <MathContent>{feedback[item.id]!.solution!}</MathContent>
                        </div>
                      )}
                    </div>
                  )}
                </div>
              ) : (
                <div className="space-y-3 text-sm">
                  <p className="text-muted-foreground">
                    Rezolvă pe hârtie, apoi fotografiază rezolvarea și încarc-o aici — până la {MAX_PHOTOS} pagini, în ordine.
                  </p>
                  {pagesOf(item.id).length > 0 && (
                    <ul className="flex flex-wrap gap-3" aria-label={`Pozele tale pentru subiectul ${labelOf.get(item.id)}`}>
                      {pagesOf(item.id).map((photoId, index) => (
                        <li key={photoId} className="flex flex-col items-start gap-1" data-testid="item-photo-page">
                          <a href={ownPhotoUrl(attempt.attemptId, item.id, photoId)} target="_blank" rel="noreferrer">
                            <img
                              src={ownPhotoUrl(attempt.attemptId, item.id, photoId)}
                              alt={`Pagina ${index + 1} a rezolvării, subiectul ${labelOf.get(item.id)}`}
                              className="h-32 w-auto rounded-lg border bg-background object-contain"
                            />
                          </a>
                          <span className="flex items-center gap-1 text-xs text-muted-foreground">
                            Pagina {index + 1}
                            <Button
                              type="button"
                              size="xs"
                              variant="ghost"
                              disabled={saving[item.id] || timeUp}
                              onClick={() => removePhoto(item.id, photoId)}
                              aria-label={`Șterge pagina ${index + 1}`}
                            >
                              Șterge
                            </Button>
                          </span>
                        </li>
                      ))}
                    </ul>
                  )}
                  {pagesOf(item.id).length < MAX_PHOTOS ? (
                    <div className="flex flex-wrap gap-2">
                      {/* On a phone `capture` opens the camera straight away; on a computer it is a file picker. */}
                      <PhotoPicker
                        testId="item-camera"
                        capture
                        primary={pagesOf(item.id).length === 0}
                        disabled={saving[item.id] || timeUp}
                        onPick={(file) => upload(item.id, file)}
                      >
                        <span aria-hidden="true">📷</span>
                        <span>Fă o poză</span>
                      </PhotoPicker>
                      <PhotoPicker
                        testId="item-photo"
                        primary={false}
                        disabled={saving[item.id] || timeUp}
                        onPick={(file) => upload(item.id, file)}
                      >
                        <span aria-hidden="true">🖼️</span>
                        <span>{pagesOf(item.id).length > 0 ? 'Adaugă încă o pagină' : 'Alege o poză din galerie'}</span>
                      </PhotoPicker>
                    </div>
                  ) : (
                    <p className="text-muted-foreground">
                      Ai {MAX_PHOTOS} pagini, cât se poate trimite. Șterge una ca să pui alta în locul ei.
                    </p>
                  )}
                  {pagesOf(item.id).length > 0 && (
                    <p role="status" className="text-emerald-700 dark:text-emerald-300">
                      ✓ Poză încărcată · {roCount(pagesOf(item.id).length, 'pagină trimisă', 'pagini trimise')}.
                    </p>
                  )}
                  {feedback[item.id]?.solution && (
                    <div data-testid="item-feedback" className="space-y-1 rounded-lg bg-muted p-3">
                      <p className="text-xs font-medium text-muted-foreground">Barem — compară-l cu rezolvarea ta</p>
                      <MathContent>{feedback[item.id]!.solution!}</MathContent>
                    </div>
                  )}
                </div>
              )}

              {practice && item.hintCount > 0 && (
                <div data-testid="item-hints" className="space-y-2">
                  {(hints[item.id] ?? []).map((text, i) => (
                    <div
                      key={i}
                      data-testid="hint"
                      className="rounded-lg border border-amber-500/40 bg-amber-500/10 p-3 text-sm"
                    >
                      <p className="text-xs font-medium text-amber-800 dark:text-amber-200">
                        Indiciul {i + 1} din {item.hintCount}
                      </p>
                      <MathContent>{text}</MathContent>
                    </div>
                  ))}
                  {(hints[item.id] ?? []).length < item.hintCount && (
                    <Button
                      type="button"
                      size="sm"
                      variant="outline"
                      data-testid="hint-button"
                      disabled={hintBusy[item.id]}
                      onClick={() => askHint(item.id, (hints[item.id] ?? []).length + 1)}
                    >
                      {(hints[item.id] ?? []).length === 0 ? 'Vrei un indiciu?' : 'Încă un indiciu'} (
                      {(hints[item.id] ?? []).length}/{item.hintCount})
                    </Button>
                  )}
                </div>
              )}

              {saving[item.id] && <p className="text-xs text-muted-foreground">Se salvează...</p>}
              {itemErrors[item.id] && <p className="text-sm text-destructive">{itemErrors[item.id]}</p>}
            </CardContent>
          </Card>
          </Fragment>
        ))}

        {error && <p className="text-sm text-destructive">{error}</p>}
      </div>

      <div className="fixed inset-x-0 bottom-0 border-t bg-background p-3">
        <div className="mx-auto max-w-3xl space-y-2">
          <nav aria-label="Subiecte" className="flex gap-1.5 overflow-x-auto pb-1">
            {numbered.map(({ item, label }) => {
              const done = isAnswered(item)
              return (
                <button
                  key={item.id}
                  type="button"
                  aria-label={`Subiectul ${label}, ${done ? 'cu răspuns' : 'fără răspuns'}`}
                  onClick={() => goToItem(item.id)}
                  className={cn(
                    'h-9 min-w-9 shrink-0 rounded-lg border px-1.5 text-sm font-medium transition-colors outline-none focus-visible:ring-3 focus-visible:ring-ring/50',
                    done ? 'border-primary bg-primary text-primary-foreground' : 'border-border bg-background hover:bg-muted',
                  )}
                >
                  {label}
                </button>
              )
            })}
          </nav>
          <div className="flex items-center justify-between gap-3">
            <p className="text-sm text-muted-foreground">
              {answeredCount} / {items.length} {practice ? 'subiecte rezolvate' : 'răspunsuri salvate'}
            </p>
            <Button onClick={() => setConfirming(true)} disabled={submitting} data-testid="quiz-submit">
              {submitting ? 'Se trimite...' : practice ? 'Termină practica' : 'Trimite lucrarea'}
            </Button>
          </div>
        </div>
      </div>

      <ConfirmDialog
        open={confirming}
        title={dialogTitle}
        confirmLabel={practice ? 'Termină practica' : askAboutUnanswered ? 'Trimite oricum' : 'Trimite'}
        onConfirm={() => void submit()}
        onCancel={() => setConfirming(false)}
        finalFocus={() => jumpTarget.current ?? true}
        extraAction={
          askAboutUnanswered && (
            <Button
              variant="ghost"
              onClick={() => {
                const first = unanswered[0].item.id
                jumpTarget.current = document.getElementById(headingId(first))
                setConfirming(false)
                goToItem(first)
              }}
            >
              Mergi la subiectul {unanswered[0].number}
            </Button>
          )
        }
      >
        {dialogBody}
      </ConfirmDialog>
    </div>
  )
}
