import { useEffect, useId, useRef, useState, type FormEvent } from 'react'
import { Link } from 'react-router-dom'
import { MessageCircleQuestion } from 'lucide-react'
import { Button, buttonVariants } from '@/components/ui/button'
import { Dialog } from '@/components/ui/dialog'
import { Textarea } from '@/components/ui/textarea'
import { MathContent } from '@/components/MathContent'
import {
  askStudentTutor,
  getStudentTutor,
  type StudentTutorStatus,
  type TutorExplanation,
  type TutorTurn,
} from '@/lib/api'
import { errorMessage } from '@/lib/errors'
import { roCount } from '@/lib/format'
import { cn } from '@/lib/utils'

const NOT_IN_BOOK = 'Nu am asta în manual.'
const MAX_QUESTION = 500
/** The server takes the last 6 messages (3 questions and their answers) to understand a follow-up. */
const HISTORY = 6

type Message = { role: 'user'; text: string } | { role: 'bot'; answer: TutorExplanation }

/** The server refuses a history message over 1000 characters; only the gist of an answer is needed for a follow-up. */
const MAX_TURN = 1000

function asTurn(message: Message): TutorTurn {
  return message.role === 'user'
    ? { role: 'user', text: message.text }
    : { role: 'bot', text: (message.answer.text ?? NOT_IN_BOOK).slice(0, MAX_TURN) }
}

/**
 * The student's "Nu înțeleg…" box: they write what they do not understand, an AI answers in two or three sentences
 * taken only from the EN lessons, with a link to the lesson at its page and to "Exersează capitolul" - or says it is not
 * in the lessons and that the teacher will see the question. It never solves an exercise, says on every answer that
 * an AI wrote it, and is closed while a graded test is open (the server decides; this only shows it). Not there at
 * all while the server has it switched off.
 */
