import { useEffect, useMemo, useRef, useState } from 'react'
import { Button } from '@/components/ui/button'
import { FigureCropEditor } from '@/components/FigureCropEditor'
import {
  createQuestionBaremRead, getQuestionBaremRead, type QuestionBarem, type QuestionBaremRequest,
} from '@/lib/api'
import { errorMessage } from '@/lib/errors'
import { cropImage } from '@/lib/image'
import { pdfPageImages } from '@/lib/pdfPages'

/** How often the reading is asked about while the AI reads (it takes a few seconds to a minute). */
const POLL_MS = 2000

/** The whole page: where the frame starts before the teacher pulls it onto the problem. */
const WHOLE = { x: 0, y: 0, w: 1, h: 1 }

const isPdf = (f: File) => f.type === 'application/pdf' || f.name.toLowerCase().endsWith('.pdf')

type Step = 'closed' | 'pick' | 'frame' | 'reading'

/**
 * "Barem din poză (AI)" for ONE open question of the review, before the test exists: the teacher picks the photo (or
 * PDF) of the official barem once, frames this problem on it, and the AI reads only that part and gives back the barem
 * of each requirement. The pages stay with the review (`photos`), so the next problem only needs framing. Nothing is
 * saved: the barem lands in the question's fields, where the teacher checks it before creating the test.
 */
export function QuestionBaremReader({ question, photos, onPhotos, onRead, pollMs = POLL_MS }: {
  question: QuestionBaremRequest
  /** The barem pages, chosen for this question or an earlier one. */
  photos: File[]
  onPhotos: (photos: File[]) => void
  onRead: (barem: QuestionBarem) => void
  pollMs?: number
}) {
  const [step, setStep] = useState<Step>('closed')
  const [page, setPage] = useState(0)
  const [readingId, setReadingId] = useState<number | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [done, setDone] = useState<string[] | null>(null)
  const shown = photos[Math.min(page, photos.length - 1)] ?? null
  const url = useMemo(() => (shown ? URL.createObjectURL(shown) : null), [shown])
  useEffect(() => () => { if (url) URL.revokeObjectURL(url) }, [url])
  // The review passes a new function on every render: polling must not restart for that.
  const onReadRef = useRef(onRead)
  useEffect(() => { onReadRef.current = onRead }, [onRead])

  // While the AI reads: ask every pollMs until it is DONE or FAILED.
  useEffect(() => {
    if (readingId === null) return
    let stopped = false
    let timer: ReturnType<typeof setTimeout>
    const poll = async () => {
      try {
        const read = await getQuestionBaremRead(readingId)
        if (stopped) return
        if (read.status === 'DONE' && read.barem) {
          onReadRef.current(read.barem)
          setDone(read.barem.warnings)
          setReadingId(null)
          setStep('closed')
        } else if (read.status === 'FAILED' || read.status === 'DONE') {
          setError(read.error ?? 'Nu am putut citi baremul.')
          setReadingId(null)
          setStep('frame')
        } else {
          timer = setTimeout(poll, pollMs)
        }
      } catch (e) {
        if (stopped) return
        setError(errorMessage(e))
        setReadingId(null)
        setStep('frame')
      }
    }
    timer = setTimeout(poll, pollMs)
    return () => { stopped = true; clearTimeout(timer) }
  }, [readingId, pollMs])

  async function choose(files: FileList | null) {
    if (!files || files.length === 0) return
    setError(null)
    const pages: File[] = []
    try {
      for (const file of Array.from(files)) {
        if (!isPdf(file)) {
          pages.push(file)
          continue
        }
        for await (const drawn of pdfPageImages(file)) pages.push(drawn.file)
      }
    } catch {
      setError('Nu am putut deschide PDF-ul. Încearcă o poză a paginii cu baremul.')
      return
    }
    onPhotos(pages)
    setPage(0)
    setStep('frame')
  }

  async function read(box: typeof WHOLE) {
    if (!shown) return
    setError(null)
    setDone(null)
    setStep('reading')
    try {
      const framed = await cropImage(shown, box)
      const started = await createQuestionBaremRead(framed, question)
      setReadingId(started.id)
    } catch (e) {
      setError(errorMessage(e))
      setStep('frame')
    }
  }

  const open = () => {
    setError(null)
    setDone(null)
    setStep(photos.length > 0 ? 'frame' : 'pick')
  }

  return (
    <div className="space-y-2 rounded-md border border-dashed p-3" data-testid="question-barem">
      {step === 'closed' && (
        <div className="flex flex-wrap items-center gap-3">
          <Button type="button" variant="outline" size="xs" onClick={open}>Barem din poză (AI)</Button>
          <span className="text-xs text-muted-foreground">
            Încadrezi problema pe poza baremului, iar AI-ul scrie baremul fiecărei cerințe.
          </span>
        </div>
      )}
      {done && (
        <div className="space-y-1 text-sm" role="status">
          <p className="text-green-700 dark:text-green-400">Am pus baremul la cerințe. Verifică-l înainte să creezi testul.</p>
          {done.length > 0 && (
            <ul className="space-y-1 text-amber-700 dark:text-amber-400">
              {done.map((w) => <li key={w}>⚠ {w}</li>)}
            </ul>
          )}
        </div>
      )}
      {step === 'pick' && (
        <div className="space-y-2">
          <label className="flex flex-col gap-1 text-sm font-medium">
            Poza cu baremul
            <input type="file" accept="image/*,application/pdf" multiple aria-label="Poza cu baremul"
                   onChange={(e) => void choose(e.target.files)} className="text-sm font-normal" />
          </label>
          <p className="text-xs text-muted-foreground">
            O poză sau PDF-ul baremului. O păstrez cât lucrezi aici, ca la celelalte probleme doar s-o încadrezi.
          </p>
          <Button type="button" variant="ghost" size="xs" onClick={() => setStep('closed')}>Anulează</Button>
        </div>
      )}
      {(step === 'frame' || step === 'reading') && url && (
        <div className="space-y-2">
          <p className="text-sm font-medium">Încadrează doar această problemă pe barem</p>
          <div className="flex flex-wrap items-center gap-2">
            {photos.length > 1 && photos.map((_, i) => (
              <Button key={i} type="button" size="xs" variant={i === page ? 'default' : 'outline'} aria-pressed={i === page}
                      disabled={step === 'reading'} onClick={() => setPage(i)}>
                Pagina {i + 1}
              </Button>
            ))}
            <Button type="button" variant="ghost" size="xs" disabled={step === 'reading'} onClick={() => setStep('pick')}>
              Altă poză
            </Button>
          </div>
          {step === 'reading' ? (
            <p className="text-sm text-muted-foreground" aria-live="polite">Se citește baremul...</p>
          ) : (
            <FigureCropEditor key={page} src={url} box={WHOLE} alt={`Baremul, pagina ${page + 1}`}
                              boxLabel="Chenarul problemei" doneLabel="Citește baremul (AI)"
                              onDone={(box) => void read(box)} onCancel={() => setStep('closed')} />
          )}
        </div>
      )}
      {error && <p role="alert" className="text-sm text-destructive">{error}</p>}
    </div>
  )
}
