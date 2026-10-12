import { useEffect, useId, useLayoutEffect, useRef } from 'react'
import { Button } from '@/components/ui/button'
import { Textarea } from '@/components/ui/textarea'
import { CommentBank } from '@/components/grading/CommentBank'
import { PointsPicker } from '@/components/grading/PointsPicker'
import { MathContent } from '@/components/MathContent'
import type { AdminItemReview } from '@/lib/api'
import { errorMessage } from '@/lib/errors'
import { gradeReady, hasParts, initialGrade, sameGrade, totalOf, type GradeValue } from '@/lib/grading'
import { useAutosave, type SaveStatus } from '@/lib/useAutosave'
import { EMPTY_BANK, type SnippetBank } from '@/lib/useCommentSnippets'

/** Same limit as the server (V20 columns). */
const MAX_COMMENT = 2000

/**
 * Points and comment for one open answer, saved by themselves: a points tap saves at once, the comment when the
 * teacher leaves the box. Nothing is final before "Finalizează" (students see nothing until then), so there is no
 * Save button to forget. The endpoint is a PUT of the whole grade, so sending it again is harmless.
 *
 * A problem with requirements a), b)... (EN Subiectul III) gets a row of points per requirement, each with its barem;
 * the answer's total appears once every requirement has points.
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
  save: (grade: GradeValue) => Promise<void>
  readOnly?: boolean
  bank?: SnippetBank
  autoFocus?: boolean
  registerFlush?: (flush: (() => Promise<boolean>) | null) => void
  onStatus?: (status: SaveStatus) => void
}) {
  const commentId = useId()
  const autosave = useAutosave<GradeValue>(initialGrade(item), save, {
    equals: sameGrade,
    isReady: (g) => gradeReady(item, g),
  })
  const { value, setValue, status, flush } = autosave
  const parted = hasParts(item)
  const total = totalOf(item, value)

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

  const statusLine = (
    <SaveLine status={status} error={autosave.error} awarded={item.awardedPoints} max={item.points} onRetry={() => void flush()} />
  )

  return (
    <div className="space-y-3">
      {parted ? (
        <div className="space-y-3" data-testid="part-grades">
          {(item.parts ?? []).map((part, index) => (
            <div key={part.id} className="space-y-1" data-testid="part-grade">
              <div className="flex gap-2 text-sm">
                <span className="shrink-0 font-semibold">{part.label})</span>
                <MathContent className="min-w-0 flex-1">{part.statement}</MathContent>
              </div>
              {part.barem && (
                <div className="ml-5 rounded-md bg-muted p-2 text-xs">
                  <span className="font-medium">Barem {part.label}): </span>
                  <MathContent inline>{part.barem}</MathContent>
                </div>
              )}
              <div className="ml-5">
                <PointsPicker
                  max={part.points}
                  value={value.parts[part.id] ?? null}
                  disabled={readOnly}
                  autoFocus={autoFocus && index === 0}
                  label={`Puncte cerința ${part.label})`}
                  testId={`grade-part-${part.label}`}
                  onChange={(points, commit) => {
                    setValue({ ...value, parts: { ...value.parts, [part.id]: points } })
                    if (commit) void flush()
                  }}
                />
              </div>
            </div>
          ))}
          <div className="flex flex-wrap items-center gap-x-3 gap-y-1 text-sm">
            <span className="font-medium" data-testid="part-total">
              Total: {total === null ? '—' : total} / {item.points} p
            </span>
            {total === null && (
              <span className="text-muted-foreground">
                (lipsește punctajul la{' '}
                {(item.parts ?? []).filter((p) => (value.parts[p.id] ?? null) === null).map((p) => `${p.label})`).join(', ')})
              </span>
            )}
            {statusLine}
          </div>
        </div>
      ) : (
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
          {statusLine}
        </div>
      )}
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
