import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { buttonVariants } from '@/components/ui/button'
import { Card, CardContent } from '@/components/ui/card'
import { FirstRunChecklist } from '@/components/home/FirstRunChecklist'
import { HomeEmpty, HomeSection, StatTile } from '@/components/home/HomeBits'
import {
  listActiveUsers,
  listAssignments,
  listAttemptsForGrading,
  listClasses,
  listPendingUsers,
  listQuizzes,
  type AdminAttemptSummary,
  type AssignmentSummary,
} from '@/lib/api'
import { dueCountdown } from '@/lib/assignments'
import { errorMessage } from '@/lib/errors'
import { formatDate } from '@/lib/format'
import { firstRunSteps, upcomingDeadlines, type FirstRunStep } from '@/lib/home'

const GRADING_PREVIEW = 5

interface TeacherData {
  toGrade: AdminAttemptSummary[]
  /** null for a TEACHER: approving accounts is an admin's job. */
  pending: number | null
  assignments: AssignmentSummary[]
}

/**
 * The teacher's start page answers "what waits for me?": three counts (papers to grade, accounts to approve, open
 * homework) that lead to where each is handled, the deadlines worth a look with how many handed in, and who waits
 * for a grade. A TEACHER ({@code canApprove} false) gets the same page for their own classes, without the accounts.
 */
export function TeacherHome({ canApprove = true }: { canApprove?: boolean }) {
  const [data, setData] = useState<TeacherData | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [firstRun, setFirstRun] = useState<FirstRunStep[] | null>(null)
  const [loadedAt] = useState(() => Date.now())

  useEffect(() => {
    let cancelled = false
    const pendingUsers = canApprove ? listPendingUsers().then((list) => list.length) : Promise.resolve(null)
    Promise.all([listAttemptsForGrading('SUBMITTED'), pendingUsers, listAssignments()])
      .then(([toGrade, pending, assignments]) => {
        if (cancelled) return
        setData({ toGrade, pending, assignments })
        // The first-run checklist (T7) is a help, not the page: if these lists fail it is simply not shown.
        Promise.all([listClasses(), listActiveUsers('STUDENT'), listQuizzes()])
          .then(([classes, students, quizzes]) => !cancelled && setFirstRun(firstRunSteps({
            classes: classes.length, students: students.length, quizzes: quizzes.length, assignments: assignments.length,
          })))
          .catch(() => undefined)
      })
      .catch((e) => !cancelled && setError(errorMessage(e)))
    return () => {
      cancelled = true
    }
  }, [canApprove])

  if (error) return <p className="text-sm text-destructive">{error}</p>
  if (!data) return <p className="text-muted-foreground">Se încarcă...</p>

  const open = data.assignments.filter((a) => !a.overdue).length
  const deadlines = upcomingDeadlines(data.assignments, loadedAt)

  return (
    <>
      {firstRun && <FirstRunChecklist steps={firstRun} />}
      <div className={data.pending === null ? 'grid grid-cols-2 gap-3' : 'grid grid-cols-3 gap-3'}>
        <StatTile to="/admin/grading" value={data.toGrade.length} label="Lucrări de corectat" urgent testId="tile-grading" />
        {data.pending !== null && (
          <StatTile to="/admin/students" value={data.pending} label="Conturi de aprobat" urgent testId="tile-pending" />
        )}
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

      <p className="flex flex-wrap gap-x-4 gap-y-1 text-sm">
        <Link to="/admin/en-chapters" className="text-muted-foreground hover:text-foreground hover:underline">
          Capitole EN: materia și modelele rezolvate
        </Link>
        <Link to="/admin/journal" className="text-muted-foreground hover:text-foreground hover:underline">
          Jurnal: cine a schimbat ce și când
        </Link>
      </p>
    </>
  )
}
