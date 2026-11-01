import { Fragment, useEffect, useState } from 'react'
import { useParams } from 'react-router-dom'
import { Card, CardContent } from '@/components/ui/card'
import { ErrorState } from '@/components/ErrorState'
import { LoadingCards } from '@/components/LoadingCards'
import { MathContent } from '@/components/MathContent'
import { OptionLetter } from '@/components/OptionLetter'
import { PageHeader } from '@/components/PageHeader'
import { PartsView } from '@/components/PartsView'
import { QuestionImage } from '@/components/QuestionImage'
import { SectionHeadingView } from '@/components/SectionHeadingView'
import { getQuiz, type QuizDetail } from '@/lib/api'
import { errorMessage } from '@/lib/errors'
import { numberItems } from '@/lib/sections'
import { usePageTitle } from '@/lib/usePageTitle'

/**
 * "Vezi ca elev": the test the way a student reads it, numbered and grouped the same, with the right answers and the
 * barem left out. Only to look at: the choices are switched off and nothing starts an attempt.
 */
export function AdminQuizPreviewPage() {
  const { quizId } = useParams<{ quizId: string }>()
  const id = Number(quizId)
  const [quiz, setQuiz] = useState<QuizDetail | null>(null)
  const [error, setError] = useState<string | null>(null)
  usePageTitle(quiz ? `Vezi ca elev · ${quiz.title}` : 'Vezi ca elev')

  useEffect(() => {
    let cancelled = false
    getQuiz(id)
      .then((q) => !cancelled && setQuiz(q))
      .catch((e) => !cancelled && setError(errorMessage(e)))
    return () => {
      cancelled = true
    }
  }, [id])

  const total = quiz?.items.reduce((sum, item) => sum + item.points, 0) ?? 0

  return (
    <div className="p-4">
      <div className="mx-auto max-w-3xl space-y-4">
        <PageHeader
          title={quiz?.title ?? 'Vezi ca elev'}
          description={quiz?.description ?? undefined}
          breadcrumbs={[
            { label: 'Teste', to: '/admin/quizzes' },
            { label: quiz?.title ?? 'Test', to: `/admin/quizzes/${id}` },
            { label: 'Vezi ca elev' },
          ]}
        />
        {error && <ErrorState message={error} />}
        {!quiz && !error && <LoadingCards />}
        {quiz && (
          <>
            <p data-testid="preview-note" className="rounded-lg border border-sky-500/40 bg-sky-500/10 p-3 text-sm">
              Așa vede elevul testul: {quiz.items.length} {quiz.items.length === 1 ? 'întrebare' : 'întrebări'}, {total} p.
              Răspunsurile corecte și baremul sunt ascunse, iar aici nu se salvează nimic.
            </p>
            {numberItems(quiz.items).map(({ item, label, local, heading }) => (
              <Fragment key={item.id}>
                {heading && <SectionHeadingView heading={heading} />}
                <Card data-testid="preview-item">
                  <CardContent className="space-y-3 py-4">
                    <h2 className="text-sm font-medium">
                      {label.includes('.') ? `${local}. (${item.points} p)` : `Întrebarea ${label} · ${item.points} p`}
                    </h2>
                    <MathContent>{item.statement}</MathContent>
                    <QuestionImage itemId={item.id} image={item.image} />
                    <PartsView parts={(item.parts ?? []).map((p) => ({ label: p.label, statement: p.statement, points: p.points }))} />
                    {item.type === 'SINGLE_CHOICE' ? (
                      <div className="space-y-2">
                        {item.options.map((option, index) => (
                          <label key={option.id} className="flex items-center gap-3 rounded-lg border border-border p-2 text-sm">
                            <input type="radio" name={`item-${item.id}`} className="size-4 shrink-0" disabled />
                            <OptionLetter index={index} />
                            <MathContent inline>{option.text}</MathContent>
                          </label>
                        ))}
                      </div>
                    ) : (
                      <p className="text-sm text-muted-foreground">
                        Elevul rezolvă pe hârtie, apoi fotografiază rezolvarea și o încarcă aici.
                      </p>
                    )}
                  </CardContent>
                </Card>
              </Fragment>
            ))}
          </>
        )}
      </div>
    </div>
  )
}
