import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { AdminEnChapterSlotsPage } from '@/pages/AdminEnChapterSlotsPage'
import type { EnChapterSummary } from '@/lib/api'

const api = vi.hoisted(() => ({
  listEnChapters: vi.fn(),
  getEnChapterSlots: vi.fn(),
  saveEnChapterSlots: vi.fn(),
}))
vi.mock('@/lib/api', async (original) => ({ ...(await original<typeof import('@/lib/api')>()), ...api }))

const chapter = (id: number, code: string, title: string): EnChapterSummary => ({
  id, code, title, area: code.startsWith('A') ? 'ALGEBRA' : 'GEOMETRY', position: id, parentId: null, bookPage: null, photoCount: 0, exampleCount: 0,
})

describe('AdminEnChapterSlotsPage', () => {
  beforeEach(() => {
    Object.values(api).forEach((fn) => fn.mockReset())
    api.listEnChapters.mockResolvedValue([chapter(1, 'A1', 'Ordinea operațiilor'), chapter(2, 'A2', 'Proporții'),
      chapter(3, 'A3', 'Procente'), chapter(19, 'G8', 'Corpuri'), chapter(20, 'G9', 'Drepte și plane')])
    api.getEnChapterSlots.mockResolvedValue([
      { slot: 'I.1', chapterId: 1 }, { slot: 'I.2', chapterId: 2 },
      { slot: 'III.6', chapterId: 20 }, { slot: 'III.6.a', chapterId: 19 }, { slot: 'III.6.b', chapterId: 20 },
    ])
    api.saveEnChapterSlots.mockResolvedValue(undefined)
  })

  function renderPage() {
    render(<AdminEnChapterSlotsPage />, { wrapper: MemoryRouter })
  }

  it('shows every place on the paper with its chapter, and nothing for a place without one', async () => {
    renderPage()

    await waitFor(() => expect(screen.getByLabelText('Capitolul pentru I.1')).toHaveDisplayValue('A1 Ordinea operațiilor'))
    expect(screen.getByLabelText('Capitolul pentru I.3')).toHaveDisplayValue('— fără legătură —')
    expect(screen.getByLabelText('Capitolul pentru III.6 a)')).toHaveDisplayValue('G8 Corpuri')
    expect(screen.getByLabelText('Capitolul pentru III.1 a)')).toHaveDisplayValue('Ca problema')
    expect(screen.queryByLabelText('Capitolul pentru I.1 a)')).not.toBeInTheDocument() // grile have no a), b)
    expect(screen.getByRole('button', { name: 'Salvează' })).toBeDisabled()
  })

  it('saves the whole list with the changes, leaving out the places set to none', async () => {
    renderPage()
    await waitFor(() => expect(screen.getByLabelText('Capitolul pentru I.2')).toHaveDisplayValue('A2 Proporții'))

    fireEvent.change(screen.getByLabelText('Capitolul pentru I.2'), { target: { value: '3' } })
    fireEvent.change(screen.getByLabelText('Capitolul pentru III.6 b)'), { target: { value: '' } })
    fireEvent.change(screen.getByLabelText('Capitolul pentru II.1'), { target: { value: '19' } })
    fireEvent.click(screen.getByRole('button', { name: 'Salvează (3)' }))

    await waitFor(() => expect(api.saveEnChapterSlots).toHaveBeenCalled())
    expect(api.saveEnChapterSlots.mock.calls[0][0]).toEqual(expect.arrayContaining([
      { slot: 'I.1', chapterId: 1 }, { slot: 'I.2', chapterId: 3 }, { slot: 'III.6', chapterId: 20 },
      { slot: 'III.6.a', chapterId: 19 }, { slot: 'II.1', chapterId: 19 },
    ]))
    expect(api.saveEnChapterSlots.mock.calls[0][0]).toHaveLength(5)
    expect(await screen.findByRole('status')).toHaveTextContent('Salvat.')
  })

  it('takes changes back before saving', async () => {
    renderPage()
    await waitFor(() => expect(screen.getByLabelText('Capitolul pentru I.1')).toHaveDisplayValue('A1 Ordinea operațiilor'))

    fireEvent.change(screen.getByLabelText('Capitolul pentru I.1'), { target: { value: '2' } })
    fireEvent.click(screen.getByRole('button', { name: 'Anulează modificările' }))

    expect(screen.getByLabelText('Capitolul pentru I.1')).toHaveDisplayValue('A1 Ordinea operațiilor')
    expect(api.saveEnChapterSlots).not.toHaveBeenCalled()
  })
})
