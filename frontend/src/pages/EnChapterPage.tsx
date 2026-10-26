import { useEffect, useRef, useState } from 'react'
import { Navigate, useParams, useSearchParams } from 'react-router-dom'
import { Button } from '@/components/ui/button'
import { Card, CardContent } from '@/components/ui/card'
import { MarkdownText } from '@/components/MarkdownText'
import { MathContent } from '@/components/MathContent'
import { PageHeader } from '@/components/PageHeader'
import { PhotoViewer } from '@/components/PhotoViewer'
import { HomeSection } from '@/components/home/HomeBits'
import {
  enChapterPhotoUrl,
  enExampleImageUrl,
  getEnChapter,
  type EnChapterPage as EnChapterPageDto,
  type EnExample,
  type EnSection,
} from '@/lib/api'
import { photoIndexOf } from '@/lib/enChapters'
import { errorMessage } from '@/lib/errors'
import { LoadingCards } from '@/components/LoadingCards'

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

/** A point is stored as "Section: Point"; under its section only the point's own name is shown. */
function shortTitle(section: EnSection, all: EnSection[]): string {
  const parent = all.find((s) => s.id === section.parentId)
  const prefix = parent ? `${parent.title}: ` : ''
  return prefix && section.title.startsWith(prefix) ? section.title.slice(prefix.length) : section.title
}

/** The number the book gives it: "5." for AIS5, "5.5." for AIS5P5; nothing for a code that is not a section. */
function numberOf(code: string): string {
  const match = /S(\d+)(?:P(\d+))?$/.exec(code)
  if (!match) return ''
  return match[2] ? `${match[1]}.${match[2]}.` : `${match[1]}.`
}

/** The heading of a section in the book's own style: "5.5. Ordinea efectuării operațiilor". */
function headingOf(section: EnSection, all: EnSection[]): string {
  const number = numberOf(section.code)
  return number ? `${number} ${shortTitle(section, all)}` : shortTitle(section, all)
}

const SERIF = '"Times New Roman", Times, serif'

/**
 * An EN chapter as a student (or parent) reads it: the theory written out, chapter section by section, with a table
 * of contents that jumps to each one, the pages of the book as photos (to check the text against), and the worked
 * examples. A section or a point has no page of its own: its address opens its chapter, at that section.
 */
