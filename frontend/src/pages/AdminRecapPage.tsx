import { useEffect, useMemo, useRef, useState, type FormEvent } from 'react'
import { useNavigate } from 'react-router-dom'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, CardContent } from '@/components/ui/card'
import { Dialog } from '@/components/ui/dialog'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Textarea } from '@/components/ui/textarea'
import { ErrorState } from '@/components/ErrorState'
import { FilterBar, FilterSelect, NoMatches } from '@/components/FilterBar'
import { MathContent } from '@/components/MathContent'
import { PageHeader } from '@/components/PageHeader'
import { ZoomableImage } from '@/components/QuestionImage'
import { TestsTabs } from '@/components/TestsTabs'
import { useAuth } from '@/context/AuthContext'
import {
  attachRecapImage, createRecapQuiz, importRecap, listClasses, listEnChapters, listRecapExercises, recapImageUrl,
  reviewRecapExercise, updateRecapExercise, waiveRecapFigure,
  type EnChapterSummary, type RecapEdit, type RecapExercise, type RecapReview, type SchoolClass,
} from '@/lib/api'
import { errorMessage } from '@/lib/errors'
import { roCount } from '@/lib/format'
import { selectClass } from '@/lib/formClasses'
import { recapMatches, recapStatusLabel, REVIEW_LABELS, type RecapListFilter } from '@/lib/recap'

const PAGE = 30

/**
 * „Culegere”: the „Teme recapitulative” exercises solved twice by AI. The teacher reads each one (statement, the book's
 * drawing, the barem and answers), corrects it, approves it, and makes a test of the approved ones - published and given
 * as homework the usual way. Nothing unapproved reaches a student. An admin loads the book's zip here.
 */
