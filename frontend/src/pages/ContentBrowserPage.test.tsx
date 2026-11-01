import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ContentBrowserPage } from '@/pages/ContentBrowserPage'

const api = vi.hoisted(() => ({
  listMyClasses: vi.fn(),
  listClasses: vi.fn(),
  listBooks: vi.fn(),
  listChapters: vi.fn(),
  listExercises: vi.fn(),
}))
vi.mock('@/lib/api', async (original) => ({ ...(await original<typeof import('@/lib/api')>()), ...api }))
vi.mock('@/context/AuthContext', () => ({ useAuth: () => ({ user: { role: 'STUDENT' } }) }))

function renderAt(url: string) {
  return render(
    <MemoryRouter initialEntries={[url]}>
      <ContentBrowserPage />
    </MemoryRouter>,
  )
}

describe('ContentBrowserPage', () => {
  beforeEach(() => {
    api.listMyClasses.mockReset().mockResolvedValue([{ id: 1, name: 'Clasa a 8-a', description: null }])
    api.listBooks.mockReset().mockResolvedValue([{ id: 2, title: 'Algebră', description: null }])
    api.listChapters.mockReset().mockResolvedValue([{ id: 3, title: 'Fracții', description: null }])
    api.listExercises.mockReset().mockResolvedValue([{ id: 4, statement: 'Calculează 1/2 + 1/4', solution: null, difficulty: 'EASY' }])
  })

  it('opens the chapter a result links to', async () => {
    renderAt('/content?class=1&book=2&chapter=3')

    expect(await screen.findByText('Calculează 1/2 + 1/4')).toBeInTheDocument()
    expect(api.listExercises).toHaveBeenCalledWith(3)
  })

  it('stays on the list when the linked class is not one of the student\'s', async () => {
    renderAt('/content?class=9&book=2&chapter=3')

    expect(await screen.findByText('Clasa a 8-a')).toBeInTheDocument()
    expect(api.listExercises).not.toHaveBeenCalled()
  })

  it('opens on the list without a link', async () => {
    renderAt('/content')

    expect(await screen.findByText('Clasa a 8-a')).toBeInTheDocument()
    expect(api.listBooks).not.toHaveBeenCalled()
  })
})
