import { Fragment, useState } from 'react'
import { Link } from 'react-router-dom'
import { Button, buttonVariants } from '@/components/ui/button'
import { Card, CardContent } from '@/components/ui/card'
import { AttemptModeBadge } from '@/components/AttemptModeBadge'
import { AttemptStatusBadge } from '@/components/AttemptStatusBadge'
import { MathContent } from '@/components/MathContent'
import { PhotoPages } from '@/components/PhotoPages'
import { QuestionImage } from '@/components/QuestionImage'
import { PartsView } from '@/components/PartsView'
import { SectionHeadingView } from '@/components/SectionHeadingView'
import { numberItems } from '@/lib/sections'
import { ownPhotoUrl, type AttemptResultViewDto, type ItemResultDto } from '@/lib/api'
import { roCount } from '@/lib/format'
import { Confetti } from '@/components/Confetti'
import { GradeFigure } from '@/components/GradeFigure'
import { celebrateFirstTen } from '@/lib/celebrate'
import { mistakesOf, practiceMistakesHref } from '@/lib/mistakes'
import { revisionLinks } from '@/lib/enChapters'

/** The teacher's words, set apart from the app's own text so they read as a note from a person. */
function TeacherNote({ text, testId, title }: { text: string; testId: string; title: string }) {
  return (
    <div data-testid={testId} className="rounded-lg border-l-4 border-sky-500 bg-sky-500/10 p-3 text-sm">
      <p className="text-xs font-medium text-sky-800 dark:text-sky-200">{title}</p>
      <p className="whitespace-pre-line">{text}</p>
    </div>
  )
}

/**
 * S4, on the student's own graded test: which questions to go back to, that the wrong grile come back in the daily
 * review, and - when the teacher opened the quiz for practice - a practice of just those questions.
 */
/** Where a question's card starts: the mistakes card links there, focus lands on its title. */
const itemTitleId = (itemId: number) => `result-item-${itemId}`

/** Scrolls to a question and puts focus on its title, so keyboard and screen-reader users land there too. */
function goToQuestion(itemId: number) {
  const title = document.getElementById(itemTitleId(itemId))
  if (!title) return
  const reduceMotion = window.matchMedia?.('(prefers-reduced-motion: reduce)').matches
  title.scrollIntoView?.({ behavior: reduceMotion ? 'auto' : 'smooth', block: 'start' })
  title.focus({ preventScroll: true })
}

function PracticeMistakes({ result }: { result: AttemptResultViewDto }) {
  const mistakes = mistakesOf(result.items)
  if (mistakes.labels.length === 0) return null
  const many = mistakes.labels.length > 1
  return (
    <Card data-testid="practice-mistakes">
      <CardContent className="space-y-2 py-4 text-sm">
        <p className="font-medium">Exersează ce ai greșit</p>
        <p>
          {many ? 'Întrebările ' : 'Întrebarea '}
          {mistakes.labels.map((label, i) => (
            <Fragment key={mistakes.itemIds[i]}>
              {i > 0 && ', '}
              <a
                href={`#${itemTitleId(mistakes.itemIds[i])}`}
                aria-label={`Întrebarea ${label}`}
                className="font-medium text-primary underline underline-offset-2"
                onClick={(e) => {
                  e.preventDefault()
                  goToQuestion(mistakes.itemIds[i])
                }}
              >
                {label}
              </a>
            </Fragment>
          ))}
          {many ? ' nu au ieșit complet' : ' nu a ieșit complet'}: uită-te mai jos la răspunsul corect și la barem.
        </p>
        {mistakes.wrongChoices > 0 && (
          <p className="text-muted-foreground">
            {roCount(mistakes.wrongChoices, 'grilă greșită revine', 'grile greșite revin')} în{' '}
            <Link to="/review" className="text-primary hover:underline">recapitularea zilnică</Link>, începând de mâine.
          </p>
        )}
        {result.practiceAllowed && (
          <Link
            to={practiceMistakesHref(result.quizId, mistakes.itemIds)}
            className={buttonVariants({ size: 'sm' })}
            data-testid="practice-mistakes-link"
          >
            Exersează doar ce ai greșit
          </Link>
        )}
      </CardContent>
    </Card>
  )
}

/** The numbered items that pass `keep`, each section heading moved onto the first one kept under it. */
function withHeadings<T extends { heading: unknown }>(numbered: T[], keep: (n: T) => boolean): T[] {
  let pending: T['heading'] = null
  const out: T[] = []
  for (const n of numbered) {
    if (n.heading) pending = n.heading
    if (!keep(n)) continue
    out.push({ ...n, heading: pending })
    pending = null
  }
  return out
}

