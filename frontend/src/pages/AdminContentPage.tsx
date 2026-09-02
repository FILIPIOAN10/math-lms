import { useEffect, useState, type FormEvent, type ReactNode } from 'react'
import { Link } from 'react-router-dom'
import { Button, buttonVariants } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Dialog } from '@/components/ui/dialog'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Textarea } from '@/components/ui/textarea'
import {
  createBook,
  createChapter,
  createClass,
  createExercise,
  deleteBook,
  deleteChapter,
  deleteClass,
  deleteExercise,
  enrollStudent,
  listActiveUsers,
  listBooks,
  listChapters,
  listClasses,
  listExercises,
  listRoster,
  unenroll,
  updateBook,
  updateChapter,
  updateClass,
  updateExercise,
  type AdminUserSummary,
  type Book,
  type Chapter,
  type Difficulty,
  type Enrollment,
  type Exercise,
  type SchoolClass,
} from '@/lib/api'

const DIFFICULTIES: Difficulty[] = ['EASY', 'MEDIUM', 'HARD']
const selectClass =
  'h-9 rounded-lg border border-border bg-background px-2 text-sm outline-none focus-visible:border-ring focus-visible:ring-3 focus-visible:ring-ring/50 disabled:opacity-50'

function errorMessage(e: unknown): string {
  const body = (e as { body?: string })?.body
  return body && body.length > 0 ? body : 'Operația a eșuat. Reîncearcă.'
}

/** Create/edit dialog for a class, book or chapter (name/title + optional description). */
function TitleDescriptionDialog({
  open,
  onClose,
  heading,
  nameLabel,
  initialName,
  initialDescription,
  onSubmit,
}: {
  open: boolean
  onClose: () => void
  heading: string
  nameLabel: string
  initialName: string
  initialDescription: string
  onSubmit: (name: string, description: string | null) => Promise<void>
}) {
  const [name, setName] = useState(initialName)
  const [description, setDescription] = useState(initialDescription)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)

  async function handleSubmit(e: FormEvent) {
    e.preventDefault()
    setError(null)
    setBusy(true)
    try {
      await onSubmit(name.trim(), description.trim() || null)
      onClose()
    } catch (err) {
      setError(errorMessage(err))
    } finally {
      setBusy(false)
    }
  }

  return (
    <Dialog open={open} onOpenChange={(o) => !o && onClose()} title={heading}>
      <form className="flex flex-col gap-4" onSubmit={handleSubmit}>
        <div className="flex flex-col gap-2">
          <Label htmlFor="name">{nameLabel}</Label>
          <Input id="name" value={name} onChange={(e) => setName(e.target.value)} required autoFocus />
        </div>
        <div className="flex flex-col gap-2">
          <Label htmlFor="description">Descriere (opțional)</Label>
          <Textarea id="description" value={description} onChange={(e) => setDescription(e.target.value)} />
        </div>
        {error && <p className="text-sm text-destructive">{error}</p>}
        <div className="flex justify-end gap-2">
          <Button type="button" variant="outline" onClick={onClose} disabled={busy}>
            Anulează
          </Button>
          <Button type="submit" disabled={busy || name.trim() === ''}>
            {busy ? 'Se salvează…' : 'Salvează'}
          </Button>
        </div>
      </form>
    </Dialog>
  )
}

/** Create/edit dialog for an exercise (statement/solution LaTeX + difficulty). */
function ExerciseDialog({
  open,
  onClose,
  initial,
  onSubmit,
}: {
  open: boolean
  onClose: () => void
  initial: Exercise | null
  onSubmit: (input: { statement: string; solution: string | null; difficulty: Difficulty | null }) => Promise<void>
}) {
  const [statement, setStatement] = useState(initial?.statement ?? '')
  const [solution, setSolution] = useState(initial?.solution ?? '')
  const [difficulty, setDifficulty] = useState<Difficulty | ''>(initial?.difficulty ?? '')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)

  async function handleSubmit(e: FormEvent) {
    e.preventDefault()
    setError(null)
    setBusy(true)
    try {
      await onSubmit({
        statement: statement.trim(),
        solution: solution.trim() || null,
        difficulty: difficulty === '' ? null : difficulty,
      })
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
      title={initial ? 'Editează exercițiul' : 'Adaugă exercițiu'}
      description="Enunțul și soluția pot conține LaTeX între $…$ sau $$…$$."
    >
      <form className="flex flex-col gap-4" onSubmit={handleSubmit}>
        <div className="flex flex-col gap-2">
          <Label htmlFor="statement">Enunț</Label>
          <Textarea id="statement" value={statement} onChange={(e) => setStatement(e.target.value)} required autoFocus />
        </div>
        <div className="flex flex-col gap-2">
          <Label htmlFor="solution">Soluție (opțional)</Label>
          <Textarea id="solution" value={solution} onChange={(e) => setSolution(e.target.value)} />
        </div>
        <div className="flex flex-col gap-2">
          <Label htmlFor="difficulty">Dificultate</Label>
          <select
            id="difficulty"
            className={selectClass}
            value={difficulty}
            onChange={(e) => setDifficulty(e.target.value as Difficulty | '')}
          >
            <option value="">— nespecificată —</option>
            {DIFFICULTIES.map((d) => (
              <option key={d} value={d}>
                {d}
              </option>
            ))}
          </select>
        </div>
        {error && <p className="text-sm text-destructive">{error}</p>}
        <div className="flex justify-end gap-2">
          <Button type="button" variant="outline" onClick={onClose} disabled={busy}>
            Anulează
          </Button>
          <Button type="submit" disabled={busy || statement.trim() === ''}>
            {busy ? 'Se salvează…' : 'Salvează'}
          </Button>
        </div>
      </form>
    </Dialog>
  )
}

