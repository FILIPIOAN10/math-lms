import { useEffect, useState, type FormEvent } from 'react'
import { Link } from 'react-router-dom'
import { Button, buttonVariants } from '@/components/ui/button'
import { Card, CardContent } from '@/components/ui/card'
import { Dialog } from '@/components/ui/dialog'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Textarea } from '@/components/ui/textarea'
import { MathContent } from '@/components/MathContent'
import {
  addQuizItem,
  createQuiz,
  deleteQuiz,
  deleteQuizItem,
  getQuiz,
  listQuizzes,
  setQuizPublished,
  updateQuiz,
  updateQuizItem,
  type ItemInput,
  type QuizDetail,
  type QuizItemDto,
  type QuizItemType,
  type QuizSummary,
} from '@/lib/api'

const selectClass =
  'h-9 rounded-lg border border-border bg-background px-2 text-sm outline-none focus-visible:border-ring focus-visible:ring-3 focus-visible:ring-ring/50 disabled:opacity-50'

function errorMessage(e: unknown): string {
  const body = (e as { body?: string })?.body
  return body && body.length > 0 ? body : 'Operația a eșuat. Reîncearcă.'
}

// ---------- Quiz create/edit dialog ----------

function QuizDialog({
  open,
  onClose,
  initialTitle,
  initialDescription,
  onSubmit,
}: {
  open: boolean
  onClose: () => void
  initialTitle: string
  initialDescription: string
  onSubmit: (title: string, description: string | null) => Promise<void>
}) {
  const [title, setTitle] = useState(initialTitle)
  const [description, setDescription] = useState(initialDescription)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)

  async function submit(e: FormEvent) {
    e.preventDefault()
    setError(null)
    setBusy(true)
    try {
      await onSubmit(title.trim(), description.trim() || null)
      onClose()
    } catch (err) {
      setError(errorMessage(err))
    } finally {
      setBusy(false)
    }
  }

  return (
    <Dialog open={open} onOpenChange={(o) => !o && onClose()} title={initialTitle ? 'Editează quiz-ul' : 'Adaugă quiz'}>
      <form className="flex flex-col gap-4" onSubmit={submit}>
        <div className="flex flex-col gap-2">
          <Label htmlFor="qtitle">Titlu</Label>
          <Input id="qtitle" value={title} onChange={(e) => setTitle(e.target.value)} required autoFocus />
        </div>
        <div className="flex flex-col gap-2">
          <Label htmlFor="qdesc">Descriere (opțional)</Label>
          <Textarea id="qdesc" value={description} onChange={(e) => setDescription(e.target.value)} />
        </div>
        {error && <p className="text-sm text-destructive">{error}</p>}
        <div className="flex justify-end gap-2">
          <Button type="button" variant="outline" onClick={onClose} disabled={busy}>Anulează</Button>
          <Button type="submit" disabled={busy || title.trim() === ''}>Salvează</Button>
        </div>
      </form>
    </Dialog>
  )
}

// ---------- Item create/edit dialog ----------

interface OptionDraft {
  text: string
  correct: boolean
}

