import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { buttonVariants } from '@/components/ui/button'
import { Card, CardContent } from '@/components/ui/card'
import { HomeEmpty, HomeSection, StatTile } from '@/components/home/HomeBits'
import {
  listAssignments,
  listAttemptsForGrading,
  listPendingUsers,
  type AdminAttemptSummary,
  type AssignmentSummary,
} from '@/lib/api'
import { dueCountdown } from '@/lib/assignments'
import { errorMessage } from '@/lib/errors'
import { formatDate } from '@/lib/format'
import { upcomingDeadlines } from '@/lib/home'

const GRADING_PREVIEW = 5

interface TeacherData {
  toGrade: AdminAttemptSummary[]
  pending: number
  assignments: AssignmentSummary[]
}

/**
 * The teacher's start page answers "what waits for me?": three counts (papers to grade, accounts to approve, open
 * homework) that lead to where each is handled, the deadlines worth a look with how many handed in, and who waits
 * for a grade.
 */
export function TeacherHome() {
  const [data, setData] = useState<TeacherData | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [loadedAt] = useState(() => Date.now())

  useEffect(() => {
    let cancelled = false
    Promise.all([listAttemptsForGrading('SUBMITTED'), listPendingUsers(), listAssignments()])
      .then(([toGrade, pending, assignments]) => !cancelled && setData({ toGrade, pending: pending.length, assignments }))
      .catch((e) => !cancelled && setError(errorMessage(e)))
    return () => {
      cancelled = true
    }
  }, [])

  if (error) return <p className="text-sm text-destructive">{error}</p>
  if (!data) return <p className="text-muted-foreground">Se încarcă...</p>

  const open = data.assignments.filter((a) => !a.overdue).length
  const deadlines = upcomingDeadlines(data.assignments, loadedAt)

  return (
    <>
      <div className="grid grid-cols-3 gap-3">
        <StatTile to="/admin/grading" value={data.toGrade.length} label="Lucrări de corectat" urgent testId="tile-grading" />
        <StatTile to="/admin/pending" value={data.pending} label="Conturi de aprobat" urgent testId="tile-pending" />
        <StatTile to="/admin/assignments" value={open} label="Teme deschise" testId="tile-assignments" />
      </div>

      <HomeSection
        title="Termene"
        action={
          deadlines.length > 0 && (
            <Link to="/admin/assignments" className={buttonVariants({ variant: 'ghost', size: 'sm' })}>
              Toate temele
            </Link>
          )
        }
      >
        {deadlines.length === 0 ? (
          <HomeEmpty
            action={
              <Link to="/admin/assignments" className={buttonVariants({ variant: 'outline', size: 'sm' })}>
                Dă o temă
              </Link>
            }
          >
            Nicio temă cu termen apropiat.
          </HomeEmpty>
        ) : (
          deadlines.map((a) => (
            <Card key={a.id} data-testid="deadline-row">
              <CardContent className="flex flex-wrap items-center justify-between gap-x-3 gap-y-1 py-3">
                <div className="min-w-0 space-y-1">
                  <p data-testid="deadline-title" className="truncate font-medium">{a.quizTitle}</p>
                  <p className="flex flex-wrap items-center gap-2 text-sm text-muted-foreground">
                    {a.overdue && (
                      <span className="rounded-md bg-destructive/15 px-2 py-0.5 text-xs font-medium text-destructive">
                        Termen depășit
                      </span>
                    )}
                    <span>{a.schoolClassName}</span>
                    <span>
                      {formatDate(a.dueAt)} ({dueCountdown(a.dueAt, loadedAt)})
                    </span>
                  </p>
                </div>
                {a.enrolled === 0 ? (
                  <p className="text-sm text-muted-foreground">Niciun elev în clasă</p>
                ) : (
                  <p className="text-sm font-medium">
                    {a.done} / {a.enrolled} predate
                  </p>
                )}
              </CardContent>
            </Card>
          ))
        )}
      </HomeSection>

      {data.toGrade.length > 0 && (
        <HomeSection
          title="De corectat"
          action={
            <Link to="/admin/grading" className={buttonVariants({ size: 'sm' })}>
              Corectează
            </Link>
          }
        >
          <Card>
            <CardContent className="divide-y py-1">
              {data.toGrade.slice(0, GRADING_PREVIEW).map((a) => (
                <div key={a.attemptId} className="flex flex-wrap items-center justify-between gap-x-3 py-2 text-sm">
                  <span className="font-medium">{a.studentName}</span>
                  <span className="text-muted-foreground">
                    {a.quizTitle} · {formatDate(a.submittedAt)}
                  </span>
                </div>
              ))}
              {data.toGrade.length > GRADING_PREVIEW && (
                <p className="py-2 text-sm text-muted-foreground">
                  și încă {data.toGrade.length - GRADING_PREVIEW}…
                </p>
              )}
            </CardContent>
          </Card>
        </HomeSection>
      )}
    </>
  )
}
