import { useEffect, useState } from 'react'
import { Bell, BookOpen, ClipboardList, History, PenLine, TriangleAlert, UserCheck } from 'lucide-react'
import { Link } from 'react-router-dom'
import { Button, buttonVariants } from '@/components/ui/button'
import { useConfirm } from '@/components/useConfirm'
import { Badge } from '@/components/ui/badge'
import { Progress } from '@/components/ui/progress'
import { ErrorState } from '@/components/ErrorState'
import { Card, CardContent } from '@/components/ui/card'
import { FirstRunChecklist } from '@/components/home/FirstRunChecklist'
import { HomeColumns, HomeEmpty, HomeSection, HomeShortcut, StatTile } from '@/components/home/HomeBits'
import {
  getAssignmentStatus,
  listActiveUsers,
  listAssignments,
  listAttemptsForGrading,
  listClasses,
  listPendingUsers,
  listQuizSummaries,
  listQuizzes,
  sendAssignmentReminder,
  type AdminAttemptSummary,
  type AssignmentSummary,
} from '@/lib/api'
import { dueCountdown, dueSoonLabel, reminderState } from '@/lib/assignments'
import { toast } from '@/lib/toast'
import { errorMessage } from '@/lib/errors'
import { formatDate, roCount } from '@/lib/format'
import { firstRunSteps, upcomingDeadlines, type FirstRunStep } from '@/lib/home'
import { LoadingCards } from '@/components/LoadingCards'
import { cn } from '@/lib/utils'
import { ExplanationRequestsSection } from '@/components/ExplanationRequestsSection'

const GRADING_PREVIEW = 5
/** With this few students left, a deadline names them ("Lipsește: Radu Marin") - the one thing the teacher acts on. */
const MISSING_NAMES_MAX = 3

/** A test with questions whose marked answer looks wrong. */
interface KeyToCheck {
  quizId: number
  title: string
  count: number
}

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
 * Every row opens what it is about: a deadline its hand-in list, a waiting paper that paper. A test whose marked
 * answer looks wrong comes first: it marks every paper wrong until the teacher fixes it on Statistici.
 */
