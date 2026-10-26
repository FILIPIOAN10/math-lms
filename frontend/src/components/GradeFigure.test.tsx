import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { GradeFigure } from '@/components/GradeFigure'

describe('GradeFigure', () => {
  it('puts the 1–10 grade first and large, the points and the percent under it', () => {
    render(<GradeFigure score={19} maxScore={25} testId="g" />)

    expect(screen.getByTestId('g')).toHaveTextContent(/^Nota\s*7,84\s*19 \/ 25 puncte · 76%$/)
    expect(screen.getByTestId('g-grade')).toHaveTextContent('7,84')
    expect(screen.getByTestId('g-grade').className).toMatch(/text-4xl/)
  })

  it('is a little smaller where it shares a card', () => {
    render(<GradeFigure score={10} maxScore={10} size="md" testId="g" />)

    expect(screen.getByTestId('g-grade')).toHaveTextContent('10')
    expect(screen.getByTestId('g-grade').className).toMatch(/text-3xl/)
  })

  it('shows only the points for a quiz worth nothing (it has no grade)', () => {
    render(<GradeFigure score={0} maxScore={0} testId="g" />)

    expect(screen.getByTestId('g')).toHaveTextContent('0 / 0 puncte')
    expect(screen.getByTestId('g')).not.toHaveTextContent('Nota')
  })
})
