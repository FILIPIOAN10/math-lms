import { useLayoutEffect, useRef } from 'react'
import renderMathInElement from 'katex/contrib/auto-render'
import { splitTables } from '@/lib/textTables'

/**
 * Renders plain text that may contain LaTeX math (KaTeX). Math is written with $…$ /
 * $$…$$ (or \(…\) / \[…\]) delimiters; everything else stays literal text. The text is
 * set as a text node (never innerHTML), so untrusted content cannot inject markup —
 * KaTeX only replaces the recognised math spans.
 *
 * {@code inline} renders a <span> instead of a <div>, for math inside running text (a <p>, a
 * <label>, a list item's sentence), where a <div> is not valid HTML.
 *
 * {@code large} sets the math at full size even inside a line: KaTeX shrinks a fraction in running text, which leaves
 * its digits at 70% - too small for an answer the student has to tell apart on a phone.
 *
 * The text goes in before the browser paints (a layout effect): with a plain effect the element was briefly empty -
 * a blank flash on screen, and tests looking for the text right after a render found nothing under load.
 */
export function MathContent({ children, className, inline = false, large = false }: {
  children: string
  className?: string
  inline?: boolean
  large?: boolean
}) {
  const blocks = splitTables(children)
  if (blocks.length === 1 && blocks[0].kind === 'text') {
    return <MathText className={className} inline={inline} large={large}>{children}</MathText>
  }
  // A statement with a table (an imported LaTeX tabular or a Markdown one): the text around it as before, the
  // table as a real table, formulas in its cells typeset too.
  const Wrapper = inline ? 'span' : 'div'
  return (
    <Wrapper className={className}>
      {blocks.map((block, i) =>
        block.kind === 'text' ? (
          <MathText key={i} large={large}>{block.text}</MathText>
        ) : (
          <span key={i} className="my-2 block overflow-x-auto">
            <table data-testid="statement-table" className="border-collapse text-sm">
              <tbody>
                {block.rows.map((row, r) => (
                  <tr key={r}>
                    {row.map((cell, c) => {
                      const Cell = block.header && r === 0 ? 'th' : 'td'
                      return (
                        <Cell
                          key={c}
                          className={`border border-border px-2.5 py-1 text-center ${Cell === 'th' || (!block.header && c === 0) ? 'bg-muted/60 font-medium' : ''}`}
                        >
                          <MathText inline>{cell}</MathText>
                        </Cell>
                      )
                    })}
                  </tr>
                ))}
              </tbody>
            </table>
          </span>
        ),
      )}
    </Wrapper>
  )
}

/** Text with formulas, no tables: set as a text node, then KaTeX replaces the math spans. */
function MathText({ children, className, inline = false, large = false }: {
  children: string
  className?: string
  inline?: boolean
  large?: boolean
}) {
  const ref = useRef<HTMLElement | null>(null)

  useLayoutEffect(() => {
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
      preProcess: large ? (math) => `\\displaystyle ${math}` : undefined,
    })
  }, [children, large])

  const attach = (el: HTMLElement | null) => {
    ref.current = el
  }
  return inline
    ? <span ref={attach} className={className} style={{ whiteSpace: 'pre-wrap' }} />
    : <div ref={attach} className={className} style={{ whiteSpace: 'pre-wrap' }} />
}
