import { MathContent } from '@/components/MathContent'

/** Math delimiters MathContent understands: $…$, $$…$$, \(…\), \[…\]. */
const HAS_MATH = /\$|\\\(|\\\[/

/**
 * Under a comment box: how a comment with maths will look to the student and the parent, set by the same MathContent
 * as their result page. Plain words need no preview, so nothing is shown for them.
 */
export function CommentPreview({ text }: { text: string }) {
  if (!HAS_MATH.test(text)) return null
  return (
    <div data-testid="comment-preview" className="rounded-md bg-muted px-3 py-2 text-sm">
      <p className="text-xs text-muted-foreground">Așa îl vede elevul:</p>
      <MathContent>{text}</MathContent>
    </div>
  )
}
