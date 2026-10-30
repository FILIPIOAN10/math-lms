import { Fragment, useEffect, useMemo, useRef, useState, type ClipboardEvent, type FormEvent, type KeyboardEvent } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { Button, buttonVariants } from '@/components/ui/button'
import { Card, CardContent } from '@/components/ui/card'
import { Dialog } from '@/components/ui/dialog'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Textarea } from '@/components/ui/textarea'
import { ImportItemsDialog } from '@/components/ImportItemsDialog'
import { MathContent } from '@/components/MathContent'
import { MathField } from '@/components/MathField'
import { QuestionImage } from '@/components/QuestionImage'
import { PageHeader } from '@/components/PageHeader'
import { RowMenu, type RowMenuItem } from '@/components/RowMenu'
import { TestsTabs } from '@/components/TestsTabs'
import { PublishDialog } from '@/components/PublishDialog'
import { SectionHeadingView } from '@/components/SectionHeadingView'
import { PartsView } from '@/components/PartsView'
import { numberItems } from '@/lib/sections'
import { hasVariantsInStatement } from '@/lib/importItems'
import { useConfirm } from '@/components/useConfirm'
import {
  addQuizItem,
  copyQuiz,
  createQuiz,
  deleteQuiz,
  deleteQuizItem,
  getAiImportSettings,
  getAiGradingSettings,
  getTutorSettings,
  getQuiz,
  listChapterOptions,
  listClasses,
  listQuizSummaries,
  listQuizzes,
  questionImageUrl,
  removeQuestionImage,
  setQuizPublished,
  updateQuiz,
  updateQuizItem,
  uploadQuestionImage,
  type ItemInput,
  type QuizDetail,
  type ChapterOption,
  type QuizItemDto,
  type QuizItemType,
  type QuizListSummary,
  type QuizSummary,
  type QuizInput,
  type SchoolClass,
  listEnChapters,
  type EnChapterSummary,
} from '@/lib/api'
import { EnChapterSelect } from '@/components/EnChapterSelect'
import { EnChapterSuggestionsDialog } from '@/components/EnChapterSuggestionsDialog'
import { TutorHintsPanel } from '@/components/TutorHintsPanel'
import { BaremDialog } from '@/components/BaremDialog'
import { formatDate, optionLetter, roCount } from '@/lib/format'
import { errorMessage } from '@/lib/errors'
import { formatGrade, GRADE_TEXT, gradeBand } from '@/lib/grades'
import { shrinkImage } from '@/lib/image'
import { cn } from '@/lib/utils'
import { LoadingCards } from '@/components/LoadingCards'

/** A requirement a), b)... while it is being edited (points as typed). */
interface PartDraft {
  statement: string
  points: string
  solution: string
  /** '' = automatic */
  enChapterId: string
}

/** Same limit as the server (QuizItemPart.MAX_PARTS). */
const MAX_PARTS = 6

/** What the question dialog does with the picture under the statement. */
export type ImageChange = 'keep' | 'remove' | File

const selectClass =
  'h-9 rounded-lg border border-border bg-background px-2 text-sm outline-none focus-visible:border-ring focus-visible:ring-3 focus-visible:ring-ring/50 disabled:opacity-50'


// ---------- Quiz create/edit dialog ----------