function ItemDialog({
  open,
  onClose,
  initial,
  onSubmit,
}: {
  open: boolean
  onClose: () => void
  initial: QuizItemDto | null
  onSubmit: (input: ItemInput) => Promise<void>
}) {
  const [type, setType] = useState<QuizItemType>(initial?.type ?? 'SINGLE_CHOICE')
  const [statement, setStatement] = useState(initial?.statement ?? '')
  const [points, setPoints] = useState(String(initial?.points ?? 5))
  const [solution, setSolution] = useState(initial?.solution ?? '')
  const [options, setOptions] = useState<OptionDraft[]>(
    initial && initial.options.length > 0
      ? initial.options.map((o) => ({ text: o.text, correct: o.correct }))
      : [
          { text: '', correct: true },
          { text: '', correct: false },
        ],
  )
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const isEdit = initial !== null

  function setCorrect(index: number) {
    setOptions((prev) => prev.map((o, i) => ({ ...o, correct: i === index })))
  }
  function setOptionText(index: number, text: string) {
    setOptions((prev) => prev.map((o, i) => (i === index ? { ...o, text } : o)))
  }
  function addOption() {
    setOptions((prev) => [...prev, { text: '', correct: false }])
  }
  function removeOption(index: number) {
    setOptions((prev) => (prev.length <= 2 ? prev : prev.filter((_, i) => i !== index)))
  }

  async function submit(e: FormEvent) {
    e.preventDefault()
    setError(null)
    if (type === 'SINGLE_CHOICE') {
      if (options.some((o) => o.text.trim() === '')) {
        setError('Completează textul fiecărei variante.')
        return
      }
      if (options.filter((o) => o.correct).length !== 1) {
        setError('Bifează exact o variantă corectă.')
        return
      }
    }
    const input: ItemInput = {
      type,
      position: initial?.position ?? 0,
      statement: statement.trim(),
      points: Number(points) || 0,
      solution: type === 'OPEN' ? solution.trim() || null : null,
      options:
        type === 'SINGLE_CHOICE'
          ? options.map((o, i) => ({ position: i, text: o.text.trim(), correct: o.correct }))
          : null,
    }
    setBusy(true)
    try {
      await onSubmit(input)
      onClose()
    } catch (err) {
      setError(errorMessage(err))
    } finally {
      setBusy(false)
    }
  }

  return (
    <Dialog
      open={open}
      onOpenChange={(o) => !o && onClose()}
      title={isEdit ? 'Editează subiectul' : 'Adaugă subiect'}
      description="Enunțul, variantele și baremul pot conține LaTeX între $…$."
    >
      <form className="flex max-h-[75vh] flex-col gap-4 overflow-y-auto pr-1" onSubmit={submit}>
        <div className="flex flex-col gap-2">
          <Label htmlFor="itype">Tip</Label>
          <select
            id="itype"
            className={selectClass}
            value={type}
            disabled={isEdit}
            onChange={(e) => setType(e.target.value as QuizItemType)}
          >
            <option value="SINGLE_CHOICE">Grilă (o singură variantă corectă)</option>
            <option value="OPEN">Deschis (rezolvare completă)</option>
          </select>
          {isEdit && <p className="text-xs text-muted-foreground">Tipul nu se poate schimba după creare.</p>}
        </div>

        <div className="flex flex-col gap-2">
          <Label htmlFor="istatement">Enunț</Label>
          <Textarea id="istatement" value={statement} onChange={(e) => setStatement(e.target.value)} required />
          {statement.trim() !== '' && (
            <MathContent className="rounded-md bg-muted/40 px-2 py-1 text-sm">{statement}</MathContent>
          )}
        </div>

        <div className="flex flex-col gap-2">
          <Label htmlFor="ipoints">Punctaj</Label>
          <Input id="ipoints" type="number" min={0} value={points} onChange={(e) => setPoints(e.target.value)} className="w-28" />
        </div>

        {type === 'SINGLE_CHOICE' ? (
          <div className="flex flex-col gap-2">
            <Label>Variante (bifează corecta)</Label>
            {options.map((o, i) => (
              <div key={i} className="flex items-center gap-2">
                <input
                  type="radio"
                  name="correct-option"
                  checked={o.correct}
                  onChange={() => setCorrect(i)}
                  aria-label={`Varianta ${i + 1} corectă`}
                />
                <Input value={o.text} onChange={(e) => setOptionText(i, e.target.value)} placeholder={`Varianta ${i + 1}`} />
                <Button type="button" variant="ghost" size="icon-sm" onClick={() => removeOption(i)} disabled={options.length <= 2} aria-label="Șterge varianta">
                  ×
                </Button>
              </div>
            ))}
            <Button type="button" variant="outline" size="sm" onClick={addOption} className="self-start">
              Adaugă variantă
            </Button>
          </div>
        ) : (
          <div className="flex flex-col gap-2">
            <Label htmlFor="isolution">Barem / răspuns corect (opțional)</Label>
            <Textarea id="isolution" value={solution} onChange={(e) => setSolution(e.target.value)} />
          </div>
        )}

        {error && <p className="text-sm text-destructive">{error}</p>}
        <div className="flex justify-end gap-2">
          <Button type="button" variant="outline" onClick={onClose} disabled={busy}>Anulează</Button>
          <Button type="submit" disabled={busy || statement.trim() === ''}>Salvează</Button>
        </div>
      </form>
    </Dialog>
  )
}

