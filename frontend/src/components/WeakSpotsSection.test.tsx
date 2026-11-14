import { render, screen, within } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { type WeakSpot } from '@/lib/api'
import { WeakSpotsSection } from './WeakSpotsSection'

const api = vi.hoisted(() => ({ getWeakSpots: vi.fn() }))
vi.mock('@/lib/api', async (original) => ({ ...(await original<typeof import('@/lib/api')>()), ...api }))

const spot = (over: Partial<WeakSpot> = {}): WeakSpot => ({
  chapterId: 9, code: 'A3P2', title: 'Puteri', percent: 33, questions: 3, recentWrong: 2, recentAnswered: 3,
  sureWrong: 1, lastAnsweredAt: new Date().toISOString(), lessonChapterId: 4, lessonSectionId: 9, ready: true, ...over,
})

function renderSection() {
  return render(<MemoryRouter><WeakSpotsSection /></MemoryRouter>)
}

describe('WeakSpotsSection', () => {
  beforeEach(() => {
    api.getWeakSpots.mockReset()
  })

  it('lists the weakest chapters with why, the lesson and the practice', async () => {
    api.getWeakSpots.mockResolvedValue([
      spot(),
      spot({ chapterId: 5, code: 'A1', title: 'Fracții', percent: 50, lessonChapterId: 5, lessonSectionId: null, ready: false }),
    ])

    renderSection()

    expect(await screen.findByRole('heading', { name: 'La ce stau prost?' })).toBeInTheDocument()
    const items = screen.getAllByTestId('weak-spot')
    expect(items).toHaveLength(2)
    expect(items[0]).toHaveTextContent('A3P2 Puteri')
    expect(items[0]).toHaveTextContent('33%')
    expect(items[0]).toHaveTextContent('În ultimele 2 săptămâni ai greșit 2 din 3.')
    expect(within(items[0]).getByRole('link', { name: /Deschide lecția/ })).toHaveAttribute('href', '/capitole/4?sectiune=9')
    expect(within(items[0]).getByRole('link', { name: /Exersează capitolul/ })).toHaveAttribute('href', '/capitole/9/exersare')
    // a lesson page with nothing on it yet is not worth a link; the practice still is
    expect(within(items[1]).queryByRole('link', { name: /Deschide lecția/ })).not.toBeInTheDocument()
    expect(within(items[1]).getByRole('link', { name: /Exersează capitolul/ })).toBeInTheDocument()
  })

  it('says so when no chapter is weak', async () => {
    api.getWeakSpots.mockResolvedValue([])

    renderSection()

    expect(await screen.findByText(/Nu ai niciun capitol sub 70%/)).toBeInTheDocument()
  })

  it('shows nothing when the list cannot be read', async () => {
    api.getWeakSpots.mockImplementation(() => Promise.reject(new Error('500')))

    const { container } = renderSection()

    await vi.waitFor(() => expect(api.getWeakSpots).toHaveBeenCalled())
    expect(container).toBeEmptyDOMElement()
  })
})
