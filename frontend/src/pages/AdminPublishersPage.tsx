import { useEffect, useMemo, useState, type FormEvent } from 'react'
import { Link } from 'react-router-dom'
import { Button } from '@/components/ui/button'
import { Card, CardContent } from '@/components/ui/card'
import { Dialog } from '@/components/ui/dialog'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Textarea } from '@/components/ui/textarea'
import { ErrorState } from '@/components/ErrorState'
import { LoadingCards } from '@/components/LoadingCards'
import { PageHeader } from '@/components/PageHeader'
import { useConfirm } from '@/components/useConfirm'
import {
  createPublisher, deletePublisher, listPublishers, publisherLogoUrl, removePublisherLogo, setPublisherLogo,
  updatePublisher, type Publisher,
} from '@/lib/api'
import { errorMessage } from '@/lib/errors'
import { initials, roCount } from '@/lib/format'
import { shrinkImage } from '@/lib/image'

/** "2 teste", "niciun test": how many tests name a publisher. */
const testsLabel = (n: number) => (n === 0 ? 'niciun test' : roCount(n, 'test', 'teste'))

/** The logo, or the publisher's initials where it has none. */
export function PublisherLogo({ publisher }: { publisher: Publisher }) {
  return publisher.hasLogo ? (
    <img src={publisherLogoUrl(publisher)} alt={`Sigla ${publisher.name}`}
         className="size-12 shrink-0 rounded-md border bg-white object-contain p-1" />
  ) : (
    <span aria-hidden="true"
          className="flex size-12 shrink-0 items-center justify-center rounded-md border bg-muted text-sm font-semibold text-muted-foreground">
      {initials(publisher.name)}
    </span>
  )
}

/**
 * "Edituri": the publishers the administrators added by hand (none ships with the app). Each has a name, an optional
 * description and logo; a test names one of them in its "Editură" dropdown, or none for an official EN paper.
 */
