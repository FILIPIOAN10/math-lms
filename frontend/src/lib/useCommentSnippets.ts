import { useCallback, useEffect, useMemo, useState } from 'react'
import {
  createCommentSnippet,
  deleteCommentSnippet,
  listCommentSnippets,
  markCommentSnippetUsed,
  type CommentSnippet,
} from '@/lib/api'
import { errorMessage } from '@/lib/errors'

/** The teacher's saved grading comments, loaded once per grading page and shared by every question's form. */
export interface SnippetBank {
  snippets: CommentSnippet[]
  /** Saves `text` for reuse; resolves with an error message instead of throwing. */
  save: (text: string) => Promise<string | null>
  remove: (id: number) => Promise<string | null>
  /** Fire-and-forget: a failure only leaves the order as it was. */
  markUsed: (id: number) => void
}

export const EMPTY_BANK: SnippetBank = {
  snippets: [],
  save: () => Promise.resolve(null),
  remove: () => Promise.resolve(null),
  markUsed: () => undefined,
}

export function useCommentSnippets(): SnippetBank {
  const [snippets, setSnippets] = useState<CommentSnippet[]>([])

  useEffect(() => {
    let cancelled = false
    listCommentSnippets()
      .then((list) => !cancelled && setSnippets(list))
      .catch(() => undefined) // grading works without them
    return () => {
      cancelled = true
    }
  }, [])

  const save = useCallback(async (text: string) => {
    try {
      const saved = await createCommentSnippet(text)
      setSnippets((list) => (list.some((s) => s.id === saved.id) ? list : [...list, saved]))
      return null
    } catch (e) {
      return errorMessage(e)
    }
  }, [])

  const remove = useCallback(async (id: number) => {
    try {
      await deleteCommentSnippet(id)
      setSnippets((list) => list.filter((s) => s.id !== id))
      return null
    } catch (e) {
      return errorMessage(e)
    }
  }, [])

  const markUsed = useCallback((id: number) => {
    // The order on screen stays put while grading (chips jumping under the pointer would be worse); it shows next time.
    setSnippets((list) => list.map((s) => (s.id === id ? { ...s, useCount: s.useCount + 1 } : s)))
    markCommentSnippetUsed(id).catch(() => undefined)
  }, [])

  return useMemo(() => ({ snippets, save, remove, markUsed }), [snippets, save, remove, markUsed])
}
