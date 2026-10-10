import { useEffect, useRef } from 'react'
import renderMathInElement from 'katex/contrib/auto-render'

/**
 * Renders plain text that may contain LaTeX math (KaTeX). Math is written with $…$ /
 * $$…$$ (or \(…\) / \[…\]) delimiters; everything else stays literal text. The text is
 * set as a text node (never innerHTML), so untrusted content cannot inject markup —
 * KaTeX only replaces the recognised math spans.
 *
 * {@code inline} renders a <span> instead of a <div>, for math inside running text (a <p>, a
 * <label>, a list item's sentence), where a <div> is not valid HTML.
 */
export function MathContent({ children, className, inline = false }: { children: string; className?: string; inline?: boolean }) {
  const ref = useRef<HTMLElement | null>(null)

  useEffect(() => {
    const el = ref.current
    if (!el) {
      return
    }
    el.textContent = children
    renderMathInElement(el, {
      delimiters: [
        { left: '$$', right: '$$', display: true },
        { left: '$', right: '$', display: false },
        { left: '\\[', right: '\\]', display: true },
        { left: '\\(', right: '\\)', display: false },
      ],
      throwOnError: false,
    })
  }, [children])

  const attach = (el: HTMLElement | null) => {
    ref.current = el
  }
  return inline
    ? <span ref={attach} className={className} style={{ whiteSpace: 'pre-wrap' }} />
    : <div ref={attach} className={className} style={{ whiteSpace: 'pre-wrap' }} />
}