export function AdminRecapPage() {
  const { user } = useAuth()
  const navigate = useNavigate()
  const isAdmin = user?.role === 'ADMIN'
  const [all, setAll] = useState<RecapExercise[] | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [chapters, setChapters] = useState<EnChapterSummary[]>([])
  const [classes, setClasses] = useState<SchoolClass[]>([])
  const [filter, setFilter] = useState<RecapListFilter>({ review: 'DRAFT', chapter: '', ai: '', search: '' })
  const [shown, setShown] = useState(PAGE)
  const [selected, setSelected] = useState<number[]>([])
  const [editing, setEditing] = useState<RecapExercise | null>(null)
  const [creating, setCreating] = useState(false)
  const [notice, setNotice] = useState<string | null>(null)
  const [imageVersion, setImageVersion] = useState<Record<number, number>>({})

  async function load() {
    setError(null)
    try {
      setAll(await listRecapExercises())
    } catch (e) {
      setError(errorMessage(e))
    }
  }

  useEffect(() => {
    listRecapExercises().then(setAll).catch((e) => setError(errorMessage(e)))
    listEnChapters().then(setChapters).catch(() => setChapters([]))
    listClasses().then(setClasses).catch(() => setClasses([]))
  }, [])

  const matching = useMemo(() => (all ?? []).filter((e) => recapMatches(e, filter)), [all, filter])
  const counts = useMemo(() => ({
    draft: (all ?? []).filter((e) => e.review === 'DRAFT').length,
    approved: (all ?? []).filter((e) => e.review === 'APPROVED').length,
  }), [all])

  function replace(updated: RecapExercise) {
    setAll((prev) => (prev ?? []).map((e) => (e.id === updated.id ? updated : e)))
    if (updated.review !== 'APPROVED') setSelected((prev) => prev.filter((id) => id !== updated.id))
  }

  function changeFilter(next: Partial<RecapListFilter>) {
    setFilter((prev) => ({ ...prev, ...next }))
    setShown(PAGE)
  }

  const header = (
    <PageHeader
      title="Culegere"
      description="Teme recapitulative: exerciții rezolvate de două ori de AI. Citește, corectează și aprobă; doar cele aprobate intră în teste."
      actions={isAdmin ? <ImportButton onDone={(text) => { setNotice(text); void load() }} /> : undefined}
    />
  )

  if (error) {
    return (
      <div className="space-y-4">
        {header}
        <ErrorState message={error} />
        <Button variant="outline" onClick={() => void load()}>Reîncearcă</Button>
      </div>
    )
  }

  return (
    <div className="space-y-4">
      {header}
      <TestsTabs current="culegere" />
      {notice && <p role="status" className="text-sm">{notice}</p>}
      {all !== null && all.length === 0 ? (
        <Card>
          <CardContent className="py-8 text-center text-sm text-muted-foreground">
            Culegerea e goală. {isAdmin ? 'Încarcă arhiva teme-culegere.zip cu butonul de sus.' : 'Un administrator trebuie să încarce culegerea.'}
          </CardContent>
        </Card>
      ) : (
        <>
          <p className="text-sm text-muted-foreground" data-testid="recap-counts">
            {roCount(counts.approved, 'exercițiu aprobat', 'exerciții aprobate')}, {counts.draft} de verificat.
          </p>
          <FilterBar label="Caută în enunț sau după cod (ex. T8-19)" search={filter.search}
                     onSearch={(search) => changeFilter({ search })}
                     active={filter.chapter !== '' || filter.ai !== '' || filter.search !== '' || filter.review !== 'DRAFT'}
                     onClear={() => changeFilter({ review: 'DRAFT', chapter: '', ai: '', search: '' })}
                     shown={matching.length} total={all?.length ?? 0}>
            <FilterSelect label="Stare" value={filter.review} onChange={(review) => changeFilter({ review: review as RecapListFilter['review'] })}
                          options={[{ value: '', label: 'Toate' }, { value: 'DRAFT', label: 'De verificat' },
                            { value: 'APPROVED', label: 'Aprobate' }, { value: 'REJECTED', label: 'Respinse' }]} />
            <FilterSelect label="Capitol EN" value={filter.chapter} onChange={(chapter) => changeFilter({ chapter })}
                          options={[{ value: '', label: 'Toate capitolele' },
                            ...chapters.map((c) => ({ value: String(c.id), label: `${c.code} ${c.title}` }))]} />
            <FilterSelect label="Verificare AI" value={filter.ai} onChange={(ai) => changeFilter({ ai: ai as RecapListFilter['ai'] })}
                          options={[{ value: '', label: 'Orice verificare' }, { value: 'agree', label: 'Verificate de AI' },
                            { value: 'check', label: 'De verificat atent' }]} />
          </FilterBar>
          {all === null ? <p className="text-sm text-muted-foreground">Se încarcă…</p> : matching.length === 0 ? <NoMatches onClear={() => changeFilter({ review: '', chapter: '', ai: '', search: '' })} /> : (
            <ul className="space-y-3">
              {matching.slice(0, shown).map((exercise) => (
                <li key={exercise.id}>
                  <ExerciseCard
                    exercise={exercise}
                    imageVersion={imageVersion[exercise.id] ?? 0}
                    selected={selected.includes(exercise.id)}
                    onSelect={(on) => setSelected((prev) => (on ? [...prev, exercise.id] : prev.filter((id) => id !== exercise.id)))}
                    onChanged={(updated, newImage) => {
                      replace(updated)
                      if (newImage) setImageVersion((prev) => ({ ...prev, [updated.id]: Date.now() }))
                    }}
                    onEdit={() => setEditing(exercise)}
                  />
                </li>
              ))}
            </ul>
          )}
          {matching.length > shown && (
            <Button variant="outline" onClick={() => setShown((n) => n + PAGE)}>
              Arată încă {Math.min(PAGE, matching.length - shown)}
            </Button>
          )}
        </>
      )}
      {selected.length > 0 && (
        <div className="sticky bottom-20 z-20 flex flex-wrap items-center justify-between gap-2 rounded-lg border bg-card p-3 shadow-lg lg:bottom-4">
          <span className="text-sm">{roCount(selected.length, 'exercițiu ales', 'exerciții alese')}</span>
          <div className="flex gap-2">
            <Button variant="outline" onClick={() => setSelected([])}>Renunță</Button>
            <Button onClick={() => setCreating(true)}>Creează test</Button>
          </div>
        </div>
      )}
      {editing && (
        <EditDialog exercise={editing} chapters={chapters} onClose={() => setEditing(null)}
                    onSaved={(updated) => { replace(updated); setEditing(null) }} />
      )}
      {creating && (
        <CreateQuizDialog count={selected.length} classes={classes} onClose={() => setCreating(false)}
                          onCreate={async (title, classId) => {
                            const quizId = await createRecapQuiz({ title, exerciseIds: selected, schoolClassId: classId })
                            navigate(`/admin/quizzes/${quizId}`)
                          }} />
      )}
    </div>
  )
}