/** Who is reading: only the wording changes ("Răspunsul tău" vs "Răspunsul elevului"). */
export type ResultAudience = 'student' | 'parent'

function ItemResult({
  item,
  label,
  title,
  audience,
  attemptId,
}: {
  item: ItemResultDto
  /** "3" or "I.3" (a test with sections) */
  label: string
  /** What the card is headed with: "Subiectul 3", or just "3." under a section heading. */
  title: string
  audience: ResultAudience
  attemptId: number
}) {
  const [showBarem, setShowBarem] = useState(false)
  const own = audience === 'student'
  // An unanswered grilă has no response row at all, so null there means 0 — only OPEN waits for the teacher.
  const points =
    item.type === 'OPEN' && item.awardedPoints === null
      ? 'în corectare'
      : `${item.awardedPoints ?? 0} / ${item.points} p`

  return (
    <Card>
      <CardContent className="space-y-3 py-4">
        <div className="flex items-center justify-between gap-2">
          <p id={itemTitleId(item.itemId)} tabIndex={-1} className="scroll-mt-4 text-sm font-medium outline-none">{title}</p>
          <span className="text-sm text-muted-foreground">{points}</span>
        </div>
        <MathContent>{item.statement}</MathContent>
        <QuestionImage itemId={item.itemId} image={item.image} />
        <PartsView
          parts={(item.parts ?? []).map((p) => ({
            label: p.label,
            statement: p.statement,
            points: p.points,
            awardedPoints: p.awardedPoints,
            barem: showBarem ? p.barem : null,
          }))}
        />
        {item.hintsAvailable > 0 && (
          <p data-testid="hints-used" className="text-xs text-muted-foreground">
            Indicii folosite: {item.hintsUsed} din {item.hintsAvailable}
          </p>
        )}

        {item.type === 'SINGLE_CHOICE' ? (
          <div className="space-y-1 text-sm">
            <p className={item.correct ? 'text-emerald-700 dark:text-emerald-300' : 'text-destructive'}>
              {item.correct ? '✓ ' : '✗ '}
              {own ? 'Răspunsul tău:' : 'Răspunsul elevului:'}{' '}
              {item.selectedOptionText === null ? (
                <em>fără răspuns</em>
              ) : (
                <>
                  {item.selectedOptionLetter && `${item.selectedOptionLetter}) `}
                  <MathContent inline>{item.selectedOptionText}</MathContent>
                </>
              )}
            </p>
            {!item.correct && item.correctOptionText !== null && (
              <p>
                Răspuns corect: {item.correctOptionLetter && `${item.correctOptionLetter}) `}
                <MathContent inline>{item.correctOptionText}</MathContent>
              </p>
            )}
          </div>
        ) : (
          <div className="space-y-2">
          <p className="text-sm text-muted-foreground">
            {item.photoIds.length > 0
              ? `${own ? 'Ai trimis' : 'Elevul a trimis'} ${roCount(item.photoIds.length, 'pagină', 'pagini')} cu rezolvarea.`
              : own
                ? 'Nu ai trimis nicio poză pentru această întrebare.'
                : 'Elevul nu a trimis nicio poză pentru această întrebare.'}
          </p>
          {/* Only the student's own API serves the photos; the parent view has no endpoint for them. */}
          {own && (
            <PhotoPages
              urls={item.photoIds.map((photoId) => ownPhotoUrl(attemptId, item.itemId, photoId))}
              alt={(page) => `Poza ta pentru întrebarea ${label}, pagina ${page}`}
            />
          )}
          </div>
        )}

        {item.teacherComment && (
          <TeacherNote text={item.teacherComment} testId="item-teacher-comment" title="Comentariul profesorului" />
        )}

        {revisionLinks(item).length > 0 && (
          <div className="flex flex-wrap gap-2" data-testid="revision-links">
            {revisionLinks(item).map(({ chapter, parts }) => (
              <Link
                key={chapter.id}
                to={`/capitole/${chapter.id}`}
                className={buttonVariants({ size: 'sm', variant: 'secondary' })}
              >
                {parts.length > 0 && `${parts.map((p) => `${p})`).join(', ')} · `}
                Vezi modele: {chapter.code} {chapter.title} →
              </Link>
            ))}
          </div>
        )}

        {(item.barem || (item.parts ?? []).some((p) => p.barem)) && (
          <div className="space-y-2">
            <Button size="xs" variant="outline" onClick={() => setShowBarem((v) => !v)}>
              {showBarem ? 'Ascunde baremul' : 'Arată baremul'}
            </Button>
            {showBarem && item.barem && (
              <div className="rounded-lg bg-muted p-3 text-sm">
                <MathContent>{item.barem}</MathContent>
              </div>
            )}
          </div>
        )}
      </CardContent>
    </Card>
  )
}

