import { useEffect, useState } from 'react'
import { Button } from '@/components/ui/button'
import { Textarea } from '@/components/ui/textarea'
import { MarkdownText } from '@/components/MarkdownText'
import { HomeSection } from '@/components/home/HomeBits'
import {
  approveWrittenLesson,
  editWrittenLesson,
  getWrittenLesson,
  type WrittenLesson,
  type WrittenLessonClaim,
  type WrittenLessonSource,
} from '@/lib/api'
import { errorMessage } from '@/lib/errors'
import { cn } from '@/lib/utils'

const COLOUR_CLASS: Record<WrittenLessonClaim['colour'], string> = {
  green: 'border-l-emerald-600 bg-emerald-500/5',
  yellow: 'border-l-amber-500 bg-amber-500/10',
  red: 'border-l-red-600 bg-red-500/10',
}

/** The bits of HTML a transcription left in a source (<u>, <b>, <br>…): the reader would show them as text. */
function withoutTags(text: string) {
  return text.replace(/<\/?(?:u|b|i|em|strong|span|sup|sub|br)\b[^>]*>/gi, '')
}

function plural(n: number, one: string, many: string) {
  return `${n} ${n === 1 ? one : many}`
}

/** One claim of the review: its colour, why, and the sources it stands on (opened on demand). */
function ClaimRow({ claim, sources, photoUrl }: {
  claim: WrittenLessonClaim
  sources: Map<string, WrittenLessonSource>
  photoUrl?: (photoId: number) => string
}) {
  const [open, setOpen] = useState<string | null>(null)
  return (
    <div data-testid={`claim-${claim.id}`} data-colour={claim.colour}
      className={cn('space-y-1 rounded-md border-l-4 px-3 py-2', COLOUR_CLASS[claim.colour])}>
      <span className="font-mono text-xs text-muted-foreground">{claim.id}</span>
      {claim.text && <MarkdownText photoUrl={photoUrl} headingLevel={5}>{claim.text}</MarkdownText>}
      {claim.reasons.map((reason) => <p key={reason} className="text-sm text-muted-foreground">{reason}</p>)}
      <div className="flex flex-wrap gap-1">
        {claim.sourceIds.filter((id) => sources.has(id)).map((id) => (
          <Button key={id} size="sm" variant="ghost" className="h-7 text-xs" aria-expanded={open === id}
            onClick={() => setOpen((o) => (o === id ? null : id))}>
            {`${id} · ${sources.get(id)!.label}`}
          </Button>
        ))}
      </div>
      {open && sources.get(open) && (
        <div className="rounded bg-muted px-3 py-2 text-sm">
          <MarkdownText photoUrl={photoUrl} headingLevel={6}>{withoutTags(sources.get(open)!.text)}</MarkdownText>
        </div>
      )}
    </div>
  )
}

/**
 * A chapter's NEW lesson (V78): written from every source of the chapter and checked claim by claim. A student reads
 * it once the teacher has approved it and shown the chapter; the teacher reviews the claims with the colours of the
 * checks, may correct the text, and approves it (never with a red claim in the AI's text).
 */
