import { useEffect, useState } from 'react'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { approveUser, listPendingUsers, rejectUser, type PendingUser, type Role } from '@/lib/api'
import { roleLabel } from '@/lib/roles'

const ROLE_OPTIONS: Role[] = ['STUDENT', 'PARENT', 'TEACHER', 'ADMIN']

const selectClass =
  'h-8 rounded-lg border border-border bg-background px-2 text-sm outline-none focus-visible:border-ring focus-visible:ring-3 focus-visible:ring-ring/50 disabled:opacity-50'

/**
 * Accounts that confirmed their email and wait for the teacher (they came through a general link or Google; class and
 * parent links skip this). Shown on "Elevi și părinți" only while someone waits. `onApproved` lets the page refresh its
 * list, since an approved student or parent belongs there next.
 */
export function PendingAccountsCard({ onApproved }: { onApproved?: () => void }) {
  const [pending, setPending] = useState<PendingUser[] | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [actionError, setActionError] = useState<string | null>(null)
  // Role the teacher picks per row (defaults to the requested role, else STUDENT).
  const [roleById, setRoleById] = useState<Record<number, Role>>({})
  const [busyIds, setBusyIds] = useState<Set<number>>(new Set())

  useEffect(() => {
    listPendingUsers()
      .then((users) => {
        setPending(users)
        setRoleById(Object.fromEntries(users.map((u) => [u.id, u.requestedRole ?? 'STUDENT'])))
      })
      .catch(() => setError('Nu am putut încărca conturile în așteptare.'))
  }, [])

  function setBusy(id: number, busy: boolean) {
    setBusyIds((prev) => {
      const next = new Set(prev)
      if (busy) next.add(id)
      else next.delete(id)
      return next
    })
  }

  async function decide(id: number, approve: boolean) {
    setActionError(null)
    setBusy(id, true)
    try {
      if (approve) await approveUser(id, roleById[id] ?? 'STUDENT')
      else await rejectUser(id)
      setPending((prev) => (prev ?? []).filter((u) => u.id !== id))
      if (approve) onApproved?.()
    } catch {
      setActionError(approve ? 'Aprobarea a eșuat. Reîncearcă.' : 'Respingerea a eșuat. Reîncearcă.')
    } finally {
      setBusy(id, false)
    }
  }

  if (error) return <p className="text-sm text-destructive">{error}</p>
  if (!pending || pending.length === 0) return null

  return (
    <Card data-testid="pending-accounts">
      <CardHeader>
        <CardTitle className="text-xl">Conturi de aprobat ({pending.length})</CardTitle>
        <CardDescription>
          Au venit printr-un link general sau prin Google și și-au confirmat emailul. Alege rolul și aprobă-le.
        </CardDescription>
      </CardHeader>
      <CardContent className="space-y-3">
        {actionError && <p className="text-sm text-destructive">{actionError}</p>}
        <div className="overflow-x-auto">
          <table className="w-full border-collapse text-sm">
            <thead>
              <tr className="border-b text-left text-muted-foreground">
                <th className="py-2 pr-4 font-medium">Nume</th>
                <th className="py-2 pr-4 font-medium">Email</th>
                <th className="py-2 pr-4 font-medium">A cerut</th>
                <th className="py-2 pr-4 font-medium">Rol la aprobare</th>
                <th className="py-2 font-medium">Acțiuni</th>
              </tr>
            </thead>
            <tbody>
              {pending.map((u) => {
                const busy = busyIds.has(u.id)
                return (
                  <tr key={u.id} className="border-b last:border-0">
                    <td className="py-3 pr-4">{u.fullName}</td>
                    <td className="py-3 pr-4">{u.email}</td>
                    <td className="py-3 pr-4 text-muted-foreground">{u.requestedRole ? roleLabel(u.requestedRole) : '—'}</td>
                    <td className="py-3 pr-4">
                      <select
                        className={selectClass}
                        aria-label={`Rolul lui ${u.fullName}`}
                        value={roleById[u.id] ?? 'STUDENT'}
                        disabled={busy}
                        onChange={(e) => setRoleById((prev) => ({ ...prev, [u.id]: e.target.value as Role }))}
                      >
                        {ROLE_OPTIONS.map((role) => (
                          <option key={role} value={role}>
                            {roleLabel(role)}
                          </option>
                        ))}
                      </select>
                    </td>
                    <td className="py-3">
                      <div className="flex gap-2">
                        <Button size="sm" disabled={busy} onClick={() => decide(u.id, true)}>
                          Aprobă
                        </Button>
                        <Button size="sm" variant="destructive" disabled={busy} onClick={() => decide(u.id, false)}>
                          Respinge
                        </Button>
                      </div>
                    </td>
                  </tr>
                )
              })}
            </tbody>
          </table>
        </div>
      </CardContent>
    </Card>
  )
}
