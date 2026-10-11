import { useEffect, useState, type FormEvent } from 'react'
import { CopyLinkField } from '@/components/CopyLinkField'
import { Button } from '@/components/ui/button'
import { Dialog } from '@/components/ui/dialog'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import {
  createClassInvite as createClassInviteRequest,
  listClassInvites as listClassInvitesRequest,
  revokeInviteLink as revokeInviteLinkRequest,
  type InviteLink,
  type SchoolClass,
} from '@/lib/api'
import { errorMessage } from '@/lib/errors'
import { formatDate } from '@/lib/format'

const DEFAULT_USES = 30
const MAX_USES = 200

interface Props {
  open: boolean
  onClose: () => void
  schoolClass: SchoolClass
  /** Injected in tests; default to the real API calls. */
  listClassInvites?: (classId: number) => Promise<InviteLink[]>
  createClassInvite?: (classId: number, maxUses?: number) => Promise<InviteLink>
  revokeInviteLink?: (id: number) => Promise<void>
}

/**
 * "Invită elevi" on a class (T1): one link for the whole class. Whoever registers through it is enrolled in the class
 * and active as soon as their email is confirmed — nothing to approve, enrol or link afterwards. The cap, the 7-day
 * expiry and "Oprește" bound what a forwarded link can do.
 */
export function ClassInviteDialog({
  open,
  onClose,
  schoolClass,
  listClassInvites = listClassInvitesRequest,
  createClassInvite = createClassInviteRequest,
  revokeInviteLink = revokeInviteLinkRequest,
}: Props) {
  const [links, setLinks] = useState<InviteLink[] | null>(null)
  const [maxUses, setMaxUses] = useState(String(DEFAULT_USES))
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    listClassInvites(schoolClass.id)
      .then(setLinks)
      .catch((e) => {
        setLinks([])
        setError(errorMessage(e))
      })
  }, [schoolClass.id, listClassInvites])

  const cap = Number(maxUses)
  const capValid = Number.isInteger(cap) && cap >= 1 && cap <= MAX_USES

  async function handleCreate(e: FormEvent) {
    e.preventDefault()
    if (!capValid) return
    setError(null)
    setBusy(true)
    try {
      const created = await createClassInvite(schoolClass.id, cap)
      setLinks((prev) => [created, ...(prev ?? [])])
    } catch (err) {
      setError(errorMessage(err))
    } finally {
      setBusy(false)
    }
  }

  async function handleRevoke(id: number) {
    setError(null)
    try {
      await revokeInviteLink(id)
      setLinks((prev) => (prev ?? []).filter((l) => l.id !== id))
    } catch (err) {
      setError(errorMessage(err))
    }
  }

  return (
    <Dialog
      open={open}
      onOpenChange={(o) => !o && onClose()}
      title={`Invită elevi — ${schoolClass.name}`}
      description={`Trimite linkul elevilor. Cine se înregistrează prin el intră direct în ${schoolClass.name}, fără aprobare, după ce își confirmă emailul.`}
    >
      <div className="space-y-4">
        {links === null && <p className="text-sm text-muted-foreground">Se încarcă…</p>}

        {links !== null && links.length === 0 && (
          <p className="text-sm text-muted-foreground">Niciun link activ. Generează unul mai jos.</p>
        )}

        {links !== null && links.length > 0 && (
          <ul className="space-y-3" data-testid="class-invites">
            {links.map((link) => {
              const full = link.uses >= link.maxUses
              return (
                <li key={link.id} className="space-y-1 rounded-lg border border-border p-3">
                  <CopyLinkField id={`class-invite-${link.id}`} label="Link pentru clasă" url={link.url} />
                  <div className="flex flex-wrap items-center justify-between gap-2 text-sm text-muted-foreground">
                    <span>
                      Folosit {link.uses} / {link.maxUses} · expiră {formatDate(link.expiresAt)}
                      {full && <strong className="text-destructive"> · plin — generează altul</strong>}
                    </span>
                    <Button size="xs" variant="destructive" onClick={() => handleRevoke(link.id)}>
                      Oprește linkul
                    </Button>
                  </div>
                </li>
              )
            })}
          </ul>
        )}

        <form className="flex flex-wrap items-end gap-2" onSubmit={handleCreate}>
          <div className="space-y-1">
            <Label htmlFor="invite-max-uses">Câți elevi îl pot folosi</Label>
            <Input
              id="invite-max-uses"
              type="number"
              min={1}
              max={MAX_USES}
              className="w-28"
              value={maxUses}
              onChange={(e) => setMaxUses(e.target.value)}
            />
          </div>
          <Button type="submit" disabled={busy || !capValid}>
            {busy ? 'Se generează…' : 'Generează link nou'}
          </Button>
        </form>
        {!capValid && <p className="text-sm text-destructive">Alege un număr între 1 și {MAX_USES}.</p>}

        {error && (
          <p role="alert" className="text-sm text-destructive">
            {error}
          </p>
        )}

        <div className="flex justify-end">
          <Button variant="outline" onClick={onClose}>
            Închide
          </Button>
        </div>
      </div>
    </Dialog>
  )
}