function ImportButton({ onDone }: { onDone: (text: string) => void }) {
  const input = useRef<HTMLInputElement>(null)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)

  async function upload(file: File | undefined) {
    if (!file) return
    setBusy(true)
    setError(null)
    try {
      const result = await importRecap(file)
      onDone(`Am adăugat ${roCount(result.added, 'exercițiu', 'exerciții')}; ${result.skipped} existau deja și au rămas neatinse.`)
    } catch (e) {
      setError(errorMessage(e))
    } finally {
      setBusy(false)
      if (input.current) input.current.value = ''
    }
  }

  return (
    <div className="flex flex-col items-end gap-1">
      <input ref={input} type="file" accept=".zip,application/zip" className="hidden" aria-label="Arhiva culegerii"
             onChange={(e) => void upload(e.target.files?.[0])} />
      <Button variant="outline" disabled={busy} onClick={() => input.current?.click()}>
        {busy ? 'Se încarcă…' : 'Încarcă culegerea (.zip)'}
      </Button>
      {error && <p className="text-sm text-destructive">{error}</p>}
    </div>
  )
}

function ExerciseCard({ exercise, imageVersion, selected, onSelect, onChanged, onEdit }: {
  exercise: RecapExercise
  imageVersion: number
  selected: boolean
  onSelect: (on: boolean) => void
  onChanged: (updated: RecapExercise, newImage?: boolean) => void
  onEdit: () => void
}) {
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const file = useRef<HTMLInputElement>(null)
  const status = recapStatusLabel(exercise)

  async function run(action: () => Promise<RecapExercise>, newImage = false) {
    setBusy(true)
    setError(null)
    try {
      onChanged(await action(), newImage)
    } catch (e) {
      setError(errorMessage(e))
    } finally {
      setBusy(false)
    }
  }

  const review = (r: RecapReview) => run(() => reviewRecapExercise(exercise.id, r))

  return (
    <Card data-testid={`recap-${exercise.key}`}>
      <CardContent className="space-y-3 py-4">
        <div className="flex flex-wrap items-center gap-2">
          {exercise.review === 'APPROVED' && (
            <input type="checkbox" className="size-4" checked={selected} onChange={(e) => onSelect(e.target.checked)}
                   aria-label={`Alege ${exercise.key} pentru test`} />
          )}
          <span className="font-mono text-sm font-semibold">{exercise.key}</span>
          <span className="text-xs text-muted-foreground">Tema {exercise.tema}, nr. {exercise.number}</span>
          {exercise.enChapterCode && (
            <Badge variant="outline" title={exercise.enChapterTitle ?? undefined}>{exercise.enChapterCode}</Badge>
          )}
          <Badge variant={status.tone}>{status.label}</Badge>
          <Badge variant={exercise.review === 'APPROVED' ? 'success' : exercise.review === 'REJECTED' ? 'destructive' : 'secondary'}>
            {REVIEW_LABELS[exercise.review]}
          </Badge>
          {exercise.usedIn > 0 && <span className="text-xs text-muted-foreground">în {roCount(exercise.usedIn, 'test', 'teste')}</span>}
        </div>
        <MathContent className="whitespace-pre-line">{exercise.statement}</MathContent>
        {exercise.hasImage && (
          <ZoomableImage src={recapImageUrl(exercise.id, imageVersion)} alt={`Figura de la ${exercise.key}`} imgClassName="max-h-64" />
        )}
        {exercise.figureMissing && (
          <p className="rounded-md bg-warning/15 p-2 text-sm text-warning-foreground">
            În carte exercițiul are un desen care nu a fost găsit. Adaugă imaginea sau confirmă că merge fără ea.
          </p>
        )}
        {exercise.parts.length > 0 ? (
          <ol className="space-y-2">
            {exercise.parts.map((p) => (
              <li key={p.label} className="space-y-1">
                <div className="flex gap-2">
                  <span className="font-medium">{p.label})</span>
                  <MathContent inline>{p.statement}</MathContent>
                </div>
                <AnswerLine answer={p.answer} result={p.result} verified={p.verified} />
                {p.solution && <Solution text={p.solution} />}
              </li>
            ))}
          </ol>
        ) : (
          <AnswerLine answer={exercise.answer} result={exercise.result} verified={exercise.verified} />
        )}
        {exercise.solution && <Solution text={exercise.solution} />}
        {error && <p className="text-sm text-destructive">{error}</p>}
        <div className="flex flex-wrap gap-2">
          {exercise.review !== 'APPROVED' && <Button size="sm" disabled={busy} onClick={() => review('APPROVED')}>Aprobă</Button>}
          {exercise.review !== 'REJECTED' && <Button size="sm" variant="outline" disabled={busy} onClick={() => review('REJECTED')}>Respinge</Button>}
          {exercise.review !== 'DRAFT' && <Button size="sm" variant="ghost" disabled={busy} onClick={() => review('DRAFT')}>Înapoi la verificat</Button>}
          <Button size="sm" variant="outline" disabled={busy} onClick={onEdit}>Corectează</Button>
          <input ref={file} type="file" accept="image/png,image/jpeg,image/webp" className="hidden"
                 aria-label={`Imagine pentru ${exercise.key}`}
                 onChange={(e) => {
                   const chosen = e.target.files?.[0]
                   if (chosen) void run(() => attachRecapImage(exercise.id, chosen), true)
                   e.target.value = ''
                 }} />
          <Button size="sm" variant="outline" disabled={busy} onClick={() => file.current?.click()}>
            {exercise.hasImage ? 'Schimbă figura' : 'Adaugă figura'}
          </Button>
          {exercise.figureMissing && (
            <Button size="sm" variant="ghost" disabled={busy} onClick={() => run(() => waiveRecapFigure(exercise.id))}>
              Merge fără figură
            </Button>
          )}
        </div>
      </CardContent>
    </Card>
  )
}

