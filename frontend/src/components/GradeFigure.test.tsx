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

  it('colours the grade by how it went: red under 5, amber up to 8, green from 8', () => {
    const { rerender } = render(<GradeFigure score={4} maxScore={10} testId="g" />) // nota 4,60
    expect(screen.getByTestId('g-grade')).toHaveAttribute('data-band', 'low')
    expect(screen.getByTestId('g-grade').className).toMatch(/text-red-700/)

    rerender(<GradeFigure score={5} maxScore={10} testId="g" />) // nota 5,50
    expect(screen.getByTestId('g-grade')).toHaveAttribute('data-band', 'mid')

    rerender(<GradeFigure score={8} maxScore={10} testId="g" />) // nota 8,20
    expect(screen.getByTestId('g-grade')).toHaveAttribute('data-band', 'high')
    expect(screen.getByTestId('g-grade').className).toMatch(/text-emerald-700/)
  })

  it('shows only the points for a quiz worth nothing (it has no grade)', () => {
    render(<GradeFigure score={0} maxScore={0} testId="g" />)

    expect(screen.getByTestId('g')).toHaveTextContent('0 / 0 puncte')
    expect(screen.getByTestId('g')).not.toHaveTextContent('Nota')
  })
})
