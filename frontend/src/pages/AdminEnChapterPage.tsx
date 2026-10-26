import { useCallback, useEffect, useState, type ChangeEvent } from 'react'
import { Link, useParams } from 'react-router-dom'
import { Button, buttonVariants } from '@/components/ui/button'
import { Card, CardContent } from '@/components/ui/card'
import { EnChapterDialog, EnExampleDialog } from '@/components/EnChapterDialogs'
import { MarkdownText } from '@/components/MarkdownText'
import { MathContent } from '@/components/MathContent'
import { PageHeader } from '@/components/PageHeader'
import { PhotoViewer } from '@/components/PhotoViewer'
import { HomeSection } from '@/components/home/HomeBits'
import { useConfirm } from '@/components/useConfirm'
import {
  addEnExample,
  deleteEnExample,
  enChapterPhotoUrl,
  enExampleImageUrl,
  getEnChapter,
  removeEnChapterPhoto,
  removeEnExampleImage,
  updateEnChapter,
  updateEnExample,
  uploadEnChapterPhoto,
  uploadEnExampleImage,
  type EnChapterPage,
  ApiError,
  type EnExample,
} from '@/lib/api'
import { errorMessage } from '@/lib/errors'
import { shrinkImage } from '@/lib/image'
import { pdfPageCount, pdfPageImages } from '@/lib/pdfPages'
import { cn } from '@/lib/utils'
import { LoadingCards } from '@/components/LoadingCards'

/** What the server accepts for one chapter (EnChapterService.MAX_PHOTOS). */
const MAX_PHOTOS = 40

/** A file input that looks like a button; the label is what screen readers (and tests) find it by. */
function FilePicker({ id, label, text, multiple = false, accept = 'image/*', disabled, onFiles }: {
  id: string
  label: string
  text: string
  multiple?: boolean
  accept?: string
  disabled?: boolean
  onFiles: (files: File[]) => void
}) {
  function handle(e: ChangeEvent<HTMLInputElement>) {
    const files = Array.from(e.target.files ?? [])
    e.target.value = '' // the same file may be chosen again later
    if (files.length > 0) onFiles(files)
  }
  return (
    <>
      <label htmlFor={id} className={cn(buttonVariants({ variant: 'outline', size: 'sm' }), 'cursor-pointer', disabled && 'pointer-events-none opacity-50')}>
        {text}
      </label>
      <input id={id} type="file" accept={accept} multiple={multiple} aria-label={label} className="sr-only"
        disabled={disabled} onChange={handle} />
    </>
  )
}

/** One worked example in the editor: everything visible, with its own edit, figure and delete actions. */
function ExampleEditorCard({ page, example, number, busy, onEdit, onFigure, onRemoveFigure, onDelete }: {
  page: EnChapterPage
  example: EnExample
  number: number
  busy: boolean
  onEdit: () => void
  onFigure: (file: File) => void
  onRemoveFigure: () => void
  onDelete: () => void
}) {
  return (
    <Card data-testid={`en-example-${example.id}`}>
      <CardContent className="space-y-3 py-4">
        <div className="flex flex-wrap items-center justify-between gap-2">
          <p className="text-sm font-medium text-muted-foreground">Modelul {number}</p>
          <div className="flex flex-wrap gap-1">
            <Button size="sm" variant="outline" onClick={onEdit} disabled={busy}>Editează</Button>
            <FilePicker
              id={`en-example-figure-${example.id}`}
              label={example.image ? `Schimbă figura modelului ${number}` : `Adaugă o figură modelului ${number}`}
              text={example.image ? 'Schimbă figura' : 'Adaugă figură'}
              disabled={busy}
              onFiles={(files) => onFigure(files[0])}
            />
            {example.image && <Button size="sm" variant="outline" onClick={onRemoveFigure} disabled={busy}>Șterge figura</Button>}
            <Button size="sm" variant="outline" onClick={onDelete} disabled={busy}>Șterge</Button>
          </div>
        </div>
        <MathContent className="whitespace-pre-wrap">{example.statement}</MathContent>
        {example.image && (
          <img
            src={enExampleImageUrl(page.id, example.id, example.image)}
            alt={`Figura modelului ${number}`}
            className="max-h-60 rounded-lg border bg-white object-contain"
          />
        )}
        <div className="rounded-lg border-l-4 border-primary/40 bg-muted px-3 py-2">
          <p className="mb-1 text-xs font-medium text-muted-foreground">Rezolvarea (elevul o vede la „Arată rezolvarea”)</p>
          <MathContent className="whitespace-pre-wrap">{example.solution}</MathContent>
        </div>
      </CardContent>
    </Card>
  )
}

