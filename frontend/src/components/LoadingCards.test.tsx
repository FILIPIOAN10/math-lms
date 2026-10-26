import { render, screen, within } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { LoadingCards } from '@/components/LoadingCards'

describe('LoadingCards', () => {
  it('draws grey cards shaped like the page instead of a line of text, and still says "Se încarcă" to a screen reader', () => {
    render(<LoadingCards />)

    const status = screen.getByRole('status')
    expect(status).toHaveAttribute('aria-busy', 'true')
    expect(within(status).getByText('Se încarcă...')).toHaveClass('sr-only')
    expect(within(status).getAllByTestId('skeleton-card')).toHaveLength(3)
  })

  it('draws as many cards as asked, and stops pulsing for people who turned animations off', () => {
    render(<LoadingCards cards={2} />)

    const cards = screen.getAllByTestId('skeleton-card')
    expect(cards).toHaveLength(2)
    expect(cards[0].innerHTML).toMatch(/motion-reduce:animate-none/)
  })
})
