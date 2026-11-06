import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { AdminContentPage } from '@/pages/AdminContentPage'
import type { Book, Chapter, SchoolClass, User } from '@/lib/api'

const auth = vi.hoisted(() => ({ value: { user: null as User | null, loading: false } }))
vi.mock('@/context/AuthContext', () => ({ useAuth: () => auth.value }))

const api = vi.hoisted(() => ({
  listClasses: vi.fn(),
  listClassSummaries: vi.fn(),
  listActiveUsers: vi.fn(),
  listBooks: vi.fn(),
  listChapters: vi.fn(),
  moveChapter: vi.fn(),
}))
vi.mock('@/lib/api', async (original) => ({ ...(await original<typeof import('@/lib/api')>()), ...api }))

const cls: SchoolClass = { id: 1, name: 'Clasa a 8-a B', description: null } as SchoolClass
const book: Book = { id: 2, schoolClassId: 1, title: 'Matematică, clasa a VIII-a', description: null } as Book
const chapter = (id: number, title: string): Chapter => ({ id, bookId: 2, title, description: null }) as Chapter

/** Clase → the class → its book: the book's chapters, in the order the server keeps them. */
async function openTheBook() {
  render(
    <MemoryRouter>
      <AdminContentPage />
    </MemoryRouter>,
  )
  await userEvent.click(within(await screen.findByTestId('class-card')).getByRole('button', { name: 'Deschide' }))
  await userEvent.click(await screen.findByRole('button', { name: 'Deschide' }))
  await screen.findByText('Numere reale')
}

describe('AdminContentPage — the chapters of a book', () => {
  beforeEach(() => {
    Object.values(api).forEach((fn) => fn.mockReset())
    auth.value = { user: { email: 'a@x.ro', fullName: 'Prof', role: 'ADMIN', status: 'ACTIVE' }, loading: false }
    api.listClasses.mockResolvedValue([cls])
    api.listClassSummaries.mockResolvedValue([])
    api.listActiveUsers.mockResolvedValue([])
    api.listBooks.mockResolvedValue([book])
    api.listChapters.mockResolvedValue([chapter(10, 'Numere reale'), chapter(11, 'Calcul algebric')])
    api.moveChapter.mockResolvedValue(undefined)
  })

  it('moves a chapter in the book with "Mută mai jos" / "Mută mai sus", offered only where it can go', async () => {
    await openTheBook()

    await userEvent.click(screen.getByRole('button', { name: 'Mai multe acțiuni pentru Numere reale' }))
    const firstItems = (await screen.findAllByRole('menuitem')).map((i) => i.textContent)
    expect(firstItems).toContain('Mută mai jos')
    expect(firstItems).not.toContain('Mută mai sus')

    api.listChapters.mockResolvedValue([chapter(11, 'Calcul algebric'), chapter(10, 'Numere reale')])
    await userEvent.click(screen.getByRole('menuitem', { name: 'Mută mai jos' }))

    expect(api.moveChapter).toHaveBeenCalledWith(10, false)
    await waitFor(() => expect(screen.getAllByTestId('content-row').map((r) => r.textContent)).toEqual([
      expect.stringContaining('Calcul algebric'), expect.stringContaining('Numere reale'),
    ]))
  })

  it('offers no "Mută mai jos" on the last chapter', async () => {
    await openTheBook()

    await userEvent.click(screen.getByRole('button', { name: 'Mai multe acțiuni pentru Calcul algebric' }))
    const items = (await screen.findAllByRole('menuitem')).map((i) => i.textContent)
    expect(items).toContain('Mută mai sus')
    expect(items).not.toContain('Mută mai jos')
  })
})
