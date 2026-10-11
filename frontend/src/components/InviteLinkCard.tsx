import { useState } from 'react'
import { CopyLinkField } from '@/components/CopyLinkField'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
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

  async function handleGenerate() {
    setError(null)
    setGenerating(true)
    try {
      setInvite(await createInvite(role))
    } catch {
      setInvite(null)
      setError('Nu am putut genera linkul. Reîncearcă.')
    } finally {
      setGenerating(false)
    }
  }

  return (
    <Card>
      <CardHeader>
        <CardTitle className="text-xl">Link general (cu aprobare)</CardTitle>
        <CardDescription>
          Mai simplu: pentru elevi folosește <strong>Invită elevi</strong> din Clase (pe rândul clasei), iar pentru
          părinți <strong>Invită părintele</strong> din lista de mai jos — contul intră direct în clasă / se leagă
          singur, fără aprobare. Linkul general de aici nu știe clasa: e valabil 7 zile, îl pot folosi mai multe
          persoane, iar fiecare cont apare sus, la „Conturi de aprobat”, unde îl aprobi, înscrii și legi tu.
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
            <CopyLinkField
              key={invite.url}
              id="invite-url"
              label="Link de invitație"
              url={invite.url}
              inputTestId="invite-url"
              copyTestId="invite-copy"
            />
            <p className="text-sm text-muted-foreground">
              Pentru rolul: <span data-testid="invite-role">{roleLabel(invite.role)}</span>
            </p>
          </div>
        )}
      </CardContent>
    </Card>
  )
}
