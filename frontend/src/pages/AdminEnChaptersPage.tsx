import { useCallback, useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { Button, buttonVariants } from '@/components/ui/button'
import { Card, CardContent } from '@/components/ui/card'
import { EnChapterDialog } from '@/components/EnChapterDialogs'
import { PageHeader } from '@/components/PageHeader'
import { HomeSection } from '@/components/home/HomeBits'
import { useConfirm } from '@/components/useConfirm'
import {
  createEnChapter,
  deleteEnChapter,
  getEnChapter,
  listEnChapters,
  moveEnChapter,
  updateEnChapter,
  type EnChapterInput,
  type EnChapterSummary,
} from '@/lib/api'
import { EN_AREAS, enPageContents, sectionPageStatus, treeRows } from '@/lib/enChapters'
import { errorMessage } from '@/lib/errors'
import { roCount } from '@/lib/format'
import { LoadingCards } from '@/components/LoadingCards'

/**
 * The teacher's list of EN chapters: add, rename, reorder within algebra or geometry, delete an empty one, and open a
 * page to fill it. A chapter lists its sections and points when opened; those only mark where the chapter's pages
 * (photos) are to be opened, so they show their page of the book instead of photo counts.
 */
export function AdminEnChaptersPage() {
  const [chapters, setChapters] = useState<EnChapterSummary[] | null>(null)
  const [error, setError] = useState<string | null>(null)
  // null = closed; 'new' = adding; otherwise the chapter being edited, with its current values.
  const [dialog, setDialog] = useState<'new' | { id: number; input: EnChapterInput } | null>(null)
  const [busy, setBusy] = useState(false)
  const [open, setOpen] = useState<ReadonlySet<number>>(new Set())
  const { confirm, dialog: confirmDialog } = useConfirm()

  const load = useCallback(() => {
    return listEnChapters()
      .then(setChapters)
      .catch((e) => setError(errorMessage(e)))
  }, [])

  useEffect(() => {
    void load()
  }, [load])

  async function run(action: () => Promise<void>) {
    setError(null)
    setBusy(true)
    try {
      await action()
      await load()
    } catch (e) {
      setError(errorMessage(e))
    } finally {
      setBusy(false)
    }
  }

  function toggle(id: number) {
    setOpen((current) => {
      const next = new Set(current)
      if (!next.delete(id)) next.add(id)
      return next
    })
  }

  /** The list has no page text: read the page first, so saving the dialog keeps it (and its place in the book). */
  async function edit(c: EnChapterSummary) {
    setError(null)
    try {
      const page = await getEnChapter(c.id)
      setDialog({
        id: c.id,
        input: { code: page.code, title: page.title, area: page.area, notes: page.notes, parentId: page.parentId, bookPage: page.bookPage },
      })
    } catch (e) {
      setError(errorMessage(e))
    }
  }

  async function remove(c: EnChapterSummary) {
    const yes = await confirm({
      title: `Ștergi ${c.code}?`,
      body: 'Se poate șterge doar o pagină fără poze, fără modele și fără subcapitole.',
      confirmLabel: 'Șterge capitolul',
      destructive: true,
    })
    if (yes) await run(() => deleteEnChapter(c.id))
  }

  return (
    <div className="p-4">
      <div className="mx-auto max-w-4xl space-y-4">
        <PageHeader
          title="Capitole EN"
          description="Un capitol are poze cu materia (paginile cărții, în ordine) și modele rezolvate; subcapitolele lui sunt linkuri către pagina unde încep."
          breadcrumbs={[{ label: 'Clase', to: '/admin/content' }, { label: 'Capitole EN' }]}
          actions={
            <>
              <Link to="/admin/en-chapters/legaturi" className={buttonVariants({ variant: 'outline' })}>Legături automate</Link>
              <Button onClick={() => setDialog('new')}>Adaugă capitol</Button>
            </>
          }
        />
        {error && <p className="text-sm text-destructive">{error}</p>}
        {!chapters && !error && <LoadingCards />}

        {chapters &&
          EN_AREAS.map((area) => {
            const rows = treeRows(chapters, area.value, open)
            if (rows.length === 0) return null
            return (
              <HomeSection key={area.value} title={area.label}>
                <Card>
                  <CardContent className="divide-y py-1">
                    {rows.map(({ chapter: c, depth, first, last, childCount }) => (
                      <div
                        key={c.id}
                        data-testid={`en-chapter-row-${c.id}`}
                        className="flex flex-wrap items-center gap-x-3 gap-y-1 py-2"
                        style={{ paddingLeft: `${depth * 1.5}rem` }}
                      >
                        <Link to={`/admin/en-chapters/${c.id}`} className={`min-w-0 flex-1 hover:underline ${depth === 0 ? 'font-medium' : 'text-sm'}`}>
                          <span className="mr-1 font-mono text-sm text-muted-foreground">{c.code}</span>{' '}
                          {c.title}
                        </Link>
                        {depth === 0 && childCount > 0 && (
                          <Button
                            size="sm"
                            variant="ghost"
                            aria-expanded={open.has(c.id)}
                            aria-label={`${open.has(c.id) ? 'Ascunde' : 'Arată'} subcapitolele lui ${c.code}`}
                            onClick={() => toggle(c.id)}
                          >
                            {open.has(c.id) ? '▾' : '▸'} {roCount(descendantCount(chapters, c.id), 'subcapitol', 'subcapitole')}
                          </Button>
                        )}
                        <span className="text-xs text-muted-foreground">
                          {c.parentId === null ? enPageContents(c) : sectionStatusLabel(c, chapters)}
                        </span>
                        <div className="flex gap-1">
                          <Button size="sm" variant="ghost" aria-label={`Mută ${c.code} mai sus`} disabled={busy || first}
                            onClick={() => run(() => moveEnChapter(c.id, 'UP'))}>↑</Button>
                          <Button size="sm" variant="ghost" aria-label={`Mută ${c.code} mai jos`} disabled={busy || last}
                            onClick={() => run(() => moveEnChapter(c.id, 'DOWN'))}>↓</Button>
                          <Button size="sm" variant="outline" aria-label={`Editează ${c.code}`} onClick={() => edit(c)}>Editează</Button>
                          <Button size="sm" variant="outline" aria-label={`Șterge ${c.code}`} disabled={busy} onClick={() => remove(c)}>Șterge</Button>
                        </div>
                      </div>
                    ))}
                  </CardContent>
                </Card>
              </HomeSection>
            )
          })}
      </div>

      {dialog !== null && chapters && (
        <EnChapterDialog
          open
          onClose={() => setDialog(null)}
          initial={dialog === 'new' ? null : dialog.input}
          chapters={chapters}
          selfId={dialog === 'new' ? null : dialog.id}
          onSubmit={async (input) => {
            if (dialog === 'new') {
              await createEnChapter(input)
            } else {
              await updateEnChapter(dialog.id, input)
            }
            await load()
          }}
        />
      )}
      {confirmDialog}
    </div>
  )
}

/** "pag. 12 · în materie": the section's page is among the chapter's uploaded photos; otherwise it says it is not. */
function sectionStatusLabel(section: EnChapterSummary, chapters: EnChapterSummary[]): string {
  const status = sectionPageStatus(section, chapters)
  if (status === 'no-page') return 'fără pagină'
  return `pag. ${section.bookPage} · ${status === 'in-materia' ? 'în materie' : 'pagina nu e încărcată'}`
}

/** Sections and points under a chapter, however deep. */
function descendantCount(chapters: EnChapterSummary[], id: number): number {
  const direct = chapters.filter((c) => c.parentId === id)
  return direct.reduce((sum, c) => sum + 1 + descendantCount(chapters, c.id), 0)
}
