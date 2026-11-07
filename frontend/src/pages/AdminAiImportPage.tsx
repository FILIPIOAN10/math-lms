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
import { EnChapterSelect } from '@/components/EnChapterSelect'
import {
  aiImportPageUrl,
  createAiImport,
  createQuizFromAiImport,
  discardAiImport,
  getAiImport,
  getAiGradingSettings,
  getAiImportSettings,
  listAiImports,
  listChapterOptions,
  listEnChapters,
  type EnChapterSummary,
  listClasses,
  publishAndAssign,
  retryAiImport,
  type AiImport,
  type AiImportSettings,
  type ChapterOption,
  type QuizItemType,
  type SchoolClass,
} from '@/lib/api'
import {
  boxToPhoto, draftPlaces, draftPoints, draftProblems, photoToBox, toBaremQuestion, toDrafts, toFigureInputs, toItemInputs,
  withQuestionBarem, type DraftItem, type DraftPart,
} from '@/lib/aiImport'
import { QuestionBaremReader } from '@/components/QuestionBaremReader'
import { FigureCropEditor } from '@/components/FigureCropEditor'
import { PhotoCrop } from '@/components/PhotoCrop'
import { defaultHomeworkDue, localInputToIso } from '@/lib/assignments'
import { errorMessage } from '@/lib/errors'
import { formatDate, optionLetter, roCount } from '@/lib/format'
import { shrinkImage } from '@/lib/image'
import { pdfPageImages } from '@/lib/pdfPages'
import { LoadingCards } from '@/components/LoadingCards'
import { selectClass } from '@/lib/formClasses'

/** How often the page asks whether the pages have been read. */
const POLL_MS = 2000

/** A PDF (scanned pages): sent as it is, the server renders its pages. Some browsers leave its type empty. */
const isPdf = (f: File) => f.type === 'application/pdf' || f.name.toLowerCase().endsWith('.pdf')

/**
 * What goes up: photos with their long edge at 2000 px (small print stays readable at a few hundred KB a page), and a
 * PDF as its pages drawn here the same way - a scanned PDF can be tens of MB, more than the server takes in one
 * request, while its pages are not. A PDF the browser cannot draw goes as it is: the server draws it then.
 */
async function pagesToSend(files: File[], limit: number): Promise<File[]> {
  const out: File[] = []
  for (const file of files) {
    if (!isPdf(file)) {
      out.push(await shrinkImage(file, 2000, 0.85))
      continue
    }
    try {
      const drawn: File[] = []
      for await (const page of pdfPageImages(file)) {
        drawn.push(page.file)
        if (out.length + drawn.length > limit) break // already too many: no point drawing the rest
      }
      out.push(...drawn)
    } catch {
      out.push(file)
    }
  }
  return out
}

const STATUS_LABEL: Record<AiImport['status'], string> = {
  QUEUED: 'În așteptare',
  RUNNING: 'Se citește',
  DONE: 'Citit',
  FAILED: 'Eșuat',
}

/** "12 octombrie 2026": the default title names the day the pages were photographed. */
const DAY = new Intl.DateTimeFormat('ro-RO', { day: 'numeric', month: 'long', year: 'numeric', timeZone: 'Europe/Bucharest' })

