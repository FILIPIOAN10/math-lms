import { render } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { MathContent } from '@/components/MathContent'

describe('MathContent large', () => {
  const fraction = '$\\frac{3}{4}$'

  it('shrinks a fraction inside a line, as KaTeX does by default', () => {
    const { container } = render(<MathContent inline>{fraction}</MathContent>)
    expect(container.querySelector('.mfrac .mtight')).not.toBeNull()
  })

  it('sets the digits of a fraction at full size in an answer', () => {
    const { container } = render(<MathContent inline large>{fraction}</MathContent>)
    expect(container.querySelector('.mfrac')).not.toBeNull()
    expect(container.querySelector('.mfrac .mtight')).toBeNull()
  })
})

describe('MathContent', () => {
  it('is a block (a div) by default and renders the math with KaTeX', () => {
    const { container } = render(<MathContent>{'Rezolvă $x^2 = 4$.'}</MathContent>)

    expect(container.firstElementChild?.tagName).toBe('DIV')
    expect(container.querySelector('.katex')).not.toBeNull()
    expect(container.textContent).toContain('Rezolvă')
  })

  it('renders inside running text as a span, so a <p> stays valid HTML', () => {
    const { container } = render(<p>Răspuns corect: <MathContent inline>{'$x = 4$'}</MathContent></p>)

    expect(container.querySelector('p > span')).not.toBeNull()
    expect(container.querySelector('p div')).toBeNull()
    expect(container.querySelector('.katex')).not.toBeNull()
  })

  it('shows text without math as it is, never as markup', () => {
    const { container } = render(<MathContent inline>{'<b>nu e HTML</b>'}</MathContent>)

    expect(container.querySelector('b')).toBeNull()
    expect(container.textContent).toBe('<b>nu e HTML</b>')
  })
})

describe('MathContent tables', () => {
  it('draws an imported LaTeX tabular as a real table, with the text around it and math in the cells', () => {
    const statement = String.raw`Tabelul:
\begin{tabular}{|l|c|}
\hline
Sport & $x^2$ \\
\hline
Elevi & 7 \\
\end{tabular}
Cât face?`
    const { container } = render(<MathContent>{statement}</MathContent>)
    const table = container.querySelector('[data-testid="statement-table"]')!
    expect(table).not.toBeNull()
    expect(container.textContent).not.toContain('tabular')
    expect(container.textContent).not.toContain('hline')
    const cells = [...table.querySelectorAll('td')].map((td) => td.textContent)
    expect(cells[0]).toBe('Sport')
    expect(cells[2]).toBe('Elevi')
    expect(cells[3]).toBe('7')
    expect(table.querySelectorAll('.katex').length).toBe(1) // $x^2$ typeset inside its cell
    expect(container.textContent).toContain('Tabelul:')
    expect(container.textContent).toContain('Cât face?')
  })

  it('draws a Markdown table with its first row as the header', () => {
    const { container } = render(<MathContent>{'| x | 1 |\n|---|---|\n| f(x) | 3 |'}</MathContent>)
    expect([...container.querySelectorAll('th')].map((th) => th.textContent)).toEqual(['x', '1'])
    expect([...container.querySelectorAll('td')].map((td) => td.textContent)).toEqual(['f(x)', '3'])
  })

  it('still never turns cell text into markup', () => {
    const { container } = render(<MathContent>{String.raw`\begin{tabular}{c} <b>nu</b> \end{tabular}`}</MathContent>)
    expect(container.querySelector('b')).toBeNull()
    expect(container.querySelector('td')!.textContent).toBe('<b>nu</b>')
  })
})
