import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { ResultCheckLine } from '@/components/ResultCheckLine'

describe('ResultCheckLine', () => {
  it('shows nothing for a requirement that asks for no result', () => {
    const { container } = render(<ResultCheckLine audience="teacher" check={null} typed={null} expected={null} />)
    expect(container).toBeEmptyDOMElement()
  })

  it('tells the teacher a right result is right, and to look at the method on the photo', () => {
    render(<ResultCheckLine audience="teacher" check="RIGHT" typed="24,0" expected="24" />)

    const line = screen.getByTestId('result-check')
    expect(line).toHaveTextContent('Rezultat scris de elev: 24,0 ✓ corect')
    expect(line).toHaveTextContent('verifică rezolvarea din poză')
    expect(line).not.toHaveTextContent('în barem') // nothing to compare by eye when they agree
  })

  it('shows a wrong result next to the one in the barem', () => {
    render(<ResultCheckLine audience="teacher" check="WRONG" typed="3" expected="1/2" />)

    expect(screen.getByTestId('result-check')).toHaveTextContent('Rezultat scris de elev: 3 ✗ greșit · în barem: 1/2')
  })

  it('says when the student typed no result', () => {
    render(<ResultCheckLine audience="teacher" check="MISSING" typed={null} expected="1/2" />)

    expect(screen.getByTestId('result-check')).toHaveTextContent('Elevul nu a scris rezultatul final · în barem: 1/2')
  })

  it('speaks to the student on the result page', () => {
    const { rerender } = render(<ResultCheckLine audience="student" check="WRONG" typed="3" expected="1/2" />)
    expect(screen.getByTestId('result-check')).toHaveTextContent('Rezultatul tău: 3 ✗ greșit · în barem: 1/2')

    rerender(<ResultCheckLine audience="student" check="RIGHT" typed="0,5" expected="1/2" />)
    expect(screen.getByTestId('result-check')).toHaveTextContent('Rezultatul tău: 0,5 ✓ corect')
    expect(screen.getByTestId('result-check')).not.toHaveTextContent('verifică')

    rerender(<ResultCheckLine audience="student" check="MISSING" typed={null} expected="1/2" />)
    expect(screen.getByTestId('result-check')).toHaveTextContent('Nu ai scris rezultatul final · în barem: 1/2')
  })

  it('while the test can be handed in again the student sees only what they typed', () => {
    render(<ResultCheckLine audience="student" check={null} typed="3" expected={null} />)

    const line = screen.getByTestId('result-check')
    expect(line).toHaveTextContent('Rezultatul tău: 3')
    expect(line).not.toHaveTextContent('greșit')
    expect(line).not.toHaveTextContent('corect')
  })

  it('shows what the student typed as plain text, never as markup', () => {
    render(<ResultCheckLine audience="teacher" check="WRONG" typed={'<img src=x onerror=alert(1)>'} expected="2" />)

    expect(screen.getByTestId('result-check')).toHaveTextContent('<img src=x onerror=alert(1)>')
    expect(screen.queryByRole('img')).not.toBeInTheDocument()
  })
})
