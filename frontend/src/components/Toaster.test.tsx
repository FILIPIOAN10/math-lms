import { act, render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { Toaster } from '@/components/Toaster'
import { clearToasts, toast } from '@/lib/toast'

describe('Toaster', () => {
  afterEach(() => act(() => clearToasts()))

  it('shows a toast in a polite live region', () => {
    render(<Toaster />)
    act(() => {
      toast({ message: 'Lucrarea a fost salvată', tone: 'success' })
    })
    const region = screen.getByRole('region', { name: 'Notificări' })
    expect(region).toHaveAttribute('aria-live', 'polite')
    expect(screen.getByText('Lucrarea a fost salvată')).toBeInTheDocument()
    expect(screen.getByTestId('toast')).toHaveAttribute('data-tone', 'success')
  })

  it('runs the action and closes the toast', async () => {
    const undo = vi.fn()
    render(<Toaster />)
    act(() => {
      toast({ message: 'Capitol ascuns', action: { label: 'Anulează', onClick: undo } })
    })
    await userEvent.click(screen.getByRole('button', { name: 'Anulează' }))
    expect(undo).toHaveBeenCalledOnce()
    expect(screen.queryByText('Capitol ascuns')).not.toBeInTheDocument()
  })

  it('closes on the × button', async () => {
    render(<Toaster />)
    act(() => {
      toast({ message: 'Salvat' })
    })
    await userEvent.click(screen.getByRole('button', { name: 'Închide notificarea' }))
    expect(screen.queryByText('Salvat')).not.toBeInTheDocument()
  })
})
