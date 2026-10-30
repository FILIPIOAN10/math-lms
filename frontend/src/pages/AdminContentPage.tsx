import { useEffect, useState, type FormEvent } from 'react'
import { MathContent } from '@/components/MathContent'
import { Link, useNavigate } from 'react-router-dom'
import { ChapterQuizDialog } from '@/components/ChapterQuizDialog'
import { ClassInviteDialog } from '@/components/ClassInviteDialog'
import { RowMenu, type RowMenuItem } from '@/components/RowMenu'
import { Button, buttonVariants } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Dialog } from '@/components/ui/dialog'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Textarea } from '@/components/ui/textarea'
import { useAuth } from '@/context/AuthContext'
import {
  assignClassTeacher,
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
  listClassSummaries,
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
  type ClassSummary,
  type Difficulty,
  type Enrollment,
  type Exercise,
  type SchoolClass,
} from '@/lib/api'
import { PageHeader } from '@/components/PageHeader'
import { useConfirm } from '@/components/useConfirm'
import { errorMessage } from '@/lib/errors'
import { roCount } from '@/lib/format'
import { formatGrade, GRADE_TEXT, gradeBand } from '@/lib/grades'
import { cn } from '@/lib/utils'
import { LoadingCards } from '@/components/LoadingCards'

const DIFFICULTIES: Difficulty[] = ['EASY', 'MEDIUM', 'HARD']
const selectClass =
  'h-9 rounded-lg border border-border bg-background px-2 text-sm outline-none focus-visible:border-ring focus-visible:ring-3 focus-visible:ring-ring/50 disabled:opacity-50'


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
  const [picked, setPicked] = useState<Set<number>>(new Set())
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

  function toggle(id: number) {
    setPicked((prev) => {
      const next = new Set(prev)
      if (next.has(id)) {
        next.delete(id)
      } else {
        next.add(id)
      }
      return next
    })
  }

  /** One request per student, in order; one that fails does not stop the others, and is reported by name. */
  async function addPicked() {
    setError(null)
    setBusy(true)
    const failures: string[] = []
    for (const id of picked) {
      try {
        const created = await enrollStudent(schoolClass.id, id)
        setRoster((prev) => [...prev, created])
      } catch (e) {
        failures.push(`${students.find((s) => s.id === id)?.fullName ?? id}: ${errorMessage(e)}`)
      }
    }
    setPicked(new Set())
    setBusy(false)
    if (failures.length > 0) setError(failures.join(' · '))
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
        {candidates.length === 0 ? (
          <p className="text-sm text-muted-foreground">Toți elevii activi sunt deja în această clasă.</p>
        ) : (
          <div className="space-y-2">
            <div className="flex items-center justify-between text-sm">
              <span className="font-medium">Adaugă elevi</span>
              <button
                type="button"
                className="text-muted-foreground hover:text-foreground hover:underline"
                onClick={() => setPicked(picked.size === candidates.length ? new Set() : new Set(candidates.map((s) => s.id)))}
              >
                {picked.size === candidates.length ? 'Deselectează tot' : 'Selectează tot'}
              </button>
            </div>
            <ul className="max-h-48 divide-y divide-border overflow-y-auto rounded-lg border border-border">
              {candidates.map((s) => (
                <li key={s.id}>
                  <label className="flex cursor-pointer items-center gap-2 px-3 py-2 text-sm">
                    <input type="checkbox" checked={picked.has(s.id)} disabled={busy} onChange={() => toggle(s.id)} />
                    {s.fullName} <span className="text-muted-foreground">({s.email})</span>
                  </label>
                </li>
              ))}
            </ul>
            <Button onClick={addPicked} disabled={busy || picked.size === 0}>
              {busy ? 'Se adaugă…' : `Adaugă selectații (${picked.size})`}
            </Button>
          </div>
        )}

        {error && <p className="text-sm text-destructive">{error}</p>}

        {roster.length === 0 ? (
          <p className="text-sm text-muted-foreground">Niciun elev înscris.</p>
        ) : (
          <ul className="divide-y divide-border rounded-lg border border-border">
            {roster.map((r) => (
              <li key={r.id} className="flex items-center justify-between gap-2 px-3 py-2 text-sm">
                <span>
                  <Link to={`/admin/students/${r.studentId}`} className="hover:underline">{r.studentName}</Link>{' '}
                  <span className="text-muted-foreground">({r.studentEmail})</span>
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
  | { kind: 'invite'; schoolClass: SchoolClass }
  | { kind: 'teacher'; schoolClass: SchoolClass }
  | { kind: 'chapterQuiz' }
  | null

export function AdminContentPage() {
  const { user } = useAuth()
  const isAdmin = user?.role === 'ADMIN'
  // ADMIN only: who may own a class (teachers and admins), for the "Profesor" picker on each class row.
  const [teachers, setTeachers] = useState<AdminUserSummary[]>([])
  const navigate = useNavigate()
  const { confirm, dialog: confirmDialog } = useConfirm()
  const [classes, setClasses] = useState<SchoolClass[]>([])
  // The numbers on each class card; a card simply shows none if they could not be loaded.
  const [summaries, setSummaries] = useState<Record<number, ClassSummary>>({})
  const [cls, setCls] = useState<SchoolClass | null>(null)
  const [books, setBooks] = useState<Book[]>([])
  const [book, setBook] = useState<Book | null>(null)
  const [chapters, setChapters] = useState<Chapter[]>([])
  const [chapter, setChapter] = useState<Chapter | null>(null)
  const [exercises, setExercises] = useState<Exercise[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [dialog, setDialog] = useState<Dialogs>(null)

  const loadSummaries = () =>
    listClassSummaries()
      .then((list) => setSummaries(Object.fromEntries(list.map((row) => [row.classId, row]))))
      .catch(() => undefined)

  useEffect(() => {
    listClasses()
      .then(setClasses)
      .catch((e) => setError(errorMessage(e)))
      .finally(() => setLoading(false))
    void loadSummaries()
  }, [])

  useEffect(() => {
    if (!isAdmin) return
    Promise.all([listActiveUsers('TEACHER'), listActiveUsers('ADMIN')])
      .then(([t, a]) => setTeachers([...t, ...a]))
      .catch(() => undefined) // the picker is a help: without it the classes still work
  }, [isAdmin])

  const reloadClasses = () => Promise.all([listClasses().then(setClasses), loadSummaries()])

  async function changeTeacher(c: SchoolClass, teacherId: number) {
    setError(null)
    try {
      await assignClassTeacher(c.id, teacherId)
      await reloadClasses()
    } catch (e) {
      setError(errorMessage(e))
    }
  }
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
    if (!(await confirm({ title: `Ștergi ${label}?`, confirmLabel: 'Șterge', destructive: true }))) return
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
    <div className="p-4">
      <div className="mx-auto max-w-3xl space-y-4">
        {confirmDialog}
        <PageHeader
          title={chapter?.title ?? book?.title ?? cls?.name ?? 'Clase'}
          description={
            chapter ? 'Exercițiile capitolului.'
              : book ? 'Capitolele cărții.'
                : cls ? 'Cărțile clasei.'
                  : 'Clasele, cu cărțile, capitolele și exercițiile lor. Deschide o clasă ca să-i adaugi cărți.'
          }
          breadcrumbs={cls ? [
            { label: 'Clase', onClick: () => { setCls(null); setBook(null); setChapter(null) } },
            { label: cls.name, onClick: () => { setBook(null); setChapter(null) } },
            ...(book ? [{ label: book.title, onClick: () => setChapter(null) }] : []),
            ...(chapter ? [{ label: chapter.title }] : []),
          ] : undefined}
          actions={
            <>
              {!cls && isAdmin && <Link to="/admin/en-chapters" className={buttonVariants({ variant: 'outline' })}>Capitole EN</Link>}
              {!cls && <Button onClick={() => setDialog({ kind: 'class', item: null })}>Adaugă clasă</Button>}
              {cls && !book && <Button onClick={() => setDialog({ kind: 'book', item: null })}>Adaugă carte</Button>}
              {book && !chapter && <Button onClick={() => setDialog({ kind: 'chapter', item: null })}>Adaugă capitol</Button>}
              {chapter && <Button onClick={() => setDialog({ kind: 'exercise', item: null })}>Adaugă exercițiu</Button>}
            </>
          }
        />

        {error && <p className="text-sm text-destructive">{error}</p>}

        {loading && <LoadingCards />}

        {/* Exercises level */}
        {!loading && chapter && exercises.length > 0 && (
          <Card data-testid="chapter-quiz-cta" className="border-primary/30 bg-primary/5">
            <CardContent className="flex flex-wrap items-center justify-between gap-3 py-3">
              <p className="text-sm">
                <span className="font-medium">Fă un test din aceste exerciții.</span>{' '}
                <span className="text-muted-foreground">Alegi exercițiile, iar testul se creează pentru clasa capitolului.</span>
              </p>
              <Button size="sm" onClick={() => setDialog({ kind: 'chapterQuiz' })}>Creează test din capitol</Button>
            </CardContent>
          </Card>
        )}
        {!loading && chapter && (
          exercises.length === 0 ? <p className="text-muted-foreground">Niciun exercițiu. Adaugă exerciții, apoi poți face un test din ele.</p> : (
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
                    <MathContent className="text-sm text-muted-foreground">{ex.statement}</MathContent>
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
            <div className="space-y-2">
              {classes.map((c) => (
                <ClassCard
                  key={c.id}
                  schoolClass={c}
                  summary={summaries[c.id] ?? null}
                  teacherName={isAdmin ? teachers.find((t) => t.id === c.teacherId)?.fullName ?? null : null}
                  onOpen={() => openClass(c)}
                  onInvite={() => setDialog({ kind: 'invite', schoolClass: c })}
                  menu={[
                    { label: 'Elevii clasei', onClick: () => setDialog({ kind: 'enroll', schoolClass: c }) },
                    { label: 'Pe capitole', to: `/admin/classes/${c.id}/mastery` },
                    ...(isAdmin && teachers.length > 0
                      ? [{ label: 'Schimbă profesorul', onClick: () => setDialog({ kind: 'teacher', schoolClass: c }), testId: `class-teacher-${c.id}` }]
                      : []),
                    { label: 'Editează', onClick: () => setDialog({ kind: 'class', item: c }) },
                    {
                      label: 'Șterge',
                      destructive: true,
                      onClick: () => void removeWithConfirm(`clasa „${c.name}"`, () => deleteClass(c.id), reloadClasses),
                    },
                  ]}
                />
              ))}
            </div>
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
      {dialog?.kind === 'invite' && (
        <ClassInviteDialog open onClose={closeDialog} schoolClass={dialog.schoolClass} />
      )}
      {dialog?.kind === 'teacher' && (
        <TeacherDialog
          schoolClass={dialog.schoolClass}
          teachers={teachers}
          onClose={closeDialog}
          onSave={async (teacherId) => {
            await changeTeacher(dialog.schoolClass, teacherId)
            closeDialog()
          }}
        />
      )}
      {dialog?.kind === 'chapterQuiz' && chapter && (
        <ChapterQuizDialog
          open onClose={closeDialog} chapter={chapter} exercises={exercises}
          onCreated={(quiz) => navigate(`/admin/quizzes/${quiz.id}`)}
        />
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
            <div className="flex flex-wrap justify-end gap-2">
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

/**
 * One class: its name, how big it is and how it is doing (students, tests, mean grade in the grade's colour), and two
 * buttons - open it, invite students. Everything else (roster, chapters map, teacher, edit, delete) is in the ⋯ menu,
 * so the row reads at a glance instead of being a wall of equal buttons.
 */
function ClassCard({ schoolClass, summary, teacherName, onOpen, onInvite, menu }: {
  schoolClass: SchoolClass
  summary: ClassSummary | null
  /** ADMIN only: who owns the class. */
  teacherName: string | null
  onOpen: () => void
  onInvite: () => void
  menu: RowMenuItem[]
}) {
  return (
    <Card data-testid="class-card" className="hover-lift">
      <CardContent className="flex flex-wrap items-center justify-between gap-3 py-4">
        <div className="min-w-0">
          <p className="truncate font-medium">{schoolClass.name}</p>
          {schoolClass.description && <p className="truncate text-sm text-muted-foreground">{schoolClass.description}</p>}
          {(summary || teacherName) && (
            <p data-testid="class-summary" className="mt-1 flex flex-wrap gap-x-3 gap-y-0.5 text-sm text-muted-foreground">
              {summary && (
                <>
                  <span>{roCount(summary.students, 'elev', 'elevi')}</span>
                  <span>{roCount(summary.quizzes, 'test', 'teste')}</span>
                  {summary.average === null ? (
                    <span>fără note încă</span>
                  ) : (
                    <span>
                      media{' '}
                      <span className={cn('font-semibold tabular-nums', GRADE_TEXT[gradeBand(summary.average)])}>
                        {formatGrade(summary.average)}
                      </span>
                    </span>
                  )}
                </>
              )}
              {teacherName && <span>Profesor: {teacherName}</span>}
            </p>
          )}
        </div>
        <div className="flex items-center gap-2">
          <Button size="sm" variant="outline" onClick={onInvite}>Invită elevi</Button>
          <Button size="sm" onClick={onOpen}>Deschide</Button>
          <RowMenu label={`Mai multe acțiuni pentru ${schoolClass.name}`} items={menu} />
        </div>
      </CardContent>
    </Card>
  )
}

/** ADMIN: hands a class to another teacher (or admin), who then sees it, its students and its homework. */
function TeacherDialog({ schoolClass, teachers, onClose, onSave }: {
  schoolClass: SchoolClass
  teachers: AdminUserSummary[]
  onClose: () => void
  onSave: (teacherId: number) => Promise<void>
}) {
  const [teacherId, setTeacherId] = useState<string>(schoolClass.teacherId === null ? '' : String(schoolClass.teacherId))
  const [busy, setBusy] = useState(false)

  async function handleSubmit(e: FormEvent) {
    e.preventDefault()
    if (teacherId === '') return
    setBusy(true)
    try {
      await onSave(Number(teacherId))
    } finally {
      setBusy(false)
    }
  }

  return (
    <Dialog open onOpenChange={(o) => !o && onClose()} title={`Profesorul clasei ${schoolClass.name}`}>
      <form className="flex flex-col gap-4" onSubmit={handleSubmit}>
        <div className="flex flex-col gap-2">
          <Label htmlFor="class-teacher">Profesor</Label>
          <select
            id="class-teacher"
            data-testid="class-teacher-select"
            className="h-9 rounded-md border border-border bg-background px-2 text-sm"
            value={teacherId}
            onChange={(e) => setTeacherId(e.target.value)}
          >
            {schoolClass.teacherId === null && <option value="">Fără profesor</option>}
            {teachers.map((t) => (
              <option key={t.id} value={t.id}>{t.fullName}</option>
            ))}
          </select>
        </div>
        <div className="flex justify-end gap-2">
          <Button type="button" variant="outline" onClick={onClose} disabled={busy}>Anulează</Button>
          <Button type="submit" disabled={busy || teacherId === '' || Number(teacherId) === schoolClass.teacherId}>
            Salvează
          </Button>
        </div>
      </form>
    </Dialog>
  )
}
