import { useEffect, useMemo, useRef, useState, type ClipboardEvent } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { Button } from '@/components/ui/button'
import { Card, CardContent } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Textarea } from '@/components/ui/textarea'
import { MathContent } from '@/components/MathContent'
import { PageHeader } from '@/components/PageHeader'
import { PhotoPages } from '@/components/PhotoPages'
import { useConfirm } from '@/components/useConfirm'
import {
  aiImportPageUrl,
  createAiImport,
  createQuizFromAiImport,
  discardAiImport,
  getAiImport,
  getAiImportSettings,
  listAiImports,
  listChapterOptions,
  listClasses,
  retryAiImport,
  type AiImport,
  type AiImportSettings,
  type ChapterOption,
  type QuizItemType,
  type SchoolClass,
} from '@/lib/api'
import { draftPoints, draftProblems, toDrafts, toItemInputs, type DraftItem } from '@/lib/aiImport'
import { errorMessage } from '@/lib/errors'
import { formatDate, roCount } from '@/lib/format'
import { shrinkImage } from '@/lib/image'

const selectClass =
  'h-9 rounded-lg border border-border bg-background px-2 text-sm outline-none focus-visible:border-ring focus-visible:ring-3 focus-visible:ring-ring/50 disabled:opacity-50'

/** How often the page asks whether the pages have been read. */
const POLL_MS = 2000

const STATUS_LABEL: Record<AiImport['status'], string> = {
  QUEUED: 'În așteptare',
  RUNNING: 'Se citește',
  DONE: 'Citit',
  FAILED: 'Eșuat',
}

/** "12 octombrie 2026": the default title names the day the pages were photographed. */
const DAY = new Intl.DateTimeFormat('ro-RO', { day: 'numeric', month: 'long', year: 'numeric', timeZone: 'Europe/Bucharest' })

const crumbs = (last: string) => [{ label: 'Quiz-uri', to: '/admin/quizzes' }, { label: last }]

/**
 * Faza 3: a quiz from photographed textbook pages. Without an id: pick 1-5 pages and send them (plus the recent
 * imports). With one: wait while they are read, then review every question - fix the text, mark each grilă's right
 * answer (the AI never guesses it), drop what is not wanted - and make a DRAFT quiz.
 */
export function AdminAiImportPage() {
  const { importId: importIdParam } = useParams<{ importId?: string }>()
  const importId = importIdParam === undefined ? null : Number(importIdParam)
  const [classes, setClasses] = useState<SchoolClass[]>([])
  const [chapters, setChapters] = useState<ChapterOption[]>([])

  useEffect(() => {
    listClasses().then(setClasses).catch(() => undefined)
    listChapterOptions().then(setChapters).catch(() => undefined)
  }, [])

  return (
    <div className="p-4">
      <div className="mx-auto max-w-3xl space-y-4">
        {importId === null
          ? <UploadView classes={classes} chapters={chapters} />
          : <ImportView key={importId} id={importId} classes={classes} chapters={chapters} />}
      </div>
    </div>
  )
}

// ---------- Step 1: choose the pages ----------

