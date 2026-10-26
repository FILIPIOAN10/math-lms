import { useEffect, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { buttonVariants } from '@/components/ui/button'
import { Card, CardContent } from '@/components/ui/card'
import { AssignmentsSection } from '@/components/AssignmentsSection'
import { AttemptModeBadge } from '@/components/AttemptModeBadge'
import { AttemptStatusBadge } from '@/components/AttemptStatusBadge'
import { HomeSection } from '@/components/home/HomeBits'
import { MasteryList } from '@/components/MasteryList'
import { EnMasteryList } from '@/components/EnMasteryList'
import { PageHeader } from '@/components/PageHeader'
import { ProgressChart } from '@/components/ProgressChart'
import { ReviewStatusLine } from '@/components/ReviewStatusLine'
import {
  getStudentAssignments,
  getStudentAttempts,
  getStudentEnMastery,
  getStudentMastery,
  type EnChapterMastery,
  getStudentProfile,
  getStudentProgress,
  getStudentReview,
  type ChapterMastery,
  type MyAttemptDto,
  type ProgressPointDto,
  type ReviewStatus,
  type StudentAssignment,
  type StudentProfile,
} from '@/lib/api'
import { errorMessage } from '@/lib/errors'
import { formatDate } from '@/lib/format'
import { scoreLine } from '@/lib/grades'
import { LoadingCards } from '@/components/LoadingCards'

/** Where the teacher reads (and, while it waits, grades) one paper: the grading screen opened on it. */
const paperHref = (attemptId: number) => `/admin/grading?attempt=${attemptId}`

interface StudentData {
  profile: StudentProfile
  attempts: MyAttemptDto[]
  assignments: StudentAssignment[]
  progress: ProgressPointDto[]
}

/**
 * T5: one student on one page — classes, parent, daily review, homework, chapter map, progress and every paper — for
 * the teacher who wants to talk about Ana without visiting four pages. The sections are the very ones the parent sees.
 */
export function AdminStudentPage() {
  const { studentId } = useParams<{ studentId: string }>()
  const [data, setData] = useState<StudentData | null>(null)
  const [mastery, setMastery] = useState<ChapterMastery[] | null>(null)
  const [enMastery, setEnMastery] = useState<EnChapterMastery[] | null>(null)
  const [review, setReview] = useState<ReviewStatus | null>(null)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    let cancelled = false
    const id = Number(studentId)
    Promise.all([getStudentProfile(id), getStudentAttempts(id), getStudentAssignments(id), getStudentProgress(id)])
      .then(([profile, attempts, assignments, progress]) => {
        if (!cancelled) setData({ profile, attempts, assignments, progress })
      })
      .catch((e) => !cancelled && setError(errorMessage(e)))
    // The two derived views are extras: the page works without them.
    getStudentMastery(id)
      .then((m) => !cancelled && setMastery(m))
      .catch(() => !cancelled && setMastery([]))
    getStudentEnMastery(id)
      .then((m) => !cancelled && setEnMastery(m))
      .catch(() => !cancelled && setEnMastery([]))
    getStudentReview(id)
      .then((r) => !cancelled && setReview(r))
      .catch(() => undefined)
    return () => {
      cancelled = true
    }
  }, [studentId])

  const name = data?.profile.fullName ?? 'Elev'

  return (
    <div className="p-4">
      <div className="mx-auto max-w-3xl space-y-4">
        <PageHeader
          title={name}
          description={data?.profile.email}
          breadcrumbs={[{ label: 'Elevi', to: '/admin/students' }, { label: name }]}
          actions={
            <Link to={`/admin/journal?student=${studentId}`} className={buttonVariants({ variant: 'outline', size: 'sm' })}>
              Istoric modificări
            </Link>
          }
        />

        {error && <p className="text-sm text-destructive">{error}</p>}
        {!data && !error && <LoadingCards />}

        {data && (
          <>
            <Card>
              <CardContent className="space-y-2 py-4 text-sm" data-testid="student-about">
                <p>
                  <span className="text-muted-foreground">Clase: </span>
                  {data.profile.classes.length === 0 ? (
                    <span>neînscris în nicio clasă</span>
                  ) : (
                    data.profile.classes.map((c, i) => (
                      <span key={c.id}>
                        {i > 0 && ', '}
                        <Link to={`/admin/classes/${c.id}/mastery`} className="font-medium hover:underline">
                          {c.name}
                        </Link>
                      </span>
                    ))
                  )}
                </p>
                <p>
                  <span className="text-muted-foreground">Părinte: </span>
                  {data.profile.parentName ? (
                    <span className="font-medium">
                      {data.profile.parentName}{' '}
                      <span className="font-normal text-muted-foreground">({data.profile.parentEmail})</span>
                    </span>
                  ) : (
                    <>
                      niciunul legat ·{' '}
                      <Link to="/admin/students" className="text-primary hover:underline">
                        Invită părintele
                      </Link>
                    </>
                  )}
                </p>
                <ReviewStatusLine review={review} testId="student-review" />
              </CardContent>
            </Card>

            <AssignmentsSection
              assignments={data.assignments}
              attempts={data.attempts}
              resultHref={paperHref}
              title="Teme"
              emptyText="Nicio temă dată claselor elevului."
            />

            {mastery && (
              <HomeSection title="Pe capitole">
                <Card>
                  <CardContent className="py-4">
                    <MasteryList rows={mastery} />
                  </CardContent>
                </Card>
              </HomeSection>
            )}

            {enMastery && enMastery.some((r) => r.questions > 0) && (
              <HomeSection title="Pe capitolele EN">
                <Card>
                  <CardContent className="py-4">
                    <EnMasteryList rows={enMastery} />
                  </CardContent>
                </Card>
              </HomeSection>
            )}

            <Card>
              <CardContent className="space-y-2 py-4">
                <h2 className="font-medium">Progres în timp</h2>
                <ProgressChart points={data.progress} />
              </CardContent>
            </Card>

            <section className="space-y-2">
              <h2 className="font-medium">Lucrări</h2>
              {data.attempts.length === 0 && (
                <p className="text-sm text-muted-foreground">Elevul nu a dat încă niciun test.</p>
              )}
              {data.attempts.map((a) => (
                <Card key={a.attemptId}>
                  <CardContent className="flex items-center justify-between gap-3 py-3" data-testid="student-attempt">
                    <div className="min-w-0 space-y-1">
                      <p className="truncate font-medium">{a.quizTitle}</p>
                      <p className="flex flex-wrap items-center gap-2 text-sm text-muted-foreground">
                        {a.mode === 'PRACTICE' ? <AttemptModeBadge mode={a.mode} /> : <AttemptStatusBadge status={a.status} />}
                        <span>{formatDate(a.submittedAt ?? a.startedAt)}</span>
                        {a.score !== null && <span className="text-foreground">{scoreLine(a.score, a.maxScore)}</span>}
                      </p>
                    </div>
                    {a.status !== 'IN_PROGRESS' && (
                      <Link
                        to={paperHref(a.attemptId)}
                        className={buttonVariants({ size: 'sm', variant: a.status === 'SUBMITTED' ? 'default' : 'outline' })}
                      >
                        {a.status === 'SUBMITTED' ? 'Corectează' : 'Vezi lucrarea'}
                      </Link>
                    )}
                  </CardContent>
                </Card>
              ))}
            </section>
          </>
        )}
      </div>
    </div>
  )
}
