import { useEffect, useId, useLayoutEffect, useRef } from 'react'
import { Button } from '@/components/ui/button'
import { Textarea } from '@/components/ui/textarea'
import { CommentBank } from '@/components/grading/CommentBank'
import { PointsPicker } from '@/components/grading/PointsPicker'
import type { AdminItemReview } from '@/lib/api'
import { errorMessage } from '@/lib/errors'
import { useAutosave, type SaveStatus } from '@/lib/useAutosave'
import { EMPTY_BANK, type SnippetBank } from '@/lib/useCommentSnippets'

/** Same limit as the server (V20 columns). */
const MAX_COMMENT = 2000

interface Grade {
  points: number | null
  comment: string
}

const sameGrade = (a: Grade, b: Grade) => a.points === b.points && a.comment.trim() === b.comment.trim()

/**
 * Points and comment for one open answer, saved by themselves: a points tap saves at once, the comment when the
 * teacher leaves the box. Nothing is final before "Finalizează" (students see nothing until then), so there is no
 * Save button to forget. The endpoint is a PUT of the whole grade, so sending it again is harmless.
 *
 * `registerFlush` hands the page a way to send anything still pending (before finalising, before moving on);
 * `onStatus` tells it how this answer stands.
 */
export function ItemGradeForm({
  item,
  save,
  readOnly = false,
  bank = EMPTY_BANK,
  autoFocus = false,
  registerFlush,
  onStatus,
}: {
  item: AdminItemReview
  save: (points: number, comment: string | null) => Promise<void>
  readOnly?: boolean
  bank?: SnippetBank
  autoFocus?: boolean
  registerFlush?: (flush: (() => Promise<boolean>) | null) => void
  onStatus?: (status: SaveStatus) => void
}) {
  const commentId = useId()
  const autosave = useAutosave<Grade>(
    { points: item.awardedPoints, comment: item.teacherComment ?? '' },
    (g) => save(g.points!, g.comment.trim() === '' ? null : g.comment.trim()),
    { equals: sameGrade, isReady: (g) => g.points !== null },
  )
  const { value, setValue, status, flush } = autosave

  // The page may pass new callbacks on every render; only the latest ones are called.
  const register = useRef(registerFlush)
  const report = useRef(onStatus)
  useLayoutEffect(() => {
    register.current = registerFlush
    report.current = onStatus
  })
  useEffect(() => {
    register.current?.(flush)
    return () => register.current?.(null)
  }, [flush])
  useEffect(() => {
    report.current?.(status)
  }, [status])

  return (
    <div className="space-y-3">
      <div className="flex flex-wrap items-center gap-x-3 gap-y-2">
        <PointsPicker
          max={item.points}
          value={value.points}
          disabled={readOnly}
          autoFocus={autoFocus}
          onChange={(points, commit) => {
            setValue({ ...value, points })
            if (commit) void flush()
          }}
        />
        <SaveLine status={status} error={autosave.error} awarded={item.awardedPoints} max={item.points} onRetry={() => void flush()} />
      </div>
      <div className="space-y-1">
        <label htmlFor={commentId} className="text-sm font-medium">Comentariu pentru elev (opțional)</label>
        <Textarea
          id={commentId}
          value={value.comment}
          maxLength={MAX_COMMENT}
          onChange={(e) => setValue({ ...value, comment: e.target.value })}
          onBlur={() => void flush()}
          disabled={readOnly}
          placeholder="Ex.: Raționament corect, dar ai uitat unitatea de măsură."
          className="min-h-16"
        />
      </div>
      {!readOnly && (
        <CommentBank
          bank={bank}
          comment={value.comment}
          onInsert={(text) => {
            const comment = value.comment.trim() === '' ? text : `${value.comment.trimEnd()} ${text}`
            setValue({ ...value, comment })
            void flush()
          }}
        />
      )}
    </div>
  )
}

function SaveLine({ status, error, awarded, max, onRetry }: {
  status: SaveStatus
  error: unknown
  awarded: number | null
  max: number
  onRetry: () => void
}) {
  return (
    <span role="status" className="text-sm" data-testid="grade-status">
      {status === 'saving' && <span className="text-muted-foreground">Se salvează…</span>}
      {status === 'saved' && <span className="text-emerald-700 dark:text-emerald-300">Salvat ✓</span>}
      {status === 'idle' && awarded !== null && (
        <span className="text-emerald-700 dark:text-emerald-300">✓ notat cu {awarded} p</span>
      )}
      {status === 'incomplete' && (
        <span className="text-amber-700 dark:text-amber-400">Alege punctajul (0–{max}) ca să se salveze.</span>
      )}
      {status === 'error' && (
        <span className="text-destructive">
          Nesalvat: {errorMessage(error)}{' '}
          <Button type="button" size="xs" variant="outline" onClick={onRetry}>Reîncearcă</Button>
        </span>
      )}
    </span>
  )
}
