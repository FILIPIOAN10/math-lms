import { useEffect, useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { Button } from '@/components/ui/button'
import { Card, CardContent } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { PageHeader } from '@/components/PageHeader'
import {
  getAuditLog,
  listActiveUsers,
  type AdminUserSummary,
  type AuditCategory,
  type AuditEntry,
  type AuditQuery,
} from '@/lib/api'
import { AUDIT_CATEGORIES, auditActionLabel, auditActor, auditSubject, linksToStudent } from '@/lib/audit'
import { errorMessage } from '@/lib/errors'
import { formatDate } from '@/lib/format'

const selectClass =
  'h-9 rounded-lg border border-border bg-background px-2 text-sm outline-none focus-visible:border-ring focus-visible:ring-3 focus-visible:ring-ring/50 disabled:opacity-50'

const PAGE_SIZE = 50

/** One read of the journal, tagged with the filters (`key`) it answers. */
interface Loaded {
  key: string
  entries: AuditEntry[]
  nextBefore: number | null
  error: string | null
}

/** The filters live in the address, so "Istoric" on a student's page opens this page already filtered. */
function queryOf(params: URLSearchParams): AuditQuery {
  const category = params.get('category') as AuditCategory | null
  const student = Number(params.get('student'))
  return {
    category: category && AUDIT_CATEGORIES.some((c) => c.value === category) ? category : undefined,
    studentId: Number.isInteger(student) && student > 0 ? student : undefined,
    from: params.get('from') || undefined,
    to: params.get('to') || undefined,
  }
}

function AuditRow({ entry }: { entry: AuditEntry }) {
  const subject = auditSubject(entry)
  const isAccount = entry.action === 'ACCOUNT_APPROVED' || entry.action === 'ACCOUNT_REJECTED'
  return (
    <li className="space-y-0.5 py-3" data-testid="audit-row">
      <p className="text-sm">
        <span className="font-medium">{auditActionLabel(entry.action)}</span>
        {subject && <span> · {subject}</span>}
      </p>
      {entry.details && <p className="text-sm">{entry.details}</p>}
      <p className="flex flex-wrap gap-x-3 gap-y-0.5 text-xs text-muted-foreground">
        <span>{formatDate(entry.occurredAt)}</span>
        <span>de {auditActor(entry)}</span>
        {entry.studentName && (
          <span>
            {isAccount ? 'cont: ' : 'elev: '}
            {linksToStudent(entry) ? (
              <Link to={`/admin/students/${entry.studentId}`} className="text-foreground hover:underline">
                {entry.studentName}
              </Link>
            ) : (
              <span className="text-foreground">{entry.studentName}</span>
            )}
          </span>
        )}
        {entry.relatedName && (
          <span>
            părinte: <span className="text-foreground">{entry.relatedName}</span>
          </span>
        )}
        {entry.category === 'GRADING' && entry.entityId !== null && (
          <Link to={`/admin/grading?attempt=${entry.entityId}`} className="text-primary hover:underline">
            Vezi lucrarea
          </Link>
        )}
      </p>
    </li>
  )
}

/**
 * F1: who changed what, and when - grades, tests, homework, content, accounts. Answers a parent's "who changed my
 * child's grade?" with a row written in the same transaction as the change. Newest first, 50 at a time.
 */
export function AdminAuditPage() {
  const [params, setParams] = useSearchParams()
  const query = queryOf(params)
  const queryKey = JSON.stringify(query)
  // The list remembers which filters it was read for: after a filter change the old list is not shown as the new one.
  const [result, setResult] = useState<Loaded | null>(null)
  const [loadingMore, setLoadingMore] = useState(false)
  const [students, setStudents] = useState<AdminUserSummary[] | null>(null)

  useEffect(() => {
    let cancelled = false
    getAuditLog({ ...(JSON.parse(queryKey) as AuditQuery), limit: PAGE_SIZE })
      .then((page) => !cancelled && setResult({ key: queryKey, ...page, error: null }))
      .catch((e) => !cancelled && setResult({ key: queryKey, entries: [], nextBefore: null, error: errorMessage(e) }))
    return () => {
      cancelled = true
    }
  }, [queryKey])

  const current = result?.key === queryKey ? result : null
  const entries = current && !current.error ? current.entries : null
  const nextBefore = current?.nextBefore ?? null
  const error = current?.error ?? null

  useEffect(() => {
    // Only the filter's list: without it the page still works, just without the student picker.
    listActiveUsers('STUDENT').then(setStudents).catch(() => setStudents(null))
  }, [])

  const setFilter = (key: string, value: string) => {
    const next = new URLSearchParams(params)
    if (value) next.set(key, value)
    else next.delete(key)
    setParams(next, { replace: true })
  }

  const loadMore = () => {
    if (nextBefore === null) return
    setLoadingMore(true)
    getAuditLog({ ...query, before: nextBefore, limit: PAGE_SIZE })
      .then((page) => setResult((r) => r && r.key === queryKey
        ? { ...r, entries: [...r.entries, ...page.entries], nextBefore: page.nextBefore } : r))
      .catch((e) => setResult((r) => r && { ...r, error: errorMessage(e) }))
      .finally(() => setLoadingMore(false))
  }

  const filtered = query.category || query.studentId || query.from || query.to
  const studentKnown = !query.studentId || students?.some((s) => s.id === query.studentId)

  return (
    <div className="p-4">
      <div className="mx-auto max-w-3xl space-y-4">
        <PageHeader
          title="Jurnal"
          description="Cine a schimbat ce și când: note, teste, teme, conținut și conturi. Nimic de aici nu se poate modifica."
          breadcrumbs={[{ label: 'Acasă', to: '/' }, { label: 'Jurnal' }]}
        />

        <Card>
          <CardContent className="flex flex-wrap items-end gap-3 py-4">
            <div className="space-y-1">
              <Label htmlFor="audit-category">Ce</Label>
              <select
                id="audit-category"
                className={selectClass}
                value={query.category ?? ''}
                onChange={(e) => setFilter('category', e.target.value)}
              >
                <option value="">Tot</option>
                {AUDIT_CATEGORIES.map((c) => (
                  <option key={c.value} value={c.value}>{c.label}</option>
                ))}
              </select>
            </div>
            {students && (
              <div className="space-y-1">
                <Label htmlFor="audit-student">Elev</Label>
                <select
                  id="audit-student"
                  className={selectClass}
                  value={query.studentId ?? ''}
                  onChange={(e) => setFilter('student', e.target.value)}
                >
                  <option value="">Toți elevii</option>
                  {!studentKnown && <option value={query.studentId}>Elevul #{query.studentId}</option>}
                  {students.map((s) => (
                    <option key={s.id} value={s.id}>{s.fullName}</option>
                  ))}
                </select>
              </div>
            )}
            <div className="space-y-1">
              <Label htmlFor="audit-from">De la</Label>
              <Input id="audit-from" type="date" className="w-auto" value={query.from ?? ''}
                     onChange={(e) => setFilter('from', e.target.value)} />
            </div>
            <div className="space-y-1">
              <Label htmlFor="audit-to">Până la</Label>
              <Input id="audit-to" type="date" className="w-auto" value={query.to ?? ''}
                     onChange={(e) => setFilter('to', e.target.value)} />
            </div>
            {filtered && (
              <Button variant="ghost" size="sm" onClick={() => setParams(new URLSearchParams(), { replace: true })}>
                Șterge filtrele
              </Button>
            )}
          </CardContent>
        </Card>

        {error && <p className="text-sm text-destructive">{error}</p>}
        {!entries && !error && <p className="text-muted-foreground">Se încarcă...</p>}

        {entries && entries.length === 0 && (
          <Card>
            <CardContent className="py-4 text-sm text-muted-foreground">
              {filtered ? 'Nimic în jurnal pentru filtrele alese.' : 'Jurnalul e gol: nicio modificare încă.'}
            </CardContent>
          </Card>
        )}

        {entries && entries.length > 0 && (
          <Card>
            <CardContent className="py-1">
              <ul className="divide-y" aria-label="Modificări, cele mai noi primele">
                {entries.map((entry) => (
                  <AuditRow key={entry.id} entry={entry} />
                ))}
              </ul>
            </CardContent>
          </Card>
        )}

        {nextBefore !== null && entries && (
          <div className="flex justify-center">
            <Button variant="outline" onClick={loadMore} disabled={loadingMore}>
              {loadingMore ? 'Se încarcă...' : 'Mai vechi'}
            </Button>
          </div>
        )}
      </div>
    </div>
  )
}