function QuizDialog({
  open,
  onClose,
  initialTitle,
  initialDescription,
  initialClassId,
  initialTimeLimit,
  initialPracticeAllowed,
  initialChapterId = null,
  classes,
  chapters = [],
  onSubmit,
}: {
  open: boolean
  onClose: () => void
  initialTitle: string
  initialDescription: string
  initialClassId: number | null
  initialTimeLimit: number | null
  initialPracticeAllowed: boolean
  initialChapterId?: number | null
  classes: SchoolClass[]
  chapters?: ChapterOption[]
  onSubmit: (input: QuizInput) => Promise<void>
}) {
  const [title, setTitle] = useState(initialTitle)
  const [description, setDescription] = useState(initialDescription)
  const [classId, setClassId] = useState(initialClassId === null ? '' : String(initialClassId))
  const [timeLimit, setTimeLimit] = useState(initialTimeLimit === null ? '' : String(initialTimeLimit))
  const [practiceAllowed, setPracticeAllowed] = useState(initialPracticeAllowed)
  const [chapterId, setChapterId] = useState(initialChapterId === null ? '' : String(initialChapterId))
  // A quiz for one class may only use that class's chapters (the server checks it too).
  const offered = classId === '' ? chapters : chapters.filter((c) => String(c.schoolClassId) === classId)
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
        chapterId: chapterId === '' ? null : Number(chapterId),
      })
      onClose()
    } catch (err) {
      setError(errorMessage(err))
    } finally {
      setBusy(false)
    }
  }

  return (
    <Dialog open={open} onOpenChange={(o) => !o && onClose()} title={initialTitle ? 'Editează testul' : 'Adaugă test'}>
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
          <select
            id="qclass"
            className={selectClass}
            value={classId}
            onChange={(e) => {
              setClassId(e.target.value)
              // a chapter of another class no longer fits
              if (e.target.value !== '' && !chapters.some((c) => String(c.id) === chapterId && String(c.schoolClassId) === e.target.value)) {
                setChapterId('')
              }
            }}
          >
            <option value="">Toți elevii</option>
            {classes.map((c) => (
              <option key={c.id} value={c.id}>{c.name}</option>
            ))}
          </select>
          <p className="text-xs text-muted-foreground">
            „Toți elevii” = orice elev activ îl vede. Altfel, doar elevii înscriși în clasa aleasă.
          </p>
        </div>
        {chapters.length > 0 && (
          <div className="flex flex-col gap-2">
            <Label htmlFor="qchapter">Capitol (pentru harta pe capitole)</Label>
            <ChapterSelect id="qchapter" value={chapterId} onChange={setChapterId} chapters={offered} emptyLabel="— niciunul —" />
            <p className="text-xs text-muted-foreground">
              Întrebările testului contează la acest capitol pe harta elevului, dacă nu le alegi altul fiecăruia.
            </p>
          </div>
        )}
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
            Elevii pot exersa testul fără notă, cu răspunsul corect afișat imediat după fiecare întrebare, fără cronometru
            și fără efect asupra progresului sau statisticilor. Atenție: practica dezvăluie răspunsurile — bifeaz-o
            doar pentru teste care pot servi ca material de exersare.
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

// ---------- Chapter select (E6 mastery map) ----------

/** The chapters as a select grouped by class and book; the empty value means "none" (or "inherit"). */
function ChapterSelect({ id, value, onChange, chapters, emptyLabel }: {
  id: string
  value: string
  onChange: (value: string) => void
  chapters: ChapterOption[]
  emptyLabel: string
}) {
  const groups: { label: string; chapters: ChapterOption[] }[] = []
  for (const chapter of chapters) {
    const label = `${chapter.schoolClassName} · ${chapter.bookTitle}`
    const group = groups.find((g) => g.label === label)
    if (group) group.chapters.push(chapter)
    else groups.push({ label, chapters: [chapter] })
  }
  return (
    <select id={id} className={selectClass} value={value} onChange={(e) => onChange(e.target.value)}>
      <option value="">{emptyLabel}</option>
      {groups.map((g) => (
        <optgroup key={g.label} label={g.label}>
          {g.chapters.map((c) => (
            <option key={c.id} value={c.id}>{c.title}</option>
          ))}
        </optgroup>
      ))}
    </select>
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
  chapters = [],
  enChapters = [],
  quizChapterTitle = null,
  defaultSection = null,
  tutorOn = false,
}: {
  open: boolean
  onClose: () => void
  initial: QuizItemDto | null
  /** The EN chapter pages a question may send a student to; none = no choice shown. */
  enChapters?: EnChapterSummary[]
  /** `image`: a new picture to put under the statement, 'remove' to take it away, 'keep' to leave it as it is. */
  onSubmit: (input: ItemInput, image: ImageChange) => Promise<void>
  /** The chapters this question may be tagged with; none = no chapter choice shown. */
  chapters?: ChapterOption[]
  /** What "inherit" means here, e.g. "Fracții". */
  quizChapterTitle?: string | null
  /** For a new question: the heading of the last one, so a section keeps filling up ("SUBIECTUL al III-lea"). */
  defaultSection?: string | null
  /** The server has the tutor on: a saved question can ask it for hints ("Sugerează indicii (AI)"). */
  tutorOn?: boolean
}) {
  const [type, setType] = useState<QuizItemType>(initial?.type ?? 'SINGLE_CHOICE')
  const [statement, setStatement] = useState(initial?.statement ?? '')
  const [points, setPoints] = useState(String(initial?.points ?? 5))
  const [solution, setSolution] = useState(initial?.solution ?? '')
  const [hints, setHints] = useState<string[]>(initial?.hints ?? [])
  const [chapterId, setChapterId] = useState(initial?.chapterId == null ? '' : String(initial.chapterId))
  const [enChapterId, setEnChapterId] = useState(initial?.enChapterId == null ? '' : String(initial.enChapterId))
  const [section, setSection] = useState(initial ? initial.section ?? '' : defaultSection ?? '')
  const [options, setOptions] = useState<OptionDraft[]>(
    initial && initial.options.length > 0
      ? initial.options.map((o) => ({ text: o.text, correct: o.correct }))
      : emptyOptions(),
  )
  // EN Subiectul III: requirements a), b)... each with its points and barem; the question's points are their sum.
  const [parts, setParts] = useState<PartDraft[]>(
    (initial?.parts ?? []).map((p) => ({
      statement: p.statement, points: String(p.points), solution: p.solution ?? '',
      enChapterId: p.enChapterId == null ? '' : String(p.enChapterId),
    })),
  )
  const partsTotal = parts.reduce((sum, p) => sum + (Number(p.points) || 0), 0)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [saved, setSaved] = useState(0) // items added with "Salvează și adaugă încă unul" while the dialog stayed open
  // The picture under the statement: what is stored (initial), and what this dialog will do with it.
  const [image, setImage] = useState<ImageChange>('keep')
  const imagePicker = useRef<HTMLInputElement>(null)
  const newImageUrl = useMemo(() => (image instanceof File ? URL.createObjectURL(image) : null), [image])
  useEffect(() => () => {
    if (newImageUrl) URL.revokeObjectURL(newImageUrl)
  }, [newImageUrl])
  const shownImage = newImageUrl
    ?? (image === 'keep' && initial?.image ? questionImageUrl(initial.id, initial.image) : null)

  // "Mai multe opțiuni": folded unless the question still waits for its figure.
  const [more, setMore] = useState(Boolean(initial?.imageRequired))
  const moreSet = [
    shownImage ? 'desen' : null,
    section.trim() ? section.split('\n')[0].trim() : null,
    chapterId ? 'capitol' : null,
    enChapterId ? 'capitol EN' : null,
    solution.trim() ? (type === 'OPEN' ? 'barem' : 'rezolvare') : null,
    hints.some((h) => h.trim()) ? roCount(hints.filter((h) => h.trim()).length, 'indiciu', 'indicii') : null,
  ].filter((x): x is string => x !== null)

  function pickImage(file: File | undefined) {
    if (!file) return
    if (!file.type.startsWith('image/')) {
      setError('Alege o imagine (PNG, JPG, WEBP sau GIF).')
      return
    }
    setError(null)
    setImage(file)
  }

  /** Ctrl+V anywhere in the dialog with a screenshot on the clipboard puts it under the statement; text pastes as usual. */
  function onPaste(e: ClipboardEvent<HTMLFormElement>) {
    const file = Array.from(e.clipboardData.files).find((f) => f.type.startsWith('image/'))
    if (!file) return
    e.preventDefault()
    pickImage(file)
    setMore(true) // show the figure just pasted
  }

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

  function setPart(index: number, change: Partial<PartDraft>) {
    setParts((prev) => prev.map((p, i) => (i === index ? { ...p, ...change } : p)))
  }

  async function save(addAnother: boolean) {
    setError(null)
    const withParts = type === 'OPEN' && parts.length > 0
    if (withParts) {
      if (parts.some((p) => p.statement.trim() === '')) {
        setError('Completează enunțul fiecărei cerințe.')
        return
      }
      if (parts.some((p) => !/^\d+$/.test(p.points.trim()))) {
        setError('Fiecare cerință are nevoie de un punctaj (număr întreg).')
        return
      }
    }
    if (type === 'SINGLE_CHOICE') {
      if (options.some((o) => o.text.trim() === '')) {
        setError('Completează textul fiecărei variante.')
        return
      }
      // None is fine (the barem marks it later; the test is not published before); two is not.
      if (options.filter((o) => o.correct).length > 1) {
        setError('Bifează o singură variantă corectă.')
        return
      }
    }
    const input: ItemInput = {
      type,
      position: initial?.position ?? 0,
      statement: statement.trim(),
      points: withParts ? partsTotal : Number(points) || 0,
      solution: solution.trim() || null,
      options:
        type === 'SINGLE_CHOICE'
          ? options.map((o, i) => ({ position: i, text: o.text.trim(), correct: o.correct }))
          : null,
      hints: hints.map((h) => h.trim()).filter((h) => h !== ''), // an empty box is just dropped
      chapterId: chapterId === '' ? null : Number(chapterId),
      enChapterId: enChapterId === '' ? null : Number(enChapterId),
      section: section.trim() || null,
      parts:
        type === 'OPEN'
          ? parts.map((p) => ({
              statement: p.statement.trim(), points: Number(p.points), solution: p.solution.trim() || null,
              enChapterId: p.enChapterId === '' ? null : Number(p.enChapterId),
            }))
          : null,
    }
    setBusy(true)
    try {
      await onSubmit(input, image)
      if (addAnother) {
        // Same type and points as the item just saved: a test is usually a run of similar items.
        setStatement('')
        setSolution('')
        setHints([])
        setParts([])
        setEnChapterId('')
        setOptions(emptyOptions())
        setImage('keep')
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
      title={isEdit ? 'Editează întrebarea' : 'Adaugă întrebare'}
      description="Enunțul, variantele și baremul pot conține LaTeX între $…$."
    >
      <form className="flex max-h-[75vh] flex-col gap-4 overflow-y-auto pr-1" onSubmit={submit} onPaste={onPaste}>
        {!isEdit && (
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
              <option value="OPEN">Cu rezolvare (elevul trimite poza)</option>
            </select>
          </div>

        )}

        <div className="flex flex-col gap-2">
          <Label htmlFor="istatement">Enunț</Label>
          {/* Typeset as the student sees it; a click makes it editable (with the typeset preview under the box). */}
          <MathField id="istatement" label="Enunț" multiline livePreview value={statement} onChange={setStatement} />
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
                  aria-label={`Varianta ${optionLetter(i)}) corectă`}
                />
                <span aria-hidden="true" className="w-4 shrink-0 text-sm font-semibold">{optionLetter(i)})</span>
                <MathField
                  id={`ioption-${i}`}
                  label={`Varianta ${optionLetter(i)})`}
                  value={o.text}
                  onChange={(text) => setOptionText(i, text)}
                  onKeyDown={(e) => onOptionEnter(e, i)}
                  placeholder={`Varianta ${optionLetter(i)})`}
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

        {type === 'OPEN' && (
          <div className="flex flex-col gap-2" data-testid="parts-editor">
            <Label>Cerințe a), b)… (opțional, ca la Subiectul III)</Label>
            {parts.map((part, i) => {
              const label = String.fromCharCode(97 + i)
              return (
                <div key={i} className="space-y-2 rounded-lg border p-2">
                  <div className="flex items-start gap-2">
                    <span className="mt-2 w-5 shrink-0 text-sm font-semibold">{label})</span>
                    <Textarea
                      value={part.statement}
                      onChange={(e) => setPart(i, { statement: e.target.value })}
                      placeholder={i === 0 ? 'Arată că $x = 25$.' : 'Calculează …'}
                      aria-label={`Cerința ${label})`}
                      className="min-h-14"
                    />
                    <Input
                      type="number"
                      min={0}
                      value={part.points}
                      onChange={(e) => setPart(i, { points: e.target.value })}
                      aria-label={`Puncte cerința ${label})`}
                      className="w-16 shrink-0"
                    />
                    <span className="mt-2 text-sm text-muted-foreground">p</span>
                    <Button
                      type="button"
                      variant="ghost"
                      size="icon-sm"
                      onClick={() => setParts((prev) => prev.filter((_, j) => j !== i))}
                      aria-label={`Șterge cerința ${label})`}
                    >
                      ×
                    </Button>
                  </div>
                  <Textarea
                    value={part.solution}
                    onChange={(e) => setPart(i, { solution: e.target.value })}
                    placeholder={`Barem ${label}) (opțional)`}
                    aria-label={`Barem cerința ${label})`}
                    className="ml-7 min-h-12 w-[calc(100%-1.75rem)]"
                  />
                  {more && enChapters.length > 0 && (
                    <div className="ml-7">
                      <EnChapterSelect
                        id={`ienchapter-part-${i}`}
                        label={`Capitol EN cerința ${label})`}
                        value={part.enChapterId}
                        onChange={(value) => setPart(i, { enChapterId: value })}
                        chapters={enChapters}
                        autoId={initial?.parts?.[i]?.enChapterAuto}
                      />
                    </div>
                  )}
                </div>
              )
            })}
            <Button
              type="button"
              variant="outline"
              size="sm"
              className="self-start"
              disabled={parts.length >= MAX_PARTS}
              onClick={() => setParts((prev) => [...prev, { statement: '', points: prev.length === 0 ? '2' : '3', solution: '', enChapterId: '' }])}
            >
              Adaugă cerința {String.fromCharCode(97 + parts.length)})
            </Button>
            <p className="text-xs text-muted-foreground">
              Fiecare cerință se notează separat la corectură; elevul trimite o singură rezolvare (poze) pentru toată întrebarea.
            </p>
          </div>
        )}

        <div className="flex flex-col gap-2">
          <Label htmlFor="ipoints">Punctaj</Label>
          <Input
            id="ipoints"
            type="number"
            min={0}
            value={type === 'OPEN' && parts.length > 0 ? String(partsTotal) : points}
            onChange={(e) => setPoints(e.target.value)}
            disabled={type === 'OPEN' && parts.length > 0}
            className="w-28"
          />
          {type === 'OPEN' && parts.length > 0 && (
            <p className="text-xs text-muted-foreground">Suma cerințelor de mai jos.</p>
          )}
        </div>

        {/* What most questions never need sits folded below: the folded line says what is already set. */}
        <details data-testid="item-more" open={more} onToggle={(e) => setMore(e.currentTarget.open)}
                 className="rounded-lg border px-3 py-2">
          <summary className="cursor-pointer text-sm font-medium">
            Mai multe opțiuni
            {moreSet.length > 0 && (
              <span data-testid="item-more-summary" className="ml-2 font-normal text-muted-foreground">({moreSet.join(' · ')})</span>
            )}
          </summary>
          <div className="mt-3 flex flex-col gap-4">
            <div className="flex flex-col gap-2" data-testid="image-field">
              <Label>Desen sau figură (opțional)</Label>
              {shownImage ? (
                <img src={shownImage} alt="Figura întrebării" className="max-h-56 w-fit rounded-lg border bg-white object-contain" />
              ) : (
                <p className="rounded-lg border border-dashed px-3 py-4 text-center text-sm text-muted-foreground">
                  Doar dacă enunțul are un desen, un grafic sau un tabel (ex. un triunghi, o dreaptă cu puncte). Fă o captură
                  (Win+Shift+S) și apasă Ctrl+V oriunde aici, sau alege un fișier.
                  {initial?.imageRequired && <span className="block font-medium text-amber-700 dark:text-amber-300">Întrebarea așteaptă o figură.</span>}
                </p>
              )}
              <div className="flex flex-wrap gap-2">
                <Button type="button" size="sm" variant="outline" onClick={() => imagePicker.current?.click()}>
                  {shownImage ? 'Înlocuiește imaginea' : 'Alege imaginea'}
                </Button>
                {shownImage && (
                  <Button type="button" size="sm" variant="outline" className="text-destructive"
                          onClick={() => setImage(initial?.image ? 'remove' : 'keep')}>
                    Scoate imaginea
                  </Button>
                )}
              </div>
              <input
                ref={imagePicker}
                type="file"
                accept="image/png,image/jpeg,image/webp,image/gif"
                className="hidden"
                aria-label="Fișierul imaginii"
                onChange={(e) => {
                  pickImage(e.target.files?.[0])
                  e.target.value = '' // the same file again still counts as a choice
                }}
              />
            </div>

            <div className="flex flex-col gap-2">
              <Label htmlFor="isection">Secțiune (opțional)</Label>
              <Textarea id="isection" rows={2} maxLength={300} value={section} onChange={(e) => setSection(e.target.value)}
                        placeholder={'SUBIECTUL I\nÎncercuiește litera corespunzătoare răspunsului corect.'} className="min-h-0" />
              <p className="text-xs text-muted-foreground">
                Ca la Evaluarea Națională: întrebările cu aceeași secțiune apar grupate sub titlu (primul rând) și numerotate de la 1.
                Gol = fără secțiune.
              </p>
            </div>

            {chapters.length > 0 && (
              <div className="flex flex-col gap-2">
                <Label htmlFor="ichapter">Capitol</Label>
                <ChapterSelect
                  id="ichapter"
                  value={chapterId}
                  onChange={setChapterId}
                  chapters={chapters}
                  emptyLabel={quizChapterTitle ? `Ca la test (${quizChapterTitle})` : 'Ca la test (niciunul)'}
                />
              </div>
            )}
            {enChapters.length > 0 && (
              <div className="flex flex-col gap-2">
                <Label htmlFor="ienchapter">Capitol EN (unde ajunge elevul dacă greșește)</Label>
                <EnChapterSelect id="ienchapter" value={enChapterId} onChange={setEnChapterId} chapters={enChapters}
                                 autoId={initial?.enChapterAuto} />
              </div>
            )}
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
              {tutorOn && initial && (
                <TutorHintsPanel
                  itemId={initial.id}
                  hintsLeft={MAX_HINTS - hints.length}
                  canUseAsSolution={type !== 'OPEN' && solution.trim() === ''}
                  onAddHint={(text) => setHints((prev) => (prev.length < MAX_HINTS ? [...prev, text] : prev))}
                  onUseAsSolution={setSolution}
                />
              )}
            </div>
          </div>
        </details>

        {saved > 0 && (
          <p role="status" className="text-sm text-emerald-700 dark:text-emerald-300">
            ✓ {roCount(saved, 'întrebare salvată', 'întrebări salvate')}. Continuă cu următorul sau închide.
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

/** The confirmation after "Publică": what happened, and the way to the homework when one was given. */
function PublishedNotice({ notice, onClose }: { notice: { text: string; homework: boolean }; onClose: () => void }) {
  return (
    <div role="status" className="flex flex-wrap items-center justify-between gap-2 rounded-lg border border-emerald-500/40 bg-emerald-500/10 px-3 py-2 text-sm text-emerald-800 dark:text-emerald-200">
      <span>✓ {notice.text}</span>
      <span className="flex items-center gap-3">
        {notice.homework && <Link to="/admin/assignments" className="font-medium underline-offset-4 hover:underline">Vezi tema</Link>}
        <button type="button" onClick={onClose} aria-label="Închide mesajul" className="text-lg leading-none">×</button>
      </span>
    </div>
  )
}

const statusBadge = (status: QuizSummary['status']) =>
  `rounded-md px-1.5 py-0.5 text-xs font-medium whitespace-nowrap ${
    status === 'PUBLISHED'
      ? 'bg-emerald-500/15 text-emerald-700 dark:text-emerald-300'
      : 'bg-amber-500/15 text-amber-700 dark:text-amber-300'
  }`

/**
 * The teacher's quizzes. The list (`/admin/quizzes`) shows each quiz with ONE main action - "Dă ca temă" once it is
 * published, "Publică" while it is a draft - and the rest in a "⋯" menu. The builder has its own address
 * (`/admin/quizzes/:quizId`), so Back, refresh and a bookmark all land on the same quiz.
 */
export function AdminQuizzesPage() {
  const { quizId: quizIdParam } = useParams<{ quizId?: string }>()
  const quizId = quizIdParam === undefined ? null : Number(quizIdParam)
  const navigate = useNavigate()
  const { confirm, dialog: confirmDialog } = useConfirm()

  const [quizzes, setQuizzes] = useState<QuizSummary[]>([])
  // The numbers on each row; a row simply shows none if they could not be loaded.
  const [summaries, setSummaries] = useState<Record<number, QuizListSummary>>({})
  const [classes, setClasses] = useState<SchoolClass[]>([])
  const [chapters, setChapters] = useState<ChapterOption[]>([])
  const [enChapters, setEnChapters] = useState<EnChapterSummary[]>([])
  const [loadedQuiz, setQuiz] = useState<QuizDetail | null>(null)
  const [loadError, setLoadError] = useState<{ id: number; message: string } | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [quizDialog, setQuizDialog] = useState<{ item: QuizSummary | null } | null>(null)
  const [itemDialog, setItemDialog] = useState<{ item: QuizItemDto | null } | null>(null)
  const [importing, setImporting] = useState(false)
  // "Sugerează capitolele (AI)": EN chapter proposals for this test's questions (stage 3).
  const [suggesting, setSuggesting] = useState(false)
  // "Publică" opens a dialog that can give the quiz as homework in the same step (T3).
  const [publishing, setPublishing] = useState<{ id: number; title: string; classId: number | null } | null>(null)
  // What just happened, said in words: "published and given as homework to Clasa a 8-a, due …".
  const [notice, setNotice] = useState<{ text: string; homework: boolean } | null>(null)
  // Faza 3: the "Din poze (AI)" button only where the server has the reader switched on.
  const [aiImportOn, setAiImportOn] = useState(false)
  // "Barem din poze (AI)": only where the server has the barem reader (and the AI grading) switched on.
  const [aiGradingOn, setAiGradingOn] = useState(false)
  const [readingBarem, setReadingBarem] = useState(false)
  // "Sugerează indicii (AI)" in the question editor: only where the server has the tutor switched on.
  const [tutorOn, setTutorOn] = useState(false)

  const loadSummaries = () =>
    listQuizSummaries()
      .then((list) => setSummaries(Object.fromEntries(list.map((row) => [row.quizId, row]))))
      .catch(() => undefined)

  useEffect(() => {
    void loadSummaries()
    listQuizzes()
      .then(setQuizzes)
      .catch((e) => setError(errorMessage(e)))
      .finally(() => setLoading(false))
    listClasses()
      .then(setClasses)
      .catch((e) => setError(errorMessage(e)))
    listChapterOptions()
      .then(setChapters)
      .catch(() => undefined) // only the chapter choice is missing without it
    listEnChapters()
      .then(setEnChapters)
      .catch(() => undefined) // only the EN chapter choice is missing without it
    getAiImportSettings()
      .then((s) => setAiImportOn(s.enabled))
      .catch(() => undefined) // the button just stays hidden
    getAiGradingSettings()
      .then((s) => setAiGradingOn(s.enabled))
      .catch(() => undefined) // the button just stays hidden
    getTutorSettings()
      .then((s) => setTutorOn(s.enabled))
      .catch(() => undefined) // the button just stays hidden
  }, [])

  // The builder follows the address. What is shown is derived from it, so a quiz loaded for an earlier address never
  // flashes up while the next one loads.
  useEffect(() => {
    if (quizId === null) return
    let cancelled = false
    getQuiz(quizId)
      .then((q) => !cancelled && setQuiz(q))
      .catch((e) => !cancelled && setLoadError({ id: quizId, message: errorMessage(e) }))
    return () => {
      cancelled = true
    }
  }, [quizId])
  const quiz = loadedQuiz !== null && loadedQuiz.id === quizId ? loadedQuiz : null
  // Grile still waiting for their right answer (read from photos, the barem not applied yet), by their number.
  const unanswered = quiz
    ? numberItems(quiz.items)
      .filter(({ item }) => item.type === 'SINGLE_CHOICE' && !item.options.some((o) => o.correct))
      .map(({ label }) => label)
    : []
  const quizError = loadError !== null && loadError.id === quizId && quiz === null ? loadError.message : null
  // The editor's AI helpers, each only where the server has it on; they need questions to work on.
  const aiTools: RowMenuItem[] = quiz && quiz.items.length > 0 ? [
    ...(aiImportOn && enChapters.length > 0 ? [{ label: 'Sugerează capitolele (AI)', onClick: () => setSuggesting(true) }] : []),
    ...(aiGradingOn ? [{ label: 'Barem din poze (AI)', onClick: () => setReadingBarem(true) }] : []),
  ] : []

  const reloadList = () => Promise.all([listQuizzes().then(setQuizzes), loadSummaries()])
  const chapterTitle = (id: number | null) => (id === null ? null : chapters.find((c) => c.id === id)?.title ?? null)
  /** The EN chapter a question sends students to: the teacher's choice, else the default for its place. */
  const enChapterOf = (item: QuizItemDto) => {
    const id = item.enChapterId ?? item.enChapterAuto ?? null
    return id === null ? null : enChapters.find((c) => c.id === id) ?? null
  }
  // A question of a class quiz may only use that class's chapters.
  const quizClassId = quiz ? quizzes.find((q) => q.id === quiz.id)?.schoolClassId ?? null : null
  const chaptersForQuiz = quizClassId === null ? chapters : chapters.filter((c) => c.schoolClassId === quizClassId)
  const reloadQuiz = (id: number) => getQuiz(id).then(setQuiz)
  const openBuilder = (id: number) => navigate(`/admin/quizzes/${id}`)

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
      openBuilder(copy.id)
    } catch (e) {
      setError(errorMessage(e))
    }
  }

  async function removeQuiz(q: QuizSummary) {
    const yes = await confirm({
      title: `Ștergi testul „${q.title}”?`,
      body: 'Se șterg și întrebările lui, și încercările începute de elevi dar nepredate. Un test predat deja de elevi '
        + '(cu note) nu se poate șterge; unul pe care îl lucrează acum se depublică întâi.',
      confirmLabel: 'Șterge',
      destructive: true,
    })
    if (!yes) return
    setError(null)
    try {
      await deleteQuiz(q.id)
      await reloadList()
    } catch (e) {
      setError(errorMessage(e))
    }
  }

  async function removeItem(item: QuizItemDto, number: string) {
    if (!quiz) return
    const yes = await confirm({ title: `Ștergi întrebarea ${number}?`, confirmLabel: 'Șterge', destructive: true })
    if (!yes) return
    setError(null)
    try {
      await deleteQuizItem(item.id)
      await reloadQuiz(quiz.id)
    } catch (e) {
      setError(errorMessage(e))
    }
  }

  return (
    <div className="p-4">
      <div className="mx-auto max-w-3xl space-y-4">
        {quizId !== null ? (
          /* ----- Builder ----- */
          quizError ? (
            <>
              <PageHeader title="Test" breadcrumbs={[{ label: 'Teste', to: '/admin/quizzes' }, { label: 'Test' }]} />
              <p className="text-sm text-destructive">{quizError}</p>
              <Link to="/admin/quizzes" className={buttonVariants({ variant: 'outline', size: 'sm' })}>
                Înapoi la teste
              </Link>
            </>
          ) : !quiz ? (
            <LoadingCards />
          ) : (
            <div className="space-y-4">
              <PageHeader
                title={quiz.title}
                breadcrumbs={[{ label: 'Teste', to: '/admin/quizzes' }, { label: quiz.title }]}
                description={
                  <span className="flex flex-wrap items-center gap-2">
                    <span className={statusBadge(quiz.status)}>{quiz.status === 'PUBLISHED' ? 'Publicat' : 'Ciornă'}</span>
                    <span data-testid="quiz-total">
                      {roCount(quiz.items.length, 'întrebare', 'întrebări')} · Total: {quiz.items.reduce((sum, item) => sum + item.points, 0)} p
                    </span>
                    <span>· Capitol: {chapterTitle(quiz.chapterId) ?? 'niciunul'}</span>
                  </span>
                }
                actions={
                  <>
                    <Button size="sm" variant="outline"
                            onClick={() => quiz.status === 'PUBLISHED'
                              ? void togglePublish(quiz)
                              : setPublishing({ id: quiz.id, title: quiz.title, classId: quizClassId })}
                            disabled={quiz.status !== 'PUBLISHED' && quiz.items.length === 0}
                            title={quiz.items.length === 0 ? 'Adaugă cel puțin o întrebare înainte să publici' : undefined}>
                      {quiz.status === 'PUBLISHED' ? 'Depublică' : 'Publică'}
                    </Button>
                    <Button size="sm" variant="outline" onClick={() => setImporting(true)}>Importă din text</Button>
                    {aiTools.length > 0 && <RowMenu label="Instrumente AI" text="Instrumente AI" items={aiTools} />}
                    <Button size="sm" onClick={() => setItemDialog({ item: null })}>Adaugă întrebare</Button>
                  </>
                }
              />

              {notice && <PublishedNotice notice={notice} onClose={() => setNotice(null)} />}
              {unanswered.length > 0 && (
                <div data-testid="unanswered-grile"
                     className="flex flex-wrap items-center justify-between gap-2 rounded-lg border border-amber-500/40 bg-amber-500/10 p-3 text-sm">
                  <p>
                    {unanswered.length === 1 ? '1 grilă nu are' : `${unanswered.length} grile nu au`} încă răspunsul corect:{' '}
                    {unanswered.join(', ')}. Testul nu se poate publica până atunci.
                  </p>
                  {aiGradingOn && (
                    <Button size="sm" onClick={() => setReadingBarem(true)}>Barem din poze (AI)</Button>
                  )}
                </div>
              )}
              {error && <p className="text-sm text-destructive">{error}</p>}

              {quiz.items.length === 0 ? (
                <p className="text-muted-foreground">Nicio întrebare încă. Adaug-o pe prima.</p>
              ) : (
                <div className="space-y-2">
                  {numberItems(quiz.items).map(({ item, label, local, heading }) => (
                    <Fragment key={item.id}>
                    {heading && <SectionHeadingView heading={heading} />}
                    <Card>
                      <CardContent className="space-y-2 py-3">
                        <div className="flex items-start justify-between gap-2">
                          <p className="text-sm font-medium">
                            {local}. {item.type === 'SINGLE_CHOICE' ? 'Grilă' : 'Cu rezolvare'} · {item.points} p
                            {item.chapterId !== null && (
                              <span className="ml-2 rounded-md bg-sky-500/15 px-1.5 py-0.5 text-xs font-medium text-sky-800 dark:text-sky-200">
                                {chapterTitle(item.chapterId) ?? 'alt capitol'}
                              </span>
                            )}
                            {enChapterOf(item) && (
                              <span
                                data-testid="item-en-chapter"
                                title={`Dacă greșește, elevul e trimis la ${enChapterOf(item)!.code} ${enChapterOf(item)!.title}`
                                  + (item.enChapterId == null ? ' (automat, după locul din test)' : '')}
                                className="ml-2 rounded-md bg-violet-500/15 px-1.5 py-0.5 text-xs font-medium text-violet-800 dark:text-violet-200"
                              >
                                → {enChapterOf(item)!.code}
                              </span>
                            )}
                          </p>
                          {/* Deleting sits behind ⋯ (with its own confirmation), not as a red button on every card. */}
                          <div className="flex shrink-0 items-center gap-2">
                            <Button size="xs" variant="outline" onClick={() => setItemDialog({ item })} aria-label={`Editează întrebarea ${label}`}>
                              Editează
                            </Button>
                            <RowMenu
                              label={`Mai multe acțiuni pentru întrebarea ${label}`}
                              items={[{ label: 'Șterge întrebarea', destructive: true, onClick: () => void removeItem(item, label) }]}
                            />
                          </div>
                        </div>
                        <MathContent className="text-sm">{item.statement}</MathContent>
                        <QuestionImage itemId={item.id} image={item.image} />
                        {item.type === 'OPEN' && (item.parts ?? []).length === 0 && hasVariantsInStatement(item.statement) && (
                          <p data-testid="variants-in-statement" className="text-sm font-medium text-amber-700 dark:text-amber-300">
                            ⚠ Variantele a), b)… sunt scrise în enunț, așa că elevul nu le poate bifa (primește doar „Fă o poză”).
                            Șterge întrebarea și adaug-o ca grilă, sau import-o din nou cu * la varianta corectă (ex. „*b)”).
                          </p>
                        )}
                        {item.imageRequired && !item.image && (
                          <p data-testid="image-missing" className="text-sm font-medium text-amber-700 dark:text-amber-300">
                            ⚠ Lipsește imaginea — apasă „Editează” și lipește figura (Ctrl+V).
                          </p>
                        )}
                        <PartsView
                          parts={(item.parts ?? []).map((p) => ({ label: p.label, statement: p.statement, points: p.points, barem: p.solution }))}
                        />
                        {item.type === 'SINGLE_CHOICE' && (
                          <ul className="space-y-1 text-sm">
                            {item.options.map((o, i) => (
                              <li key={o.id} className={o.correct ? 'font-medium text-emerald-700 dark:text-emerald-300' : ''}>
                                {o.correct ? '✓ ' : ''}
                                {optionLetter(i)}){' '}
                                <MathContent inline>{o.text}</MathContent>
                              </li>
                            ))}
                          </ul>
                        )}
                        {item.type === 'SINGLE_CHOICE' && !item.options.some((o) => o.correct) && (
                          <p data-testid="no-correct-answer" className="text-sm font-medium text-amber-700 dark:text-amber-300">
                            ⚠ Fără răspuns corect: îl pune baremul sau îl bifezi în „Editează”.
                          </p>
                        )}
                      </CardContent>
                    </Card>
                    </Fragment>
                  ))}
                </div>
              )}
            </div>
          )
        ) : (
          /* ----- List ----- */
          <>
            <PageHeader
              title="Teste"
              description="Creează teste, publică-le și dă-le ca temă. Deschide un test ca să-i adaugi întrebări."
              actions={
                <>
                  {aiImportOn && (
                    <Link to="/admin/quizzes/ai-import" className={buttonVariants({ size: 'sm', variant: 'outline' })}>
                      Din poze (AI)
                    </Link>
                  )}
                  <Button size="sm" onClick={() => setQuizDialog({ item: null })}>Adaugă test</Button>
                </>
              }
            />
            <TestsTabs current="quizzes" />
            {notice && <PublishedNotice notice={notice} onClose={() => setNotice(null)} />}
            {error && <p className="text-sm text-destructive">{error}</p>}
            {loading && <LoadingCards />}
            {!loading && quizzes.length === 0 && <p className="text-muted-foreground">Niciun test. Adaugă primul.</p>}
            <div className="space-y-2">
              {quizzes.map((q) => (
                <Card key={q.id}>
                  <CardContent data-testid="quiz-row" className="flex flex-wrap items-center justify-between gap-3 py-3">
                    <div className="min-w-0 flex-1 basis-56 space-y-0.5">
                      <p className="flex min-w-0 items-center gap-2">
                        <Link to={`/admin/quizzes/${q.id}`} className="truncate font-medium hover:underline">
                          {q.title}
                        </Link>
                        <span className={statusBadge(q.status)}>{q.status === 'PUBLISHED' ? 'Publicat' : 'Ciornă'}</span>
                      </p>
                      <p className="truncate text-xs text-muted-foreground" data-testid="quiz-audience">
                        {q.schoolClassName ? `Clasa: ${q.schoolClassName}` : 'Toți elevii'}
                        {q.timeLimitMinutes !== null && ` · ⏱ ${q.timeLimitMinutes} min`}
                        {q.practiceAllowed && ' · practică permisă'}
                      </p>
                      {q.description && <p className="truncate text-sm text-muted-foreground">{q.description}</p>}
                      {summaries[q.id] && <QuizRowNumbers summary={summaries[q.id]} />}
                    </div>
                    <div className="flex shrink-0 items-center gap-2">
                      {(q.status === 'PUBLISHED' || (summaries[q.id]?.papers ?? 0) > 0) && (
                        <Link
                          to={`/admin/quizzes/${q.id}/stats`}
                          data-testid="quiz-stats"
                          className={buttonVariants({ size: 'sm', variant: 'ghost' })}
                        >
                          Statistici
                        </Link>
                      )}
                      {q.status === 'PUBLISHED' ? (
                        <Link to={`/admin/assignments?quizId=${q.id}`} className={buttonVariants({ size: 'sm', variant: 'outline' })}>
                          Dă ca temă
                        </Link>
                      ) : (
                        <Button size="sm" variant="outline" onClick={() => setPublishing({ id: q.id, title: q.title, classId: q.schoolClassId })}>
                          Publică
                        </Button>
                      )}
                      <RowMenu
                        label={`Mai multe acțiuni pentru ${q.title}`}
                        items={[
                          { label: 'Editează detaliile', onClick: () => setQuizDialog({ item: q }) },
                          { label: 'Copiază', onClick: () => void duplicate(q) },
                          ...(q.status === 'PUBLISHED' ? [{ label: 'Depublică', onClick: () => void togglePublish(q) }] : []),
                          { label: 'Șterge', onClick: () => void removeQuiz(q), destructive: true },
                        ]}
                      />
                    </div>
                  </CardContent>
                </Card>
              ))}
            </div>
          </>
        )}
      </div>

      {confirmDialog}

      {quizDialog && (
        <QuizDialog
          open
          onClose={() => setQuizDialog(null)}
          initialTitle={quizDialog.item?.title ?? ''}
          initialDescription={quizDialog.item?.description ?? ''}
          initialClassId={quizDialog.item?.schoolClassId ?? null}
          initialTimeLimit={quizDialog.item?.timeLimitMinutes ?? null}
          initialPracticeAllowed={quizDialog.item?.practiceAllowed ?? false}
          initialChapterId={quizDialog.item?.chapterId ?? null}
          classes={classes}
          chapters={chapters}
          onSubmit={async (input) => {
            if (quizDialog.item) {
              await updateQuiz(quizDialog.item.id, input)
              await reloadList()
            } else {
              const created = await createQuiz(input)
              await reloadList()
              openBuilder(created.id) // a new quiz has no items yet: go straight to adding them
            }
          }}
        />
      )}

      {publishing && (
        <PublishDialog
          open
          onClose={() => setPublishing(null)}
          quiz={publishing}
          quizClassId={publishing.classId}
          classes={classes}
          onPublished={async (assigned) => {
            setNotice(assigned
              ? {
                  text: `„${publishing.title}” e publicat și dat ca temă clasei ${assigned.schoolClassName}, termen ${formatDate(assigned.dueAt)}.`,
                  homework: true,
                }
              : { text: `„${publishing.title}” e publicat — elevii îl văd la „Teste disponibile”.`, homework: false })
            await reloadList()
            if (quiz && quiz.id === publishing.id) await reloadQuiz(quiz.id)
          }}
        />
      )}

      {itemDialog && quiz && (
        <ItemDialog
          open
          onClose={() => setItemDialog(null)}
          initial={itemDialog.item}
          chapters={chaptersForQuiz}
          enChapters={enChapters}
          quizChapterTitle={chapterTitle(quiz.chapterId)}
          defaultSection={quiz.items.at(-1)?.section ?? null}
          tutorOn={tutorOn}
          onSubmit={async (input, image) => {
            const saved = itemDialog.item
              ? await updateQuizItem(itemDialog.item.id, input)
              : await addQuizItem(quiz.id, { ...input, position: quiz.items.length }) // new items go to the end
            try {
              if (image instanceof File) {
                await uploadQuestionImage(saved.id, await shrinkImage(image, 1600))
              } else if (image === 'remove') {
                await removeQuestionImage(saved.id)
              }
            } catch (e) {
              // The question itself is saved: say so on the page instead of keeping the dialog (a retry would add it twice).
              setError(`Întrebarea a fost salvată, dar imaginea nu: ${errorMessage(e)} Deschide-o cu „Editează” și încearcă din nou.`)
            }
            await reloadQuiz(quiz.id)
          }}
        />
      )}

      {readingBarem && quiz && (
        <BaremDialog
          quizId={quiz.id}
          items={quiz.items}
          onClose={() => setReadingBarem(false)}
          onApplied={() => reloadQuiz(quiz.id)}
        />
      )}

      {suggesting && quiz && (
        <EnChapterSuggestionsDialog
          quizId={quiz.id}
          items={quiz.items}
          enChapters={enChapters}
          onClose={() => setSuggesting(false)}
          onApplied={() => reloadQuiz(quiz.id)}
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

/** How a test is doing at a glance: its size, the papers handed in, their mean grade and the homework deadline. */
function QuizRowNumbers({ summary }: { summary: QuizListSummary }) {
  return (
    <p data-testid="quiz-numbers" className="flex flex-wrap gap-x-3 gap-y-0.5 text-sm text-muted-foreground">
      <span>{roCount(summary.questions, 'întrebare', 'întrebări')}</span>
      <span>{roCount(summary.papers, 'lucrare', 'lucrări')}</span>
      {summary.average !== null && (
        <span>
          media{' '}
          <span className={cn('font-semibold tabular-nums', GRADE_TEXT[gradeBand(summary.average)])}>
            {formatGrade(summary.average)}
          </span>
        </span>
      )}
      {summary.dueAt && <span>Termen: {formatDate(summary.dueAt)}</span>}
    </p>
  )
}
