import { useEffect, useState } from 'react'
import { BookOpen, ClipboardList, History, PenLine, UserCheck } from 'lucide-react'
import { Link } from 'react-router-dom'
import { buttonVariants } from '@/components/ui/button'
import { Card, CardContent } from '@/components/ui/card'
import { FirstRunChecklist } from '@/components/home/FirstRunChecklist'
import { HomeEmpty, HomeSection, HomeShortcut, StatTile } from '@/components/home/HomeBits'
import {
  getAssignmentStatus,
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
import { LoadingCards } from '@/components/LoadingCards'

const GRADING_PREVIEW = 5
/** With this few students left, a deadline names them ("Lipsește: Radu Marin") - the one thing the teacher acts on. */
const MISSING_NAMES_MAX = 3

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
 * Every row opens what it is about: a deadline its hand-in list, a waiting paper that paper.
 */
export function TeacherHome({ canApprove = true }: { canApprove?: boolean }) {
  const [data, setData] = useState<TeacherData | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [firstRun, setFirstRun] = useState<FirstRunStep[] | null>(null)
  const [loadedAt] = useState(() => Date.now())
  /** Assignment id -> who has not handed in yet, only for deadlines with a few students left. */
  const [missing, setMissing] = useState<Record<number, string[]>>({})

  useEffect(() => {
    let cancelled = false
    const pendingUsers = canApprove ? listPendingUsers().then((list) => list.length) : Promise.resolve(null)
    Promise.all([listAttemptsForGrading('SUBMITTED'), pendingUsers, listAssignments()])
      .then(([toGrade, pending, assignments]) => {
        if (cancelled) return
        setData({ toGrade, pending, assignments })
        // The names are a hint on top of the counts: a deadline whose list fails simply shows no names.
        upcomingDeadlines(assignments, loadedAt)
          .filter((a) => a.enrolled > a.done && a.enrolled - a.done <= MISSING_NAMES_MAX)
          .forEach((a) =>
            getAssignmentStatus(a.id)
              .then((list) => {
                if (cancelled) return
                const names = list
                  .filter((st) => st.state === 'NOT_STARTED' || st.state === 'IN_PROGRESS')
                  .map((st) => st.fullName)
                setMissing((m) => ({ ...m, [a.id]: names }))
              })
              .catch(() => undefined),
          )
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
  }, [canApprove, loadedAt])

  if (error) return <p className="text-sm text-destructive">{error}</p>
  if (!data) return <LoadingCards />

  const open = data.assignments.filter((a) => !a.overdue).length
  const deadlines = upcomingDeadlines(data.assignments, loadedAt)

  return (
    <>
      {firstRun && <FirstRunChecklist steps={firstRun} />}
      <div className={data.pending === null ? 'grid grid-cols-2 gap-3' : 'grid grid-cols-3 gap-3'}>
        <StatTile
          to="/admin/grading" value={data.toGrade.length} label="Lucrări de corectat" icon={PenLine}
          urgent doneLabel="Totul corectat" testId="tile-grading"
        />
        {data.pending !== null && (
          <StatTile
            to="/admin/students" value={data.pending} label="Conturi de aprobat" icon={UserCheck}
            urgent doneLabel="Niciun cont în așteptare" testId="tile-pending"
          />
        )}
        <StatTile to="/admin/assignments" value={open} label="Teme deschise" icon={ClipboardList} testId="tile-assignments" />
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
            <Link
              key={a.id}
              to={`/admin/assignments?open=${a.id}`}
              className="block rounded-xl outline-none focus-visible:ring-3 focus-visible:ring-ring/50"
            >
              <Card data-testid="deadline-row" className="hover-lift">
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
                    {missing[a.id]?.length > 0 && (
                      <p className="text-sm">
                        {missing[a.id].length === 1 ? 'Lipsește' : 'Lipsesc'}: {missing[a.id].join(', ')}
                      </p>
                    )}
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
            </Link>
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
                <Link
                  key={a.attemptId}
                  to={`/admin/grading?attempt=${a.attemptId}`}
                  className="group -mx-2 flex flex-wrap items-center justify-between gap-x-3 rounded-md px-2 py-2 text-sm outline-none hover:bg-muted/60 focus-visible:ring-3 focus-visible:ring-ring/50"
                >
                  <span className="font-medium group-hover:underline">{a.studentName}</span>
                  <span className="text-muted-foreground">
                    {a.quizTitle} · {formatDate(a.submittedAt)}
                  </span>
                </Link>
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

      {/* Both pages are the admins': a TEACHER would only bounce back here. */}
      {canApprove && (
        <HomeSection title="Scurtături">
          <div className="grid gap-3 sm:grid-cols-2">
            <HomeShortcut to="/admin/en-chapters" icon={BookOpen} title="Capitole EN" text="Materia și modelele rezolvate" />
            <HomeShortcut to="/admin/journal" icon={History} title="Jurnal" text="Cine a schimbat ce și când" />
          </div>
        </HomeSection>
      )}
    </>
  )
}
