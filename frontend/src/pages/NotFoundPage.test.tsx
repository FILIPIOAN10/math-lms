import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { describe, expect, it } from 'vitest'
import { NotFoundPage } from '@/pages/NotFoundPage'

describe('NotFoundPage', () => {
  it('says the page does not exist and offers the way home', () => {
    render(<MemoryRouter><NotFoundPage /></MemoryRouter>)

    expect(screen.getByText('Pagina nu există')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Înapoi acasă' })).toHaveAttribute('href', '/')
  })
})
