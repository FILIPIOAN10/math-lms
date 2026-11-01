import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { AdminQuizStatsPage } from '@/pages/AdminQuizStatsPage'
import type { QuizStatsDto } from '@/lib/api'

const api = vi.hoisted(() => ({ getQuizStats: vi.fn(), correctAnswerKey: vi.fn() }))
vi.mock('@/lib/api', async (original) => ({ ...(await original<typeof import('@/lib/api')>()), ...api }))

const stats = (over: Partial<QuizStatsDto> = {}): QuizStatsDto => ({
  quizId: 3, title: 'Ecuații', gradedAttempts: 4, maxScore: 25, averageScore: 20, averagePercent: 80,
  distribution: [
    { label: 'sub 5', count: 0 }, { label: '5 – 6,99', count: 1 }, { label: '7 – 8,99', count: 2 }, { label: '9 – 10', count: 1 },
  ],
  items: [{ itemId: 1, position: 0, type: 'OPEN', statement: 'Rezolvă.', points: 10, correctRate: null, averagePoints: 7.5, options: [], suspect: null }],
  ...over,
})

const grila = (suspect: QuizStatsDto['items'][number]['suspect'] = null): QuizStatsDto['items'][number] => ({
  itemId: 2, position: 0, type: 'SINGLE_CHOICE', statement: 'Cât face 1/2 + 1/4?', points: 5, correctRate: 0.25, averagePoints: 1.3,
  suspect,
  options: [
    { id: 20, letter: 'a', text: '2/6', correct: true, chosen: 1 },
    { id: 21, letter: 'b', text: '3/4', correct: false, chosen: 2 },
  ],
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

  it('shows how many students chose each option, and who left it blank', async () => {
    api.getQuizStats.mockResolvedValue(stats({ items: [grila()] }))
    renderPage()

    const rows = await screen.findAllByTestId('stats-option')
    expect(rows[0]).toHaveTextContent('1 elev')
    expect(rows[1]).toHaveTextContent('2 elevi')
    expect(screen.getByTestId('stats-blank')).toHaveTextContent('Fără răspuns: 1 elev')
    expect(screen.queryByTestId('stats-suspect')).not.toBeInTheDocument()
  })

  it('warns about a marked answer the strongest students disagree with', async () => {
    api.getQuizStats.mockResolvedValue(stats({ items: [grila({ letter: 'b', chosen: 3, strong: 3 })] }))
    renderPage()

    expect(await screen.findByTestId('stats-suspects')).toHaveTextContent('De verificat întrebarea 1')
    expect(screen.getByTestId('stats-suspect')).toHaveTextContent('3 din 3 elevi buni au ales varianta b)')
  })

  it('corrects the answer after asking, then shows the new numbers', async () => {
    api.getQuizStats.mockResolvedValueOnce(stats({ items: [grila()] }))
    api.getQuizStats.mockResolvedValueOnce(stats({ averageScore: 15 }))
    api.correctAnswerKey.mockResolvedValue({ changedGrades: 3 })
    renderPage()

    await userEvent.click(await screen.findByRole('button', { name: 'Asta e varianta corectă' }))
    expect(screen.getByText('Schimbi răspunsul corect la întrebarea 1?')).toBeInTheDocument()
    expect(api.correctAnswerKey).not.toHaveBeenCalled()
    await userEvent.click(screen.getByRole('button', { name: 'Schimbă și recalculează' }))

    expect(await screen.findByTestId('stats-notice')).toHaveTextContent('Răspunsul corect e acum b). S-au schimbat 3 note.')
    expect(api.correctAnswerKey).toHaveBeenCalledWith(2, 21)
    expect(screen.getByTestId('stats-average-points')).toHaveTextContent('15 / 25 p')
  })

  it('says so before anyone has a grade', async () => {
    api.getQuizStats.mockResolvedValue(stats({ gradedAttempts: 0, averageScore: null, averagePercent: null }))
    renderPage()

    expect(await screen.findByTestId('stats-empty')).toBeInTheDocument()
    expect(screen.queryByTestId('stats-average')).not.toBeInTheDocument()
  })
})
