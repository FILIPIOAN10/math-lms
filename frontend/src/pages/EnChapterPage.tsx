import { useEffect, useState } from 'react'
import { useParams } from 'react-router-dom'
import { Button } from '@/components/ui/button'
import { Card, CardContent } from '@/components/ui/card'
import { MathContent } from '@/components/MathContent'
import { PageHeader } from '@/components/PageHeader'
import { PhotoViewer } from '@/components/PhotoViewer'
import { HomeSection } from '@/components/home/HomeBits'
import { enChapterPhotoUrl, enExampleImageUrl, getEnChapter, type EnChapterPage as EnChapterPageDto, type EnExample } from '@/lib/api'
import { errorMessage } from '@/lib/errors'

/** One worked example: try it first, then open the explained solution. */
function ExampleCard({ chapterId, example, number }: { chapterId: number; example: EnExample; number: number }) {
  const [shown, setShown] = useState(false)
  return (
    <Card data-testid={`en-example-${example.id}`}>
      <CardContent className="space-y-3 py-4">
        <p className="text-sm font-medium text-muted-foreground">Modelul {number}</p>
        <MathContent className="whitespace-pre-wrap">{example.statement}</MathContent>
        {example.image && (
          <a href={enExampleImageUrl(chapterId, example.id, example.image)} target="_blank" rel="noreferrer" className="block w-fit">
            <img
              src={enExampleImageUrl(chapterId, example.id, example.image)}
              alt={`Figura modelului ${number}`}
              className="max-h-72 rounded-lg border bg-white object-contain"
            />
          </a>
        )}
        <Button variant="outline" size="sm" onClick={() => setShown((s) => !s)} aria-expanded={shown}>
          {shown ? 'Ascunde rezolvarea' : 'Arată rezolvarea'}
        </Button>
        {shown && (
          <MathContent className="rounded-lg border-l-4 border-primary/40 bg-muted px-3 py-2 whitespace-pre-wrap">
            {example.solution}
          </MathContent>
        )}
      </CardContent>
    </Card>
  )
}

/** An EN chapter page as a student (or parent) reads it: the teacher's notes, the theory photos, the worked examples. */
export function EnChapterPage() {
  const { id } = useParams()
  const chapterId = Number(id)
  const [page, setPage] = useState<EnChapterPageDto | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [viewing, setViewing] = useState<number | null>(null)

  useEffect(() => {
    let cancelled = false
    getEnChapter(chapterId)
      .then((p) => !cancelled && setPage(p))
      .catch((e) => !cancelled && setError(errorMessage(e)))
    return () => {
      cancelled = true
    }
  }, [chapterId])

  const photoUrls = page ? page.photos.map((p) => enChapterPhotoUrl(page.id, p.id)) : []
  const empty = page && !page.notes && page.photos.length === 0 && page.examples.length === 0

  return (
    <div className="p-4">
      <div className="mx-auto max-w-3xl space-y-5">
        <PageHeader
          title={page ? `${page.code} · ${page.title}` : 'Capitol EN'}
          breadcrumbs={[{ label: 'Capitole EN', to: '/capitole' }, ...(page ? [{ label: page.code }] : [])]}
        />
        {error && <p className="text-sm text-destructive">{error}</p>}
        {!page && !error && <p className="text-muted-foreground">Se încarcă...</p>}
        {empty && <p className="text-muted-foreground">Profesorul n-a pus încă materia pentru acest capitol.</p>}

        {page?.notes && (
          <Card>
            <CardContent className="py-4">
              <MathContent className="whitespace-pre-wrap">{page.notes}</MathContent>
            </CardContent>
          </Card>
        )}

        {page && page.photos.length > 0 && (
          <HomeSection title="Materia">
            <div className="grid grid-cols-2 gap-3 sm:grid-cols-3">
              {photoUrls.map((url, i) => (
                <button
                  key={url}
                  type="button"
                  onClick={() => setViewing(i)}
                  className="overflow-hidden rounded-lg border bg-white outline-none focus-visible:ring-3 focus-visible:ring-ring/50"
                >
                  <img src={url} alt={`Materia, pagina ${i + 1}`} className="aspect-[3/4] w-full object-cover object-top" />
                </button>
              ))}
            </div>
            <p className="text-xs text-muted-foreground">Apasă pe o pagină ca s-o vezi mare.</p>
          </HomeSection>
        )}

        {page && page.examples.length > 0 && (
          <HomeSection title="Modele rezolvate">
            <div className="space-y-3">
              {page.examples.map((example, i) => (
                <ExampleCard key={example.id} chapterId={page.id} example={example} number={i + 1} />
              ))}
            </div>
          </HomeSection>
        )}

        <PhotoViewer
          urls={photoUrls}
          alt={(n) => `Materia, pagina ${n}`}
          index={viewing}
          onIndexChange={setViewing}
          onClose={() => setViewing(null)}
        />
      </div>
    </div>
  )
}
