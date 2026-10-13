import { useEffect, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { buttonVariants } from '@/components/ui/button'
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
import { formatDate } from '@/lib/format'
import { PageHeader } from '@/components/PageHeader'

/**
 * One child, read-only: their homework (what is due, overdue, handed in), progress chart and attempts. A child that is
 * not yours answers 403, shown as the error.
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

  return (
    <div className="p-4">
      <div className="mx-auto max-w-3xl space-y-4">
        <PageHeader
          title={childName ?? 'Copilul meu'}
          description="Temele, progresul și testele date."
          breadcrumbs={[{ label: 'Copiii mei', to: '/parent' }, { label: childName ?? 'Copilul meu' }]}
        />

        {error && <p className="text-sm text-destructive">{error}</p>}
        {!attempts && !error && <p className="text-muted-foreground">Se încarcă...</p>}
        {assignments && (
          <AssignmentsSection
            assignments={assignments}
            childId={Number(childId)}
            title="Teme"
            emptyText="Nicio temă dată clasei copilului."
          />
        )}
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
        {progress && (
          <Card>
            <CardContent className="space-y-2 py-4">
              <h2 className="font-medium">Progres în timp</h2>
              <ProgressChart points={progress} />
            </CardContent>
          </Card>
        )}
        {attempts && attempts.length === 0 && (
          <p className="text-sm text-muted-foreground">Copilul nu a dat încă niciun test.</p>
        )}

        {attempts?.map((a) => (
          <Card key={a.attemptId}>
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
        ))}
      </div>
    </div>
  )
}