export function AdminPublishersPage() {
  const [publishers, setPublishers] = useState<Publisher[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  // null = closed; { item: null } = a new one
  const [editing, setEditing] = useState<{ item: Publisher | null } | null>(null)
  const { confirm, dialog } = useConfirm()

  const reload = () =>
    listPublishers()
      .then((list) => { setPublishers(list); setError(null) })
      .catch((e) => setError(errorMessage(e)))
      .finally(() => setLoading(false))

  useEffect(() => { void reload() }, [])

  async function remove(p: Publisher) {
    const yes = await confirm({
      title: `Ștergi editura „${p.name}”?`,
      body: p.quizCount === 0 ? 'Niciun test nu o folosește.'
        : p.quizCount === 1 ? 'Testul care o folosește rămâne, fără editură.'
          : `Cele ${roCount(p.quizCount, 'test', 'teste')} rămân, fără editură.`,
      confirmLabel: 'Șterge editura',
      destructive: true,
    })
    if (!yes) return
    try {
      await deletePublisher(p.id)
      await reload()
    } catch (e) {
      setError(errorMessage(e))
    }
  }

  return (
    <div className="mx-auto w-full max-w-3xl space-y-4">
      <PageHeader
        title="Edituri"
        breadcrumbs={[{ label: 'Teste', to: '/admin/quizzes' }, { label: 'Edituri' }]}
        description="Editurile din care vin testele (Evaluarea Națională, clasa a VIII-a, matematică). Le adaugi tu; la fiecare test alegi editura sau „Fără editură” pentru un subiect oficial."
        actions={<Button size="sm" onClick={() => setEditing({ item: null })}>Adaugă editură</Button>}
      />
      {error && <ErrorState message={error} />}
      {loading && <LoadingCards />}
      {!loading && !error && publishers.length === 0 && (
        <p className="text-muted-foreground">Nicio editură încă. Adaugă prima cu „Adaugă editură”.</p>
      )}
      <ul className="space-y-3">
        {publishers.map((p) => (
          <li key={p.id}>
            <Card data-testid="publisher">
              <CardContent className="flex items-center gap-4 pt-4">
                <PublisherLogo publisher={p} />
                <div className="min-w-0 flex-1">
                  <Link to={`/admin/publishers/${p.id}`} className="font-medium hover:underline">{p.name}</Link>
                  {p.description && <p className="truncate text-sm text-muted-foreground">{p.description}</p>}
                  <p className="text-xs text-muted-foreground">
                    {p.books.length === 0 ? 'nicio carte' : roCount(p.books.length, 'carte', 'cărți')} · {testsLabel(p.quizCount)}
                  </p>
                </div>
                <div className="flex shrink-0 gap-2">
                  <Link to={`/admin/publishers/${p.id}`}
                        className="inline-flex h-8 items-center rounded-md border px-3 text-sm font-medium hover:bg-muted">
                    Cărți
                  </Link>
                  <Button size="sm" variant="outline" onClick={() => setEditing({ item: p })}>Editează</Button>
                  <Button size="sm" variant="ghost" className="text-destructive" onClick={() => void remove(p)}>Șterge</Button>
                </div>
              </CardContent>
            </Card>
          </li>
        ))}
      </ul>
      {editing && (
        <PublisherDialog item={editing.item} onClose={() => setEditing(null)} onSaved={() => { setEditing(null); void reload() }} />
      )}
      {dialog}
    </div>
  )
}

/** Add or edit one publisher: name, description, and the logo (chosen, kept, or taken off). */
function PublisherDialog({ item, onClose, onSaved }: {
  item: Publisher | null
  onClose: () => void
  onSaved: () => void
}) {
  const [name, setName] = useState(item?.name ?? '')
  const [description, setDescription] = useState(item?.description ?? '')
  // undefined = keep what is there; null = take it off; a File = the new one
  const [logo, setLogo] = useState<File | null | undefined>(undefined)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const preview = useMemo(() => (logo ? URL.createObjectURL(logo) : null), [logo])
  useEffect(() => () => { if (preview) URL.revokeObjectURL(preview) }, [preview])
  const shown = preview ?? (logo === undefined && item?.hasLogo ? publisherLogoUrl(item) : null)

  async function submit(e: FormEvent) {
    e.preventDefault()
    setError(null)
    setBusy(true)
    try {
      const input = { name: name.trim(), description: description.trim() || null }
      const saved = item ? await updatePublisher(item.id, input) : await createPublisher(input)
      if (logo) await setPublisherLogo(saved.id, await shrinkImage(logo, 512, 0.9))
      else if (logo === null && item?.hasLogo) await removePublisherLogo(saved.id)
      onSaved()
    } catch (err) {
      setError(errorMessage(err))
      setBusy(false)
    }
  }

  return (
    <Dialog open onOpenChange={(o) => !o && onClose()} title={item ? 'Editează editura' : 'Adaugă editură'}>
      <form className="flex flex-col gap-4" onSubmit={submit}>
        <div className="flex flex-col gap-2">
          <Label htmlFor="pname">Numele editurii</Label>
          <Input id="pname" value={name} maxLength={120} required autoFocus placeholder="ex. Art Klett"
                 onChange={(e) => setName(e.target.value)} />
        </div>
        <div className="flex flex-col gap-2">
          <Label htmlFor="pdesc">Descriere (opțional)</Label>
          <Textarea id="pdesc" value={description} maxLength={1000} rows={2} onChange={(e) => setDescription(e.target.value)} />
        </div>
        <div className="flex flex-col gap-2">
          <Label htmlFor="plogo">Siglă (opțional)</Label>
          {shown && (
            <div className="flex items-center gap-3">
              <img src={shown} alt="Sigla aleasă" className="size-16 rounded-md border bg-white object-contain p-1" />
              <Button type="button" size="xs" variant="ghost" onClick={() => setLogo(null)}>Scoate sigla</Button>
            </div>
          )}
          <input id="plogo" type="file" accept="image/png,image/jpeg,image/webp" className="text-sm"
                 onChange={(e) => setLogo(e.target.files?.[0] ?? undefined)} />
          <p className="text-xs text-muted-foreground">PNG, JPG sau WEBP, maxim 2 MB.</p>
        </div>
        {error && <p role="alert" className="text-sm text-destructive">{error}</p>}
        <div className="flex gap-2">
          <Button type="submit" disabled={busy || name.trim() === ''}>{busy ? 'Se salvează...' : 'Salvează'}</Button>
          <Button type="button" variant="outline" onClick={onClose} disabled={busy}>Anulează</Button>
        </div>
      </form>
    </Dialog>
  )
}
