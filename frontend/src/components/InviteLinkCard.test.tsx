import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { InviteLinkCard } from '@/components/InviteLinkCard'
import type { Invite, Role } from '@/lib/api'

const linkFor = (role: Role): Invite => ({ role, url: `http://localhost:5173/register?token=tok-${role}` })

function renderCard(createInvite = vi.fn((role: Role) => Promise.resolve(linkFor(role)))) {
  render(<InviteLinkCard createInvite={createInvite} />)
  return createInvite
}

describe('InviteLinkCard', () => {
  it('offers student and parent invites, never admin', () => {
    renderCard()

    const options = screen.getAllByRole('option').map((o) => o.getAttribute('value'))
    expect(options).toEqual(['STUDENT', 'PARENT'])
  })

  it('generates a link for the chosen role and shows it', async () => {
    const user = userEvent.setup()
    const createInvite = renderCard()

    await user.selectOptions(screen.getByLabelText('Rol'), 'PARENT')
    await user.click(screen.getByRole('button', { name: 'Generează link' }))

    expect(createInvite).toHaveBeenCalledWith('PARENT')
    expect(await screen.findByLabelText('Link de invitație')).toHaveValue(
      'http://localhost:5173/register?token=tok-PARENT',
    )
    expect(screen.getByText(/părinte/i, { selector: '[data-testid="invite-role"]' })).toBeInTheDocument()
  })

  it('copies the link to the clipboard', async () => {
    const user = userEvent.setup()
    renderCard()

    await user.click(screen.getByRole('button', { name: 'Generează link' }))
    await user.click(await screen.findByRole('button', { name: 'Copiază' }))

    expect(await navigator.clipboard.readText()).toBe('http://localhost:5173/register?token=tok-STUDENT')
    expect(screen.getByRole('button', { name: 'Copiat ✓' })).toBeInTheDocument()
  })

  it('selects the link for a manual copy when the browser blocks the clipboard', async () => {
    const user = userEvent.setup()
    renderCard()
    vi.spyOn(navigator.clipboard, 'writeText').mockRejectedValue(new DOMException('denied', 'NotAllowedError'))

    await user.click(screen.getByRole('button', { name: 'Generează link' }))
    await user.click(await screen.findByRole('button', { name: 'Copiază' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('Ctrl+C')
    const input = screen.getByLabelText('Link de invitație') as HTMLInputElement
    expect(input).toHaveFocus()
    expect([input.selectionStart, input.selectionEnd]).toEqual([0, input.value.length])
    expect(screen.getByRole('button', { name: 'Copiază' })).toBeInTheDocument()
  })

  it('replaces the previous link when a new one is generated', async () => {
    const user = userEvent.setup()
    renderCard()

    await user.click(screen.getByRole('button', { name: 'Generează link' }))
    await screen.findByLabelText('Link de invitație')
    await user.selectOptions(screen.getByLabelText('Rol'), 'PARENT')
    await user.click(screen.getByRole('button', { name: 'Generează link' }))

    await waitFor(() =>
      expect(screen.getByLabelText('Link de invitație')).toHaveValue('http://localhost:5173/register?token=tok-PARENT'),
    )
    expect(screen.getByRole('button', { name: 'Copiază' })).toBeInTheDocument()
  })

  it('says so when the link cannot be generated', async () => {
    const user = userEvent.setup()
    renderCard(vi.fn().mockRejectedValue(new Error('500')))

    await user.click(screen.getByRole('button', { name: 'Generează link' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('Nu am putut genera linkul')
    expect(screen.queryByLabelText('Link de invitație')).not.toBeInTheDocument()
  })
})
