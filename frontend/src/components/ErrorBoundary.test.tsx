import { render, screen } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import { ErrorBoundary } from '@/components/ErrorBoundary'

function Boom(): never {
  throw new Error('boom')
}

describe('ErrorBoundary', () => {
  it('renders its children when nothing fails', () => {
    render(<ErrorBoundary><p>pagina</p></ErrorBoundary>)

    expect(screen.getByText('pagina')).toBeInTheDocument()
  })

  it('shows a way out instead of a white page when a child crashes', () => {
    const quiet = vi.spyOn(console, 'error').mockImplementation(() => {}) // React and the boundary both log it

    render(<ErrorBoundary><Boom /></ErrorBoundary>)

    expect(screen.getByRole('alert')).toHaveTextContent('Ceva n-a mers pe această pagină')
    expect(screen.getByRole('button', { name: 'Reîncarcă' })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Acasă' })).toHaveAttribute('href', '/')
    quiet.mockRestore()
  })
})
