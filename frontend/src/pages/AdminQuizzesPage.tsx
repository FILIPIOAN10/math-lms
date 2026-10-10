import { useEffect, useState, type FormEvent, type KeyboardEvent } from 'react'
import { Link } from 'react-router-dom'
import { Button, buttonVariants } from '@/components/ui/button'
import { Card, CardContent } from '@/components/ui/card'
import { Dialog } from '@/components/ui/dialog'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Textarea } from '@/components/ui/textarea'
import { ImportItemsDialog } from '@/components/ImportItemsDialog'
import { MathContent } from '@/components/MathContent'
import {
  addQuizItem,
  copyQuiz,
  createQuiz,
  deleteQuiz,
  deleteQuizItem,
  getQuiz,
  listClasses,
  listQuizzes,
  setQuizPublished,
  updateQuiz,
  updateQuizItem,
  type ItemInput,
  type QuizDetail,
  type QuizItemDto,
  type QuizItemType,
  type QuizSummary,
  type QuizInput,
  type SchoolClass,
} from '@/lib/api'
import { roCount } from '@/lib/format'

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
  initialClassId,
  initialTimeLimit,
  initialPracticeAllowed,
  classes,
  onSubmit,
}: {
  open: boolean
  onClose: () => void
  initialTitle: string
  initialDescription: string
  initialClassId: number | null
  initialTimeLimit: number | null
  initialPracticeAllowed: boolean
  classes: SchoolClass[]
  onSubmit: (input: QuizInput) => Promise<void>
}) {
  const [title, setTitle] = useState(initialTitle)
  const [description, setDescription] = useState(initialDescription)
  const [classId, setClassId] = useState(initialClassId === null ? '' : String(initialClassId))
  const [timeLimit, setTimeLimit] = useState(initialTimeLimit === null ? '' : String(initialTimeLimit))
  const [practiceAllowed, setPracticeAllowed] = useState(initialPracticeAllowed)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)

  async function submit(e: FormEvent) {
    e.preventDefault()
    setError(null)
    setBusy(true)
    try {
      await onSubmit({
        title: title.trim(),
        description: description.trim() || null,
        schoolClassId: classId === '' ? null : Number(classId),
        timeLimitMinutes: timeLimit.trim() === '' ? null : Number(timeLimit),
        practiceAllowed,
      })
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
        <div className="flex flex-col gap-2">
          <Label htmlFor="qclass">Pentru clasa</Label>
          <select id="qclass" className={selectClass} value={classId} onChange={(e) => setClassId(e.target.value)}>
            <option value="">Toți elevii</option>
            {classes.map((c) => (
              <option key={c.id} value={c.id}>{c.name}</option>
            ))}
          </select>
          <p className="text-xs text-muted-foreground">
            „Toți elevii” = orice elev activ îl vede. Altfel, doar elevii înscriși în clasa aleasă.
          </p>
        </div>
        <div className="flex flex-col gap-2">
          <Label htmlFor="qlimit">Limită de timp (minute, opțional)</Label>
          <Input
            id="qlimit"
            type="number"
            inputMode="numeric"
            min={1}
            max={600}
            step={1}
            placeholder="fără limită"
            value={timeLimit}
            onChange={(e) => setTimeLimit(e.target.value)}
          />
          <p className="text-xs text-muted-foreground">
            Cronometrul pornește când elevul începe testul, iar serverul respinge răspunsurile după expirare.
            Încercările deja începute își păstrează timpul inițial.
          </p>
        </div>
        <div className="flex flex-col gap-1">
          <label htmlFor="qpractice" className="flex items-center gap-2 text-sm font-medium">
            <input
              id="qpractice"
              type="checkbox"
              checked={practiceAllowed}
              onChange={(e) => setPracticeAllowed(e.target.checked)}
            />
            Permite practică
          </label>
          <p className="text-xs text-muted-foreground">
            Elevii pot exersa quiz-ul fără notă, cu răspunsul corect afișat imediat după fiecare subiect, fără cronometru
            și fără efect asupra progresului sau statisticilor. Atenție: practica dezvăluie răspunsurile — bifeaz-o
            doar pentru quiz-uri care pot servi ca material de exersare.
          </p>
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

const MAX_HINTS = 5 // matches QuizItem.MAX_HINTS on the server

interface OptionDraft {
  text: string
  correct: boolean
}

/** Four boxes, the first marked right: most multiple-choice items have four options. */
const emptyOptions = (): OptionDraft[] => [
  { text: '', correct: true },
  { text: '', correct: false },
  { text: '', correct: false },
  { text: '', correct: false },
]

export function ItemDialog({
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
  const [hints, setHints] = useState<string[]>(initial?.hints ?? [])
  const [options, setOptions] = useState<OptionDraft[]>(
    initial && initial.options.length > 0
      ? initial.options.map((o) => ({ text: o.text, correct: o.correct }))
      : emptyOptions(),
  )
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [saved, setSaved] = useState(0) // items added with "Salvează și adaugă încă unul" while the dialog stayed open

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
  function setHintText(index: number, text: string) {
    setHints((prev) => prev.map((h, i) => (i === index ? text : h)))
  }
  function removeHint(index: number) {
    setHints((prev) => prev.filter((_, i) => i !== index))
  }

  /** Enter in an option moves to the next one (adding it after the last) instead of saving a half-written item. */
  function onOptionEnter(e: KeyboardEvent<HTMLInputElement>, index: number) {
    if (e.key !== 'Enter') return
    e.preventDefault()
    if (index === options.length - 1) addOption()
    setTimeout(() => document.getElementById(`ioption-${index + 1}`)?.focus(), 0)
  }

  function removeOption(index: number) {
    setOptions((prev) => (prev.length <= 2 ? prev : prev.filter((_, i) => i !== index)))
  }

  async function save(addAnother: boolean) {
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
      solution: solution.trim() || null,
      options:
        type === 'SINGLE_CHOICE'
          ? options.map((o, i) => ({ position: i, text: o.text.trim(), correct: o.correct }))
          : null,
      hints: hints.map((h) => h.trim()).filter((h) => h !== ''), // an empty box is just dropped
    }
    setBusy(true)
    try {
      await onSubmit(input)
      if (addAnother) {
        // Same type and points as the item just saved: a test is usually a run of similar items.
        setStatement('')
        setSolution('')
        setHints([])
        setOptions(emptyOptions())
        setSaved((n) => n + 1)
        document.getElementById('istatement')?.focus()
      } else {
        onClose()
      }
    } catch (err) {
      setError(errorMessage(err))
    } finally {
      setBusy(false)
    }
  }

  function submit(e: FormEvent) {
    e.preventDefault()
    void save(false)
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
                <Input
                  id={`ioption-${i}`}
                  value={o.text}
                  onChange={(e) => setOptionText(i, e.target.value)}
                  onKeyDown={(e) => onOptionEnter(e, i)}
                  placeholder={`Varianta ${i + 1}`}
                />
                <Button type="button" variant="ghost" size="icon-sm" onClick={() => removeOption(i)} disabled={options.length <= 2} aria-label="Șterge varianta">
                  ×
                </Button>
              </div>
            ))}
            <Button type="button" variant="outline" size="sm" onClick={addOption} className="self-start">
              Adaugă variantă
            </Button>
          </div>
        ) : null}

        <div className="flex flex-col gap-2">
          <Label htmlFor="isolution">
            {type === 'OPEN' ? 'Barem / rezolvare (opțional)' : 'Rezolvare explicată (opțional)'}
          </Label>
          <Textarea id="isolution" value={solution} onChange={(e) => setSolution(e.target.value)} />
          <p className="text-xs text-muted-foreground">
            Elevul o vede după trimitere (în rezultat) și, în modul practică, imediat după ce răspunde.
          </p>
        </div>

        <div className="flex flex-col gap-2" data-testid="hints-editor">
          <Label>Indicii progresive (opțional, maxim {MAX_HINTS})</Label>
          {hints.map((h, i) => (
            <div key={i} className="flex items-start gap-2">
              <span className="mt-2 w-5 text-xs text-muted-foreground">{i + 1}.</span>
              <Textarea
                value={h}
                onChange={(e) => setHintText(i, e.target.value)}
                placeholder={`Indiciul ${i + 1} — de la vag la concret`}
                aria-label={`Indiciul ${i + 1}`}
              />
              <Button type="button" variant="ghost" size="icon-sm" onClick={() => removeHint(i)} aria-label={`Șterge indiciul ${i + 1}`}>
                ×
              </Button>
            </div>
          ))}
          <Button
            type="button"
            variant="outline"
            size="sm"
            className="self-start"
            disabled={hints.length >= MAX_HINTS}
            onClick={() => setHints((prev) => [...prev, ''])}
          >
            Adaugă indiciu
          </Button>
          <p className="text-xs text-muted-foreground">
            Apar doar în modul practică, unul câte unul, în ordinea de mai sus. Testul notat nu are indicii.
          </p>
        </div>

        {saved > 0 && (
          <p role="status" className="text-sm text-emerald-700 dark:text-emerald-300">
            ✓ {roCount(saved, 'subiect salvat', 'subiecte salvate')}. Continuă cu următorul sau închide.
          </p>
        )}
        {error && <p className="text-sm text-destructive">{error}</p>}
        <div className="flex flex-wrap justify-end gap-2">
          <Button type="button" variant="outline" onClick={onClose} disabled={busy}>{saved > 0 ? 'Închide' : 'Anulează'}</Button>
          {!isEdit && (
            <Button type="button" variant="secondary" disabled={busy || statement.trim() === ''} onClick={() => void save(true)}>
              Salvează și adaugă încă unul
            </Button>
          )}
          <Button type="submit" disabled={busy || statement.trim() === ''}>Salvează</Button>
        </div>
      </form>
    </Dialog>
  )
}

