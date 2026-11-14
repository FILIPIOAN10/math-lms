import { useState } from 'react'
import { Link } from 'react-router-dom'
import { Button } from '@/components/ui/button'
import { Textarea } from '@/components/ui/textarea'
import { MathContent } from '@/components/MathContent'
import {
  draftMistakeFromLessons,
  saveOptionMistake,
  suggestOptionMistake,
  type OptionStat,
  type TutorLessonLink,
} from '@/lib/api'
import { errorMessage } from '@/lib/errors'
import { roCount } from '@/lib/format'
import { lessonHref } from '@/lib/lessons'
import { MAX_MISTAKE } from '@/lib/quizStats'

/**
 * What a wrong option gives away, written where the teacher sees how many chose it - and how many asked „De ce am
 * greșit?” about it. The AI only fills the box ("Propune cu AI"; "Propune din lecții" also names the lesson it comes
 * from): nothing reaches a student until the teacher presses "Salvează".
 */
export function MistakeEditor({ option, aiOn, tutorOn = false, onSaved }: {
  option: OptionStat
  aiOn: boolean
  /** The tutor service is on: drafts come from the EN lessons, with the page. */
  tutorOn?: boolean
  onSaved: () => void
}) {
  const [editing, setEditing] = useState(false)
  const [text, setText] = useState(option.mistake ?? '')
  const [lesson, setLesson] = useState<TutorLessonLink | null>(option.mistakeLesson ?? null)
  const [busy, setBusy] = useState(false)
  const [proposing, setProposing] = useState(false)
  const [notice, setNotice] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)
  const requests = option.requests ?? 0

  async function save() {
    setBusy(true)
    setError(null)
    try {
      const trimmed = text.trim()
      if (lesson && trimmed) {
        await saveOptionMistake(option.id, trimmed, { lessonId: lesson.sectionId ?? lesson.chapterId, lessonPage: lesson.bookPage })
      } else {
        await saveOptionMistake(option.id, trimmed)
      }
      setEditing(false)
      onSaved()
    } catch (e) {
      setError(errorMessage(e))
    } finally {
      setBusy(false)
    }
  }

  async function propose() {
    setProposing(true)
    setError(null)
    setNotice(null)
    try {
      setText(await suggestOptionMistake(option.id))
    } catch (e) {
      setError(errorMessage(e))
    } finally {
      setProposing(false)
    }
  }

  async function proposeFromLessons() {
    setProposing(true)
    setError(null)
    setNotice(null)
    try {
      const draft = await draftMistakeFromLessons(option.id)
      if (draft.answered && draft.text) {
        setText(draft.text)
        setLesson(draft.lesson)
      } else {
        setNotice(draft.refusal ?? 'Nu am asta în manual.')
      }
    } catch (e) {
      setError(errorMessage(e))
    } finally {
      setProposing(false)
    }
  }

  const asked = requests > 0 && (
    <p data-testid="option-requests" className="w-full text-xs font-medium text-amber-800 dark:text-amber-200">
      {roCount(requests, 'elev a întrebat', 'elevi au întrebat')} „De ce am greșit?”
    </p>
  )

  if (!editing) {
    return option.mistake ? (
      <div className="flex w-full flex-wrap items-baseline gap-2 text-muted-foreground" data-testid="option-mistake">
        <MathContent inline>{option.mistake}</MathContent>
        {option.mistakeLesson && (
          <Link to={lessonHref(option.mistakeLesson)} className="text-xs underline">
            Lecția {option.mistakeLesson.chapterCode}, pagina {option.mistakeLesson.bookPage}
          </Link>
        )}
        <Button type="button" size="xs" variant="ghost" onClick={() => { setText(option.mistake ?? ''); setLesson(option.mistakeLesson ?? null); setEditing(true) }}>
          Schimbă explicația
        </Button>
      </div>
    ) : (
      <>
        {asked}
        <Button type="button" size="xs" variant="outline" onClick={() => setEditing(true)}>
          Ce greșeală ascunde?
        </Button>
      </>
    )
  }

  return (
    <div className="w-full space-y-2" data-testid="option-mistake-editor">
      {asked}
      <Textarea
        value={text}
        onChange={(e) => setText(e.target.value)}
        maxLength={MAX_MISTAKE}
        aria-label={`Greșeala din spatele variantei ${option.letter})`}
        placeholder="Ai adunat numitorii. Aduci întâi la același numitor."
        className="min-h-16"
      />
      {lesson && (
        <p data-testid="mistake-lesson" className="flex flex-wrap items-center gap-2 text-xs text-muted-foreground">
          Din lecția {lesson.chapterCode}, pagina {lesson.bookPage} ({lesson.title}): elevul primește linkul sub explicație.
          <Button type="button" size="xs" variant="ghost" onClick={() => setLesson(null)}>
            Fără lecție
          </Button>
        </p>
      )}
      <div className="flex flex-wrap gap-2">
        <Button type="button" size="xs" disabled={busy} onClick={() => void save()}>
          {busy ? 'Se salvează...' : 'Salvează'}
        </Button>
        {tutorOn && (
          <Button type="button" size="xs" variant="outline" disabled={proposing} onClick={() => void proposeFromLessons()}>
            {proposing ? 'Caut în lecții...' : 'Propune din lecții'}
          </Button>
        )}
        {aiOn && (
          <Button type="button" size="xs" variant="outline" disabled={proposing} onClick={() => void propose()}>
            {proposing ? 'AI-ul se gândește...' : 'Propune cu AI'}
          </Button>
        )}
        <Button type="button" size="xs" variant="ghost" onClick={() => { setEditing(false); setError(null); setNotice(null) }}>
          Renunță
        </Button>
      </div>
      {notice && <p role="status" className="text-sm text-muted-foreground">{notice}</p>}
      {error && <p role="alert" className="text-sm text-destructive">{error}</p>}
    </div>
  )
}
