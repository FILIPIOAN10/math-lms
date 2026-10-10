import { render } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { MathContent } from '@/components/MathContent'

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