const crumbs = (last: string) => [{ label: 'Teste', to: '/admin/quizzes' }, { label: last }]

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
  const [enChapters, setEnChapters] = useState<EnChapterSummary[]>([])

  useEffect(() => {
    listClasses().then(setClasses).catch(() => undefined)
    listChapterOptions().then(setChapters).catch(() => undefined)
    listEnChapters().then(setEnChapters).catch(() => undefined) // only the EN chapter choice is missing without it
  }, [])

  return (
    <div className="p-4">
      <div className="mx-auto max-w-3xl space-y-4">
        {importId === null
          ? <UploadView classes={classes} chapters={chapters} />
          : <ImportView key={importId} id={importId} classes={classes} chapters={chapters} enChapters={enChapters} />}
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

  const previews = useMemo(() => pages.map((p) => (isPdf(p) ? '' : URL.createObjectURL(p))), [pages])
  useEffect(() => () => previews.forEach((url) => url && URL.revokeObjectURL(url)), [previews])
  const hasPdf = pages.some(isPdf)

  const maxPages = settings?.maxPages ?? 12
  const leftToday = settings ? Math.max(0, settings.dailyPageLimit - settings.pagesUsedToday) : 0
  const chapterChoices = classId === null ? chapters : chapters.filter((c) => c.schoolClassId === classId)

  function add(files: FileList | File[] | null) {
    if (!files) return
    // Photos, and scanned PDFs as they are: the server turns each PDF page into an image (and counts those pages).
    const images = Array.from(files).filter((f) => f.type.startsWith('image/') || isPdf(f))
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
      const shrunk = await pagesToSend(pages, maxPages)
      if (shrunk.length > maxPages) {
        setError(`PDF-ul are mai multe pagini decât se pot trimite odată (cel mult ${maxPages}).`)
        setBusy(false)
        return
      }
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
        description={`Fotografiază până la ${maxPages} pagini din manual sau culegere, sau încarcă un PDF, de exemplu un subiect EN întreg. Le citim, verifici fiecare exercițiu, marchezi răspunsurile corecte și primești un test ciornă.`}
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
                      disabled={pages.length >= maxPages}>Alege poze sau PDF</Button>
              <span className="self-center text-xs text-muted-foreground">sau lipește (Ctrl+V) o captură</span>
            </div>
            <input ref={camera} type="file" accept="image/*" capture="environment" className="hidden"
                   data-testid="ai-camera" onChange={(e) => { add(e.target.files); e.target.value = '' }} />
            <input ref={gallery} type="file" accept="image/*,application/pdf,.pdf" multiple className="hidden"
                   data-testid="ai-gallery" onChange={(e) => { add(e.target.files); e.target.value = '' }} />
            {pages.length > 0 && (
              <ul className="flex flex-wrap gap-3" aria-label="Paginile alese">
                {pages.map((page, i) => (
                  <li key={`${i}-${page.name}`} className="space-y-1">
                    {isPdf(page) ? (
                      <div className="flex h-32 w-24 flex-col items-center justify-center gap-1 rounded-lg border bg-muted p-2 text-center">
                        <span aria-hidden="true" className="text-2xl">📄</span>
                        <span className="line-clamp-3 text-xs break-all">{page.name}</span>
                      </div>
                    ) : (
                      <img src={previews[i]} alt={`Pagina ${i + 1}`} className="h-32 rounded-lg border object-contain" />
                    )}
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
              {busy ? 'Se trimite...' : hasPdf
                ? `Citește ${roCount(pages.length, 'fișier', 'fișiere')}`
                : `Citește ${roCount(pages.length, 'pagină', 'pagini')}`}
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

function ImportView({ id, classes, chapters, enChapters }: {
  id: number
  classes: SchoolClass[]
  chapters: ChapterOption[]
  enChapters: EnChapterSummary[]
}) {
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
      <PageHeader title="Test din poze" breadcrumbs={[{ label: 'Teste', to: '/admin/quizzes' },
        { label: 'Din poze', to: '/admin/quizzes/ai-import' }, { label: 'Verificare' }]} />
      {confirmDialog}
      {error && <p role="alert" className="text-sm text-destructive">{error}</p>}
      {ai === null && !error && <LoadingCards />}
      {ai !== null && waiting && (
        <Card>
          <CardContent className="space-y-2 pt-4" role="status">
            <p className="font-medium">{ai.pageCount === 1 ? 'Se citește pagina' : `Se citesc ${roCount(ai.pageCount, 'pagină', 'pagini')}`}...</p>
            <p className="text-sm text-muted-foreground">De obicei durează 1–2 minute (un test întreg de 4–5 pagini, spre 2 minute). Poți lăsa pagina deschisă.</p>
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
      {ai?.status === 'DONE' && ai.quizId === null && ai.reusedFrom != null && (
        <div data-testid="ai-reused"
             className="flex flex-wrap items-center justify-between gap-2 rounded-lg border border-emerald-500/40 bg-emerald-500/10 p-3 text-sm">
          <p>Ai mai trimis exact aceste pagini: am refolosit citirea de atunci, fără cost AI.</p>
          {ai.pagesAvailable && (
            <Button size="sm" variant="outline" onClick={() => void retry()} disabled={busy}>Citește din nou cu AI</Button>
          )}
        </div>
      )}
      {ai?.status === 'DONE' && ai.quizId === null && ai.items && (
        <Review ai={ai} classes={classes} chapters={chapters} enChapters={enChapters} onDiscard={() => void discard()} />
      )}
    </div>
  )
}

function Review({ ai, classes, chapters, enChapters, onDiscard }: {
  ai: AiImport
  classes: SchoolClass[]
  chapters: ChapterOption[]
  enChapters: EnChapterSummary[]
  onDiscard: () => void
}) {
  const navigate = useNavigate()
  const [title, setTitle] = useState(`Test din manual — ${DAY.format(new Date(ai.createdAt))}`)
  const [classId, setClassId] = useState<number | null>(ai.schoolClassId)
  const [chapterId, setChapterId] = useState<number | null>(ai.chapterId)
  const [drafts, setDrafts] = useState<DraftItem[]>(() => toDrafts(ai.items ?? []))
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const [assign, setAssign] = useState(false)
  const [due, setDue] = useState(() => defaultHomeworkDue(Date.now()))
  // "Barem din poză (AI)" on Subiectul III problems: only when the server reads barems; the barem pages chosen once
  // stay here for every problem of the review (they are never uploaded whole, only the framed part).
  const [baremOn, setBaremOn] = useState(false)
  const [baremPhotos, setBaremPhotos] = useState<File[]>([])
  useEffect(() => {
    let stopped = false
    getAiGradingSettings().then((s) => { if (!stopped) setBaremOn(s.enabled) }).catch(() => {})
    return () => { stopped = true }
  }, [])

  const problems = draftProblems(drafts)
  const kept = drafts.filter((d) => d.include).length
  // Publishing (and so homework) waits for every grilă's answer: the barem can still give it after the test is made.
  const unanswered = drafts.filter((d) => d.include && d.type === 'SINGLE_CHOICE'
    && (d.correct === null || (d.options[d.correct] ?? '').trim() === '')).length
  const homeworkClass = classes.find((c) => c.id === classId) ?? null
  const canAssign = homeworkClass !== null && unanswered === 0
  const assigning = assign && canAssign
  const places = draftPlaces(drafts)
  const chapterChoices = classId === null ? chapters : chapters.filter((c) => c.schoolClassId === classId)
  const update = (key: number, change: Partial<DraftItem>) =>
    setDrafts((current) => current.map((d) => (d.key === key ? { ...d, ...change } : d)))

  async function create() {
    setError(null)
    if (assigning && Date.parse(localInputToIso(due)) <= Date.now()) {
      setError('Termenul trebuie să fie în viitor.')
      return
    }
    setBusy(true)
    let quizId: number
    try {
      quizId = await createQuizFromAiImport(ai.id, {
        title: title.trim(), schoolClassId: classId, chapterId, items: toItemInputs(drafts, enChapters),
        figures: toFigureInputs(drafts),
      })
    } catch (e) {
      setError(errorMessage(e))
      setBusy(false)
      return
    }
    if (!assigning) {
      navigate(`/admin/quizzes/${quizId}`)
      return
    }
    // The test exists by now: whatever the homework's fate, the teacher goes on in its editor (not here, where
    // trying again would make a second test).
    try {
      const assigned = await publishAndAssign(quizId, { schoolClassId: homeworkClass!.id, dueAt: localInputToIso(due) })
      navigate(`/admin/quizzes/${quizId}`, { state: { notice: {
        text: `„${title.trim()}” e publicat și dat ca temă clasei ${assigned.schoolClassName}, termen ${formatDate(assigned.dueAt)}.`,
        homework: true,
      } } })
    } catch (e) {
      navigate(`/admin/quizzes/${quizId}`, { state: { notice: {
        text: `Testul e creat, dar nu l-am dat ca temă: ${errorMessage(e)}`, homework: false, warn: true,
      } } })
    }
  }

  return (
    <div className="space-y-4">
      <p className="text-sm text-muted-foreground">
        Am găsit {roCount(drafts.length, 'exercițiu', 'exerciții')}. Verifică textul cu poza alături și debifează ce nu
        vrei în test. Răspunsul corect al grilelor îl poți bifa acum sau îl pune baremul după ce creezi testul („Barem din
        poze” în pagina testului).
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

      {subjectGroups(drafts).map((group) => (
        <section key={group.drafts[0].key} className="space-y-3">
          {group.title && (
            <h2 className="text-lg font-semibold">
              {group.title}
              <span className="ml-2 text-sm font-normal text-muted-foreground">
                · {roCount(group.drafts.filter((d) => d.include).length, 'exercițiu', 'exerciții')}
              </span>
            </h2>
          )}
          <ol className="space-y-3">
            {group.drafts.map((d) => (
              <li key={d.key}>
                <DraftCard draft={d} place={places.get(d.key) ?? null} enChapters={enChapters}
                           onChange={(change) => update(d.key, change)}
                           pageUrl={ai.pagesAvailable ? (page) => aiImportPageUrl(ai.id, page) : null}
                           barem={baremOn ? { photos: baremPhotos, onPhotos: setBaremPhotos } : null} />
              </li>
            ))}
          </ol>
        </section>
      ))}

      {problems.length > 0 && (
        <ul className="list-disc space-y-1 pl-5 text-sm text-amber-700 dark:text-amber-400" data-testid="ai-problems">
          {problems.map((p) => <li key={p}>{p}</li>)}
        </ul>
      )}
      <div className="space-y-2 rounded-lg border p-3" data-testid="ai-homework">
        <label className="flex items-center gap-2 text-sm font-medium">
          <input type="checkbox" checked={assigning} disabled={!canAssign} onChange={(e) => setAssign(e.target.checked)} />
          Dă-l și ca temă{homeworkClass ? ` clasei ${homeworkClass.name}` : ''}
        </label>
        {homeworkClass === null ? (
          <p className="text-xs text-muted-foreground">Alege clasa testului (sus) ca să-l poți da direct ca temă.</p>
        ) : unanswered > 0 ? (
          <p className="text-xs text-muted-foreground">
            {roCount(unanswered, 'grilă fără răspuns', 'grile fără răspuns')}: marchează varianta corectă ca să-l dai
            direct ca temă, sau creează testul și pune baremul din poze.
          </p>
        ) : null}
        {assigning && (
          <div className="flex flex-col gap-1 sm:w-64">
            <Label htmlFor="ai-due">Termen</Label>
            <Input id="ai-due" type="datetime-local" value={due} onChange={(e) => setDue(e.target.value)} />
          </div>
        )}
      </div>

      {error && <p role="alert" className="text-sm text-destructive">{error}</p>}
      <div className="flex flex-wrap gap-2">
        <Button onClick={() => void create()}
                disabled={busy || kept === 0 || problems.length > 0 || title.trim() === '' || (assigning && due === '')}>
          {busy ? 'Se creează...'
            : `${assigning ? 'Creează și dă ca temă' : 'Creează testul'} (${roCount(kept, 'întrebare', 'întrebări')})`}
        </Button>
        <Button variant="outline" onClick={onDiscard} disabled={busy}>Renunță</Button>
      </div>
    </div>
  )
}

/** The questions in runs of the same subject (the section's first line), as they follow on the paper. */
function subjectGroups(drafts: DraftItem[]): { title: string; drafts: DraftItem[] }[] {
  const groups: { title: string; drafts: DraftItem[] }[] = []
  for (const d of drafts) {
    const title = d.section.split('\n')[0].trim()
    const last = groups[groups.length - 1]
    if (last && last.title === title) last.drafts.push(d)
    else groups.push({ title, drafts: [d] })
  }
  return groups
}

/** What the read view cannot show sensibly: the form opens by itself for these. */
const needsForm = (d: DraftItem) => d.statement.trim() === ''
  || (d.type === 'SINGLE_CHOICE' && d.options.filter((o) => o.trim() !== '').length < 2)
  || (d.type === 'OPEN' && d.parts.some((p) => p.statement.trim() === ''))

/** The AI's reason for a proposed chapter, shown under the choice while the proposal is still the one chosen. */
function ProposalReason({ reason, shown }: { reason: string | null; shown: boolean }) {
  if (!reason || !shown) return null
  return <p className="text-xs text-muted-foreground">Propus de AI: {reason}</p>
}

function DraftCard({ draft: d, place, enChapters, onChange, pageUrl, barem = null }: {
  draft: DraftItem
  /** "SUBIECTUL II · Exercițiul 3": where it sits on the paper and in the test; null when left out. */
  place: string | null
  enChapters: EnChapterSummary[]
  onChange: (change: Partial<DraftItem>) => void
  /** The page photos, while they are kept (null once gone: then there is nothing to cut from). */
  pageUrl: ((page: number) => string) | null
  /** "Barem din poză (AI)": the barem pages shared by the review; null when the server does not read barems. */
  barem?: { photos: File[]; onPhotos: (photos: File[]) => void } | null
}) {
  const [editingFigure, setEditingFigure] = useState(false)
  // Read first: the form is there for the few questions the AI got wrong.
  const [editing, setEditing] = useState(() => needsForm(d))
  const enChapter = d.enChapter === '' ? null : enChapters.find((c) => c.code === d.enChapter) ?? null
  // What the AI proposed, to keep showing its reason only while the teacher has not picked something else.
  const [proposed] = useState(() => ({ item: d.enChapter, parts: d.parts.map((p) => p.enChapter) }))
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
        {/* Which exercise of the paper this is, so it can be checked against the photo at a glance. */}
        <div className="flex flex-wrap items-center gap-3">
          {/* The subject is the group's heading above; here only the exercise's number in it. */}
          <h3 data-testid="ai-draft-place" className="mr-auto text-base font-semibold">
            {place ? place.split(' · ').pop() : 'Nu intră în test'}
          </h3>
          <span className="text-xs text-muted-foreground">pagina {d.page}</span>
          <label className="flex items-center gap-2 text-sm font-medium">
            <input type="checkbox" checked={d.include} onChange={(e) => onChange({ include: e.target.checked })} />
            Îl includ
          </label>
          {d.include && (
            <Button type="button" variant={editing ? 'default' : 'outline'} size="xs" onClick={() => setEditing(!editing)}
                    disabled={editing && needsForm(d)}>
              {editing ? 'Gata' : 'Editează'}
            </Button>
          )}
        </div>

        {d.include && (
          <>
            {d.warnings.length > 0 && (
              <ul className="space-y-1 text-sm text-amber-700 dark:text-amber-400">
                {d.warnings.map((w) => <li key={w}>⚠ {w}</li>)}
              </ul>
            )}
            {!editing && (
              <div className="space-y-3">
                <MathContent className="text-sm">{d.statement}</MathContent>
                {d.type === 'SINGLE_CHOICE' && (
                  <div className="space-y-1">
                    <p className="text-xs text-muted-foreground">
                      Apasă pe varianta corectă (sau las-o: o pune baremul după ce creezi testul).
                    </p>
                    <div className="grid gap-2 sm:grid-cols-2">
                      {d.options.map((option, i) => option.trim() !== '' && (
                        <button key={i} type="button" aria-pressed={d.correct === i}
                                aria-label={`Varianta ${optionLetter(i)}) e corectă`}
                                onClick={() => onChange({ correct: d.correct === i ? null : i })}
                                className={`flex min-h-11 items-center gap-2 rounded-md border px-3 py-2 text-left text-sm ${
                                  d.correct === i
                                    ? 'border-green-600 bg-green-50 font-medium dark:bg-green-950/40'
                                    : 'hover:bg-muted/60'}`}>
                          <span className="font-semibold">{optionLetter(i)})</span>
                          <MathContent inline>{option}</MathContent>
                          {d.correct === i && <span className="ml-auto text-green-700 dark:text-green-400">✓</span>}
                        </button>
                      ))}
                    </div>
                  </div>
                )}
                {d.type === 'OPEN' && d.parts.length > 0 && (
                  <ol className="space-y-1 text-sm" data-testid="ai-parts">
                    {d.parts.map((part, i) => (
                      <li key={i} className="flex gap-2">
                        <span className="font-semibold">{String.fromCharCode(97 + i)})</span>
                        <MathContent className="flex-1">{part.statement}</MathContent>
                        <span className="shrink-0 text-muted-foreground">{part.points} p</span>
                      </li>
                    ))}
                  </ol>
                )}
                <p className="text-xs text-muted-foreground">
                  {d.type === 'SINGLE_CHOICE' ? 'Grilă' : 'Rezolvare completă'} · {draftPoints(d)} p
                  {enChapter && <> · Capitol EN {enChapter.code} {enChapter.title}</>}
                  {(d.solution.trim() !== '' || d.parts.some((p) => p.solution.trim() !== '')) && <> · are barem</>}
                </p>
              </div>
            )}
            {editing && (<>
            <div className="flex flex-wrap items-center gap-3">
              <select aria-label="Tipul" className={selectClass} value={d.type}
                      onChange={(e) => setType(e.target.value as QuizItemType)}>
                <option value="SINGLE_CHOICE">Grilă</option>
                <option value="OPEN">Rezolvare completă</option>
              </select>
              <label className="flex items-center gap-2 text-sm">
                Puncte
                <Input type="number" min={0} max={100} className="w-20" value={draftPoints(d)}
                       disabled={d.parts.length > 0}
                       onChange={(e) => onChange({ points: e.target.value === '' ? 0 : Number(e.target.value) })} />
              </label>
            </div>
            <div className="flex flex-col gap-2">
              <Label htmlFor={`${id}-section`}>Secțiunea</Label>
              {/* Two lines: the heading, then its instruction ("Încercuiește litera…"), as the quiz builder shows them. */}
              <Textarea id={`${id}-section`} rows={2} value={d.section} maxLength={300} placeholder="ex. SUBIECTUL I"
                        onChange={(e) => onChange({ section: e.target.value })} />
            </div>
            {enChapters.length > 0 && (
              <div className="flex flex-col gap-1">
                <Label htmlFor={`${id}-en`}>Capitol EN (unde ajunge elevul dacă greșește)</Label>
                <EnChapterSelect id={`${id}-en`} by="code" value={d.enChapter} chapters={enChapters}
                                 onChange={(code) => onChange({ enChapter: code })} />
                <ProposalReason reason={d.enChapterReason} shown={d.enChapter !== '' && d.enChapter === proposed.item} />
              </div>
            )}
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
                    <input type="radio" name={`${id}-correct`} checked={d.correct === i} aria-label={`Varianta ${optionLetter(i)}) e corectă`}
                           onChange={() => onChange({ correct: i })} />
                    <span aria-hidden="true" className="w-4 shrink-0 text-sm font-semibold">{optionLetter(i)})</span>
                    <Input value={option} aria-label={`Varianta ${optionLetter(i)})`} onChange={(e) => setOption(i, e.target.value)} />
                    <MathContent inline className="min-w-16 text-sm">{option}</MathContent>
                    <Button type="button" variant="ghost" size="xs" onClick={() => removeOption(i)}
                            disabled={d.options.length <= 2} aria-label={`Șterge varianta ${optionLetter(i)})`}>✕</Button>
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
                  const setPart = (change: Partial<DraftPart>) =>
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
                      <div className="ml-7 space-y-1">
                        <Textarea rows={2} value={part.solution} aria-label={`Barem cerința ${letter})`}
                                  placeholder={`Barem ${letter}) (opțional)`}
                                  onChange={(e) => setPart({ solution: e.target.value })} />
                        {part.solution.trim() !== '' && (
                          <MathContent className="whitespace-pre-line rounded-md bg-muted/40 p-2 text-sm">{part.solution}</MathContent>
                        )}
                      </div>
                      {enChapters.length > 0 && (
                        <div className="ml-7 space-y-1">
                          <EnChapterSelect id={`${id}-part-${i}-en`} label={`Capitol EN cerința ${letter})`} by="code"
                                           value={part.enChapter} chapters={enChapters}
                                           onChange={(code) => setPart({ enChapter: code })} />
                          <ProposalReason reason={part.enChapterReason}
                                          shown={part.enChapter !== '' && part.enChapter === proposed.parts[i]} />
                        </div>
                      )}
                    </div>
                  )
                })}
              </fieldset>
            )}
            {d.type === 'OPEN' && barem && d.statement.trim() !== '' && (
              <QuestionBaremReader question={toBaremQuestion(d)} photos={barem.photos} onPhotos={barem.onPhotos}
                                   onRead={(read) => onChange(withQuestionBarem(d, read))} />
            )}
            <div className="flex flex-col gap-2">
              <Label htmlFor={`${id}-solution`}>Barem / rezolvare (opțional)</Label>
              <Textarea id={`${id}-solution`} rows={2} value={d.solution} onChange={(e) => onChange({ solution: e.target.value })} />
            </div>
            </>)}
            {d.figure && pageUrl ? (
              <div className="space-y-2" data-testid="ai-figure">
                <p className="text-sm font-medium">Desenul (decupat automat din pagina {d.figure.page})</p>
                {editingFigure ? (
                  <FigureCropEditor src={pageUrl(d.figure.page)} box={boxToPhoto(d.figure.box)}
                                    alt={`Pagina ${d.figure.page} din manual`}
                                    onDone={(box) => { onChange({ figure: { page: d.figure!.page, box: photoToBox(box) } }); setEditingFigure(false) }}
                                    onCancel={() => setEditingFigure(false)} />
                ) : (
                  <>
                    <PhotoCrop src={pageUrl(d.figure.page)} box={boxToPhoto(d.figure.box)} alt={`Desenul întrebării ${d.key + 1}`}
                               className="max-h-72" />
                    <div className="flex flex-wrap gap-2">
                      <Button type="button" variant="outline" size="xs" onClick={() => setEditingFigure(true)}>
                        Ajustează decupajul
                      </Button>
                      <Button type="button" variant="ghost" size="xs" onClick={() => onChange({ figure: null })}>
                        Scoate desenul
                      </Button>
                    </div>
                  </>
                )}
              </div>
            ) : (editing || d.imageRequired) && (
              // Read view: only for a question that has one (so it can be unticked); the form always offers it.
              <label className="flex items-center gap-2 text-sm">
                <input type="checkbox" checked={d.imageRequired} onChange={(e) => onChange({ imageRequired: e.target.checked })} />
                Are desen (îl adaugi ca imagine după ce creezi testul)
              </label>
            )}
          </>
        )}
      </CardContent>
    </Card>
  )
}
