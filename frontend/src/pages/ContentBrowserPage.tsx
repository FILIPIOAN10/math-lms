import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { Button, buttonVariants } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { MathContent } from '@/components/MathContent'
import {
  listBooks,
  listChapters,
  listClasses,
  listExercises,
  type Book,
  type Chapter,
  type Difficulty,
  type Exercise,
  type SchoolClass,
} from '@/lib/api'

const DIFFICULTY_STYLE: Record<Difficulty, { label: string; className: string }> = {
  EASY: { label: 'Ușor', className: 'bg-emerald-500/15 text-emerald-700 dark:text-emerald-300' },
  MEDIUM: { label: 'Mediu', className: 'bg-amber-500/15 text-amber-700 dark:text-amber-300' },
  HARD: { label: 'Dificil', className: 'bg-rose-500/15 text-rose-700 dark:text-rose-300' },
}

function DifficultyBadge({ difficulty }: { difficulty: Difficulty }) {
  const { label, className } = DIFFICULTY_STYLE[difficulty]
  return (
    <span className={`rounded-md px-2 py-0.5 text-xs font-medium ${className}`}>{label}</span>
  )
}

function ExerciseCard({ exercise, index }: { exercise: Exercise; index: number }) {
  const [showSolution, setShowSolution] = useState(false)
  return (
    <Card>
      <CardHeader>
        <div className="flex items-center justify-between gap-2">
          <CardTitle className="text-base">Exercițiul {index}</CardTitle>
          {exercise.difficulty && <DifficultyBadge difficulty={exercise.difficulty} />}
        </div>
      </CardHeader>
      <CardContent className="space-y-3">
        <MathContent className="text-sm leading-relaxed">{exercise.statement}</MathContent>
        {exercise.solution && (
          <div>
            <Button variant="ghost" size="sm" onClick={() => setShowSolution((s) => !s)}>
              {showSolution ? 'Ascunde soluția' : 'Vezi soluția'}
            </Button>
            {showSolution && (
              <div className="mt-2 rounded-lg border border-border bg-muted/40 p-3">
                <MathContent className="text-sm leading-relaxed">{exercise.solution}</MathContent>
              </div>
            )}
          </div>
        )}
      </CardContent>
    </Card>
  )
}

export function ContentBrowserPage() {
  const [classes, setClasses] = useState<SchoolClass[]>([])
  const [cls, setCls] = useState<SchoolClass | null>(null)
  const [books, setBooks] = useState<Book[]>([])
  const [book, setBook] = useState<Book | null>(null)
  const [chapters, setChapters] = useState<Chapter[]>([])
  const [chapter, setChapter] = useState<Chapter | null>(null)
  const [exercises, setExercises] = useState<Exercise[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    listClasses()
      .then(setClasses)
      .catch(() => setError('Nu am putut încărca lista de clase.'))
      .finally(() => setLoading(false))
  }, [])

  async function load<T>(promise: Promise<T>, apply: (value: T) => void) {
    setError(null)
    setLoading(true)
    try {
      apply(await promise)
    } catch {
      setError('Nu am putut încărca conținutul.')
    } finally {
      setLoading(false)
    }
  }

  function openClass(next: SchoolClass) {
    setCls(next)
    setBook(null)
    setChapter(null)
    setBooks([])
    setChapters([])
    setExercises([])
    void load(listBooks(next.id), setBooks)
  }

  function openBook(next: Book) {
    setBook(next)
    setChapter(null)
    setChapters([])
    setExercises([])
    void load(listChapters(next.id), setChapters)
  }

  function openChapter(next: Chapter) {
    setChapter(next)
    setExercises([])
    void load(listExercises(next.id), setExercises)
  }

  function goRoot() {
    setCls(null)
    setBook(null)
    setChapter(null)
  }

  function goClass() {
    setBook(null)
    setChapter(null)
  }

  function goBook() {
    setChapter(null)
  }

  return (
    <div className="min-h-screen bg-muted p-4">
      <div className="mx-auto max-w-3xl space-y-4">
        <div className="flex items-center justify-between">
          <nav className="flex flex-wrap items-center gap-1 text-sm text-muted-foreground">
            <button className="hover:text-foreground hover:underline" onClick={goRoot}>
              Clase
            </button>
            {cls && (
              <>
                <span>/</span>
                <button className="hover:text-foreground hover:underline" onClick={goClass}>
                  {cls.name}
                </button>
              </>
            )}
            {book && (
              <>
                <span>/</span>
                <button className="hover:text-foreground hover:underline" onClick={goBook}>
                  {book.title}
                </button>
              </>
            )}
            {chapter && (
              <>
                <span>/</span>
                <span className="text-foreground">{chapter.title}</span>
              </>
            )}
          </nav>
          <Link to="/" className={buttonVariants({ variant: 'outline', size: 'sm' })}>
            Acasă
          </Link>
        </div>

        {loading && <p className="text-muted-foreground">Se încarcă...</p>}
        {!loading && error && <p className="text-sm text-destructive">{error}</p>}

        {/* Level: exercises (a chapter is open) */}
        {!loading && !error && chapter && (
          exercises.length === 0 ? (
            <p className="text-muted-foreground">Acest capitol nu are încă exerciții.</p>
          ) : (
            <div className="space-y-3">
              {exercises.map((ex, i) => (
                <ExerciseCard key={ex.id} exercise={ex} index={i + 1} />
              ))}
            </div>
          )
        )}

        {/* Level: chapters (a book is open) */}
        {!loading && !error && book && !chapter && (
          <ItemList
            empty="Această carte nu are încă capitole."
            items={chapters.map((c) => ({ id: c.id, title: c.title, description: c.description, onOpen: () => openChapter(c) }))}
          />
        )}

        {/* Level: books (a class is open) */}
        {!loading && !error && cls && !book && (
          <ItemList
            empty="Această clasă nu are încă cărți."
            items={books.map((b) => ({ id: b.id, title: b.title, description: b.description, onOpen: () => openBook(b) }))}
          />
        )}

        {/* Level: classes (root) */}
        {!loading && !error && !cls && (
          <ItemList
            empty="Nu există încă nicio clasă."
            items={classes.map((c) => ({ id: c.id, title: c.name, description: c.description, onOpen: () => openClass(c) }))}
          />
        )}
      </div>
    </div>
  )
}

interface Row {
  id: number
  title: string
  description: string | null
  onOpen: () => void
}

function ItemList({ items, empty }: { items: Row[]; empty: string }) {
  if (items.length === 0) {
    return <p className="text-muted-foreground">{empty}</p>
  }
  return (
    <div className="space-y-2">
      {items.map((row) => (
        <Card key={row.id}>
          <CardHeader>
            <CardTitle className="text-base">{row.title}</CardTitle>
            {row.description && <CardDescription>{row.description}</CardDescription>}
          </CardHeader>
          <CardContent>
            <Button variant="outline" size="sm" onClick={row.onOpen}>
              Deschide
            </Button>
          </CardContent>
        </Card>
      ))}
    </div>
  )
}
