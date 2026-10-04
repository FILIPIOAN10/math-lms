import { useEffect, useRef, useState } from 'react'
import { Link, useNavigate, useParams, useSearchParams } from 'react-router-dom'
import { Button, buttonVariants } from '@/components/ui/button'
import { Card, CardContent } from '@/components/ui/card'
import { MathContent } from '@/components/MathContent'
import {
  ApiError,
  getStudentQuizzes,
  revealQuizHint,
  saveQuizAnswer,
  startQuizAttempt,
  submitQuizAttempt,
  uploadQuizPhoto,
  type AnswerFeedback,
  type AttemptMode,
  type StartedAttemptDto,
} from '@/lib/api'
import { formatClock, secondsLeft } from '@/lib/countdown'
import { errorMessage } from '@/lib/errors'
import { formatMinutes } from '@/lib/format'
import { shrinkImage } from '@/lib/image'

const MAX_UPLOAD_BYTES = 8 * 1024 * 1024 // matches spring.servlet.multipart.max-file-size

/**
 * Taking one quiz. Nothing is created until the student presses "Începe" (the start call creates
 * the attempt, or resumes the one in progress). Every answer is saved to the server as soon as it
 * is given, so a closed tab or a dead battery loses nothing — reopening resumes with the saved
 * answers restored.
 *
 * A timed quiz shows a countdown, but only as a display: the SERVER holds the deadline and rejects answers
 * after it (HTTP 409). When the countdown reaches zero — or the server says time is up — the page hands the
 * attempt in with whatever was saved in time.
 *
 * With {@code ?mode=practice} (only for quizzes the teacher opened for it) the same screen becomes a practice: no
 * clock, and each answer comes back from the server with its verdict, the right option and the barem. It is never
 * graded. The server alone decides what to reveal — a graded test returns no feedback at all.
 */