function UploadView({ classes, chapters }: { classes: SchoolClass[]; chapters: ChapterOption[] }) {
  const navigate = useNavigate()
  const [settings, setSettings] = useState<AiImportSettings | null>(null)
  const [recent, setRecent] = useState<AiImport[]>([])
  const [pages, setPages] = useState<File[]>([])
  const [classId, setClassId] = useState<number | null>(null)
  const [chapterId, setChapterId] = useState<number | null>(null)
  const [onlyTextbook, setOnlyTextbook] = useState(false)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const gallery = useRef<HTMLInputElement>(null)
  const camera = useRef<HTMLInputElement>(null)

  useEffect(() => {
    getAiImportSettings().then(setSettings).catch((e) => setError(errorMessage(e)))
    listAiImports().then(setRecent).catch(() => undefined)
  }, [])

  const previews = useMemo(() => pages.map((p) => URL.createObjectURL(p)), [pages])
  useEffect(() => () => previews.forEach((url) => URL.revokeObjectURL(url)), [previews])

  const maxPages = settings?.maxPages ?? 5
  const leftToday = settings ? Math.max(0, settings.dailyPageLimit - settings.pagesUsedToday) : 0
  const chapterChoices = classId === null ? chapters : chapters.filter((c) => c.schoolClassId === classId)

  function add(files: FileList | File[] | null) {
    if (!files) return
    const images = Array.from(files).filter((f) => f.type.startsWith('image/'))
    setError(images.length + pages.length > maxPages ? `Poți trimite cel mult ${maxPages} pagini odată.` : null)
    setPages((current) => [...current, ...images].slice(0, maxPages))
  }

  function onPaste(e: ClipboardEvent<HTMLDivElement>) {
    if (e.clipboardData.files.length === 0) return
    e.preventDefault()
    add(e.clipboardData.files)
  }

  async function send() {
    setBusy(true)
    setError(null)
    try {
      // Long edge 2000 px keeps small print readable at a few hundred KB a page.
      const shrunk = await Promise.all(pages.map((p) => shrinkImage(p, 2000, 0.85)))
      const created = await createAiImport(shrunk, classId, chapterId)
      navigate(`/admin/quizzes/ai-import/${created.id}`)
    } catch (e) {
      setError(errorMessage(e))
      setBusy(false)
    }
  }

  if (settings && !settings.enabled) {
    return (
      <>
        <PageHeader title="Test din poze" breadcrumbs={crumbs('Din poze')} />
        <p className="text-muted-foreground">Citirea testelor din poze nu e pornită pe acest server.</p>
      </>
    )
  }

  return (
    <div className="space-y-4" onPaste={onPaste}>
      <PageHeader
        title="Test din poze"
        breadcrumbs={crumbs('Din poze')}
        description="Fotografiază 1–5 pagini din manual sau culegere. Le citim, verifici fiecare exercițiu, marchezi răspunsurile corecte și primești un test ciornă."
      />

      <Card>
        <CardContent className="space-y-4 pt-4">
          <div className="grid gap-3 sm:grid-cols-2">
            <div className="flex flex-col gap-2">
              <Label htmlFor="ai-class">Clasa</Label>
              <select id="ai-class" className={selectClass} value={classId ?? ''}
                      onChange={(e) => { setClassId(e.target.value === '' ? null : Number(e.target.value)); setChapterId(null) }}>
                <option value="">Toți elevii</option>
                {classes.map((c) => <option key={c.id} value={c.id}>{c.name}</option>)}
              </select>
            </div>
            <div className="flex flex-col gap-2">
              <Label htmlFor="ai-chapter">Capitolul (opțional)</Label>
              <select id="ai-chapter" className={selectClass} value={chapterId ?? ''}
                      onChange={(e) => setChapterId(e.target.value === '' ? null : Number(e.target.value))}>
                <option value="">Fără capitol</option>
                {chapterChoices.map((c) => <option key={c.id} value={c.id}>{c.schoolClassName} · {c.title}</option>)}
              </select>
            </div>
          </div>

          <div className="space-y-2">
            <div className="flex flex-wrap gap-2">
              <Button type="button" variant="outline" size="sm" onClick={() => camera.current?.click()}
                      disabled={pages.length >= maxPages}>Fă o poză</Button>
              <Button type="button" variant="outline" size="sm" onClick={() => gallery.current?.click()}
                      disabled={pages.length >= maxPages}>Alege din galerie</Button>
              <span className="self-center text-xs text-muted-foreground">sau lipește (Ctrl+V) o captură</span>
            </div>
            <input ref={camera} type="file" accept="image/*" capture="environment" className="hidden"
                   data-testid="ai-camera" onChange={(e) => { add(e.target.files); e.target.value = '' }} />
            <input ref={gallery} type="file" accept="image/*" multiple className="hidden"
                   data-testid="ai-gallery" onChange={(e) => { add(e.target.files); e.target.value = '' }} />
            {pages.length > 0 && (
              <ul className="flex flex-wrap gap-3" aria-label="Paginile alese">
                {pages.map((page, i) => (
                  <li key={previews[i]} className="space-y-1">
                    <img src={previews[i]} alt={`Pagina ${i + 1}`} className="h-32 rounded-lg border object-contain" />
                    <Button type="button" variant="ghost" size="xs" aria-label={`Scoate pagina ${i + 1}`}
                            onClick={() => setPages(pages.filter((p) => p !== page))}>Scoate</Button>
                  </li>
                ))}
              </ul>
            )}
          </div>

          <label className="flex items-start gap-2 text-sm">
            <input type="checkbox" className="mt-1" checked={onlyTextbook} onChange={(e) => setOnlyTextbook(e.target.checked)} />
            <span>Pozele sunt doar pagini din manual sau culegere — fără lucrări ale elevilor, nume sau fețe.</span>
          </label>

          {error && <p role="alert" className="text-sm text-destructive">{error}</p>}
          <div className="flex flex-wrap items-center gap-3">
            <Button type="button" onClick={() => void send()}
                    disabled={busy || pages.length === 0 || !onlyTextbook || pages.length > leftToday}>
              {busy ? 'Se trimite...' : `Citește ${roCount(pages.length, 'pagină', 'pagini')}`}
            </Button>
            {settings && (
              <span className="text-xs text-muted-foreground">
                Azi mai poți trimite {roCount(leftToday, 'pagină', 'pagini')} (din {settings.dailyPageLimit}).
              </span>
            )}
          </div>
        </CardContent>
      </Card>

      {recent.length > 0 && (
        <section className="space-y-2">
          <h2 className="text-sm font-medium">Citiri recente</h2>
          <ul className="divide-y rounded-lg border">
            {recent.map((r) => (
              <li key={r.id} className="flex flex-wrap items-center justify-between gap-2 px-3 py-2 text-sm">
                <span>
                  {formatDate(r.createdAt)} · {roCount(r.pageCount, 'pagină', 'pagini')} · {STATUS_LABEL[r.status]}
                  {r.itemCount !== null && ` · ${roCount(r.itemCount, 'exercițiu', 'exerciții')}`}
                </span>
                {r.quizId !== null
                  ? <Link className="underline" to={`/admin/quizzes/${r.quizId}`}>Testul creat</Link>
                  : <Link className="underline" to={`/admin/quizzes/ai-import/${r.id}`}>Deschide</Link>}
              </li>
            ))}
          </ul>
        </section>
      )}
    </div>
  )
}

