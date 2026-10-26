import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { CopyLinkField } from '@/components/CopyLinkField'
import { InviteLinkCard } from '@/components/InviteLinkCard'
import { AdminInviteCard } from '@/components/AdminInviteCard'
import { PageHeader } from '@/components/PageHeader'
import { PendingAccountsCard } from '@/components/PendingAccountsCard'
import { useAuth } from '@/context/AuthContext'
import {
  createParentInvite,
  linkParent,
  listActiveUsers,
  type AdminUserSummary,
  type InviteLink,
} from '@/lib/api'
import { errorMessage } from '@/lib/errors'
import { formatDate } from '@/lib/format'
import { LoadingCards } from '@/components/LoadingCards'

/**
 * T6 "Elevi și părinți": everything about people in one place — accounts waiting for approval (only while someone
 * waits), every student with their parent (invite or link one; the name opens the student's page), and the general
 * invite link. New students join through "Invită elevi" on a class, so they need nothing here. A TEACHER sees only the
 * students of their own classes and can invite their parents; approving accounts, linking existing parents and the
 * invite links for other roles stay with the admins.
 */
export function AdminStudentsPage() {
  const { user } = useAuth()
  const isAdmin = user?.role === 'ADMIN'
  const [students, setStudents] = useState<AdminUserSummary[]>([])
  const [parents, setParents] = useState<AdminUserSummary[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [actionError, setActionError] = useState<string | null>(null)
  // Parent the admin picked per student row (parent id, or '' for none chosen yet).
  const [selectedParent, setSelectedParent] = useState<Record<number, number | ''>>({})
  const [busyIds, setBusyIds] = useState<Set<number>>(new Set())
  // The parent link minted for a student row in this visit, shown under the row until the page is left.
  const [parentLinks, setParentLinks] = useState<Record<number, InviteLink>>({})

  function loadPeople() {
    return Promise.all([listActiveUsers('STUDENT'), isAdmin ? listActiveUsers('PARENT') : Promise.resolve([])])
      .then(([studentList, parentList]) => {
        setStudents(studentList)
        setParents(parentList)
      })
      .catch(() => setError('Nu am putut încărca elevii și părinții.'))
      .finally(() => setLoading(false))
  }

  useEffect(() => {
    void loadPeople()
    // eslint-disable-next-line react-hooks/exhaustive-deps -- loads once per role
  }, [isAdmin])

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

  async function handleParentInvite(studentId: number) {
    setActionError(null)
    setBusy(studentId, true)
    try {
      const link = await createParentInvite(studentId)
      setParentLinks((prev) => ({ ...prev, [studentId]: link }))
    } catch (e) {
      setActionError(errorMessage(e))
    } finally {
      setBusy(studentId, false)
    }
  }

  return (
    <div className="p-4">
      <div className="mx-auto max-w-4xl space-y-4">
        <PageHeader
          title="Elevi și părinți"
          description="Elevii noi intră cu „Invită elevi” de pe rândul clasei (în Clase). Aici vezi fiecare elev, îi inviți sau îi legi părintele și aprobi conturile venite pe altă cale."
        />
        {isAdmin && <PendingAccountsCard onApproved={() => void loadPeople()} />}
        <Card>
          <CardHeader>
            <CardTitle className="text-xl">Elevii{loading ? '' : ` (${students.length})`}</CardTitle>
            <CardDescription>
              Numele deschide pagina elevului (teme, note, capitole, recapitulare). <strong>Invită părintele</strong> generează un link de o singură folosință, valabil 7 zile: părintele
              care își face cont prin el e legat automat de elev, fără aprobare. Dacă părintele are deja cont, alege-l
              din listă și apasă <strong>Leagă</strong>.
            </CardDescription>
          </CardHeader>
          <CardContent className="space-y-4">
            {loading && <LoadingCards />}

            {!loading && error && <p className="text-sm text-destructive">{error}</p>}

            {!loading && !error && students.length === 0 && (
              <p className="text-muted-foreground">
                Niciun elev încă. Deschide <Link to="/admin/content" className="text-primary hover:underline">Clase</Link> și
                apasă „Invită elevi” pe rândul clasei.
              </p>
            )}

            {isAdmin && !loading && !error && students.length > 0 && parents.length === 0 && (
              <p className="text-sm text-muted-foreground">
                Niciun părinte nu are încă un cont. Folosește <strong>Invită părintele</strong> pe rândul elevului.
              </p>
            )}

            {actionError && <p className="text-sm text-destructive">{actionError}</p>}

            {!loading && !error && students.length > 0 && (
              <div className="overflow-x-auto">
                <table className="w-full border-collapse text-sm">
                  <thead>
                    <tr className="border-b text-left text-muted-foreground">
                      <th className="py-2 pr-4 font-medium">Elev</th>
                      <th className="py-2 pr-4 font-medium">Email</th>
                      <th className="py-2 pr-4 font-medium">Părinte curent</th>
                      <th className="py-2 font-medium">{isAdmin ? 'Leagă de' : 'Părinte'}</th>
                    </tr>
                  </thead>
                  <tbody>
                    {students.map((s) => {
                      const busy = busyIds.has(s.id)
                      const choice = selectedParent[s.id] ?? ''
                      return (
                        <tr key={s.id} className="border-b last:border-0">
                          <td className="py-3 pr-4">
                            <Link to={`/admin/students/${s.id}`} className="font-medium hover:underline">
                              {s.fullName}
                            </Link>
                          </td>
                          <td className="py-3 pr-4">{s.email}</td>
                          <td className="py-3 pr-4 text-muted-foreground">
                            {s.parentName ?? '—'}
                          </td>
                          <td className="py-3">
                            <div className="flex flex-wrap gap-2">
                              {isAdmin && (
                                <>
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
                                </>
                              )}
                              {s.parentId === null && (
                                <Button
                                  size="sm"
                                  variant="outline"
                                  data-testid={`parent-invite-${s.id}`}
                                  disabled={busy}
                                  onClick={() => handleParentInvite(s.id)}
                                >
                                  Invită părintele
                                </Button>
                              )}
                            </div>
                            {parentLinks[s.id] && s.parentId === null && (
                              <div className="mt-2 max-w-md space-y-1">
                                <CopyLinkField
                                  key={parentLinks[s.id].url}
                                  id={`parent-invite-url-${s.id}`}
                                  label={`Link pentru părintele lui ${s.fullName}`}
                                  url={parentLinks[s.id].url}
                                />
                                <p className="text-xs text-muted-foreground">
                                  O singură folosință · expiră {formatDate(parentLinks[s.id].expiresAt)} · un link nou
                                  îl anulează pe acesta
                                </p>
                              </div>
                            )}
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
        {isAdmin && <AdminInviteCard />}

        {isAdmin && <InviteLinkCard />}
      </div>
    </div>
  )
}