/**
 * The result of one attempt: score (or "waiting for the teacher"), then every item with right/wrong
 * and the barem. Shared by the student's own result page and the parent's view of a child's result.
 */
export function AttemptResultView({ result, audience = 'student' }: { result: AttemptResultViewDto; audience?: ResultAudience }) {
  const pointsSoFar = result.items.reduce((sum, i) => sum + (i.awardedPoints ?? 0), 0)
  const practice = result.mode === 'PRACTICE'
  const choiceItems = result.items.filter((i) => i.type === 'SINGLE_CHOICE')
  const choiceCorrect = choiceItems.filter((i) => i.correct).length
  const hintsUsed = result.items.reduce((sum, i) => sum + i.hintsUsed, 0)
  const hintsAvailable = result.items.reduce((sum, i) => sum + i.hintsAvailable, 0)
  // "Doar greșelile": only the questions not fully right, when there are some right ones to hide. A section heading
  // goes with the first question shown under it, so the filtered list still says SUBIECTUL I / II.
  const [onlyMistakes, setOnlyMistakes] = useState(false)
  const mistakeIds = new Set(result.status === 'GRADED' && !practice ? mistakesOf(result.items).itemIds : [])
  const offerFilter = mistakeIds.size > 0 && mistakeIds.size < result.items.length
  const shown = withHeadings(numberItems(result.items), (n) => !onlyMistakes || mistakeIds.has(n.item.itemId))
  // The student's first 10, on their own result; read once, so a later render does not ask again.
  const [celebrate] = useState(() =>
    audience === 'student' && !practice && result.status === 'GRADED' && result.maxScore > 0
    && result.finalScore === result.maxScore && celebrateFirstTen())

  return (
    <>
      {celebrate && <Confetti />}
      <Card>
        <CardContent className="space-y-2 py-4">
          <div className="flex flex-wrap items-center justify-between gap-2">
            <h1 className="text-xl font-semibold">{result.quizTitle}</h1>
            {practice ? <AttemptModeBadge mode={result.mode} /> : <AttemptStatusBadge status={result.status} />}
          </div>
          {practice ? (
            <p data-testid="result-practice" className="text-sm text-muted-foreground">
              Sesiune de practică — fără notă și fără efect asupra progresului.
              {choiceItems.length > 0 && ` Grile corecte: ${choiceCorrect} din ${choiceItems.length}.`}
              {hintsAvailable > 0 && ` Indicii folosite: ${hintsUsed} din ${hintsAvailable}.`}
            </p>
          ) : result.status === 'GRADED' && result.finalScore !== null ? (
            <GradeFigure score={result.finalScore} maxScore={result.maxScore} testId="result-score" />
          ) : (
            <p className="text-sm text-muted-foreground">
              Grilele sunt corectate ({pointsSoFar} puncte până acum). Întrebările cu rezolvare
              așteaptă corectura profesorului — revino mai târziu pentru nota finală.
            </p>
          )}
          {result.teacherComment && (
            <TeacherNote text={result.teacherComment} testId="teacher-comment" title="Comentariul profesorului pentru toată lucrarea" />
          )}
        </CardContent>
      </Card>

      {audience === 'student' && !practice && result.status === 'GRADED' && <PracticeMistakes result={result} />}

      {offerFilter && (
        <div className="flex justify-end">
          <Button
            size="sm"
            variant={onlyMistakes ? 'default' : 'outline'}
            aria-pressed={onlyMistakes}
            onClick={() => setOnlyMistakes((v) => !v)}
          >
            Doar greșelile ({mistakeIds.size})
          </Button>
        </div>
      )}

      {shown.map(({ item, label, local, heading }, index) => (
        <Fragment key={item.position + '-' + index}>
          {heading && <SectionHeadingView heading={heading} />}
          <ItemResult
            item={item}
            label={label}
            title={label.includes('.') ? `${local}.` : `Întrebarea ${label}`}
            audience={audience}
            attemptId={result.attemptId}
          />
        </Fragment>
      ))}
    </>
  )
}
