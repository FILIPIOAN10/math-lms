import { MathContent } from '@/components/MathContent'

/** What the teacher wrote about the wrong option a student chose: their mistake, not the general solution. */
export function MistakeNote({ text }: { text: string }) {
  return (
    <div data-testid="item-mistake" className="rounded-lg border-l-4 border-amber-500 bg-amber-500/10 p-3 text-sm">
      <p className="text-xs font-medium text-amber-800 dark:text-amber-200">De ce e greșit</p>
      <MathContent>{text}</MathContent>
    </div>
  )
}