/** The teacher fills one EN chapter page: the text on top, the photographed theory, the worked examples. */
export function AdminEnChapterPage() {
  const { id } = useParams()
  const chapterId = Number(id)
  const [page, setPage] = useState<EnChapterPage | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)
  const [progress, setProgress] = useState<string | null>(null)
  const [editingChapter, setEditingChapter] = useState(false)
  // null = closed; 'new' = adding; an example = editing it.
  const [exampleDialog, setExampleDialog] = useState<'new' | EnExample | null>(null)
  const [viewing, setViewing] = useState<number | null>(null)
  const { confirm, dialog: confirmDialog } = useConfirm()

  const load = useCallback(() => {
    return getEnChapter(chapterId)
      .then(setPage)
      .catch((e) => setError(errorMessage(e)))
  }, [chapterId])

  useEffect(() => {
    void load()
  }, [load])

  async function run(action: () => Promise<void>) {
    setError(null)
    setBusy(true)
    try {
      await action()
    } catch (e) {
      setError(errorMessage(e))
    } finally {
      setBusy(false)
      setProgress(null)
      await load()
    }
  }

  /** One by one, in the order chosen: the pages of the theory stay in order. */
  function uploadPhotos(files: File[]) {
    void run(async () => {
      for (let i = 0; i < files.length; i++) {
        setProgress(files.length > 1 ? `Se încarcă poza ${i + 1} din ${files.length}…` : 'Se încarcă poza…')
        await uploadEnChapterPhoto(chapterId, await shrinkImage(files[i]))
      }
    })
  }

  /**
   * A chapter scanned into one PDF: each page becomes a photo of the chapter, in order. Checked against the limit before
   * anything is sent, so a PDF that does not fit leaves the page as it was.
   */
  function uploadPdf(files: File[]) {
    void run(async () => {
      const file = files[0]
      const pages = await pdfPageCount(file)
      const room = MAX_PHOTOS - (page?.photos.length ?? 0)
      if (pages > room) {
        throw new ApiError(400, `PDF-ul are ${pages} pagini, dar capitolul mai poate primi ${room} (cel mult ${MAX_PHOTOS}).`)
      }
      for await (const image of pdfPageImages(file)) {
        setProgress(`Se încarcă pagina ${image.page} din ${image.total}…`)
        await uploadEnChapterPhoto(chapterId, image.file)
      }
    })
  }

  async function removePhoto(photoId: number, number: number) {
    const yes = await confirm({ title: `Ștergi pagina ${number} din materie?`, confirmLabel: 'Șterge poza', destructive: true })
    if (yes) await run(() => removeEnChapterPhoto(chapterId, photoId))
  }

  async function removeExample(example: EnExample, number: number) {
    const yes = await confirm({ title: `Ștergi modelul ${number}?`, confirmLabel: 'Șterge modelul', destructive: true })
    if (yes) await run(() => deleteEnExample(chapterId, example.id))
  }

  const photoUrls = page ? page.photos.map((p) => enChapterPhotoUrl(page.id, p.id)) : []

  return (
    <div className="p-4">
      <div className="mx-auto max-w-4xl space-y-5">
        <PageHeader
          title={page ? `${page.code} · ${page.title}` : 'Capitol EN'}
          description="Ce vede elevul când greșește un exercițiu din acest capitol."
          breadcrumbs={[{ label: 'Capitole EN', to: '/admin/en-chapters' }, ...(page ? [{ label: page.code }] : [])]}
          actions={page && (
            <>
              <Link to={`/capitole/${page.id}`} className={buttonVariants({ variant: 'outline' })}>Vezi ca elev</Link>
              <Button variant="outline" onClick={() => setEditingChapter(true)}>Editează capitolul</Button>
            </>
          )}
        />
        {error && <p className="text-sm text-destructive" role="alert">{error}</p>}
        {!page && !error && <LoadingCards />}

        {page && (
          <>
            {page.notes && (
              <Card>
                <CardContent className="py-4">
                  <MarkdownText>{page.notes}</MarkdownText>
                </CardContent>
              </Card>
            )}

            {page.sections.length > 0 && (
              <HomeSection title={`Subcapitole (${page.sections.length})`}>
                <p className="text-sm text-muted-foreground">
                  Textul fiecărui subcapitol se scrie sau se corectează pe pagina lui: apasă „Editează”, apoi „Editează capitolul”.
                </p>
                <Card>
                  <CardContent className="divide-y py-1">
                    {page.sections.map((section) => (
                      <div
                        key={section.id}
                        data-testid={`en-section-${section.id}`}
                        className="flex flex-wrap items-center gap-x-3 gap-y-1 py-2"
                        style={{ paddingLeft: section.parentId === page.id ? 0 : '1.5rem' }}
                      >
                        <span className="min-w-0 flex-1 text-sm">
                          <span className="mr-1 font-mono text-xs text-muted-foreground">{section.code}</span>{' '}
                          {section.title}
                        </span>
                        {section.bookPage !== null && (
                          <span className="text-xs text-muted-foreground">pag. {section.bookPage}</span>
                        )}
                        <span className={cn('text-xs', section.notes ? 'text-emerald-700 dark:text-emerald-400' : 'text-muted-foreground')}>
                          {section.notes ? 'are text' : 'fără text'}
                        </span>
                        <Link
                          to={`/admin/en-chapters/${section.id}`}
                          className={buttonVariants({ variant: 'outline', size: 'sm' })}
                          aria-label={`Editează ${section.code}`}
                        >
                          Editează
                        </Link>
                      </div>
                    ))}
                  </CardContent>
                </Card>
              </HomeSection>
            )}

            {page.rootId !== page.id ? (
              <Card data-testid="en-section-note">
                <CardContent className="space-y-1 py-4 text-sm">
                  <p>
                    Un subcapitol nu are poze proprii: elevul îl deschide în capitolul lui
                    {page.bookPage !== null ? `, la pagina ${page.bookPage} din carte` : ''}.
                  </p>
                  <Link to={`/admin/en-chapters/${page.rootId}`} className="font-medium text-primary hover:underline">
                    Pozele se încarcă la capitol
                  </Link>
                </CardContent>
              </Card>
            ) : (
              <HomeSection
                title={`Materia (${page.photos.length})`}
                action={
                  <span className="flex flex-wrap gap-2">
                    <FilePicker id="en-photos" label="Adaugă poze cu materia" text="Adaugă poze" multiple disabled={busy} onFiles={uploadPhotos} />
                    <FilePicker id="en-pdf" label="Adaugă PDF cu materia" text="Adaugă PDF" accept="application/pdf" disabled={busy} onFiles={uploadPdf} />
                  </span>
                }
              >
                <p className="text-sm text-muted-foreground">
                  Fotografiază teoria pagină cu pagină (din caiet sau din manual) sau încarcă un PDF al capitolului: fiecare
                  pagină devine o poză, în ordine. Subcapitolele deschid pagina unde încep, socotită de la prima pagină a capitolului.
                </p>
                {progress && <p className="text-sm" role="status">{progress}</p>}
                {page.photos.length > 0 && (
                  <div className="grid grid-cols-2 gap-3 sm:grid-cols-4">
                    {photoUrls.map((url, i) => (
                      <div key={url} className="space-y-1">
                        <button
                          type="button"
                          onClick={() => setViewing(i)}
                          className="block w-full overflow-hidden rounded-lg border bg-white outline-none focus-visible:ring-3 focus-visible:ring-ring/50"
                        >
                          <img src={url} alt={`Materia, pagina ${i + 1}`} className="aspect-[3/4] w-full object-cover object-top" />
                        </button>
                        <div className="flex items-center justify-between text-xs text-muted-foreground">
                          <span>{page.bookPage !== null ? `Pagina ${page.bookPage + i} din carte` : `Pagina ${i + 1}`}</span>
                          <Button size="sm" variant="ghost" aria-label={`Șterge pagina ${i + 1}`} disabled={busy}
                            onClick={() => removePhoto(page.photos[i].id, i + 1)}>Șterge</Button>
                        </div>
                      </div>
                    ))}
                  </div>
                )}
              </HomeSection>
            )}

            <HomeSection
              title={`Modele rezolvate (${page.examples.length})`}
              action={<Button size="sm" onClick={() => setExampleDialog('new')} disabled={busy}>Adaugă model</Button>}
            >
              {page.examples.length === 0 && (
                <p className="text-sm text-muted-foreground">
                  Câteva exerciții tipice cu rezolvarea explicată pas cu pas. Ideile de modele sunt în docs/EN-capitole.md.
                </p>
              )}
              <div className="space-y-3">
                {page.examples.map((example, i) => (
                  <ExampleEditorCard
                    key={example.id}
                    page={page}
                    example={example}
                    number={i + 1}
                    busy={busy}
                    onEdit={() => setExampleDialog(example)}
                    onFigure={(file) => run(async () => uploadEnExampleImage(chapterId, example.id, await shrinkImage(file)))}
                    onRemoveFigure={() => run(() => removeEnExampleImage(chapterId, example.id))}
                    onDelete={() => removeExample(example, i + 1)}
                  />
                ))}
              </div>
            </HomeSection>
          </>
        )}

        <PhotoViewer
          urls={photoUrls}
          alt={(n) => `Materia, pagina ${n}`}
          index={viewing}
          onIndexChange={setViewing}
          onClose={() => setViewing(null)}
        />
      </div>

      {page && editingChapter && (
        <EnChapterDialog
          open
          onClose={() => setEditingChapter(false)}
          initial={{
            code: page.code, title: page.title, area: page.area, notes: page.notes,
            parentId: page.parentId, bookPage: page.bookPage,
          }}
          onSubmit={async (input) => {
            await updateEnChapter(page.id, input)
            await load()
          }}
        />
      )}
      {exampleDialog !== null && (
        <EnExampleDialog
          open
          onClose={() => setExampleDialog(null)}
          initial={exampleDialog === 'new' ? null : { statement: exampleDialog.statement, solution: exampleDialog.solution }}
          onSubmit={async (input) => {
            if (exampleDialog === 'new') {
              await addEnExample(chapterId, input)
            } else {
              await updateEnExample(chapterId, exampleDialog.id, input)
            }
            await load()
          }}
        />
      )}
      {confirmDialog}
    </div>
  )
}
