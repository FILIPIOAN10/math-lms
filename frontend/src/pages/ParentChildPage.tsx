import { useEffect, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { buttonVariants } from '@/components/ui/button'
import { ErrorState } from '@/components/ErrorState'
import { Card, CardContent } from '@/components/ui/card'
import { AttemptStatusBadge } from '@/components/AttemptStatusBadge'
import { ProgressChart } from '@/components/ProgressChart'
import { AttemptModeBadge } from '@/components/AttemptModeBadge'
import { AssignmentsSection } from '@/components/AssignmentsSection'
import { HomeSection } from '@/components/home/HomeBits'
import { MasteryList } from '@/components/MasteryList'
import { EnMasteryList } from '@/components/EnMasteryList'
import {
  getChildAssignments,
  getChildAttempts,
  getChildEnMastery,
  getChildMastery,
  type EnChapterMastery,
  getChildProgress,
  listMyChildren,
  type ChapterMastery,
  type MyAttemptDto,
  type ProgressPointDto,
  type StudentAssignment,
} from '@/lib/api'
import { errorMessage } from '@/lib/errors'
import { scoreLine } from '@/lib/grades'
import { formatDate, roCount } from '@/lib/format'
import { todoAssignments } from '@/lib/home'
import { chaptersToRevise } from '@/lib/mastery'
import { PageHeader } from '@/components/PageHeader'
import { LoadingCards } from '@/components/LoadingCards'

/** What a parent came for, in plain words: the homework still to hand in, and the chapters to go back to, by name. */
function WorkOnNow({ homework, chapters }: { homework: StudentAssignment[]; chapters: string[] }) {
  const todo = todoAssignments(homework)
  const overdue = todo.filter((a) => a.overdue).length
  const nothing = todo.length === 0 && chapters.length === 0

  return (
    <Card>
      <CardContent data-testid="child-summary" className="space-y-1 py-4">
        <h2 className="font-medium">De lucrat acum</h2>
        {nothing ? (
          <p className="text-sm text-muted-foreground">Nimic urgent: temele sunt predate și niciun capitol nu cere recapitulare.</p>
        ) : (
          <ul className="space-y-1 text-sm">
            {todo.length > 0 && (
              <li>
                <span className="font-medium">{roCount(todo.length, 'temă', 'teme')} de predat</span>
                {overdue > 0 && <span className="font-medium text-destructive"> · {overdue} cu termenul depășit</span>}
              </li>
            )}
            {chapters.length > 0 && <li>De recapitulat: {chapters.join(', ')}</li>}
          </ul>
        )}
      </CardContent>
    </Card>
  )
}

/** One test or practice of the child: state, date and points, and its result once handed in. */
function ChildAttemptCard({ attempt: a, childId }: { attempt: MyAttemptDto; childId: string }) {
  return (
    <Card>
      <CardContent className="flex items-center justify-between gap-3 py-3" data-testid="child-attempt">
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
            to={`/parent/children/${childId}/attempts/${a.attemptId}`}
            className={buttonVariants({ size: 'sm', variant: 'outline' })}
          >
            Vezi rezultatul
          </Link>
        )}
      </CardContent>
    </Card>
  )
}

/**
 * One child, read-only, in the order a parent reads it: what to work on now, how it is going over time, the homework,
 * then the chapters folded away and the tests - once, under their heading, practice folded. A child that is not yours
 * answers 403, shown as the error.
 */
