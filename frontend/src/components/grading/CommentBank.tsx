import { useState } from 'react'
import type { SnippetBank } from '@/lib/useCommentSnippets'

const same = (a: string, b: string) => a.trim().toLocaleLowerCase('ro') === b.trim().toLocaleLowerCase('ro')

/**
 * The teacher's saved comments as chips under the comment box: a tap adds one to the comment, "⭐ Salvează ca frecvent"
 * keeps the current comment for next time, × takes one off the list (with an undo instead of a confirmation - losing
 * a saved sentence is cheap). Only saved on purpose: a comment typed for one student may name them.
 */
export function CommentBank({
  bank,
  comment,
  onInsert,
  disabled = false,
}: {
  bank: SnippetBank
  comment: string
  onInsert: (text: string) => void
  disabled?: boolean
}) {
  const [removed, setRemoved] = useState<string | null>(null)
  const [message, setMessage] = useState<string | null>(null)
  const known = comment.trim() !== '' && bank.snippets.some((s) => same(s.text, comment))

  async function keep() {
    setMessage((await bank.save(comment.trim())) ?? null)
  }

  async function remove(id: number, text: string) {
    const failure = await bank.remove(id)
    setMessage(failure)
    setRemoved(failure ? null : text)
  }

  async function undo() {
    if (removed === null) return
    const failure = await bank.save(removed)
    setMessage(failure)
    setRemoved(null)
  }

  return (
    <div className="space-y-2" data-testid="comment-bank">
      {bank.snippets.length > 0 && (
        <ul className="flex flex-wrap gap-1.5" aria-label="Comentarii frecvente">
          {bank.snippets.map((s) => (
            <li key={s.id} className="flex items-stretch overflow-hidden rounded-full border bg-background text-xs">
              <button
                type="button"
                className="max-w-64 truncate px-2.5 py-1 hover:bg-muted disabled:opacity-50"
                title={s.text}
                disabled={disabled}
                onClick={() => {
                  onInsert(s.text)
                  bank.markUsed(s.id)
                }}
              >
                {s.text}
              </button>
              <button
                type="button"
                className="border-l px-1.5 text-muted-foreground hover:bg-muted hover:text-foreground"
                aria-label={`Scoate „${s.text}” din comentariile frecvente`}
                onClick={() => void remove(s.id, s.text)}
              >
                ×
              </button>
            </li>
          ))}
        </ul>
      )}
      <div className="flex flex-wrap items-center gap-x-3 gap-y-1 text-xs text-muted-foreground">
        <button
          type="button"
          className="font-medium text-foreground underline-offset-2 hover:underline disabled:no-underline disabled:opacity-50"
          disabled={disabled || comment.trim() === '' || known}
          onClick={() => void keep()}
        >
          {known ? '⭐ E în comentariile frecvente' : '⭐ Salvează ca frecvent'}
        </button>
        <span>fără nume de elevi: le folosești la toate lucrările</span>
      </div>
      {removed !== null && (
        <p role="status" className="text-xs">
          Scos din comentariile frecvente.{' '}
          <button type="button" className="font-medium underline" onClick={() => void undo()}>Anulează</button>
        </p>
      )}
      {message && <p className="text-xs text-destructive">{message}</p>}
    </div>
  )
}