// ---------- Step 2 and 3: wait, then review ----------

function ImportView({ id, classes, chapters }: { id: number; classes: SchoolClass[]; chapters: ChapterOption[] }) {
  const navigate = useNavigate()
  const { confirm, dialog: confirmDialog } = useConfirm()
  const [ai, setAi] = useState<AiImport | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  const waiting = ai === null || ai.status === 'QUEUED' || ai.status === 'RUNNING'
  useEffect(() => {
    if (!waiting) return
    let cancelled = false
    // The first load at once, then one more every POLL_MS while the pages are still being read.
    const timer = setTimeout(() => {
      getAiImport(id)
        .then((next) => !cancelled && setAi(next))
        .catch((e) => !cancelled && setError(errorMessage(e)))
    }, ai === null ? 0 : POLL_MS)
    return () => {
      cancelled = true
      clearTimeout(timer)
    }
  }, [id, ai, waiting])

  async function retry() {
    setBusy(true)
    setError(null)
    try {
      setAi(await retryAiImport(id))
    } catch (e) {
      setError(errorMessage(e))
    } finally {
      setBusy(false)
    }
  }

  async function discard() {
    const yes = await confirm({
      title: 'Renunți la aceste pagini?',
      body: 'Pozele și exercițiile citite se șterg.',
      confirmLabel: 'Renunță',
      destructive: true,
    })
    if (!yes) return
    try {
      await discardAiImport(id)
      navigate('/admin/quizzes/ai-import')
    } catch (e) {
      setError(errorMessage(e))
    }
  }

  return (
    <div className="space-y-4">
      <PageHeader title="Test din poze" breadcrumbs={[{ label: 'Quiz-uri', to: '/admin/quizzes' },
        { label: 'Din poze', to: '/admin/quizzes/ai-import' }, { label: 'Verificare' }]} />
      {confirmDialog}
      {error && <p role="alert" className="text-sm text-destructive">{error}</p>}
      {ai === null && !error && <p className="text-muted-foreground">Se încarcă...</p>}
      {ai !== null && waiting && (
        <Card>
          <CardContent className="space-y-2 pt-4" role="status">
            <p className="font-medium">{ai.pageCount === 1 ? 'Se citește pagina' : `Se citesc ${roCount(ai.pageCount, 'pagină', 'pagini')}`}...</p>
            <p className="text-sm text-muted-foreground">De obicei durează între 10 și 40 de secunde. Poți lăsa pagina deschisă.</p>
          </CardContent>
        </Card>
      )}
      {ai?.status === 'FAILED' && (
        <Card>
          <CardContent className="space-y-3 pt-4">
            <p role="alert" className="text-destructive">{ai.error ?? 'Citirea a eșuat.'}</p>
            <div className="flex flex-wrap gap-2">
              {ai.pagesAvailable && <Button onClick={() => void retry()} disabled={busy}>Încearcă din nou</Button>}
              <Button variant="outline" onClick={() => void discard()}>Renunță</Button>
            </div>
          </CardContent>
        </Card>
      )}
      {ai?.status === 'DONE' && ai.quizId !== null && (
        <p>Testul a fost creat. <Link className="underline" to={`/admin/quizzes/${ai.quizId}`}>Deschide-l</Link>.</p>
      )}
      {ai?.status === 'DONE' && ai.quizId === null && ai.items && (
        <Review ai={ai} classes={classes} chapters={chapters} onDiscard={() => void discard()} />
      )}
    </div>
  )
}