export function WrittenLessonSection({ chapterId, teacher, photoUrl }: {
  chapterId: number
  teacher: boolean
  photoUrl?: (photoId: number) => string
}) {
  const [lesson, setLesson] = useState<WrittenLesson | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [reviewing, setReviewing] = useState(false)
  const [editing, setEditing] = useState<string | null>(null)
  const [readYellow, setReadYellow] = useState(false)
  const [busy, setBusy] = useState(false)

  useEffect(() => {
    let cancelled = false
    setLesson(null)
    getWrittenLesson(chapterId)
      .then((l) => !cancelled && setLesson(l))
      .catch(() => undefined) // the chapter page works without it
    return () => {
      cancelled = true
    }
  }, [chapterId])

  if (!lesson?.exists || !lesson.markdown) return null
  if (!teacher) {
    return (
      <HomeSection title="Lecția">
        <MarkdownText photoUrl={photoUrl} headingLevel={3}>{lesson.markdown}</MarkdownText>
      </HomeSection>
    )
  }

  const red = lesson.redCount ?? 0
  const yellow = lesson.yellowCount ?? 0
  const edited = Boolean(lesson.editedByTeacher)
  const approved = lesson.status === 'approved'
  const canApprove = !approved && (edited || (red === 0 && (yellow === 0 || readYellow)))
  const sources = new Map((lesson.sources ?? []).map((s) => [s.id, s]))

  function run(action: () => Promise<void>) {
    setBusy(true)
    setError(null)
    action()
      .then(() => getWrittenLesson(chapterId))
      .then((l) => {
        setLesson(l)
        setEditing(null)
      })
      .catch((e) => setError(errorMessage(e)))
      .finally(() => setBusy(false))
  }

  let state: string
  if (approved) state = 'Aprobată. Elevii o văd după ce arăți capitolul („Arată-o elevilor”).'
  else if (edited) state = 'Textul e corectat de tine: culorile de mai jos erau pentru textul scris de AI. Elevii n-o văd.'
  else state = `Ciornă: ${plural(red, 'roșie', 'roșii')}, ${yellow} de citit. Elevii n-o văd.`

  let groups: { where: string; claims: WrittenLessonClaim[] }[] = []
  for (const claim of lesson.claims ?? []) {
    const last = groups[groups.length - 1]
    if (last && last.where === claim.where) last.claims.push(claim)
    else groups = [...groups, { where: claim.where, claims: [claim] }]
  }

  return (
    <HomeSection title="Lecția">
      <p className={cn('text-sm', !approved && red > 0 && !edited ? 'font-medium text-red-700 dark:text-red-400'
        : 'text-muted-foreground')}>{state}</p>
      <div className="flex flex-wrap items-center gap-2">
        <Button size="sm" variant="outline" aria-expanded={reviewing} onClick={() => setReviewing((r) => !r)}>
          {reviewing ? 'Arată lecția' : 'Revizuiește afirmațiile'}
        </Button>
        <Button size="sm" variant="outline" onClick={() => setEditing(editing === null ? lesson.markdown ?? '' : null)}>
          {editing === null ? 'Corectează textul' : 'Renunță la corectură'}
        </Button>
        {!approved && !edited && yellow > 0 && (
          <label className="flex items-center gap-1 text-sm">
            <input type="checkbox" checked={readYellow} onChange={(e) => setReadYellow(e.target.checked)} />
            Am citit afirmațiile galbene
          </label>
        )}
        {!approved && (
          <Button size="sm" disabled={busy || !canApprove}
            title={red > 0 && !edited ? 'O afirmație roșie trebuie corectată întâi.' : undefined}
            onClick={() => run(() => approveWrittenLesson(chapterId, readYellow))}>
            Aprobă lecția
          </Button>
        )}
      </div>
      {error && <p className="text-sm text-red-700 dark:text-red-400">{error}</p>}
      {editing !== null && (
        <div className="space-y-2">
          <Textarea aria-label="Textul lecției" className="min-h-96 font-mono text-xs" value={editing}
            onChange={(e) => setEditing(e.target.value)} />
          <Button size="sm" disabled={busy || !editing.trim()} onClick={() => run(() => editWrittenLesson(chapterId, editing))}>
            Salvează
          </Button>
        </div>
      )}
      {editing === null && reviewing && (
        <div className="space-y-4">
          {groups.map((group, i) => (
            <div key={`${group.where}-${i}`} className="space-y-2">
              <h3 className="text-sm font-semibold">{group.where}</h3>
              {group.claims.map((claim) => (
                <ClaimRow key={claim.id} claim={claim} sources={sources} photoUrl={photoUrl} />
              ))}
            </div>
          ))}
        </div>
      )}
      {editing === null && !reviewing && (
        <MarkdownText photoUrl={photoUrl} headingLevel={3}>{lesson.markdown}</MarkdownText>
      )}
    </HomeSection>
  )
}