export function TeacherHome({ canApprove = true }: { canApprove?: boolean }) {
  const [data, setData] = useState<TeacherData | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [firstRun, setFirstRun] = useState<FirstRunStep[] | null>(null)
  const [loadedAt] = useState(() => Date.now())
  /** Assignment id -> who has not handed in yet, only for deadlines with a few students left. */
  const [missing, setMissing] = useState<Record<number, string[]>>({})
  const [keysToCheck, setKeysToCheck] = useState<KeyToCheck[]>([])
  // "Trimite reminder": the homework whose reminder is on its way (its button waits), and the yes/no before emailing.
  const [reminding, setReminding] = useState<number | null>(null)
  const { confirm, dialog: confirmDialog } = useConfirm()

  /** Emails now the students who have not handed in, after a yes: it cannot be taken back, so it is asked first. */
  async function remind(a: AssignmentSummary) {
    const pending = a.enrolled - a.done
    const yes = await confirm({
      title: `Trimiți un reminder pentru „${a.quizTitle}”?`,
      body: `${roCount(pending, 'elev', 'elevi')} din ${a.schoolClassName} ${pending === 1 ? 'nu a' : 'nu au'} predat încă. `
        // Whether parents get a copy is the server's setting: the toast after says who was emailed.
        + 'Primesc acum un email cu termenul și linkul spre test. '
        + 'Următorul reminder se poate trimite după 12 ore.',
      confirmLabel: 'Trimite',
    })
    if (!yes) return
    setReminding(a.id)
    try {
      const sent = await sendAssignmentReminder(a.id)
      setData((d) => d && {
        ...d,
        assignments: d.assignments.map((x) => (x.id === a.id ? { ...x, manualReminderAt: sent.sentAt } : x)),
      })
      toast({
        tone: 'success',
        message: `Reminder trimis: ${roCount(sent.students, 'elev', 'elevi')}`
          + `${sent.parents > 0 ? ` și ${roCount(sent.parents, 'părinte', 'părinți')}` : ''}.`,
      })
    } catch (e) {
      toast({ tone: 'danger', message: errorMessage(e) })
    } finally {
      setReminding(null)
    }
  }

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
        // The first-run checklist (T7) and the answer keys to check are a help, not the page: if these lists fail
        // they are simply not shown.
        const quizzes = listQuizzes()
        Promise.all([listClasses(), listActiveUsers('STUDENT'), quizzes])
          .then(([classes, students, list]) => !cancelled && setFirstRun(firstRunSteps({
            classes: classes.length, students: students.length, quizzes: list.length, assignments: assignments.length,
          })))
          .catch(() => undefined)
        Promise.all([quizzes, listQuizSummaries()])
          .then(([list, summaries]) => {
            if (cancelled) return
            const titles = new Map(list.map((q) => [q.id, q.title]))
            setKeysToCheck(summaries
              .filter((s) => s.toCheck > 0 && titles.has(s.quizId))
              .map((s) => ({ quizId: s.quizId, title: titles.get(s.quizId) ?? '', count: s.toCheck })))
          })
          .catch(() => undefined)
      })
      .catch((e) => !cancelled && setError(errorMessage(e)))
    return () => {
      cancelled = true
    }
  }, [canApprove, loadedAt])

  if (error) return <ErrorState message={error} />
  if (!data) return <LoadingCards />

  const open = data.assignments.filter((a) => !a.overdue).length
  const deadlines = upcomingDeadlines(data.assignments, loadedAt)

  return (
    <div className="space-y-6">
      {confirmDialog}
      {firstRun && <FirstRunChecklist steps={firstRun} />}
      <HomeColumns
        // On a computer the counts head the side column, so it is never empty; on a phone they come first.
        stats={
          <div className={cn('grid gap-3 lg:grid-cols-1', data.pending === null ? 'grid-cols-2' : 'grid-cols-3')}>
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
        }
        main={
          <>
            {keysToCheck.length > 0 && (
              <HomeSection title="De verificat">
                {keysToCheck.map((k) => (
                  <Link
                    key={k.quizId}
                    to={`/admin/quizzes/${k.quizId}/stats`}
                    className="block rounded-xl outline-none focus-visible:ring-3 focus-visible:ring-ring/50"
                  >
                    <Card data-testid="key-to-check" className="hover-lift border-warning/40 bg-warning/10">
                      <CardContent className="flex items-start gap-3 py-3">
                        <TriangleAlert aria-hidden="true" className="mt-0.5 size-5 shrink-0 text-warning-foreground" />
                        <div className="min-w-0 space-y-0.5">
                          <p className="truncate font-medium">{k.title}</p>
                          <p className="text-sm">
                            {roCount(k.count, 'întrebare', 'întrebări')} cu răspunsul marcat de verificat: elevii cei mai
                            buni au ales altă variantă.
                          </p>
                        </div>
                      </CardContent>
                    </Card>
                  </Link>
                ))}
              </HomeSection>
            )}
            <ExplanationRequestsSection />
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
                  symbol="Δt"
                  action={
                    <Link to="/admin/assignments" className={buttonVariants({ size: 'sm' })}>
                      Dă o temă
                    </Link>
                  }
                >
                  Nicio temă cu termen apropiat. Dă o temă și vezi aici cine a predat.
                </HomeEmpty>
              ) : (
                deadlines.map((a) => {
                  const soon = a.overdue ? null : dueSoonLabel(a.dueAt, loadedAt)
                  // Offered on the deadlines of today and tomorrow, where a nudge still helps.
                  const reminder = soon ? reminderState(a, loadedAt) : ({ kind: 'hidden' } as const)
                  return (
                    // The title's link stretches over the whole card (after:inset-0): the card still opens the hand-in
                    // list, and "Trimite reminder" stays a button of its own, above that link.
                    <Card
                      key={a.id}
                      data-testid="deadline-row"
                      className="hover-lift relative has-[a:focus-visible]:ring-3 has-[a:focus-visible]:ring-ring/50"
                    >
                      <CardContent className="space-y-2 py-3">
                        <div className="flex flex-wrap items-center justify-between gap-x-3 gap-y-1">
                        <div className="min-w-0 space-y-1">
                          <p data-testid="deadline-title" className="truncate font-medium">
                            <Link
                              to={`/admin/assignments?open=${a.id}`}
                              className="outline-none after:absolute after:inset-0 after:rounded-xl"
                            >
                              {a.quizTitle}
                            </Link>
                          </p>
                          <p className="flex flex-wrap items-center gap-2 text-sm text-muted-foreground">
                            {a.overdue && <Badge variant="destructive">Termen depășit</Badge>}
                            {soon && <Badge variant="warning" data-testid="deadline-soon">{soon}</Badge>}
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
                        </div>
                        {a.enrolled > 0 && (
                          <Progress
                            value={a.done}
                            max={a.enrolled}
                            label={`Predate la ${a.quizTitle}`}
                            valueText={`${a.done} din ${a.enrolled}`}
                            // Under half handed in with the deadline today, tomorrow or passed: worth a nudge.
                            tone={a.done === a.enrolled ? 'success' : (soon || a.overdue) && a.done * 2 < a.enrolled ? 'warning' : 'primary'}
                          />
                        )}
                        {reminder.kind === 'ready' && (
                          <div className="relative z-10 flex justify-end">
                            <Button
                              size="sm"
                              // Under half handed in: the nudge is the thing to do here, so it is the main button.
                              variant={a.done * 2 < a.enrolled ? 'default' : 'outline'}
                              disabled={reminding === a.id}
                              onClick={() => void remind(a)}
                              data-testid="send-reminder"
                            >
                              <Bell aria-hidden="true" />
                              {reminding === a.id ? 'Se trimite...' : 'Trimite reminder'}
                            </Button>
                          </div>
                        )}
                        {reminder.kind === 'sent' && (
                          <p data-testid="reminder-sent" className="relative z-10 text-right text-xs text-muted-foreground">
                            <Bell aria-hidden="true" className="mr-1 inline size-3" />
                            Reminder trimis {formatDate(reminder.at)}
                          </p>
                        )}
                      </CardContent>
                    </Card>
                  )
                })
              )}
            </HomeSection>
          </>
        }
        side={
          data.toGrade.length > 0 || canApprove ? (
            <>
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

                {/* Both pages are the admins': a TEACHER would only bounce back here. On a computer they are in the side
                    menu under "Mai mult" already. */}
                {canApprove && (
                  <div className="lg:hidden">
                  <HomeSection title="Scurtături">
                    <div className="grid gap-3 sm:grid-cols-2">
                      <HomeShortcut to="/admin/en-chapters" icon={BookOpen} title="Capitole EN" text="Materia și modelele rezolvate" />
                      <HomeShortcut to="/admin/journal" icon={History} title="Jurnal" text="Cine a schimbat ce și când" />
                    </div>
                  </HomeSection>
                  </div>
                )}
            </>
          ) : undefined
        }
      />
    </div>
  )
}
