import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { CommandPalette } from '@/components/CommandPalette'
import type { Role } from '@/lib/api'

const api = vi.hoisted(() => ({
  listQuizzes: vi.fn(),
  listActiveUsers: vi.fn(),
  listEnChapters: vi.fn(),
}))
vi.mock('@/lib/api', async (original) => ({ ...(await original<typeof import('@/lib/api')>()), ...api }))

function renderPalette(role: Role, onClose = vi.fn()) {
  render(
    <MemoryRouter initialEntries={['/']}>
      <Routes>
        <Route path="/" element={<CommandPalette role={role} open onClose={onClose} />} />
        <Route path="/admin/grading" element={<p>pagina corectură</p>} />
        <Route path="/admin/students/:id" element={<p>pagina elevului</p>} />
        <Route path="/capitole/:id" element={<p>pagina capitolului</p>} />
      </Routes>
    </MemoryRouter>,
  )
  return onClose
}

const input = () => screen.getByRole('combobox', { name: 'Caută o pagină, un test, un elev' })

describe('CommandPalette', () => {
  beforeEach(() => {
    api.listQuizzes.mockResolvedValue([{ id: 7, title: 'Simulare EN', schoolClassName: '8B' }])
    api.listActiveUsers.mockResolvedValue([{ id: 3, fullName: 'Ana Popescu', email: 'ana@scoala.ro' }])
    api.listEnChapters.mockResolvedValue([{ id: 5, code: 'A1', title: 'Numere reale', parentId: null }])
  })

  it('lists the pages before anything is typed, the first one picked', () => {
    renderPalette('TEACHER')
    const options = screen.getAllByRole('option')
    expect(options[0]).toHaveTextContent('Acasă')
    expect(options[0]).toHaveAttribute('aria-selected', 'true')
    expect(input()).toHaveAttribute('aria-activedescendant', options[0].id)
  })

  it('opens a page with Enter, typed without diacritics', async () => {
    const onClose = renderPalette('TEACHER')
    await userEvent.type(input(), 'corectura{Enter}')
    expect(await screen.findByText('pagina corectură')).toBeInTheDocument()
    expect(onClose).toHaveBeenCalled()
  })

  it("finds a teacher's student and moves to them with the arrows", async () => {
    renderPalette('TEACHER')
    await waitFor(() => expect(api.listActiveUsers).toHaveBeenCalledWith('STUDENT'))
    await userEvent.type(input(), 'ana')
    expect(await screen.findByRole('option', { name: /Ana Popescu/ })).toBeInTheDocument()
    expect(screen.getByRole('group', { name: 'Elevi' })).toBeInTheDocument()
    await userEvent.keyboard('{ArrowDown}')
    const picked = screen.getAllByRole('option').find((o) => o.getAttribute('aria-selected') === 'true')
    expect(input()).toHaveAttribute('aria-activedescendant', picked?.id)
    await userEvent.click(screen.getByRole('option', { name: /Ana Popescu/ }))
    expect(await screen.findByText('pagina elevului')).toBeInTheDocument()
  })

  it('never asks for the staff lists for a student, and finds the chapters', async () => {
    renderPalette('STUDENT')
    await userEvent.type(input(), 'numere{Enter}')
    expect(await screen.findByText('pagina capitolului')).toBeInTheDocument()
    expect(api.listQuizzes).not.toHaveBeenCalled()
    expect(api.listActiveUsers).not.toHaveBeenCalled()
  })

  it('says so when nothing matches', async () => {
    renderPalette('PARENT')
    await userEvent.type(input(), 'zzz')
    expect(screen.getByText('Nimic găsit.')).toBeInTheDocument()
  })
})
