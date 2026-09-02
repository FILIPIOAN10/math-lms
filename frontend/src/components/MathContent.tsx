import { useEffect, useRef } from 'react'
import renderMathInElement from 'katex/contrib/auto-render'

/**
 * Renders plain text that may contain LaTeX math (KaTeX). Math is written with $…$ /
 * $$…$$ (or \(…\) / \[…\]) delimiters; everything else stays literal text. The text is
 * set as a text node (never innerHTML), so untrusted content cannot inject markup —
 * KaTeX only replaces the recognised math spans.
 */
export function MathContent({ children, className }: { children: string; className?: string }) {
  const ref = useRef<HTMLDivElement>(null)

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

  return <div ref={ref} className={className} style={{ whiteSpace: 'pre-wrap' }} />
}
