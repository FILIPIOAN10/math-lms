import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { describe, expect, it, vi } from 'vitest'
import { RowMenu } from '@/components/RowMenu'

describe('RowMenu', () => {
  function renderMenu(onDelete = vi.fn(), onCopy = vi.fn()) {
    render(
      <MemoryRouter initialEntries={['/']}>
        <Routes>
          <Route
            path="/"
            element={
              <RowMenu
                label="Mai multe acțiuni pentru Simulare EN"
                items={[
                  { label: 'Statistici', to: '/stats' },
                  { label: 'Copiază', onClick: onCopy },
                  { label: 'Șterge', onClick: onDelete, destructive: true },
                ]}
              />
            }
          />
          <Route path="/stats" element={<p>pagina de statistici</p>} />
        </Routes>
      </MemoryRouter>,
    )
    return { onDelete, onCopy }
  }

  it('keeps the actions behind one labelled button until it is opened', async () => {
    renderMenu()

    expect(screen.queryByRole('menuitem')).not.toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: 'Mai multe acțiuni pentru Simulare EN' }))

    const items = await screen.findAllByRole('menuitem')
    expect(items.map((i) => i.textContent)).toEqual(['Statistici', 'Copiază', 'Șterge'])
  })

  it('runs an action and closes', async () => {
    const { onCopy } = renderMenu()
    await userEvent.click(screen.getByRole('button', { name: 'Mai multe acțiuni pentru Simulare EN' }))

    await userEvent.click(await screen.findByRole('menuitem', { name: 'Copiază' }))

    expect(onCopy).toHaveBeenCalled()
  })

  it('follows a link item', async () => {
    renderMenu()
    await userEvent.click(screen.getByRole('button', { name: 'Mai multe acțiuni pentru Simulare EN' }))

    await userEvent.click(await screen.findByRole('menuitem', { name: 'Statistici' }))

    expect(await screen.findByText('pagina de statistici')).toBeInTheDocument()
  })

  it('sets the destructive action apart', async () => {
    renderMenu()
    await userEvent.click(screen.getByRole('button', { name: 'Mai multe acțiuni pentru Simulare EN' }))

    expect(await screen.findByRole('menuitem', { name: 'Șterge' })).toHaveClass('text-destructive')
  })
})
