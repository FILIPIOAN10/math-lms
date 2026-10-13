import { useCallback, useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { Button } from '@/components/ui/button'
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
import { EN_AREAS, enPageContents, inArea } from '@/lib/enChapters'
import { errorMessage } from '@/lib/errors'

/**
 * The teacher's list of EN chapter pages (seeded from the official EN papers, see docs/EN-capitole.md): add, rename,
 * reorder within algebra or geometry, delete an empty one, and open a page to fill it.
 */
export function AdminEnChaptersPage() {
  const [chapters, setChapters] = useState<EnChapterSummary[] | null>(null)
  const [error, setError] = useState<string | null>(null)
  // null = closed; 'new' = adding; otherwise the chapter being edited, with its current values.
  const [dialog, setDialog] = useState<'new' | { id: number; input: EnChapterInput } | null>(null)
  const [busy, setBusy] = useState(false)
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

  /** The list has no page text: read the page first, so saving the dialog keeps it. */
  async function edit(c: EnChapterSummary) {
    setError(null)
    try {
      const page = await getEnChapter(c.id)
      setDialog({ id: c.id, input: { code: page.code, title: page.title, area: page.area, notes: page.notes } })
    } catch (e) {
      setError(errorMessage(e))
    }
  }

  async function remove(c: EnChapterSummary) {
    const yes = await confirm({
      title: `Ștergi capitolul ${c.code}?`,
      body: 'Se poate șterge doar un capitol fără poze și fără modele.',
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
          description="O pagină pentru fiecare capitol: poze cu materia și modele rezolvate. Elevul ajunge aici când greșește un exercițiu de la EN."
          breadcrumbs={[{ label: 'Clase', to: '/admin/content' }, { label: 'Capitole EN' }]}
          actions={<Button onClick={() => setDialog('new')}>Adaugă capitol</Button>}
        />
        {error && <p className="text-sm text-destructive">{error}</p>}
        {!chapters && !error && <p className="text-muted-foreground">Se încarcă...</p>}

        {chapters &&
          EN_AREAS.map((area) => {
            const list = inArea(chapters, area.value)
            if (list.length === 0) return null
            return (
              <HomeSection key={area.value} title={area.label}>
                <Card>
                  <CardContent className="divide-y py-1">
                    {list.map((c, i) => (
                      <div key={c.id} data-testid={`en-chapter-row-${c.id}`} className="flex flex-wrap items-center gap-x-3 gap-y-1 py-2">
                        <Link to={`/admin/en-chapters/${c.id}`} className="min-w-0 flex-1 font-medium hover:underline">
                          <span className="mr-1 font-mono text-sm text-muted-foreground">{c.code}</span>{' '}
                          {c.title}
                        </Link>
                        <span className="text-xs text-muted-foreground">{enPageContents(c)}</span>
                        <div className="flex gap-1">
                          <Button size="sm" variant="ghost" aria-label={`Mută ${c.code} mai sus`} disabled={busy || i === 0}
                            onClick={() => run(() => moveEnChapter(c.id, 'UP'))}>↑</Button>
                          <Button size="sm" variant="ghost" aria-label={`Mută ${c.code} mai jos`} disabled={busy || i === list.length - 1}
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

      {dialog !== null && (
        <EnChapterDialog
          open
          onClose={() => setDialog(null)}
          initial={dialog === 'new' ? null : dialog.input}
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
