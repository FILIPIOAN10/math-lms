import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { Button, buttonVariants } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { listActiveUsers, linkParent, type AdminUserSummary } from '@/lib/api'

export function AdminLinksPage() {
  const [students, setStudents] = useState<AdminUserSummary[]>([])
  const [parents, setParents] = useState<AdminUserSummary[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [actionError, setActionError] = useState<string | null>(null)
  // Parent the admin picked per student row (parent id, or '' for none chosen yet).
  const [selectedParent, setSelectedParent] = useState<Record<number, number | ''>>({})
  const [busyIds, setBusyIds] = useState<Set<number>>(new Set())

  useEffect(() => {
    Promise.all([listActiveUsers('STUDENT'), listActiveUsers('PARENT')])
      .then(([studentList, parentList]) => {
        setStudents(studentList)
        setParents(parentList)
      })
      .catch(() => setError('Nu am putut încărca studenții și părinții.'))
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

  async function handleLink(studentId: number) {
    const parentId = selectedParent[studentId]
    if (parentId === '' || parentId === undefined) {
      return
    }
    setActionError(null)
    setBusy(studentId, true)
    try {
      await linkParent(studentId, parentId)
      // linkParent returns the student without parent details, so reflect the choice
      // from the parent list we already hold.
      const parent = parents.find((p) => p.id === parentId)
      setStudents((prev) =>
        prev.map((s) =>
          s.id === studentId
            ? { ...s, parentId, parentName: parent?.fullName ?? s.parentName }
            : s,
        ),
      )
    } catch {
      setActionError('Legarea a eșuat. Reîncearcă.')
    } finally {
      setBusy(studentId, false)
    }
  }

  return (
    <div className="min-h-screen bg-muted p-4">
      <div className="mx-auto max-w-4xl">
        <Card>
          <CardHeader className="flex-row items-start justify-between gap-4">
            <div className="space-y-1">
              <CardTitle className="text-2xl">Leagă părinți de studenți</CardTitle>
              <CardDescription>
                Alege pentru fiecare student părintele căruia îi aparține.
              </CardDescription>
            </div>
            <Link to="/admin/pending" className={buttonVariants({ variant: 'outline', size: 'sm' })}>
              Conturi în așteptare
            </Link>
          </CardHeader>
          <CardContent className="space-y-4">
            {loading && <p className="text-muted-foreground">Se încarcă...</p>}

            {!loading && error && <p className="text-sm text-destructive">{error}</p>}

            {!loading && !error && students.length === 0 && (
              <p className="text-muted-foreground">Niciun student activ.</p>
            )}

            {!loading && !error && students.length > 0 && parents.length === 0 && (
              <p className="text-sm text-muted-foreground">
                Nu există încă niciun părinte activ de legat. Aprobă întâi un cont ca PARENT.
              </p>
            )}

            {actionError && <p className="text-sm text-destructive">{actionError}</p>}

            {!loading && !error && students.length > 0 && (
              <div className="overflow-x-auto">
                <table className="w-full border-collapse text-sm">
                  <thead>
                    <tr className="border-b text-left text-muted-foreground">
                      <th className="py-2 pr-4 font-medium">Student</th>
                      <th className="py-2 pr-4 font-medium">Email</th>
                      <th className="py-2 pr-4 font-medium">Părinte curent</th>
                      <th className="py-2 font-medium">Leagă de</th>
                    </tr>
                  </thead>
                  <tbody>
                    {students.map((s) => {
                      const busy = busyIds.has(s.id)
                      const choice = selectedParent[s.id] ?? ''
                      return (
                        <tr key={s.id} className="border-b last:border-0">
                          <td className="py-3 pr-4">{s.fullName}</td>
                          <td className="py-3 pr-4">{s.email}</td>
                          <td className="py-3 pr-4 text-muted-foreground">
                            {s.parentName ?? '—'}
                          </td>
                          <td className="py-3">
                            <div className="flex gap-2">
                              <select
                                className="h-8 rounded-lg border border-border bg-background px-2 text-sm outline-none focus-visible:border-ring focus-visible:ring-3 focus-visible:ring-ring/50 disabled:opacity-50"
                                value={choice}
                                disabled={busy || parents.length === 0}
                                onChange={(e) =>
                                  setSelectedParent((prev) => ({
                                    ...prev,
                                    [s.id]: e.target.value === '' ? '' : Number(e.target.value),
                                  }))
                                }
                              >
                                <option value="">Alege un părinte…</option>
                                {parents.map((p) => (
                                  <option key={p.id} value={p.id}>
                                    {p.fullName} ({p.email})
                                  </option>
                                ))}
                              </select>
                              <Button
                                size="sm"
                                disabled={busy || choice === ''}
                                onClick={() => handleLink(s.id)}
                              >
                                Leagă
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