/** Roster management for one class: list enrolled students, add, remove. */
function EnrollDialog({ open, onClose, schoolClass }: { open: boolean; onClose: () => void; schoolClass: SchoolClass }) {
  const [roster, setRoster] = useState<Enrollment[]>([])
  const [students, setStudents] = useState<AdminUserSummary[]>([])
  const [selected, setSelected] = useState<number | ''>('')
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  useEffect(() => {
    Promise.all([listRoster(schoolClass.id), listActiveUsers('STUDENT')])
      .then(([r, s]) => {
        setRoster(r)
        setStudents(s)
      })
      .catch((e) => setError(errorMessage(e)))
  }, [schoolClass.id])

  const enrolledIds = new Set(roster.map((r) => r.studentId))
  const candidates = students.filter((s) => !enrolledIds.has(s.id))

  async function add() {
    if (selected === '') return
    setError(null)
    setBusy(true)
    try {
      const created = await enrollStudent(schoolClass.id, selected)
      setRoster((prev) => [...prev, created])
      setSelected('')
    } catch (e) {
      setError(errorMessage(e))
    } finally {
      setBusy(false)
    }
  }

  async function remove(enrollmentId: number) {
    setError(null)
    try {
      await unenroll(enrollmentId)
      setRoster((prev) => prev.filter((r) => r.id !== enrollmentId))
    } catch (e) {
      setError(errorMessage(e))
    }
  }

  return (
    <Dialog open={open} onOpenChange={(o) => !o && onClose()} title={`Elevi — ${schoolClass.name}`}>
      <div className="space-y-4">
        <div className="flex gap-2">
          <select
            className={`${selectClass} flex-1`}
            value={selected}
            disabled={busy || candidates.length === 0}
            onChange={(e) => setSelected(e.target.value === '' ? '' : Number(e.target.value))}
          >
            <option value="">
              {candidates.length === 0 ? 'Niciun elev de adăugat' : 'Alege un elev…'}
            </option>
            {candidates.map((s) => (
              <option key={s.id} value={s.id}>
                {s.fullName} ({s.email})
              </option>
            ))}
          </select>
          <Button onClick={add} disabled={busy || selected === ''}>
            Adaugă
          </Button>
        </div>

        {error && <p className="text-sm text-destructive">{error}</p>}

        {roster.length === 0 ? (
          <p className="text-sm text-muted-foreground">Niciun elev înscris.</p>
        ) : (
          <ul className="divide-y divide-border rounded-lg border border-border">
            {roster.map((r) => (
              <li key={r.id} className="flex items-center justify-between gap-2 px-3 py-2 text-sm">
                <span>
                  {r.studentName} <span className="text-muted-foreground">({r.studentEmail})</span>
                </span>
                <Button variant="destructive" size="xs" onClick={() => remove(r.id)}>
                  Scoate
                </Button>
              </li>
            ))}
          </ul>
        )}

        <div className="flex justify-end">
          <Button variant="outline" onClick={onClose}>
            Închide
          </Button>
        </div>
      </div>
    </Dialog>
  )
}

type Dialogs =
  | { kind: 'class'; item: SchoolClass | null }
  | { kind: 'book'; item: Book | null }
  | { kind: 'chapter'; item: Chapter | null }
  | { kind: 'exercise'; item: Exercise | null }
  | { kind: 'enroll'; schoolClass: SchoolClass }
  | null