function AnswerLine({ answer, result, verified }: { answer: string | null; result: string | null; verified: boolean }) {
  if (!answer && !result) {
    return <p className="text-sm text-muted-foreground">Fără răspuns propus: verifică rezolvarea.</p>
  }
  return (
    <p className="text-sm">
      <span className="text-muted-foreground">Răspuns: </span>
      {answer ? <MathContent inline>{answer}</MathContent> : result}
      {result && <span className="ml-2 text-xs text-muted-foreground">(elevul tastează {result})</span>}
      {!verified && <span className="ml-2 text-xs text-warning-foreground">— neconfirmat de a doua rezolvare</span>}
    </p>
  )
}

function Solution({ text }: { text: string }) {
  return (
    <details className="text-sm">
      <summary className="cursor-pointer text-muted-foreground">Rezolvarea (barem)</summary>
      <MathContent className="mt-1 whitespace-pre-line">{text}</MathContent>
    </details>
  )
}

function EditDialog({ exercise, chapters, onClose, onSaved }: {
  exercise: RecapExercise
  chapters: EnChapterSummary[]
  onClose: () => void
  onSaved: (updated: RecapExercise) => void
}) {
  const [edit, setEdit] = useState<RecapEdit>(() => ({
    statement: exercise.statement,
    solution: exercise.solution,
    answer: exercise.answer,
    result: exercise.result,
    parts: exercise.parts.map((p) => ({ statement: p.statement, solution: p.solution, answer: p.answer, result: p.result })),
    enChapterId: exercise.enChapterId,
  }))
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)

  function setPart(i: number, field: keyof RecapEdit['parts'][number], value: string) {
    setEdit((prev) => ({ ...prev, parts: prev.parts.map((p, j) => (j === i ? { ...p, [field]: value } : p)) }))
  }

  async function submit(e: FormEvent) {
    e.preventDefault()
    setBusy(true)
    setError(null)
    try {
      onSaved(await updateRecapExercise(exercise.id, edit))
    } catch (err) {
      setError(errorMessage(err))
    } finally {
      setBusy(false)
    }
  }

  return (
    <Dialog open onOpenChange={(o) => !o && onClose()} title={`Corectează ${exercise.key}`}>
      <form className="flex max-h-[75vh] flex-col gap-3 overflow-y-auto" onSubmit={submit}>
        <Label htmlFor="rstatement">Enunț</Label>
        <Textarea id="rstatement" rows={4} value={edit.statement} onChange={(e) => setEdit({ ...edit, statement: e.target.value })} />
        <Label htmlFor="rchapter">Capitol EN</Label>
        <select id="rchapter" className={selectClass} value={edit.enChapterId ?? ''}
                onChange={(e) => setEdit({ ...edit, enChapterId: e.target.value === '' ? null : Number(e.target.value) })}>
          <option value="">Recunoscut din text</option>
          {chapters.map((c) => <option key={c.id} value={c.id}>{c.code} {c.title}</option>)}
        </select>
        {edit.parts.map((p, i) => (
          <fieldset key={i} className="space-y-2 rounded-md border p-2">
            <legend className="px-1 text-sm font-medium">Cerința {String.fromCharCode(97 + i)})</legend>
            <Textarea aria-label={`Cerința ${String.fromCharCode(97 + i)}`} rows={2} value={p.statement}
                      onChange={(e) => setPart(i, 'statement', e.target.value)} />
            <Input aria-label={`Răspunsul cerinței ${String.fromCharCode(97 + i)}`} placeholder="Răspuns" value={p.answer ?? ''}
                   onChange={(e) => setPart(i, 'answer', e.target.value)} />
            <Input aria-label={`Rezultatul cerinței ${String.fromCharCode(97 + i)}`} placeholder="Număr tastat de elev (opțional)"
                   value={p.result ?? ''} onChange={(e) => setPart(i, 'result', e.target.value)} />
            <Textarea aria-label={`Baremul cerinței ${String.fromCharCode(97 + i)}`} rows={3} placeholder="Rezolvare" value={p.solution ?? ''}
                      onChange={(e) => setPart(i, 'solution', e.target.value)} />
          </fieldset>
        ))}
        {edit.parts.length === 0 && (
          <>
            <Label htmlFor="ranswer">Răspuns</Label>
            <Input id="ranswer" value={edit.answer ?? ''} onChange={(e) => setEdit({ ...edit, answer: e.target.value })} />
            <Label htmlFor="rresult">Număr tastat de elev (opțional)</Label>
            <Input id="rresult" value={edit.result ?? ''} onChange={(e) => setEdit({ ...edit, result: e.target.value })} />
          </>
        )}
        <Label htmlFor="rsolution">{edit.parts.length > 0 ? 'Pași comuni (opțional)' : 'Rezolvare (barem)'}</Label>
        <Textarea id="rsolution" rows={4} value={edit.solution ?? ''} onChange={(e) => setEdit({ ...edit, solution: e.target.value })} />
        {error && <p className="text-sm text-destructive">{error}</p>}
        <div className="flex justify-end gap-2">
          <Button type="button" variant="outline" onClick={onClose} disabled={busy}>Anulează</Button>
          <Button type="submit" disabled={busy || edit.statement.trim() === ''}>Salvează</Button>
        </div>
      </form>
    </Dialog>
  )
}