function Review({ ai, classes, chapters, onDiscard }: {
  ai: AiImport
  classes: SchoolClass[]
  chapters: ChapterOption[]
  onDiscard: () => void
}) {
  const navigate = useNavigate()
  const [title, setTitle] = useState(`Test din manual — ${DAY.format(new Date(ai.createdAt))}`)
  const [classId, setClassId] = useState<number | null>(ai.schoolClassId)
  const [chapterId, setChapterId] = useState<number | null>(ai.chapterId)
  const [drafts, setDrafts] = useState<DraftItem[]>(() => toDrafts(ai.items ?? []))
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const problems = draftProblems(drafts)
  const kept = drafts.filter((d) => d.include).length
  const chapterChoices = classId === null ? chapters : chapters.filter((c) => c.schoolClassId === classId)
  const update = (key: number, change: Partial<DraftItem>) =>
    setDrafts((current) => current.map((d) => (d.key === key ? { ...d, ...change } : d)))

  async function create() {
    setBusy(true)
    setError(null)
    try {
      const quizId = await createQuizFromAiImport(ai.id, {
        title: title.trim(), schoolClassId: classId, chapterId, items: toItemInputs(drafts),
      })
      navigate(`/admin/quizzes/${quizId}`)
    } catch (e) {
      setError(errorMessage(e))
      setBusy(false)
    }
  }

  return (
    <div className="space-y-4">
      <p className="text-sm text-muted-foreground">
        Am găsit {roCount(drafts.length, 'exercițiu', 'exerciții')}. Verifică textul cu poza alături, marchează
        răspunsul corect la fiecare grilă și debifează ce nu vrei în test.
      </p>
      {ai.pagesAvailable && (
        <details className="rounded-lg border p-3" open>
          <summary className="cursor-pointer text-sm font-medium">Pozele ({ai.pageCount})</summary>
          <div className="mt-3">
            <PhotoPages urls={Array.from({ length: ai.pageCount }, (_, i) => aiImportPageUrl(ai.id, i + 1))}
                        alt={(page) => `Pagina ${page} din manual`} />
          </div>
        </details>
      )}

      <Card>
        <CardContent className="grid gap-3 pt-4 sm:grid-cols-3">
          <div className="flex flex-col gap-2 sm:col-span-3">
            <Label htmlFor="ai-title">Titlul testului</Label>
            <Input id="ai-title" value={title} maxLength={200} onChange={(e) => setTitle(e.target.value)} />
          </div>
          <div className="flex flex-col gap-2">
            <Label htmlFor="ai-review-class">Clasa</Label>
            <select id="ai-review-class" className={selectClass} value={classId ?? ''}
                    onChange={(e) => { setClassId(e.target.value === '' ? null : Number(e.target.value)); setChapterId(null) }}>
              <option value="">Toți elevii</option>
              {classes.map((c) => <option key={c.id} value={c.id}>{c.name}</option>)}
            </select>
          </div>
          <div className="flex flex-col gap-2 sm:col-span-2">
            <Label htmlFor="ai-review-chapter">Capitolul</Label>
            <select id="ai-review-chapter" className={selectClass} value={chapterId ?? ''}
                    onChange={(e) => setChapterId(e.target.value === '' ? null : Number(e.target.value))}>
              <option value="">Fără capitol</option>
              {chapterChoices.map((c) => <option key={c.id} value={c.id}>{c.schoolClassName} · {c.title}</option>)}
            </select>
          </div>
        </CardContent>
      </Card>

      <ol className="space-y-3">
        {drafts.map((d) => (
          <li key={d.key}>
            <DraftCard draft={d} onChange={(change) => update(d.key, change)} />
          </li>
        ))}
      </ol>

      {problems.length > 0 && (
        <ul className="list-disc space-y-1 pl-5 text-sm text-amber-700 dark:text-amber-400" data-testid="ai-problems">
          {problems.map((p) => <li key={p}>{p}</li>)}
        </ul>
      )}
      {error && <p role="alert" className="text-sm text-destructive">{error}</p>}
      <div className="flex flex-wrap gap-2">
        <Button onClick={() => void create()} disabled={busy || kept === 0 || problems.length > 0 || title.trim() === ''}>
          {busy ? 'Se creează...' : `Creează testul (${roCount(kept, 'subiect', 'subiecte')})`}
        </Button>
        <Button variant="outline" onClick={onDiscard} disabled={busy}>Renunță</Button>
      </div>
    </div>
  )
}

