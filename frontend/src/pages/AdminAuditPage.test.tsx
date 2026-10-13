import { fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { AdminAuditPage } from '@/pages/AdminAuditPage'
import { type AuditEntry } from '@/lib/api'

const api = vi.hoisted(() => ({
  getAuditLog: vi.fn(),
  listActiveUsers: vi.fn(),
}))
vi.mock('@/lib/api', async (original) => ({ ...(await original<typeof import('@/lib/api')>()), ...api }))

const entry = (id: number, over: Partial<AuditEntry> = {}): AuditEntry => ({
  id, occurredAt: '2026-10-11T08:30:00Z', action: 'ANSWER_GRADED', category: 'GRADING', actorName: 'Prof. Ionescu',
  entityId: 50, label: 'Teză · întrebarea I.2', details: '3 → 5 p din 5', studentId: 4, studentName: 'Ana Pop',
  relatedName: null, ...over,
})

function Where() {
  const { search } = useLocation()
  return <p data-testid="where">{search}</p>
}

function renderPage(url = '/admin/journal') {
  return render(
    <MemoryRouter initialEntries={[url]}>
      <Routes>
        <Route path="/admin/journal" element={<><AdminAuditPage /><Where /></>} />
      </Routes>
    </MemoryRouter>,
  )
}

describe('AdminAuditPage', () => {
  beforeEach(() => {
    Object.values(api).forEach((fn) => fn.mockReset())
    api.listActiveUsers.mockResolvedValue([
      { id: 4, email: 'ana@scoala.ro', fullName: 'Ana Pop', role: 'STUDENT', parentId: null, parentName: null },
    ])
  })

  it('says what changed, who did it, about whom, and opens the paper', async () => {
    api.getAuditLog.mockResolvedValue({ entries: [entry(9)], nextBefore: null })
    renderPage()

    const row = await screen.findByTestId('audit-row')
    expect(row).toHaveTextContent('Punctaj dat · Teză · întrebarea I.2')
    expect(row).toHaveTextContent('3 → 5 p din 5')
    expect(row).toHaveTextContent('de Prof. Ionescu')
    expect(within(row).getByRole('link', { name: 'Ana Pop' })).toHaveAttribute('href', '/admin/students/4')
    expect(within(row).getByRole('link', { name: 'Vezi lucrarea' })).toHaveAttribute('href', '/admin/grading?attempt=50')
    expect(api.getAuditLog).toHaveBeenCalledWith({ limit: 50 })
  })

  it('reads its filters from the address, so a student page can open it already filtered', async () => {
    api.getAuditLog.mockResolvedValue({ entries: [], nextBefore: null })
    renderPage('/admin/journal?student=4&category=GRADING')

    expect(await screen.findByText('Nimic în jurnal pentru filtrele alese.')).toBeInTheDocument()
    expect(api.getAuditLog).toHaveBeenCalledWith({ studentId: 4, category: 'GRADING', limit: 50 })
    expect(await screen.findByLabelText('Elev')).toHaveValue('4')
  })

  it('changing a filter puts it in the address and reloads; clearing removes them all', async () => {
    api.getAuditLog.mockResolvedValue({ entries: [entry(9)], nextBefore: null })
    renderPage()
    await screen.findByTestId('audit-row')

    fireEvent.change(screen.getByLabelText('Ce'), { target: { value: 'HOMEWORK' } })

    expect(screen.getByTestId('where')).toHaveTextContent('?category=HOMEWORK')
    await screen.findByTestId('audit-row')
    expect(api.getAuditLog).toHaveBeenLastCalledWith({ category: 'HOMEWORK', limit: 50 })

    fireEvent.click(screen.getByRole('button', { name: 'Șterge filtrele' }))
    expect(screen.getByTestId('where')).toBeEmptyDOMElement()
  })

  it('"Mai vechi" adds the next page below and disappears after the last one', async () => {
    api.getAuditLog
      .mockResolvedValueOnce({ entries: [entry(9)], nextBefore: 9 })
      .mockResolvedValueOnce({ entries: [entry(3, { details: '0 p din 5' })], nextBefore: null })
    renderPage()
    await screen.findByTestId('audit-row')

    fireEvent.click(screen.getByRole('button', { name: 'Mai vechi' }))

    await waitFor(() => expect(screen.getAllByTestId('audit-row')).toHaveLength(2))
    expect(api.getAuditLog).toHaveBeenLastCalledWith({ before: 9, limit: 50 })
    expect(screen.queryByRole('button', { name: 'Mai vechi' })).not.toBeInTheDocument()
  })

  it('names the parent of a link and does not link an approved account to a student page', async () => {
    api.getAuditLog.mockResolvedValue({
      entries: [
        entry(2, { action: 'PARENT_LINKED', category: 'PEOPLE', label: 'Părinte legat', details: null, entityId: 4,
          relatedName: 'Maria Pop' }),
        entry(1, { action: 'ACCOUNT_APPROVED', category: 'PEOPLE', label: 'Cont aprobat', details: 'rol: părinte',
          studentId: 7, studentName: 'Maria Pop', actorName: null }),
      ],
      nextBefore: null,
    })
    renderPage()

    const [linked, approved] = await screen.findAllByTestId('audit-row')
    expect(linked).toHaveTextContent('Părinte legat')
    expect(linked).not.toHaveTextContent('Părinte legat · Părinte legat')
    expect(linked).toHaveTextContent('părinte: Maria Pop')
    expect(within(approved).queryByRole('link')).not.toBeInTheDocument()
    expect(approved).toHaveTextContent('cont: Maria Pop')
    expect(approved).toHaveTextContent('de cont șters')
  })
})
