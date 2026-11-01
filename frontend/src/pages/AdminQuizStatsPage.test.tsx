import { render, screen, within } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { AdminQuizStatsPage } from '@/pages/AdminQuizStatsPage'
import type { QuizStatsDto } from '@/lib/api'

const api = vi.hoisted(() => ({ getQuizStats: vi.fn() }))
vi.mock('@/lib/api', async (original) => ({ ...(await original<typeof import('@/lib/api')>()), ...api }))

const stats = (over: Partial<QuizStatsDto> = {}): QuizStatsDto => ({
  quizId: 3, title: 'Ecuații', gradedAttempts: 4, maxScore: 25, averageScore: 20, averagePercent: 80,
  distribution: [
    { label: 'sub 5', count: 0 }, { label: '5 – 6,99', count: 1 }, { label: '7 – 8,99', count: 2 }, { label: '9 – 10', count: 1 },
  ],
  items: [{ itemId: 1, position: 0, type: 'OPEN', statement: 'Rezolvă.', points: 10, correctRate: null, averagePoints: 7.5 }],
  ...over,
})

function renderPage() {
  return render(
    <MemoryRouter initialEntries={['/admin/quizzes/3/stats']}>
      <Routes>
        <Route path="/admin/quizzes/:quizId/stats" element={<AdminQuizStatsPage />} />
      </Routes>
    </MemoryRouter>,
  )
}

describe('AdminQuizStatsPage', () => {
  beforeEach(() => {
    api.getQuizStats.mockReset()
    api.getQuizStats.mockResolvedValue(stats())
  })

  it('leads with the grade, the points under it, with Romanian decimals', async () => {
    renderPage()

    expect(await screen.findByTestId('stats-average')).toHaveTextContent('nota 8,20')
    expect(screen.getByTestId('stats-average-points')).toHaveTextContent('20 / 25 p · 80%')
  })

  it('writes a fraction of a point with a comma', async () => {
    api.getQuizStats.mockResolvedValue(stats({ averageScore: 7.25, averagePercent: 29 }))
    renderPage()

    expect(await screen.findByTestId('stats-average-points')).toHaveTextContent('7,3 / 25 p · 29%')
    expect(within(screen.getByTestId('stats-item')).getByText(/în medie/)).toHaveTextContent('în medie 7,5 / 10 p')
  })

  it('groups the distribution by grades', async () => {
    renderPage()

    expect(await screen.findByText('sub 5')).toBeInTheDocument()
    expect(screen.getByText('9 – 10')).toBeInTheDocument()
  })

  it('counts students, each once', async () => {
    renderPage()

    expect(await screen.findByTestId('stats-graded')).toHaveTextContent('4')
    expect(screen.getByText('Elevi cu notă')).toBeInTheDocument()
    expect(screen.getByText(/fiecare elev o dată/)).toBeInTheDocument()
  })

  it('says so before anyone has a grade', async () => {
    api.getQuizStats.mockResolvedValue(stats({ gradedAttempts: 0, averageScore: null, averagePercent: null }))
    renderPage()

    expect(await screen.findByTestId('stats-empty')).toBeInTheDocument()
    expect(screen.queryByTestId('stats-average')).not.toBeInTheDocument()
  })
})