export function EnChapterPage() {
  const { id } = useParams()
  const chapterId = Number(id)
  const [search] = useSearchParams()
  const wantedSection = Number(search.get('sectiune')) || null
  const [page, setPage] = useState<EnChapterPageDto | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [viewing, setViewing] = useState<number | null>(null)
  const [photosShown, setPhotosShown] = useState(false)
  const [contentsShown, setContentsShown] = useState(false)
  const openedFor = useRef<number | null>(null)

  useEffect(() => {
    let cancelled = false
    setPage(null)
    getEnChapter(chapterId)
      .then((p) => !cancelled && setPage(p))
      .catch((e) => !cancelled && setError(errorMessage(e)))
    return () => {
      cancelled = true
    }
  }, [chapterId])

  /**
   * The section's written text when it (or a point under it) has one: the table of contents folds away first, so the
   * text is right below instead of a screen of entries further down. Otherwise the photo of the page where it starts.
   */
  function openSection(section: EnSection) {
    if (!page) return
    if (section.notes || page.sections.some((s) => s.parentId === section.id && s.notes)) {
      setContentsShown(false)
      requestAnimationFrame(() => {
        document.getElementById(`sectiune-${section.id}`)?.scrollIntoView?.({ behavior: 'smooth', block: 'start' })
      })
      return
    }
    const index = photoIndexOf(section.bookPage, page.bookPage, page.photos.length)
    if (index !== null) setViewing(index)
  }

  // Arriving from a link to a section: go to it, once.
  useEffect(() => {
    if (!page || wantedSection === null || openedFor.current === wantedSection) return
    const section = page.sections.find((s) => s.id === wantedSection)
    if (!section) return
    openedFor.current = wantedSection
    openSection(section)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [page, wantedSection])

  if (page && page.rootId !== page.id) {
    return <Navigate to={`/capitole/${page.rootId}?sectiune=${page.id}`} replace />
  }

  const photoUrls = page ? page.photos.map((p) => enChapterPhotoUrl(page.id, p.id)) : []
  const sections = page?.sections ?? []
  const pointsOf = (parent: EnSection) => sections.filter((s) => s.parentId === parent.id)
  // A heading is shown when its section, or a point under it, has text.
  const hasText = (s: EnSection) => Boolean(s.notes) || pointsOf(s).some((p) => Boolean(p.notes))
  const chapters = page ? sections.filter((s) => s.parentId === page.id && hasText(s)) : []
  const written = sections.filter((s) => s.notes)
  const empty = page && !page.notes && written.length === 0 && page.photos.length === 0 && page.examples.length === 0
  const bookPageOf = (n: number) => (page?.bookPage != null ? page.bookPage + n - 1 : n)
  // With no written text yet the photos and the contents show straight away; with text they wait behind a button.
  const photosOpen = photosShown || written.length === 0
  const contentsOpen = contentsShown || written.length === 0
  const photoUrl = page ? (photoId: number) => enChapterPhotoUrl(page.id, photoId) : undefined

  return (
    <div className="p-4">
      <div className="mx-auto max-w-3xl space-y-5">
        <PageHeader
          title={page ? `${page.code} · ${page.title}` : 'Capitol EN'}
          breadcrumbs={[{ label: 'Capitole EN', to: '/capitole' }, ...(page ? [{ label: page.code }] : [])]}
        />
        {error && <p className="text-sm text-destructive">{error}</p>}
        {!page && !error && <LoadingCards />}
        {empty && <p className="text-muted-foreground">Profesorul n-a pus încă materia pentru acest capitol.</p>}

        {page?.notes && (
          <Card>
            <CardContent className="py-4">
              <MarkdownText photoUrl={photoUrl}>{page.notes}</MarkdownText>
            </CardContent>
          </Card>
        )}

        {page && sections.length > 0 && (
          <HomeSection title="Cuprins">
            {written.length > 0 && (
              <Button variant="outline" size="sm" onClick={() => setContentsShown((s) => !s)} aria-expanded={contentsOpen}>
                {contentsOpen ? 'Ascunde cuprinsul' : `Arată cuprinsul (${sections.length})`}
              </Button>
            )}
            {contentsOpen && (
              <ul className="space-y-1">
                {sections.map((section) => {
                  const index = photoIndexOf(section.bookPage, page.bookPage, page.photos.length)
                  const nested = section.parentId !== page.id
                  const available = hasText(section) || index !== null
                  const label = headingOf(section, sections)
                  return (
                    <li key={section.id} className={nested ? 'pl-6 text-sm' : 'font-medium'}>
                      {available ? (
                        <button
                          type="button"
                          onClick={() => openSection(section)}
                          className="flex w-full items-baseline justify-between gap-3 rounded px-1 py-0.5 text-left hover:bg-muted"
                        >
                          <span>{label}</span>
                          {section.bookPage !== null && (
                            <span className="shrink-0 text-xs text-muted-foreground">pag. {section.bookPage}</span>
                          )}
                        </button>
                      ) : (
                        <span className="text-muted-foreground">{label}</span>
                      )}
                    </li>
                  )
                })}
              </ul>
            )}
          </HomeSection>
        )}

        {chapters.length > 0 && (
          <HomeSection title="Materia">
            <div className="space-y-2">
              {chapters.map((chapter) => (
                <section key={chapter.id} id={`sectiune-${chapter.id}`} className="scroll-mt-4 pt-8">
                  <h3
                    className="border-b border-green-700/40 pb-1 font-bold text-green-700 dark:text-green-400"
                    style={{ fontFamily: SERIF, fontSize: '16pt' }}
                  >
                    {headingOf(chapter, sections)}
                  </h3>
                  {chapter.notes && (
                    <div className="mt-3">
                      <MarkdownText photoUrl={photoUrl}>{chapter.notes}</MarkdownText>
                    </div>
                  )}
                  <div className="space-y-8 pt-6">
                    {pointsOf(chapter).filter((p) => p.notes).map((point) => {
                      const index = photoIndexOf(point.bookPage, page!.bookPage, page!.photos.length)
                      return (
                        <article key={point.id} id={`sectiune-${point.id}`} className="scroll-mt-4 space-y-2">
                          <div className="flex flex-wrap items-baseline justify-between gap-2">
                            <h4 className="text-base font-semibold">{headingOf(point, sections)}</h4>
                            {index !== null && (
                              <Button size="sm" variant="ghost" onClick={() => setViewing(index)}>
                                Vezi pagina {point.bookPage} din carte
                              </Button>
                            )}
                          </div>
                          <MarkdownText photoUrl={photoUrl}>{point.notes!}</MarkdownText>
                        </article>
                      )
                    })}
                  </div>
                </section>
              ))}
            </div>
          </HomeSection>
        )}

        {page && page.photos.length > 0 && (
          <HomeSection title="Paginile cărții">
            {written.length > 0 && (
              <Button variant="outline" size="sm" onClick={() => setPhotosShown((s) => !s)} aria-expanded={photosOpen}>
                {photosOpen ? 'Ascunde pozele' : `Arată pozele cu paginile cărții (${page.photos.length})`}
              </Button>
            )}
            {photosOpen && (
              <>
                <div className="grid grid-cols-2 gap-3 sm:grid-cols-3">
                  {photoUrls.map((url, i) => (
                    <button
                      key={url}
                      type="button"
                      onClick={() => setViewing(i)}
                      className="overflow-hidden rounded-lg border bg-white outline-none focus-visible:ring-3 focus-visible:ring-ring/50"
                    >
                      <img src={url} alt={`Materia, pagina ${bookPageOf(i + 1)}`} className="aspect-[3/4] w-full object-cover object-top" />
                    </button>
                  ))}
                </div>
                <p className="text-xs text-muted-foreground">Apasă pe o pagină ca s-o vezi mare.</p>
              </>
            )}
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
          alt={(n) => `Materia, pagina ${bookPageOf(n)}`}
          title={(n) => (page?.bookPage != null ? `Pagina ${bookPageOf(n)} din carte` : `Pagina ${n} din ${photoUrls.length}`)}
          index={viewing}
          onIndexChange={setViewing}
          onClose={() => setViewing(null)}
        />
      </div>
    </div>
  )
}