export function ParentChildPage() {
  const { childId } = useParams<{ childId: string }>()
  const [attempts, setAttempts] = useState<MyAttemptDto[] | null>(null)
  const [progress, setProgress] = useState<ProgressPointDto[] | null>(null)
  const [assignments, setAssignments] = useState<StudentAssignment[] | null>(null)
  const [childName, setChildName] = useState<string | null>(null)
  const [mastery, setMastery] = useState<ChapterMastery[] | null>(null)
  const [enMastery, setEnMastery] = useState<EnChapterMastery[] | null>(null)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    let cancelled = false
    const id = Number(childId)
    Promise.all([getChildAttempts(id), getChildProgress(id), getChildAssignments(id)])
      .then(([a, p, hw]) => {
        if (!cancelled) {
          setAttempts(a)
          setProgress(p)
          setAssignments(hw)
        }
      })
      .catch((e) => !cancelled && setError(errorMessage(e)))
    getChildMastery(id)
      .then((m) => !cancelled && setMastery(m))
      .catch(() => !cancelled && setMastery([]))
    getChildEnMastery(id)
      .then((m) => !cancelled && setEnMastery(m))
      .catch(() => !cancelled && setEnMastery([]))
    // Only for the title: the page still works without the name.
    listMyChildren()
      .then((children) => !cancelled && setChildName(children.find((c) => c.id === id)?.fullName ?? null))
      .catch(() => undefined)
    return () => {
      cancelled = true
    }
  }, [childId])

  const tests = attempts?.filter((a) => a.mode !== 'PRACTICE') ?? []
  const practice = attempts?.filter((a) => a.mode === 'PRACTICE') ?? []

  return (
    <div className="p-4">
      <div className="mx-auto max-w-3xl space-y-4">
        <PageHeader
          title={childName ?? 'Copilul meu'}
          description="Temele, progresul și testele date."
          breadcrumbs={[{ label: 'Acasă', to: '/' }, { label: childName ?? 'Copilul meu' }]}
        />

        {error && <ErrorState message={error} />}

        {!error && (
          <>
            {!attempts && <LoadingCards />}
            {/* Only with everything in: "nothing urgent" must not flash up before the chapters arrive. */}
            {assignments && mastery && enMastery && (
              <WorkOnNow homework={assignments} chapters={chaptersToRevise(mastery, enMastery)} />
            )}
            {progress && (
              <Card>
                <CardContent className="space-y-2 py-4">
                  <h2 className="font-medium">Progres în timp</h2>
                  {/* The tests with their grades are listed below: the chart's own table would say it all twice. */}
                  <ProgressChart points={progress} table={false} />
                </CardContent>
              </Card>
            )}
            {assignments && (
              <AssignmentsSection
                assignments={assignments}
                attempts={attempts ?? []}
                childId={Number(childId)}
                title="Teme"
                emptyText="Nicio temă dată clasei copilului."
              />
            )}
            {mastery && (
              <details data-testid="child-chapters" className="space-y-4">
                <summary className="cursor-pointer font-medium">Pe capitole, în detaliu</summary>
                <div className="mt-3 space-y-4">
                  <HomeSection title="Pe capitole">
                    <Card>
                      <CardContent className="py-4">
                        <MasteryList rows={mastery} />
                      </CardContent>
                    </Card>
                  </HomeSection>
                  {enMastery && enMastery.some((r) => r.questions > 0) && (
                    <HomeSection title="Pe capitolele EN">
                      <Card>
                        <CardContent className="py-4">
                          <EnMasteryList rows={enMastery} showCodes={false} />
                        </CardContent>
                      </Card>
                    </HomeSection>
                  )}
                </div>
              </details>
            )}
            {attempts && (
              <HomeSection title="Testele date">
                {tests.length === 0 && (
                  <p className="text-sm text-muted-foreground">Copilul nu a dat încă niciun test.</p>
                )}
                {tests.map((a) => (
                  <ChildAttemptCard key={a.attemptId} attempt={a} childId={childId!} />
                ))}
                {/* Practice is effort, not a grade: there, but folded, so the tests stay one short list. */}
                {practice.length > 0 && (
                  <details data-testid="child-practice" className="space-y-2">
                    <summary className="cursor-pointer text-sm font-medium">Exersări ({practice.length})</summary>
                    <div className="mt-2 space-y-2">
                      {practice.map((a) => (
                        <ChildAttemptCard key={a.attemptId} attempt={a} childId={childId!} />
                      ))}
                    </div>
                  </details>
                )}
              </HomeSection>
            )}
          </>
        )}
      </div>
    </div>
  )
}