// ---------- Page ----------

export function AdminQuizzesPage() {
  const [quizzes, setQuizzes] = useState<QuizSummary[]>([])
  const [classes, setClasses] = useState<SchoolClass[]>([])
  const [quiz, setQuiz] = useState<QuizDetail | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [quizDialog, setQuizDialog] = useState<{ item: QuizSummary | null } | null>(null)
  const [itemDialog, setItemDialog] = useState<{ item: QuizItemDto | null } | null>(null)
  const [importing, setImporting] = useState(false)

  useEffect(() => {
    listQuizzes()
      .then(setQuizzes)
      .catch((e) => setError(errorMessage(e)))
      .finally(() => setLoading(false))
    listClasses()
      .then(setClasses)
      .catch((e) => setError(errorMessage(e)))
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

  /** Variant B of a test, or the editable version of one students already took (its own items are frozen). */
  async function duplicate(q: QuizSummary) {
    setError(null)
    try {
      const copy = await copyQuiz(q.id)
      await reloadList()
      await openBuilder(copy.id)
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
              <div className="flex items-center gap-2">
                <span className={`rounded-md px-2 py-0.5 text-xs font-medium ${quiz.status === 'PUBLISHED' ? 'bg-emerald-500/15 text-emerald-700 dark:text-emerald-300' : 'bg-amber-500/15 text-amber-700 dark:text-amber-300'}`}>
                  {quiz.status === 'PUBLISHED' ? 'Publicat' : 'Ciornă'}
                </span>
                <span className="text-sm text-muted-foreground" data-testid="quiz-total">
                  {roCount(quiz.items.length, 'subiect', 'subiecte')} · Total: {quiz.items.reduce((sum, item) => sum + item.points, 0)} p
                </span>
              </div>
              <div className="flex flex-wrap gap-2">
                <Button size="sm" variant="outline" onClick={() => togglePublish(quiz)}
                        disabled={quiz.status !== 'PUBLISHED' && quiz.items.length === 0}
                        title={quiz.items.length === 0 ? 'Adaugă cel puțin un subiect înainte să publici' : undefined}>
                  {quiz.status === 'PUBLISHED' ? 'Depublică' : 'Publică'}
                </Button>
                <Button size="sm" variant="outline" onClick={() => setImporting(true)}>Importă din text</Button>
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
                              <MathContent inline>{o.text}</MathContent>
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
                      <p className="truncate text-xs text-muted-foreground" data-testid="quiz-audience">
                        {q.schoolClassName ? `Clasa: ${q.schoolClassName}` : 'Toți elevii'}
                        {q.timeLimitMinutes !== null && ` · ⏱ ${q.timeLimitMinutes} min`}
                        {q.practiceAllowed && ' · practică permisă'}
                      </p>
                      {q.description && <p className="truncate text-sm text-muted-foreground">{q.description}</p>}
                    </div>
                    <div className="flex flex-wrap justify-end gap-2">
                      <Button size="xs" variant="outline" onClick={() => openBuilder(q.id)}>Deschide</Button>
                      <Link to={`/admin/quizzes/${q.id}/stats`} data-testid="quiz-stats"
                            className={buttonVariants({ size: 'xs', variant: 'outline' })}>Statistici</Link>
                      <Button size="xs" variant="secondary" onClick={() => togglePublish(q)}>{q.status === 'PUBLISHED' ? 'Depublică' : 'Publică'}</Button>
                      {q.status === 'PUBLISHED' && (
                        <Link to={`/admin/assignments?quizId=${q.id}`} className={buttonVariants({ size: 'xs', variant: 'outline' })}>
                          Dă ca temă
                        </Link>
                      )}
                      <Button size="xs" variant="outline" onClick={() => setQuizDialog({ item: q })}>Editează</Button>
                      <Button size="xs" variant="outline" onClick={() => duplicate(q)}>Copiază</Button>
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
          initialClassId={quizDialog.item?.schoolClassId ?? null}
          initialTimeLimit={quizDialog.item?.timeLimitMinutes ?? null}
          initialPracticeAllowed={quizDialog.item?.practiceAllowed ?? false}
          classes={classes}
          onSubmit={async (input) => {
            if (quizDialog.item) {
              await updateQuiz(quizDialog.item.id, input)
              await reloadList()
            } else {
              const created = await createQuiz(input)
              await reloadList()
              await openBuilder(created.id) // a new quiz has no items yet: go straight to adding them
            }
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

      {importing && quiz && (
        <ImportItemsDialog
          open
          onClose={() => setImporting(false)}
          onImport={async (items) => {
            let added = 0
            try {
              for (const item of items) {
                await addQuizItem(quiz.id, { ...item, position: quiz.items.length + added }) // after the existing items
                added++
              }
              return null
            } catch (e) {
              return `S-au adăugat ${added} din ${items.length}. ${errorMessage(e)}`
            } finally {
              await reloadQuiz(quiz.id)
            }
          }}
        />
      )}
    </div>
  )
}
