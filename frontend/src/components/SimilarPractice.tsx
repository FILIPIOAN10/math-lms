import { useState } from 'react'
import { Link } from 'react-router-dom'
import { MathContent } from '@/components/MathContent'
import { PracticeQuestionCard } from '@/components/PracticeQuestionCard'
import { Button, buttonVariants } from '@/components/ui/button'
import { Card, CardContent } from '@/components/ui/card'
import {
  answerSimilar,
  enExampleImageUrl,
  getSimilar,
  type ChapterPracticeResult,
  type SimilarExample,
  type SimilarPractice as SimilarPracticeDto,
} from '@/lib/api'
import { errorMessage } from '@/lib/errors'
import { lessonHref } from '@/lib/lessons'
import { cn } from '@/lib/utils'

/**
 * „Dă-mi una asemănătoare” under a question the student got wrong: up to three grile and worked examples nearest by
 * meaning, one at a time - a grilă answered and checked at once (nothing graded or stored), an example read with its
 * solution on request and a link to its lesson. Plain search over embeddings computed beforehand: nothing generated.
 */
export function SimilarPractice({ itemId }: { itemId: number }) {
  const [found, setFound] = useState<SimilarPracticeDto | null>(null)
  const [loading, setLoading] = useState(false)
  const [index, setIndex] = useState(0)
  const [selected, setSelected] = useState<number | null>(null)
  const [result, setResult] = useState<ChapterPracticeResult | null>(null)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)

  async function load() {
    setLoading(true)
    setError(null)
    try {
      setFound(await getSimilar(itemId))
    } catch (e) {
      setError(errorMessage(e))
    } finally {
      setLoading(false)
    }
  }

  function next() {
    setIndex((i) => i + 1)
    setSelected(null)
    setResult(null)
    setError(null)
  }

  if (found === null) {
    return (
      <div className="space-y-1">
        <Button type="button" size="sm" variant="outline" disabled={loading} onClick={() => void load()}>
          {loading ? 'Caut...' : 'Dă-mi una asemănătoare'}
        </Button>
        {error && <p role="alert" className="text-sm text-destructive">{error}</p>}
      </div>
    )
  }

  if (found.entries.length === 0) {
    return (
      <p className="text-sm text-muted-foreground" data-testid="similar-none">
        {found.indexed
          ? 'Nu am găsit exerciții destul de apropiate de aceasta.'
          : 'Încă nu am căutat exerciții asemănătoare pentru această întrebare. Revino mâine.'}
      </p>
    )
  }

  const total = found.entries.length
  if (index >= total) {
    return <p className="text-sm text-muted-foreground" data-testid="similar-done">Gata, ai trecut prin toate cele {total}.</p>
  }
  const entry = found.entries[index]
  const isLast = index === total - 1

  async function check() {
    if (selected === null || !entry.question) return
    setBusy(true)
    setError(null)
    try {
      setResult(await answerSimilar(entry.question.itemId, selected))
    } catch (e) {
      setError(errorMessage(e))
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="space-y-2" data-testid="similar-practice">
      <p className="text-xs font-medium text-muted-foreground">Asemănătoare, din banca de întrebări și din exemple</p>
      {entry.kind === 'QUESTION' && entry.question ? (
        <PracticeQuestionCard
          key={entry.question.itemId}
          name="similar"
          position={index}
          total={total}
          question={entry.question}
          selected={selected}
          onSelect={setSelected}
          result={result}
          busy={busy}
          error={error}
          nextLabel={isLast ? 'Gata' : 'Următoarea'}
          onCheck={() => void check()}
          onNext={next}
        />
      ) : entry.example ? (
        <ExampleCard key={entry.example.id} example={entry.example} position={index} total={total} isLast={isLast} onNext={next} />
      ) : null}
    </div>
  )
}

function ExampleCard({ example, position, total, isLast, onNext }: {
  example: SimilarExample
  position: number
  total: number
  isLast: boolean
  onNext: () => void
}) {
  const [shown, setShown] = useState(false)
  return (
    <Card data-testid="similar-example">
      <CardContent className="space-y-3 py-5">
        <p className="text-xs font-medium text-muted-foreground">
          Exemplul {position + 1} din {total} · exemplu rezolvat din lecții
        </p>
        <MathContent>{example.statement}</MathContent>
        {example.image && (
          <img src={enExampleImageUrl(example.chapterId, example.id, example.image)} alt="Figura exemplului" className="max-h-64 w-auto rounded-md border" />
        )}
        {shown ? (
          <div className="rounded-lg bg-muted p-3 text-sm">
            <MathContent>{example.solution}</MathContent>
          </div>
        ) : (
          <Button type="button" size="sm" variant="outline" onClick={() => setShown(true)}>
            Arată rezolvarea
          </Button>
        )}
        <div className="flex flex-wrap gap-2">
          {example.lesson && (
            <Link
              to={lessonHref(example.lesson)}
              className={cn(buttonVariants({ size: 'sm', variant: 'outline' }), 'h-auto py-1.5 text-left whitespace-normal')}
            >
              Deschide lecția {example.lesson.chapterCode}
              {example.lesson.bookPage > 0 && `, pagina ${example.lesson.bookPage}`}
            </Link>
          )}
          <Button type="button" size="sm" onClick={onNext}>
            {isLast ? 'Gata' : 'Următoarea'}
          </Button>
        </div>
      </CardContent>
    </Card>
  )
}