export function TakeQuizPage() {
  const { id } = useParams<{ id: string }>()
  const quizId = Number(id)
  const navigate = useNavigate()
  const [searchParams] = useSearchParams()
  const mode: AttemptMode = searchParams.get('mode') === 'practice' ? 'PRACTICE' : 'TEST'
  const practice = mode === 'PRACTICE'

  const [attempt, setAttempt] = useState<StartedAttemptDto | null>(null)
  const [selected, setSelected] = useState<Record<number, number>>({})
  const [photos, setPhotos] = useState<Record<number, boolean>>({})
  const [feedback, setFeedback] = useState<Record<number, AnswerFeedback | undefined>>({}) // practice only
  const [hints, setHints] = useState<Record<number, string[]>>({}) // itemId -> hints revealed so far (practice only)
  const [hintBusy, setHintBusy] = useState<Record<number, boolean>>({})
  const [saving, setSaving] = useState<Record<number, boolean>>({})
  const [itemErrors, setItemErrors] = useState<Record<number, string | undefined>>({})
  const [starting, setStarting] = useState(false)
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [limitMinutes, setLimitMinutes] = useState<number | null>(null) // shown before start, so no one is surprised by the clock
  const [receivedAt, setReceivedAt] = useState(0)
  const [now, setNow] = useState(() => Date.now())
  const autoSubmitted = useRef(false)

  useEffect(() => {
    let cancelled = false
    getStudentQuizzes()
      .then((list) => !cancelled && setLimitMinutes(list.find((q) => q.id === quizId)?.timeLimitMinutes ?? null))
      .catch(() => undefined) // purely informative: the quiz still starts without it
    return () => {
      cancelled = true
    }
  }, [quizId])

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

  /** Hands in what was saved, without asking: time is up. Tried once; if it fails the manual button remains. */
  async function submitBecauseTimeIsUp() {
    if (!attempt || autoSubmitted.current) return
    autoSubmitted.current = true
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
      const restoredPhotos: Record<number, boolean> = {}
      const restoredFeedback: Record<number, AnswerFeedback | undefined> = {}
      for (const answer of data.answers) {
        if (answer.selectedOptionId !== null) restoredChoices[answer.itemId] = answer.selectedOptionId
        if (answer.photoUploaded) restoredPhotos[answer.itemId] = true
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
      if (e instanceof ApiError && e.status === 409) void submitBecauseTimeIsUp() // the server says time is up
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
      setPhotos((s) => ({ ...s, [itemId]: true }))
      setFeedback((s) => ({ ...s, [itemId]: result ?? undefined }))
    } catch (e) {
      setItemError(itemId, errorMessage(e))
      if (e instanceof ApiError && e.status === 409) void submitBecauseTimeIsUp()
    } finally {
      setItemBusy(itemId, false)
    }
  }

  async function submit() {
    if (!attempt) return
    const unanswered = attempt.quiz.items.filter((item) =>
      item.type === 'SINGLE_CHOICE' ? selected[item.id] === undefined : !photos[item.id],
    ).length
    const question =
      practice ? 'Închei sesiunea de practică?'
      : timeUp ? 'Timpul a expirat. Trimiți lucrarea cu răspunsurile salvate?'
      : unanswered > 0
        ? `Ai ${unanswered} subiect(e) fără răspuns. Trimiți lucrarea oricum?`
        : 'Trimiți lucrarea? După trimitere nu mai poți modifica răspunsurile.'
    if (!window.confirm(question)) return

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
    return (
      <div className="min-h-screen bg-muted p-4">
        <div className="mx-auto max-w-xl space-y-4 pt-12">
          <Card>
            <CardContent className="space-y-4 py-6 text-center">
              <h1 className="text-xl font-semibold">{practice ? 'Gata de exersat?' : 'Ești gata să începi?'}</h1>
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
  const answeredCount = attempt.quiz.items.filter((item) =>
    item.type === 'SINGLE_CHOICE' ? selected[item.id] !== undefined : photos[item.id],
  ).length

  return (
    <div className="min-h-screen bg-muted p-4 pb-28">
      {timeLeft !== null && (
        <div
          role="timer"
          data-testid="quiz-timer"
          className={`fixed inset-x-0 top-0 z-10 border-b p-2 text-center text-sm font-semibold ${
            timeLeft <= 60 ? 'bg-destructive text-destructive-foreground' : 'bg-background'
          }`}
        >
          {timeUp ? 'Timpul a expirat — se trimite lucrarea...' : `⏱ Timp rămas: ${formatClock(timeLeft)}`}
        </div>
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
        </div>

        {attempt.quiz.items.map((item, index) => (
          <Card key={item.id}>
            <CardContent className="space-y-3 py-4">
              <p className="text-sm font-medium">
                Subiectul {index + 1} · {item.points} p
              </p>
              <MathContent>{item.statement}</MathContent>

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
                        checked={selected[item.id] === option.id}
                        disabled={saving[item.id] || timeUp}
                        onChange={() => choose(item.id, option.id)}
                      />
                      <MathContent className="inline">{option.text}</MathContent>
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
                        {feedback[item.id]?.correct ? '✓ Corect!' : '✗ Greșit — răspunsul corect e marcat cu verde.'}
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
                <div className="space-y-2 text-sm">
                  <p className="text-muted-foreground">
                    Rezolvă pe hârtie, apoi fotografiază rezolvarea și încarc-o aici.
                  </p>
                  <input
                    type="file"
                    data-testid="item-photo"
                    accept="image/*"
                    disabled={saving[item.id] || timeUp}
                    onChange={(e) => {
                      const file = e.target.files?.[0]
                      if (file) upload(item.id, file)
                      e.target.value = '' // lets the student pick the same file again after an error
                    }}
                  />
                  {photos[item.id] && (
                    <p className="text-emerald-700 dark:text-emerald-300">
                      ✓ Poză încărcată. Poți alege alta ca să o înlocuiești.
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
        ))}

        {error && <p className="text-sm text-destructive">{error}</p>}
      </div>

      <div className="fixed inset-x-0 bottom-0 border-t bg-background p-3">
        <div className="mx-auto flex max-w-3xl items-center justify-between gap-3">
          <p className="text-sm text-muted-foreground">
            {answeredCount} / {attempt.quiz.items.length} {practice ? 'subiecte rezolvate' : 'răspunsuri salvate'}
          </p>
          <Button onClick={submit} disabled={submitting} data-testid="quiz-submit">
            {submitting ? 'Se trimite...' : practice ? 'Termină practica' : 'Trimite lucrarea'}
          </Button>
        </div>
      </div>
    </div>
  )
}
