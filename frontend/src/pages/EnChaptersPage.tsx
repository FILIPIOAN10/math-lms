import { Fragment, useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { Card, CardContent } from '@/components/ui/card'
import { ErrorState } from '@/components/ErrorState'
import { PageHeader } from '@/components/PageHeader'
import { HomeSection } from '@/components/home/HomeBits'
import { useAuth } from '@/context/AuthContext'
import { getMyEnMastery, listEnChapters, type EnChapterMastery, type EnChapterSummary } from '@/lib/api'
import { EN_AREAS, enPageForStudents, hasEnContent, inArea, rootsOf } from '@/lib/enChapters'
import { errorMessage } from '@/lib/errors'
import { MASTERY_LABELS, MASTERY_TONES, masteryLevel } from '@/lib/mastery'
import { cn } from '@/lib/utils'
import { LoadingCards } from '@/components/LoadingCards'

/**
 * The EN chapter pages, as students and parents browse them: algebra, then geometry. A chapter whose page the teacher
 * has not filled yet is listed (so the student sees what is coming) but does not open. A student also sees, from
 * their own answers, which chapters to go back to: named first, and marked on their rows.
 */
export function EnChaptersPage() {
  const { user } = useAuth()
  const isStudent = user?.role === 'STUDENT'
  const [chapters, setChapters] = useState<EnChapterSummary[] | null>(null)
  const [mine, setMine] = useState<ReadonlyMap<number, EnChapterMastery>>(new Map())
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    let cancelled = false
    listEnChapters()
      .then((list) => !cancelled && setChapters(list))
      .catch((e) => !cancelled && setError(errorMessage(e)))
    // The marks are a help, not the page: without them the list still works.
    if (isStudent) {
      getMyEnMastery()
        .then((rows) => !cancelled && setMine(new Map(rows.map((r) => [r.chapterId, r]))))
        .catch(() => undefined)
    }
    return () => {
      cancelled = true
    }
  }, [isStudent])

  const roots = chapters ? rootsOf(chapters) : []
  const toRevise = roots
    .filter((c) => hasEnContent(c) && masteryLevel(mine.get(c.id)?.percent ?? null) === 'low')
    .sort((a, b) => (mine.get(a.id)?.percent ?? 0) - (mine.get(b.id)?.percent ?? 0))

  return (
    <div className="p-4">
      <div className="mx-auto max-w-3xl space-y-4">
        <PageHeader
          title="Capitole EN"
          description="Materia și modele rezolvate pentru fiecare tip de exercițiu de la Evaluarea Națională."
        />
        {error && <ErrorState message={error} />}
        {!chapters && !error && <LoadingCards />}
        {toRevise.length > 0 && (
          <p data-testid="en-to-revise" className="rounded-lg border border-amber-500/40 bg-amber-500/10 p-3 text-sm">
            <span className="font-medium">De recapitulat acum:</span>{' '}
            {toRevise.map((c, i) => (
              <Fragment key={c.id}>
                {i > 0 && ', '}
                <Link to={`/capitole/${c.id}`} className="font-medium underline underline-offset-2 hover:no-underline">
                  {c.code} · {c.title}
                </Link>
              </Fragment>
            ))}
          </p>
        )}
        {chapters &&
          EN_AREAS.map((area) => {
            const list = inArea(roots, area.value)
            if (list.length === 0) return null
            return (
              <HomeSection key={area.value} title={area.label}>
                <Card>
                  <CardContent className="divide-y py-1">
                    {list.map((c) => {
                      const level = masteryLevel(mine.get(c.id)?.percent ?? null)
                      return (
                        <div key={c.id} className="py-2.5">
                          {hasEnContent(c) ? (
                            <Link to={`/capitole/${c.id}`} className="font-medium hover:underline">
                              <span className="mr-1 font-mono text-sm text-muted-foreground">{c.code}</span>{' '}
                              {c.title}
                            </Link>
                          ) : (
                            <span className="text-muted-foreground">
                              <span className="mr-1 font-mono text-sm">{c.code}</span>{' '}
                              {c.title}
                            </span>
                          )}
                          {/* Always under the title: the count no longer jumps between the right side and the next line. */}
                          <p className="mt-0.5 flex flex-wrap items-center gap-2 text-xs text-muted-foreground">
                            <span>{hasEnContent(c) ? enPageForStudents(c) : 'în pregătire'}</span>
                            {level !== 'none' && (
                              <span
                                data-testid={`en-level-${c.id}`}
                                className={cn('rounded-md px-1.5 py-0.5 font-medium', MASTERY_TONES[level])}
                              >
                                {MASTERY_LABELS[level]} · {mine.get(c.id)?.percent}%
                              </span>
                            )}
                          </p>
                        </div>
                      )
                    })}
                  </CardContent>
                </Card>
              </HomeSection>
            )
          })}
      </div>
    </div>
  )
}
