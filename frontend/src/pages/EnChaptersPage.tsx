import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { Card, CardContent } from '@/components/ui/card'
import { PageHeader } from '@/components/PageHeader'
import { HomeSection } from '@/components/home/HomeBits'
import { listEnChapters, type EnChapterSummary } from '@/lib/api'
import { EN_AREAS, enPageContents, hasEnContent, inArea, rootsOf } from '@/lib/enChapters'
import { errorMessage } from '@/lib/errors'

/**
 * The EN chapter pages, as students and parents browse them: algebra, then geometry. A chapter whose page the teacher
 * has not filled yet is listed (so the student sees what is coming) but does not open.
 */
export function EnChaptersPage() {
  const [chapters, setChapters] = useState<EnChapterSummary[] | null>(null)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    let cancelled = false
    listEnChapters()
      .then((list) => !cancelled && setChapters(list))
      .catch((e) => !cancelled && setError(errorMessage(e)))
    return () => {
      cancelled = true
    }
  }, [])

  return (
    <div className="p-4">
      <div className="mx-auto max-w-3xl space-y-4">
        <PageHeader
          title="Capitole EN"
          description="Materia și modele rezolvate pentru fiecare tip de exercițiu de la Evaluarea Națională."
        />
        {error && <p className="text-sm text-destructive">{error}</p>}
        {!chapters && !error && <p className="text-muted-foreground">Se încarcă...</p>}
        {chapters &&
          EN_AREAS.map((area) => {
            const list = inArea(rootsOf(chapters), area.value)
            if (list.length === 0) return null
            return (
              <HomeSection key={area.value} title={area.label}>
                <Card>
                  <CardContent className="divide-y py-1">
                    {list.map((c) => (
                      <div key={c.id} className="flex flex-wrap items-baseline justify-between gap-x-3 gap-y-0.5 py-2.5">
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
                        <span className="text-xs text-muted-foreground">{hasEnContent(c) ? enPageContents(c) : 'în pregătire'}</span>
                      </div>
                    ))}
                  </CardContent>
                </Card>
              </HomeSection>
            )
          })}
      </div>
    </div>
  )
}
