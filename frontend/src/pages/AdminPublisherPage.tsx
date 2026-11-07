import { useEffect, useState, type FormEvent } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { Button } from '@/components/ui/button'
import { Card, CardContent } from '@/components/ui/card'
import { Dialog } from '@/components/ui/dialog'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Textarea } from '@/components/ui/textarea'
import { ErrorState } from '@/components/ErrorState'
import { LoadingCards } from '@/components/LoadingCards'
import { PageHeader } from '@/components/PageHeader'
import { useConfirm } from '@/components/useConfirm'
import { QuizDialog } from '@/pages/AdminQuizzesPage'
import { PublisherLogo } from '@/pages/AdminPublishersPage'
import {
  createPublisherBook, createQuiz, deletePublisherBook, getPublisher, listChapterOptions, listClasses, listPublishers,
  listQuizzes, updatePublisherBook, type ChapterOption, type Publisher, type PublisherBook, type QuizSummary,
  type SchoolClass,
} from '@/lib/api'
import { errorMessage } from '@/lib/errors'
import { roCount } from '@/lib/format'

/** "3 teste", "niciun test". */
const testsLabel = (n: number) => (n === 0 ? 'niciun test' : roCount(n, 'test', 'teste'))

/**
 * One publisher: its books ("Culegere EN 2026"...), each holding the tests taken from it. Editură → Carte → Teste.
 */
export function AdminPublisherPage() {
  const publisherId = Number(useParams().publisherId)
  const [publisher, setPublisher] = useState<Publisher | null>(null)
  const [error, setError] = useState<string | null>(null)
  // null = closed; { item: null } = a new book
  const [editing, setEditing] = useState<{ item: PublisherBook | null } | null>(null)
  const { confirm, dialog } = useConfirm()

  const reload = () =>
    getPublisher(publisherId)
      .then((p) => { setPublisher(p); setError(null) })
      .catch((e) => setError(errorMessage(e)))

  useEffect(() => { void reload() }, [publisherId]) // eslint-disable-line react-hooks/exhaustive-deps

  async function remove(book: PublisherBook) {
    const yes = await confirm({
      title: `Ștergi cartea „${book.title}”?`,
      body: book.quizCount === 0 ? 'Nu are niciun test.'
        : `${book.quizCount === 1 ? 'Testul ei rămâne' : `Cele ${testsLabel(book.quizCount)} rămân`} la editură, fără carte.`,
      confirmLabel: 'Șterge cartea',
      destructive: true,
    })
    if (!yes) return
    try {
      await deletePublisherBook(book.id)
      await reload()
    } catch (e) {
      setError(errorMessage(e))
    }
  }

  if (error && !publisher) return <ErrorState message={error} />
  if (!publisher) return <LoadingCards />

  return (
    <div className="mx-auto w-full max-w-3xl space-y-4">
      <PageHeader
        title={publisher.name}
        breadcrumbs={[{ label: 'Teste', to: '/admin/quizzes' }, { label: 'Edituri', to: '/admin/publishers' }, { label: publisher.name }]}
        description={
          <span className="flex items-center gap-3">
            <PublisherLogo publisher={publisher} />
            <span>{publisher.description ?? 'Cărțile editurii și testele din fiecare.'}</span>
          </span>
        }
        actions={<Button size="sm" onClick={() => setEditing({ item: null })}>Adaugă carte</Button>}
      />
      {error && <ErrorState message={error} />}
      {publisher.books.length === 0 && (
        <p className="text-muted-foreground">Nicio carte încă. Adaugă prima cu „Adaugă carte”, apoi testele din ea.</p>
      )}
      <ul className="space-y-3">
        {publisher.books.map((b) => (
          <li key={b.id}>
            <Card data-testid="publisher-book">
              <CardContent className="flex items-center gap-4 pt-4">
                <div className="min-w-0 flex-1">
                  <Link to={`/admin/publishers/${publisher.id}/books/${b.id}`} className="font-medium hover:underline">
                    {b.title}
                  </Link>
                  {b.description && <p className="truncate text-sm text-muted-foreground">{b.description}</p>}
                  <p className="text-xs text-muted-foreground">{testsLabel(b.quizCount)}</p>
                </div>
                <div className="flex shrink-0 gap-2">
                  <Link to={`/admin/publishers/${publisher.id}/books/${b.id}`}
                        className="inline-flex h-8 items-center rounded-md border px-3 text-sm font-medium hover:bg-muted">
                    Teste
                  </Link>
                  <Button size="sm" variant="outline" onClick={() => setEditing({ item: b })}>Editează</Button>
                  <Button size="sm" variant="ghost" className="text-destructive" onClick={() => void remove(b)}>Șterge</Button>
                </div>
              </CardContent>
            </Card>
          </li>
        ))}
      </ul>
      {editing && (
        <BookDialog publisherId={publisher.id} item={editing.item} onClose={() => setEditing(null)}
                    onSaved={() => { setEditing(null); void reload() }} />
      )}
      {dialog}
    </div>
  )
}

