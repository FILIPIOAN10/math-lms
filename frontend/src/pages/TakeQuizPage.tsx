import { useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { Button, buttonVariants } from '@/components/ui/button'
import { Card, CardContent } from '@/components/ui/card'
import { MathContent } from '@/components/MathContent'
import {
  ApiError,
  saveQuizAnswer,
  startQuizAttempt,
  submitQuizAttempt,
  uploadQuizPhoto,
  type StartedAttemptDto,
} from '@/lib/api'
import { errorMessage } from '@/lib/errors'
import { shrinkImage } from '@/lib/image'

const MAX_UPLOAD_BYTES = 8 * 1024 * 1024 // matches spring.servlet.multipart.max-file-size

/**
 * Taking one quiz. Nothing is created until the student presses "Începe" (the start call creates
 * the attempt, or resumes the one in progress). Every answer is saved to the server as soon as it
 * is given, so a closed tab or a dead battery loses nothing — reopening resumes with the saved
 * answers restored.
 */
export function TakeQuizPage() {
  const { id } = useParams<{ id: string }>()
  const quizId = Number(id)
  const navigate = useNavigate()

  const [attempt, setAttempt] = useState<StartedAttemptDto | null>(null)
  const [selected, setSelected] = useState<Record<number, number>>({})
  const [photos, setPhotos] = useState<Record<number, boolean>>({})
  const [saving, setSaving] = useState<Record<number, boolean>>({})
  const [itemErrors, setItemErrors] = useState<Record<number, string | undefined>>({})
  const [starting, setStarting] = useState(false)
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState<string | null>(null)

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
        data = await startQuizAttempt(quizId)
      } catch (e) {
        // 409 = a parallel start (double click, second tab) created the attempt first (Q11) — resume it.
        if (e instanceof ApiError && e.status === 409) {
          data = await startQuizAttempt(quizId)
        } else {
          throw e
        }
      }
      const restoredChoices: Record<number, number> = {}
      const restoredPhotos: Record<number, boolean> = {}
      for (const answer of data.answers) {
        if (answer.selectedOptionId !== null) restoredChoices[answer.itemId] = answer.selectedOptionId
        if (answer.photoUploaded) restoredPhotos[answer.itemId] = true
      }
      setSelected(restoredChoices)
      setPhotos(restoredPhotos)
      setAttempt(data)
    } catch (e) {
      setError(errorMessage(e))
    } finally {
      setStarting(false)
    }
  }

  async function choose(itemId: number, optionId: number) {
    if (!attempt) return
    const previous = selected[itemId]
    setSelected((s) => ({ ...s, [itemId]: optionId }))
    setItemError(itemId, undefined)
    setItemBusy(itemId, true)
    try {
      await saveQuizAnswer(attempt.attemptId, itemId, optionId)
    } catch (e) {
      // Roll the radio back so the screen never shows an answer the server does not have.
      setSelected((s) => {
        const next = { ...s }
        if (previous === undefined) delete next[itemId]
        else next[itemId] = previous
        return next
      })
      setItemError(itemId, errorMessage(e))
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
      await uploadQuizPhoto(attempt.attemptId, itemId, toSend)
      setPhotos((s) => ({ ...s, [itemId]: true }))
    } catch (e) {
      setItemError(itemId, errorMessage(e))
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
      unanswered > 0
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
              <h1 className="text-xl font-semibold">Ești gata să începi?</h1>
              <p className="text-sm text-muted-foreground">
                Răspunsurile se salvează automat pe măsură ce lucrezi. Dacă închizi pagina, poți continua
                de unde ai rămas din „Testele mele”.
              </p>
              {error && <p className="text-sm text-destructive">{error}</p>}
              <div className="flex justify-center gap-2">
                <Link to="/quizzes" className={buttonVariants({ variant: 'outline' })}>Înapoi</Link>
                <Button onClick={start} disabled={starting} data-testid="quiz-start">
                  {starting ? 'Se pregătește...' : 'Începe testul'}
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
      <div className="mx-auto max-w-3xl space-y-4">
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
                  {item.options.map((option) => (
                    <label
                      key={option.id}
                      className={`flex cursor-pointer items-center gap-3 rounded-lg border p-2 text-sm ${
                        selected[item.id] === option.id ? 'border-primary bg-primary/5' : 'border-border hover:bg-muted'
                      }`}
                    >
                      <input
                        type="radio"
                        name={`item-${item.id}`}
                        checked={selected[item.id] === option.id}
                        disabled={saving[item.id]}
                        onChange={() => choose(item.id, option.id)}
                      />
                      <MathContent className="inline">{option.text}</MathContent>
                    </label>
                  ))}
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
                    disabled={saving[item.id]}
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
            {answeredCount} / {attempt.quiz.items.length} răspunsuri salvate
          </p>
          <Button onClick={submit} disabled={submitting} data-testid="quiz-submit">
            {submitting ? 'Se trimite...' : 'Trimite lucrarea'}
          </Button>
        </div>
      </div>
    </div>
  )
}