function DraftCard({ draft: d, onChange }: { draft: DraftItem; onChange: (change: Partial<DraftItem>) => void }) {
  const id = `ai-item-${d.key}`
  // A question turned into a grilă gets at least two (empty) variants to fill in.
  const setType = (type: QuizItemType) => onChange({
    type,
    options: type === 'SINGLE_CHOICE' ? [...d.options, '', ''].slice(0, Math.max(2, d.options.length)) : d.options,
    correct: null,
  })
  const setOption = (index: number, text: string) => onChange({ options: d.options.map((o, i) => (i === index ? text : o)) })
  const removeOption = (index: number) => onChange({
    options: d.options.filter((_, i) => i !== index),
    correct: d.correct === null || d.correct === index ? null : d.correct > index ? d.correct - 1 : d.correct,
  })

  return (
    <Card className={d.include ? undefined : 'opacity-60'} data-testid="ai-draft">
      <CardContent className="space-y-3 pt-4">
        <div className="flex flex-wrap items-center gap-3">
          <label className="flex items-center gap-2 text-sm font-medium">
            <input type="checkbox" checked={d.include} onChange={(e) => onChange({ include: e.target.checked })} />
            Îl includ
          </label>
          <span className="text-xs text-muted-foreground">pagina {d.page}</span>
          <select aria-label="Tipul" className={selectClass} value={d.type} disabled={!d.include}
                  onChange={(e) => setType(e.target.value as QuizItemType)}>
            <option value="SINGLE_CHOICE">Grilă</option>
            <option value="OPEN">Rezolvare completă</option>
          </select>
          <label className="flex items-center gap-2 text-sm">
            Puncte
            <Input type="number" min={0} max={100} className="w-20" value={draftPoints(d)}
                   disabled={!d.include || d.parts.length > 0}
                   onChange={(e) => onChange({ points: e.target.value === '' ? 0 : Number(e.target.value) })} />
          </label>
        </div>

        {d.include && (
          <>
            {d.warnings.length > 0 && (
              <ul className="space-y-1 text-sm text-amber-700 dark:text-amber-400">
                {d.warnings.map((w) => <li key={w}>⚠ {w}</li>)}
              </ul>
            )}
            <div className="flex flex-col gap-2">
              <Label htmlFor={`${id}-section`}>Secțiunea</Label>
              {/* Two lines: the heading, then its instruction ("Încercuiește litera…"), as the quiz builder shows them. */}
              <Textarea id={`${id}-section`} rows={2} value={d.section} maxLength={300} placeholder="ex. SUBIECTUL I"
                        onChange={(e) => onChange({ section: e.target.value })} />
            </div>
            <div className="flex flex-col gap-2">
              <Label htmlFor={`${id}-statement`}>Enunțul</Label>
              <Textarea id={`${id}-statement`} rows={3} value={d.statement} onChange={(e) => onChange({ statement: e.target.value })} />
              <MathContent className="rounded-md bg-muted/40 p-2 text-sm">{d.statement}</MathContent>
            </div>
            {d.type === 'SINGLE_CHOICE' && (
              <fieldset className="space-y-2">
                <legend className="text-sm font-medium">Variante — bifează răspunsul corect</legend>
                {d.options.map((option, i) => (
                  <div key={i} className="flex items-center gap-2">
                    <input type="radio" name={`${id}-correct`} checked={d.correct === i} aria-label={`Varianta ${i + 1} e corectă`}
                           onChange={() => onChange({ correct: i })} />
                    <Input value={option} aria-label={`Varianta ${i + 1}`} onChange={(e) => setOption(i, e.target.value)} />
                    <MathContent inline className="min-w-16 text-sm">{option}</MathContent>
                    <Button type="button" variant="ghost" size="xs" onClick={() => removeOption(i)}
                            disabled={d.options.length <= 2} aria-label={`Șterge varianta ${i + 1}`}>✕</Button>
                  </div>
                ))}
                <Button type="button" variant="outline" size="xs" onClick={() => onChange({ options: [...d.options, ''] })}
                        disabled={d.options.length >= 6}>Adaugă variantă</Button>
              </fieldset>
            )}
            {d.type === 'OPEN' && d.parts.length > 0 && (
              <fieldset className="space-y-2" data-testid="ai-parts">
                <legend className="text-sm font-medium">Cerințe (fiecare se notează separat)</legend>
                {d.parts.map((part, i) => {
                  const letter = String.fromCharCode(97 + i)
                  const setPart = (change: Partial<{ statement: string; points: number }>) =>
                    onChange({ parts: d.parts.map((p, j) => (j === i ? { ...p, ...change } : p)) })
                  return (
                    <div key={i} className="space-y-1">
                      <div className="flex items-start gap-2">
                        <span className="mt-2 w-5 shrink-0 text-sm font-semibold">{letter})</span>
                        <Textarea rows={2} value={part.statement} aria-label={`Cerința ${letter})`}
                                  onChange={(e) => setPart({ statement: e.target.value })} />
                        <Input type="number" min={0} max={100} className="w-16 shrink-0" value={part.points}
                               aria-label={`Puncte cerința ${letter})`}
                               onChange={(e) => setPart({ points: e.target.value === '' ? 0 : Number(e.target.value) })} />
                        <span className="mt-2 text-sm text-muted-foreground">p</span>
                      </div>
                      <MathContent className="ml-7 rounded-md bg-muted/40 p-2 text-sm">{part.statement}</MathContent>
                    </div>
                  )
                })}
              </fieldset>
            )}
            <div className="flex flex-col gap-2">
              <Label htmlFor={`${id}-solution`}>Barem / rezolvare (opțional)</Label>
              <Textarea id={`${id}-solution`} rows={2} value={d.solution} onChange={(e) => onChange({ solution: e.target.value })} />
            </div>
            <label className="flex items-center gap-2 text-sm">
              <input type="checkbox" checked={d.imageRequired} onChange={(e) => onChange({ imageRequired: e.target.checked })} />
              Are desen (îl adaugi ca imagine după ce creezi testul)
            </label>
          </>
        )}
      </CardContent>
    </Card>
  )
}