export function StudentTutorBox() {
  const [status, setStatus] = useState<StudentTutorStatus | null>(null)
  const [open, setOpen] = useState(false)
  const [messages, setMessages] = useState<Message[]>([])
  const [draft, setDraft] = useState('')
  const [asking, setAsking] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const inputId = useId()
  const endRef = useRef<HTMLDivElement | null>(null)

  useEffect(() => {
    let cancelled = false
    getStudentTutor()
      .then((s) => !cancelled && setStatus(s))
      .catch(() => undefined) // no box rather than an error on every page
    return () => {
      cancelled = true
    }
  }, [])

  // A test may have been started or handed in since the page loaded: ask again each time the box opens.
  function openBox() {
    setOpen(true)
    setError(null)
    getStudentTutor().then(setStatus).catch(() => undefined)
  }

  useEffect(() => {
    endRef.current?.scrollIntoView?.({ block: 'end' })
  }, [messages, asking])

  if (!status?.enabled) return null

  const remaining = status.remainingToday
  const question = draft.trim()

  async function submit(event: FormEvent) {
    event.preventDefault()
    if (!question || asking || remaining <= 0) return
    const history = messages.slice(-HISTORY).map(asTurn)
    setMessages((m) => [...m, { role: 'user', text: question }])
    setDraft('')
    setAsking(true)
    setError(null)
    try {
      const answer = await askStudentTutor(question, history)
      setMessages((m) => [...m, { role: 'bot', answer }])
      setStatus((s) => (s ? { ...s, remainingToday: answer.remainingToday } : s))
    } catch (e) {
      setError(errorMessage(e))
      setMessages((m) => m.slice(0, -1)) // not answered: the question goes back into the box
      setDraft(question)
      getStudentTutor().then(setStatus).catch(() => undefined)
    } finally {
      setAsking(false)
    }
  }

  return (
    <>
      <Button
        type="button"
        onClick={openBox}
        data-testid="tutor-open"
        className="fixed right-4 bottom-[calc(4.25rem+env(safe-area-inset-bottom))] z-30 shadow-lg md:bottom-4"
      >
        <MessageCircleQuestion aria-hidden="true" className="size-4" />
        Nu înțeleg…
      </Button>
      <Dialog open={open} onOpenChange={setOpen} title="Nu înțeleg…" wide>
        <div className="space-y-3">
          <p data-testid="tutor-ai-notice" className="rounded-lg bg-muted px-3 py-2 text-xs text-muted-foreground">
            <strong className="font-medium text-foreground">Asistent AI.</strong> Răspunsurile sunt scrise automat de un
            model de inteligență artificială, doar din lecțiile EN din platformă. Poate greși: verifică mereu în lecție.
            Nu rezolvă exerciții, îți arată regula și unde o găsești. Nu scrie date personale.
          </p>

          {messages.length > 0 && (
            <div aria-live="polite" className="max-h-[50dvh] space-y-3 overflow-y-auto">
              {messages.map((m, i) =>
                m.role === 'user' ? (
                  <p key={i} className="ml-auto w-fit max-w-[85%] rounded-lg bg-primary/10 px-3 py-2 text-sm break-words">
                    {m.text}
                  </p>
                ) : (
                  <Answer key={i} answer={m.answer} onFollow={() => setOpen(false)} />
                ),
              )}
              {asking && <p className="text-sm text-muted-foreground">Caut în lecții…</p>}
              <div ref={endRef} />
            </div>
          )}

          {status.openTest ? (
            <p className="text-sm">
              Asistentul e oprit cât timp ai un test deschis: „{status.openTest}”. Termină-l, apoi întreabă.
            </p>
          ) : (
            <form onSubmit={submit} className="space-y-2">
              <label htmlFor={inputId} className="text-sm font-medium">
                Ce nu înțelegi?
              </label>
              <Textarea
                id={inputId}
                value={draft}
                maxLength={MAX_QUESTION}
                rows={2}
                placeholder="de ex. cum scot factor comun?"
                onChange={(e) => setDraft(e.target.value)}
                onKeyDown={(e) => {
                  if (e.key === 'Enter' && !e.shiftKey) {
                    e.preventDefault()
                    e.currentTarget.form?.requestSubmit()
                  }
                }}
              />
              {error && (
                <p role="alert" className="text-sm text-destructive">
                  {error}
                </p>
              )}
              <div className="flex flex-wrap items-center justify-between gap-2">
                <span className="text-xs text-muted-foreground">
                  {remaining > 0
                    ? `Îți ${remaining === 1 ? 'mai rămâne' : 'mai rămân'} ${roCount(remaining, 'întrebare', 'întrebări')} azi.`
                    : 'Ai folosit toate întrebările de azi. Revino mâine.'}
                </span>
                <Button type="submit" size="sm" disabled={!question || asking || remaining <= 0}>
                  Întreabă
                </Button>
              </div>
            </form>
          )}
        </div>
      </Dialog>
    </>
  )
}

function Answer({ answer, onFollow }: { answer: TutorExplanation; onFollow: () => void }) {
  return (
    <div data-testid="tutor-answer" className="max-w-[95%] space-y-2 rounded-lg border bg-card px-3 py-2 text-sm">
      <p className="text-xs font-medium text-muted-foreground">Răspuns AI</p>
      {answer.answered && answer.text ? (
        <>
          <MathContent>{answer.text}</MathContent>
          <div className="flex flex-wrap gap-2">
            {answer.lessons.map((lesson) => (
              <Link
                key={`${lesson.chapterId}-${lesson.sectionId}`}
                to={lesson.sectionId === null ? `/capitole/${lesson.chapterId}` : `/capitole/${lesson.chapterId}?sectiune=${lesson.sectionId}`}
                onClick={onFollow}
                className={cn(buttonVariants({ size: 'sm', variant: 'outline' }), 'h-auto py-1.5 text-left whitespace-normal')}
              >
                Deschide lecția {lesson.chapterCode}, pagina {lesson.bookPage}
                <span className="sr-only"> ({lesson.title})</span>
              </Link>
            ))}
            {answer.lessons[0] && (
              <Link
                to={`/capitole/${answer.lessons[0].chapterId}/exersare`}
                onClick={onFollow}
                className={buttonVariants({ size: 'sm' })}
              >
                Exersează capitolul
              </Link>
            )}
          </div>
        </>
      ) : (
        <p>
          {NOT_IN_BOOK}
          {answer.sentToTeacher && ' Am trimis întrebarea profesorului tău, ca să o lămurească la clasă.'}
        </p>
      )}
    </div>
  )
}