export function AdminContentPage() {
  const [classes, setClasses] = useState<SchoolClass[]>([])
  const [cls, setCls] = useState<SchoolClass | null>(null)
  const [books, setBooks] = useState<Book[]>([])
  const [book, setBook] = useState<Book | null>(null)
  const [chapters, setChapters] = useState<Chapter[]>([])
  const [chapter, setChapter] = useState<Chapter | null>(null)
  const [exercises, setExercises] = useState<Exercise[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [dialog, setDialog] = useState<Dialogs>(null)

  useEffect(() => {
    listClasses()
      .then(setClasses)
      .catch((e) => setError(errorMessage(e)))
      .finally(() => setLoading(false))
  }, [])

  const reloadClasses = () => listClasses().then(setClasses)
  const reloadBooks = () => (cls ? listBooks(cls.id).then(setBooks) : Promise.resolve())
  const reloadChapters = () => (book ? listChapters(book.id).then(setChapters) : Promise.resolve())
  const reloadExercises = () => (chapter ? listExercises(chapter.id).then(setExercises) : Promise.resolve())

  async function loadInto<T>(promise: Promise<T>, apply: (v: T) => void) {
    setError(null)
    setLoading(true)
    try {
      apply(await promise)
    } catch (e) {
      setError(errorMessage(e))
    } finally {
      setLoading(false)
    }
  }

  function openClass(c: SchoolClass) {
    setCls(c)
    setBook(null)
    setChapter(null)
    setBooks([])
    setChapters([])
    setExercises([])
    void loadInto(listBooks(c.id), setBooks)
  }
  function openBook(b: Book) {
    setBook(b)
    setChapter(null)
    setChapters([])
    setExercises([])
    void loadInto(listChapters(b.id), setChapters)
  }
  function openChapter(ch: Chapter) {
    setChapter(ch)
    setExercises([])
    void loadInto(listExercises(ch.id), setExercises)
  }

  async function removeWithConfirm(label: string, run: () => Promise<void>, reload: () => Promise<unknown>) {
    if (!window.confirm(`Sigur ștergi ${label}?`)) return
    setError(null)
    try {
      await run()
      await reload()
    } catch (e) {
      setError(errorMessage(e))
    }
  }

  const closeDialog = () => setDialog(null)

  return (
    <div className="min-h-screen bg-muted p-4">
      <div className="mx-auto max-w-3xl space-y-4">
        {/* breadcrumb + home */}
        <div className="flex items-center justify-between">
          <nav className="flex flex-wrap items-center gap-1 text-sm text-muted-foreground">
            <button className="hover:text-foreground hover:underline" onClick={() => { setCls(null); setBook(null); setChapter(null) }}>
              Conținut
            </button>
            {cls && (<><span>/</span><button className="hover:text-foreground hover:underline" onClick={() => { setBook(null); setChapter(null) }}>{cls.name}</button></>)}
            {book && (<><span>/</span><button className="hover:text-foreground hover:underline" onClick={() => setChapter(null)}>{book.title}</button></>)}
            {chapter && (<><span>/</span><span className="text-foreground">{chapter.title}</span></>)}
          </nav>
          <Link to="/" className={buttonVariants({ variant: 'outline', size: 'sm' })}>Acasă</Link>
        </div>

        {error && <p className="text-sm text-destructive">{error}</p>}

        {/* Toolbar: add button for the current level */}
        <div className="flex justify-end">
          {!cls && <Button size="sm" onClick={() => setDialog({ kind: 'class', item: null })}>Adaugă clasă</Button>}
          {cls && !book && <Button size="sm" onClick={() => setDialog({ kind: 'book', item: null })}>Adaugă carte</Button>}
          {book && !chapter && <Button size="sm" onClick={() => setDialog({ kind: 'chapter', item: null })}>Adaugă capitol</Button>}
          {chapter && <Button size="sm" onClick={() => setDialog({ kind: 'exercise', item: null })}>Adaugă exercițiu</Button>}
        </div>

        {loading && <p className="text-muted-foreground">Se încarcă...</p>}

        {/* Exercises level */}
        {!loading && chapter && (
          exercises.length === 0 ? <p className="text-muted-foreground">Niciun exercițiu.</p> : (
            <div className="space-y-2">
              {exercises.map((ex, i) => (
                <Card key={ex.id}>
                  <CardHeader>
                    <div className="flex items-center justify-between gap-2">
                      <CardTitle className="text-base">Exercițiul {i + 1}{ex.difficulty ? ` · ${ex.difficulty}` : ''}</CardTitle>
                      <div className="flex gap-2">
                        <Button size="xs" variant="outline" onClick={() => setDialog({ kind: 'exercise', item: ex })}>Editează</Button>
                        <Button size="xs" variant="destructive" onClick={() => removeWithConfirm('acest exercițiu', () => deleteExercise(ex.id), reloadExercises)}>Șterge</Button>
                      </div>
                    </div>
                  </CardHeader>
                  <CardContent>
                    <p className="line-clamp-2 text-sm text-muted-foreground">{ex.statement}</p>
                  </CardContent>
                </Card>
              ))}
            </div>
          )
        )}

        {/* Chapters level */}
        {!loading && book && !chapter && (
          chapters.length === 0 ? <p className="text-muted-foreground">Niciun capitol.</p> : (
            <RowList
              rows={chapters.map((c) => ({
                id: c.id, title: c.title, description: c.description,
                onOpen: () => openChapter(c),
                onEdit: () => setDialog({ kind: 'chapter', item: c }),
                onDelete: () => removeWithConfirm(`capitolul „${c.title}"`, () => deleteChapter(c.id), reloadChapters),
              }))}
            />
          )
        )}

        {/* Books level */}
        {!loading && cls && !book && (
          books.length === 0 ? <p className="text-muted-foreground">Nicio carte.</p> : (
            <RowList
              rows={books.map((b) => ({
                id: b.id, title: b.title, description: b.description,
                onOpen: () => openBook(b),
                onEdit: () => setDialog({ kind: 'book', item: b }),
                onDelete: () => removeWithConfirm(`cartea „${b.title}"`, () => deleteBook(b.id), reloadBooks),
              }))}
            />
          )
        )}

        {/* Classes level */}
        {!loading && !cls && (
          classes.length === 0 ? <p className="text-muted-foreground">Nicio clasă. Adaugă prima.</p> : (
            <RowList
              rows={classes.map((c) => ({
                id: c.id, title: c.name, description: c.description,
                onOpen: () => openClass(c),
                onEdit: () => setDialog({ kind: 'class', item: c }),
                onDelete: () => removeWithConfirm(`clasa „${c.name}"`, () => deleteClass(c.id), reloadClasses),
                extra: <Button size="xs" variant="secondary" onClick={() => setDialog({ kind: 'enroll', schoolClass: c })}>Elevi</Button>,
              }))}
            />
          )
        )}
      </div>

      {/* Dialogs */}
      {dialog?.kind === 'class' && (
        <TitleDescriptionDialog
          open onClose={closeDialog}
          heading={dialog.item ? 'Editează clasa' : 'Adaugă clasă'} nameLabel="Nume"
          initialName={dialog.item?.name ?? ''} initialDescription={dialog.item?.description ?? ''}
          onSubmit={async (name, desc) => { dialog.item ? await updateClass(dialog.item.id, name, desc) : await createClass(name, desc); await reloadClasses() }}
        />
      )}
      {dialog?.kind === 'book' && cls && (
        <TitleDescriptionDialog
          open onClose={closeDialog}
          heading={dialog.item ? 'Editează cartea' : 'Adaugă carte'} nameLabel="Titlu"
          initialName={dialog.item?.title ?? ''} initialDescription={dialog.item?.description ?? ''}
          onSubmit={async (title, desc) => { dialog.item ? await updateBook(dialog.item.id, title, desc) : await createBook(cls.id, title, desc); await reloadBooks() }}
        />
      )}
      {dialog?.kind === 'chapter' && book && (
        <TitleDescriptionDialog
          open onClose={closeDialog}
          heading={dialog.item ? 'Editează capitolul' : 'Adaugă capitol'} nameLabel="Titlu"
          initialName={dialog.item?.title ?? ''} initialDescription={dialog.item?.description ?? ''}
          onSubmit={async (title, desc) => { dialog.item ? await updateChapter(dialog.item.id, title, desc) : await createChapter(book.id, title, desc); await reloadChapters() }}
        />
      )}
      {dialog?.kind === 'exercise' && chapter && (
        <ExerciseDialog
          open onClose={closeDialog} initial={dialog.item}
          onSubmit={async (input) => {
            if (dialog.item) {
              await updateExercise(dialog.item.id, { ...input, version: dialog.item.version })
            } else {
              await createExercise(chapter.id, input)
            }
            await reloadExercises()
          }}
        />
      )}
      {dialog?.kind === 'enroll' && (
        <EnrollDialog open onClose={closeDialog} schoolClass={dialog.schoolClass} />
      )}
    </div>
  )
}

interface AdminRow {
  id: number
  title: string
  description: string | null
  onOpen: () => void
  onEdit: () => void
  onDelete: () => void
  extra?: ReactNode
}

function RowList({ rows }: { rows: AdminRow[] }) {
  return (
    <div className="space-y-2">
      {rows.map((row) => (
        <Card key={row.id}>
          <CardContent className="flex items-center justify-between gap-3 py-3">
            <div className="min-w-0">
              <p className="truncate font-medium">{row.title}</p>
              {row.description && <p className="truncate text-sm text-muted-foreground">{row.description}</p>}
            </div>
            <div className="flex shrink-0 gap-2">
              {row.extra}
              <Button size="xs" variant="outline" onClick={row.onOpen}>Deschide</Button>
              <Button size="xs" variant="outline" onClick={row.onEdit}>Editează</Button>
              <Button size="xs" variant="destructive" onClick={row.onDelete}>Șterge</Button>
            </div>
          </CardContent>
        </Card>
      ))}
    </div>
  )
}
