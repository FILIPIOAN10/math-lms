import { useEffect, useState } from 'react'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { useAuth } from '@/context/AuthContext'
import {
  approveUser,
  listPendingUsers,
  rejectUser,
  type PendingUser,
  type Role,
} from '@/lib/api'

const ROLE_OPTIONS: Role[] = ['STUDENT', 'PARENT', 'ADMIN']

export function AdminPendingPage() {
  const { logout } = useAuth()
  const [pending, setPending] = useState<PendingUser[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [actionError, setActionError] = useState<string | null>(null)
  // Role the admin picks per row (defaults to the requested role, else STUDENT).
  const [roleById, setRoleById] = useState<Record<number, Role>>({})
  // Rows with an in-flight approve/reject, so we can disable their buttons.
  const [busyIds, setBusyIds] = useState<Set<number>>(new Set())

  useEffect(() => {
    listPendingUsers()
      .then((users) => {
        setPending(users)
        setRoleById(
          Object.fromEntries(users.map((u) => [u.id, u.requestedRole ?? 'STUDENT'])),
        )
      })
      .catch(() => setError('Nu am putut încărca lista de conturi în așteptare.'))
      .finally(() => setLoading(false))
  }, [])

  function setBusy(id: number, busy: boolean) {
    setBusyIds((prev) => {
      const next = new Set(prev)
      if (busy) {
        next.add(id)
      } else {
        next.delete(id)
      }
      return next
    })
  }

  function removeRow(id: number) {
    setPending((prev) => prev.filter((u) => u.id !== id))
  }

  async function handleApprove(id: number) {
    setActionError(null)
    setBusy(id, true)
    try {
      await approveUser(id, roleById[id] ?? 'STUDENT')
      removeRow(id)
    } catch {
      setActionError('Aprobarea a eșuat. Reîncearcă.')
    } finally {
      setBusy(id, false)
    }
  }

  async function handleReject(id: number) {
    setActionError(null)
    setBusy(id, true)
    try {
      await rejectUser(id)
      removeRow(id)
    } catch {
      setActionError('Respingerea a eșuat. Reîncearcă.')
    } finally {
      setBusy(id, false)
    }
  }

  return (
    <div className="min-h-screen bg-muted p-4">
      <div className="mx-auto max-w-4xl">
        <Card>
          <CardHeader className="flex-row items-start justify-between gap-4">
            <div className="space-y-1">
              <CardTitle className="text-2xl">Conturi în așteptare</CardTitle>
              <CardDescription>
                Aprobă sau respinge conturile care și-au confirmat emailul și așteaptă decizia ta.
              </CardDescription>
            </div>
            <Button variant="outline" size="sm" onClick={logout}>
              Logout
            </Button>
          </CardHeader>
          <CardContent className="space-y-4">
            {loading && <p className="text-muted-foreground">Se încarcă...</p>}

            {!loading && error && <p className="text-sm text-destructive">{error}</p>}

            {!loading && !error && pending.length === 0 && (
              <p className="text-muted-foreground">Niciun cont în așteptare. 🎉</p>
            )}

            {actionError && <p className="text-sm text-destructive">{actionError}</p>}

            {!loading && !error && pending.length > 0 && (
              <div className="overflow-x-auto">
                <table className="w-full border-collapse text-sm">
                  <thead>
                    <tr className="border-b text-left text-muted-foreground">
                      <th className="py-2 pr-4 font-medium">Nume</th>
                      <th className="py-2 pr-4 font-medium">Email</th>
                      <th className="py-2 pr-4 font-medium">Rol cerut</th>
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
                          <td className="py-3 pr-4 text-muted-foreground">
                            {u.requestedRole ?? '—'}
                          </td>
                          <td className="py-3 pr-4">
                            <select
                              className="h-8 rounded-lg border border-border bg-background px-2 text-sm outline-none focus-visible:border-ring focus-visible:ring-3 focus-visible:ring-ring/50 disabled:opacity-50"
                              value={roleById[u.id] ?? 'STUDENT'}
                              disabled={busy}
                              onChange={(e) =>
                                setRoleById((prev) => ({
                                  ...prev,
                                  [u.id]: e.target.value as Role,
                                }))
                              }
                            >
                              {ROLE_OPTIONS.map((role) => (
                                <option key={role} value={role}>
                                  {role}
                                </option>
                              ))}
                            </select>
                          </td>
                          <td className="py-3">
                            <div className="flex gap-2">
                              <Button
                                size="sm"
                                disabled={busy}
                                onClick={() => handleApprove(u.id)}
                              >
                                Aprobă
                              </Button>
                              <Button
                                size="sm"
                                variant="destructive"
                                disabled={busy}
                                onClick={() => handleReject(u.id)}
                              >
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
            )}
          </CardContent>
        </Card>
      </div>
    </div>
  )
}
