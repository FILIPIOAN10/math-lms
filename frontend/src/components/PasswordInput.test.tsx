import { fireEvent, render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { PasswordInput } from '@/components/PasswordInput'

describe('PasswordInput', () => {
  it('shows what was typed on the eye button, and hides it again', () => {
    render(<><label htmlFor="p">Parolă</label><PasswordInput id="p" defaultValue="secret12" /></>)

    const field = screen.getByLabelText('Parolă')
    expect(field).toHaveAttribute('type', 'password')
    fireEvent.click(screen.getByRole('button', { name: 'Arată parola' }))
    expect(field).toHaveAttribute('type', 'text')
    expect(screen.getByRole('button', { name: 'Ascunde parola' })).toHaveAttribute('aria-pressed', 'true')
    fireEvent.click(screen.getByRole('button', { name: 'Ascunde parola' }))
    expect(field).toHaveAttribute('type', 'password')
  })

  it('is not a submit button, so a click does not send the form', () => {
    render(<form><PasswordInput aria-label="Parolă" /></form>)
    expect(screen.getByRole('button', { name: 'Arată parola' })).toHaveAttribute('type', 'button')
  })
})