// ---------- Page ----------

export function AdminQuizzesPage() {
  const [quizzes, setQuizzes] = useState<QuizSummary[]>([])
  const [quiz, setQuiz] = useState<QuizDetail | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [quizDialog, setQuizDialog] = useState<{ item: QuizSummary | null } | null>(null)
  const [itemDialog, setItemDialog] = useState<{ item: QuizItemDto | null } | null>(null)

  useEffect(() => {
    listQuizzes()
      .then(setQuizzes)
      .catch((e) => setError(errorMessage(e)))
      .finally(() => setLoading(false))
  }, [])

  const reloadList = () => listQuizzes().then(setQuizzes)
  const reloadQuiz = (id: number) => getQuiz(id).then(setQuiz)

  async function openBuilder(id: number) {
    setError(null)
    try {
      setQuiz(await getQuiz(id))
    } catch (e) {
      setError(errorMessage(e))
    }
  }

  async function togglePublish(q: QuizSummary | QuizDetail) {
    setError(null)
    try {
      await setQuizPublished(q.id, q.status !== 'PUBLISHED')
      await reloadList()
      if (quiz && quiz.id === q.id) await reloadQuiz(q.id)
    } catch (e) {
      setError(errorMessage(e))
    }
  }

  async function removeQuiz(q: QuizSummary) {
    if (!window.confirm(`Sigur ștergi quiz-ul „${q.title}"?`)) return
    setError(null)
    try {
      await deleteQuiz(q.id)
      await reloadList()
    } catch (e) {
      setError(errorMessage(e))
    }
  }

  async function removeItem(item: QuizItemDto) {
    if (!quiz || !window.confirm('Sigur ștergi acest subiect?')) return
    setError(null)
    try {
      await deleteQuizItem(item.id)
      await reloadQuiz(quiz.id)
    } catch (e) {
      setError(errorMessage(e))
    }
  }

  return (
    <div className="min-h-screen bg-muted p-4">
      <div className="mx-auto max-w-3xl space-y-4">
        <div className="flex items-center justify-between">
          <nav className="flex items-center gap-1 text-sm text-muted-foreground">
            <button className="hover:text-foreground hover:underline" onClick={() => setQuiz(null)}>Quiz-uri</button>
            {quiz && (<><span>/</span><span className="text-foreground">{quiz.title}</span></>)}
          </nav>
          <Link to="/" className={buttonVariants({ variant: 'outline', size: 'sm' })}>Acasă</Link>
        </div>

        {error && <p className="text-sm text-destructive">{error}</p>}

        {/* Builder mode */}
        {quiz ? (
          <div className="space-y-4">
            <div className="flex flex-wrap items-center justify-between gap-2">
              <span className={`rounded-md px-2 py-0.5 text-xs font-medium ${quiz.status === 'PUBLISHED' ? 'bg-emerald-500/15 text-emerald-700 dark:text-emerald-300' : 'bg-amber-500/15 text-amber-700 dark:text-amber-300'}`}>
                {quiz.status === 'PUBLISHED' ? 'Publicat' : 'Ciornă'}
              </span>
              <div className="flex gap-2">
                <Button size="sm" variant="outline" onClick={() => togglePublish(quiz)}>
                  {quiz.status === 'PUBLISHED' ? 'Depublică' : 'Publică'}
                </Button>
                <Button size="sm" onClick={() => setItemDialog({ item: null })}>Adaugă subiect</Button>
              </div>
            </div>

            {quiz.items.length === 0 ? (
              <p className="text-muted-foreground">Niciun subiect încă. Adaugă primul.</p>
            ) : (
              <div className="space-y-2">
                {quiz.items.map((item, i) => (
                  <Card key={item.id}>
                    <CardContent className="space-y-2 py-3">
                      <div className="flex items-start justify-between gap-2">
                        <p className="text-sm font-medium">
                          {i + 1}. {item.type === 'SINGLE_CHOICE' ? 'Grilă' : 'Deschis'} · {item.points} p
                        </p>
                        <div className="flex shrink-0 gap-2">
                          <Button size="xs" variant="outline" onClick={() => setItemDialog({ item })}>Editează</Button>
                          <Button size="xs" variant="destructive" onClick={() => removeItem(item)}>Șterge</Button>
                        </div>
                      </div>
                      <MathContent className="text-sm">{item.statement}</MathContent>
                      {item.type === 'SINGLE_CHOICE' && (
                        <ul className="space-y-1 text-sm">
                          {item.options.map((o) => (
                            <li key={o.id} className={o.correct ? 'font-medium text-emerald-700 dark:text-emerald-300' : ''}>
                              {o.correct ? '✓ ' : '• '}
                              <MathContent className="inline">{o.text}</MathContent>
                            </li>
                          ))}
                        </ul>
                      )}
                    </CardContent>
                  </Card>
                ))}
              </div>
            )}
          </div>
        ) : (
          /* List mode */
          <>
            <div className="flex justify-end">
              <Button size="sm" onClick={() => setQuizDialog({ item: null })}>Adaugă quiz</Button>
            </div>
            {loading && <p className="text-muted-foreground">Se încarcă...</p>}
            {!loading && quizzes.length === 0 && <p className="text-muted-foreground">Niciun quiz. Adaugă primul.</p>}
            <div className="space-y-2">
              {quizzes.map((q) => (
                <Card key={q.id}>
                  <CardContent className="flex items-center justify-between gap-3 py-3">
                    <div className="min-w-0">
                      <p className="truncate font-medium">
                        {q.title}{' '}
                        <span className={`ml-1 rounded px-1.5 py-0.5 text-xs ${q.status === 'PUBLISHED' ? 'bg-emerald-500/15 text-emerald-700 dark:text-emerald-300' : 'bg-amber-500/15 text-amber-700 dark:text-amber-300'}`}>
                          {q.status === 'PUBLISHED' ? 'Publicat' : 'Ciornă'}
                        </span>
                      </p>
                      {q.description && <p className="truncate text-sm text-muted-foreground">{q.description}</p>}
                    </div>
                    <div className="flex shrink-0 gap-2">
                      <Button size="xs" variant="outline" onClick={() => openBuilder(q.id)}>Deschide</Button>
                      <Button size="xs" variant="secondary" onClick={() => togglePublish(q)}>{q.status === 'PUBLISHED' ? 'Depublică' : 'Publică'}</Button>
                      <Button size="xs" variant="outline" onClick={() => setQuizDialog({ item: q })}>Editează</Button>
                      <Button size="xs" variant="destructive" onClick={() => removeQuiz(q)}>Șterge</Button>
                    </div>
                  </CardContent>
                </Card>
              ))}
            </div>
          </>
        )}
      </div>

      {quizDialog && (
        <QuizDialog
          open
          onClose={() => setQuizDialog(null)}
          initialTitle={quizDialog.item?.title ?? ''}
          initialDescription={quizDialog.item?.description ?? ''}
          onSubmit={async (title, desc) => {
            if (quizDialog.item) {
              await updateQuiz(quizDialog.item.id, title, desc)
            } else {
              await createQuiz(title, desc)
            }
            await reloadList()
          }}
        />
      )}

      {itemDialog && quiz && (
        <ItemDialog
          open
          onClose={() => setItemDialog(null)}
          initial={itemDialog.item}
          onSubmit={async (input) => {
            if (itemDialog.item) {
              await updateQuizItem(itemDialog.item.id, input)
            } else {
              // New items go to the end.
              await addQuizItem(quiz.id, { ...input, position: quiz.items.length })
            }
            await reloadQuiz(quiz.id)
          }}
        />
      )}
    </div>
  )
}
