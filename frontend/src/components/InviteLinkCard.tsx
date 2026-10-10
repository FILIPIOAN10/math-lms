import { useRef, useState } from 'react'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { createInvite as createInviteRequest, type Invite, type Role } from '@/lib/api'

// Admin invites are deliberately not offered: a link that mints a teacher account is too
// powerful to be forwarded around. Teachers come in through ADMIN_EMAILS instead.
const INVITABLE_ROLES: { role: Role; label: string }[] = [
  { role: 'STUDENT', label: 'Elev' },
  { role: 'PARENT', label: 'Părinte' },
]

const roleLabel = (role: Role) => INVITABLE_ROLES.find((r) => r.role === role)?.label ?? role

interface Props {
  /** Injected in tests; defaults to the real API call. */
  createInvite?: (role: Role) => Promise<Invite>
}

export function InviteLinkCard({ createInvite = createInviteRequest }: Props) {
  const [role, setRole] = useState<Role>('STUDENT')
  const [invite, setInvite] = useState<Invite | null>(null)
  const [generating, setGenerating] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [copied, setCopied] = useState(false)
  const urlInput = useRef<HTMLInputElement>(null)

  async function handleGenerate() {
    setError(null)
    setGenerating(true)
    try {
      setInvite(await createInvite(role))
      setCopied(false)
    } catch {
      setInvite(null)
      setError('Nu am putut genera linkul. Reîncearcă.')
    } finally {
      setGenerating(false)
    }
  }

  async function handleCopy() {
    if (!invite) {
      return
    }
    setError(null)
    try {
      await navigator.clipboard.writeText(invite.url)
      setCopied(true)
    } catch {
      // Clipboard access can be refused (permissions, embedded browsers); fall back to a
      // selection the admin only has to copy.
      urlInput.current?.focus()
      urlInput.current?.select()
      setError('Browserul nu permite copierea automată — linkul e selectat, apasă Ctrl+C.')
    }
  }

  return (
    <Card>
      <CardHeader>
        <CardTitle className="text-xl">Invită utilizatori</CardTitle>
        <CardDescription>
          Generează un link de înregistrare și trimite-l elevului sau părintelui. Linkul e valabil 7 zile
          și poate fi folosit de mai multe persoane; după înregistrare și confirmarea emailului, contul
          apare la „Conturi în așteptare”, unde îl aprobi.
        </CardDescription>
      </CardHeader>
      <CardContent className="space-y-4">
        <div className="flex flex-wrap items-end gap-2">
          <div className="space-y-1">
            <Label htmlFor="invite-role">Rol</Label>
            <select
              id="invite-role"
              data-testid="invite-role-select"
              className="h-9 rounded-lg border border-border bg-background px-2 text-sm outline-none focus-visible:border-ring focus-visible:ring-3 focus-visible:ring-ring/50 disabled:opacity-50"
              value={role}
              disabled={generating}
              onChange={(e) => setRole(e.target.value as Role)}
            >
              {INVITABLE_ROLES.map((r) => (
                <option key={r.role} value={r.role}>
                  {r.label}
                </option>
              ))}
            </select>
          </div>
          <Button data-testid="invite-generate" disabled={generating} onClick={handleGenerate}>
            {generating ? 'Se generează…' : 'Generează link'}
          </Button>
        </div>

        {error && (
          <p role="alert" className="text-sm text-destructive">
            {error}
          </p>
        )}

        {invite && (
          <div className="space-y-1">
            <Label htmlFor="invite-url">Link de invitație</Label>
            <p className="text-sm text-muted-foreground">
              Pentru rolul: <span data-testid="invite-role">{roleLabel(invite.role)}</span>
            </p>
            <div className="flex gap-2">
              <Input
                id="invite-url"
                ref={urlInput}
                data-testid="invite-url"
                readOnly
                value={invite.url}
                onFocus={(e) => e.currentTarget.select()}
              />
              <Button variant="outline" data-testid="invite-copy" onClick={handleCopy}>
                {copied ? 'Copiat ✓' : 'Copiază'}
              </Button>
            </div>
          </div>
        )}
      </CardContent>
    </Card>
  )
}