/** Add or edit a book: its title and an optional description. */
function BookDialog({ publisherId, item, onClose, onSaved }: {
  publisherId: number
  item: PublisherBook | null
  onClose: () => void
  onSaved: () => void
}) {
  const [title, setTitle] = useState(item?.title ?? '')
  const [description, setDescription] = useState(item?.description ?? '')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)

  async function submit(e: FormEvent) {
    e.preventDefault()
    setError(null)
    setBusy(true)
    try {
      const input = { title: title.trim(), description: description.trim() || null }
      if (item) await updatePublisherBook(item.id, input)
      else await createPublisherBook(publisherId, input)
      onSaved()
    } catch (err) {
      setError(errorMessage(err))
      setBusy(false)
    }
  }

  return (
    <Dialog open onOpenChange={(o) => !o && onClose()} title={item ? 'Editează cartea' : 'Adaugă carte'}>
      <form className="flex flex-col gap-4" onSubmit={submit}>
        <div className="flex flex-col gap-2">
          <Label htmlFor="btitle">Titlul cărții</Label>
          <Input id="btitle" value={title} maxLength={200} required autoFocus placeholder="ex. Culegere Evaluarea Națională 2026"
                 onChange={(e) => setTitle(e.target.value)} />
        </div>
        <div className="flex flex-col gap-2">
          <Label htmlFor="bdesc">Descriere (opțional)</Label>
          <Textarea id="bdesc" value={description} maxLength={1000} rows={2} onChange={(e) => setDescription(e.target.value)} />
        </div>
        {error && <p role="alert" className="text-sm text-destructive">{error}</p>}
        <div className="flex gap-2">
          <Button type="submit" disabled={busy || title.trim() === ''}>{busy ? 'Se salvează...' : 'Salvează'}</Button>
          <Button type="button" variant="outline" onClick={onClose} disabled={busy}>Anulează</Button>
        </div>
      </form>
    </Dialog>
  )
}

/**
 * One book of a publisher: the tests taken from it, and "Adaugă test", which makes a test already in this book (the
 * questions are then added in the test's own page, as for any test).
 */
export function AdminPublisherBookPage() {
  const params = useParams()
  const publisherId = Number(params.publisherId)
  const bookId = Number(params.bookId)
  const navigate = useNavigate()
  const [publishers, setPublishers] = useState<Publisher[] | null>(null)
  const [quizzes, setQuizzes] = useState<QuizSummary[]>([])
  const [classes, setClasses] = useState<SchoolClass[]>([])
  const [chapters, setChapters] = useState<ChapterOption[]>([])
  const [error, setError] = useState<string | null>(null)
  const [adding, setAdding] = useState(false)

  useEffect(() => {
    listPublishers().then(setPublishers).catch((e) => setError(errorMessage(e)))
    listQuizzes().then(setQuizzes).catch((e) => setError(errorMessage(e)))
    listClasses().then(setClasses).catch(() => undefined)
    listChapterOptions().then(setChapters).catch(() => undefined)
  }, [])

  const publisher = publishers?.find((p) => p.id === publisherId) ?? null
  const book = publisher?.books.find((b) => b.id === bookId) ?? null
  const inBook = quizzes.filter((q) => q.bookId === bookId)

  if (error) return <ErrorState message={error} />
  if (publishers === null) return <LoadingCards />
  if (!publisher || !book) return <ErrorState message="Cartea nu mai există." />

  return (
    <div className="mx-auto w-full max-w-3xl space-y-4">
      <PageHeader
        title={book.title}
        breadcrumbs={[
          { label: 'Teste', to: '/admin/quizzes' }, { label: 'Edituri', to: '/admin/publishers' },
          { label: publisher.name, to: `/admin/publishers/${publisher.id}` }, { label: book.title },
        ]}
        description={book.description ?? `Testele din această carte a editurii ${publisher.name}.`}
        actions={<Button size="sm" onClick={() => setAdding(true)}>Adaugă test</Button>}
      />
      {inBook.length === 0 && (
        <p className="text-muted-foreground">Niciun test în carte încă. Apasă „Adaugă test”, apoi adaugă întrebările lui.</p>
      )}
      <ul className="space-y-3">
        {inBook.map((q) => (
          <li key={q.id}>
            <Card data-testid="book-quiz">
              <CardContent className="flex items-center gap-3 pt-4">
                <Link to={`/admin/quizzes/${q.id}`} className="min-w-0 flex-1 truncate font-medium hover:underline">{q.title}</Link>
                <span className={`rounded-md px-2 py-0.5 text-xs font-medium ${q.status === 'PUBLISHED'
                  ? 'bg-green-500/15 text-green-800 dark:text-green-200' : 'bg-amber-500/15 text-amber-800 dark:text-amber-200'}`}>
                  {q.status === 'PUBLISHED' ? 'Publicat' : 'Ciornă'}
                </span>
                <span className="text-xs text-muted-foreground">{q.schoolClassName ?? 'Toți elevii'}</span>
              </CardContent>
            </Card>
          </li>
        ))}
      </ul>
      {adding && (
        <QuizDialog
          open
          onClose={() => setAdding(false)}
          initialTitle=""
          initialDescription=""
          initialClassId={null}
          initialTimeLimit={null}
          initialPracticeAllowed={false}
          initialMaxAttempts={1}
          initialPublisherId={publisher.id}
          initialBookId={book.id}
          classes={classes}
          chapters={chapters}
          publishers={publishers}
          onSubmit={async (input) => {
            const created = await createQuiz(input)
            navigate(`/admin/quizzes/${created.id}`)
          }}
        />
      )}
    </div>
  )
}