function CreateQuizDialog({ count, classes, onClose, onCreate }: {
  count: number
  classes: SchoolClass[]
  onClose: () => void
  onCreate: (title: string, classId: number | null) => Promise<void>
}) {
  const [title, setTitle] = useState('Recapitulare')
  const [classId, setClassId] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)

  async function submit(e: FormEvent) {
    e.preventDefault()
    setBusy(true)
    setError(null)
    try {
      await onCreate(title.trim(), classId === '' ? null : Number(classId))
    } catch (err) {
      setError(errorMessage(err))
      setBusy(false)
    }
  }

  return (
    <Dialog open onOpenChange={(o) => !o && onClose()} title="Test din culegere">
      <form className="flex flex-col gap-3" onSubmit={submit}>
        <p className="text-sm text-muted-foreground">
          Fac o ciornă cu {roCount(count, 'problemă', 'probleme')} (cu baremul și figurile lor). O poți modifica, apoi
          „Publică și dă ca temă”.
        </p>
        <Label htmlFor="rtitle">Titlu</Label>
        <Input id="rtitle" value={title} onChange={(e) => setTitle(e.target.value)} maxLength={200} />
        <Label htmlFor="rclass">Clasa</Label>
        <select id="rclass" className={selectClass} value={classId} onChange={(e) => setClassId(e.target.value)}>
          <option value="">Toate clasele mele</option>
          {classes.map((c) => <option key={c.id} value={c.id}>{c.name}</option>)}
        </select>
        {error && <p className="text-sm text-destructive">{error}</p>}
        <div className="flex justify-end gap-2">
          <Button type="button" variant="outline" onClick={onClose} disabled={busy}>Anulează</Button>
          <Button type="submit" disabled={busy || title.trim() === ''}>Creează ciorna</Button>
        </div>
      </form>
    </Dialog>
  )
}
