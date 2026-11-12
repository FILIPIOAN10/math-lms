import { render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { AttemptResultPage } from '@/pages/AttemptResultPage'
import { type AttemptResultViewDto, type ItemResultDto } from '@/lib/api'
import { NAV_COUNTS_CHANGED } from '@/lib/roles'

const api = vi.hoisted(() => ({
  getAttemptResult: vi.fn(),
  markResultSeen: vi.fn(),
}))
vi.mock('@/lib/api', async (original) => ({ ...(await original<typeof import('@/lib/api')>()), ...api }))

const result = (over: Partial<AttemptResultViewDto> = {}): AttemptResultViewDto => ({
  attemptId: 50, quizTitle: 'Teză', status: 'GRADED', finalScore: 8, maxScore: 10, items: [], mode: 'TEST',
  teacherComment: null, quizId: 7, practiceAllowed: false, ...over,
})

function renderPage() {
  return render(
    <MemoryRouter initialEntries={['/quizzes/attempts/50/result']}>
      <Routes>
        <Route path="/quizzes/attempts/:attemptId/result" element={<AttemptResultPage />} />
      </Routes>
    </MemoryRouter>,
  )
}

const wrongGrila = (over: Partial<ItemResultDto> = {}): ItemResultDto => ({
  itemId: 1, position: 0, type: 'SINGLE_CHOICE', statement: '1/2 + 1/4 =', points: 5, awardedPoints: 0, correct: false,
  selectedOptionText: '2/6', correctOptionText: '3/4', barem: null, photoUploaded: false, photoIds: [], hintsUsed: 0,
  hintsAvailable: 0, teacherComment: null, selectedOptionLetter: 'b', correctOptionLetter: 'a', ...over,
})

describe('AttemptResultPage', () => {
  beforeEach(() => {
    Object.values(api).forEach((fn) => fn.mockReset())
    api.markResultSeen.mockResolvedValue(undefined)
  })

  it('S3: opening a graded test marks it seen and tells the links to re-count', async () => {
    api.getAttemptResult.mockResolvedValue(result())
    const counted = vi.fn()
    window.addEventListener(NAV_COUNTS_CHANGED, counted)
    renderPage()

    expect(await screen.findByTestId('result-score')).toHaveTextContent('8')
    await waitFor(() => expect(counted).toHaveBeenCalled())
    expect(api.markResultSeen).toHaveBeenCalledWith(50)
    window.removeEventListener(NAV_COUNTS_CHANGED, counted)
  })

  it('forgets the "Revin la ea" marks of a paper handed in elsewhere (time ran out, another device)', async () => {
    localStorage.setItem('mathlms-flags-50', JSON.stringify({ ids: [1, 2], at: Date.now() }))
    localStorage.setItem('mathlms-flags-51', JSON.stringify({ ids: [3], at: Date.now() }))
    api.getAttemptResult.mockResolvedValue(result())
    renderPage()

    expect(await screen.findByTestId('result-score')).toBeInTheDocument()
    expect(localStorage.getItem('mathlms-flags-50')).toBeNull()
    expect(localStorage.getItem('mathlms-flags-51')).not.toBeNull() // another attempt's marks stay
    localStorage.clear()
  })

  it('tells a student why their option was wrong and where to practise', async () => {
    api.getAttemptResult.mockResolvedValue(result({ items: [wrongGrila({
      mistake: 'Ai adunat numitorii.',
      chapter: { id: 3, title: 'Fracții', bookId: 2, classId: 1 },
    })] }))
    renderPage()

    expect(await screen.findByTestId('item-mistake')).toHaveTextContent('Ai adunat numitorii.')
    expect(screen.getByRole('link', { name: /Exerciții: Fracții/ })).toHaveAttribute('href', '/content?class=1&book=2&chapter=3')
  })

  it('sends nobody to more exercises for a question they got right', async () => {
    api.getAttemptResult.mockResolvedValue(result({ items: [wrongGrila({
      correct: true, awardedPoints: 5, chapter: { id: 3, title: 'Fracții', bookId: 2, classId: 1 },
    })] }))
    renderPage()

    await screen.findByTestId('result-score')
    expect(screen.queryByRole('link', { name: /Exerciții/ })).not.toBeInTheDocument()
    expect(screen.queryByTestId('item-mistake')).not.toBeInTheDocument()
  })

  it('does not mark a result still waiting for the teacher, nor a practice', async () => {
    api.getAttemptResult.mockResolvedValueOnce(result({ status: 'SUBMITTED', finalScore: null }))
    const { unmount } = renderPage()
    await screen.findByText(/așteaptă corectura profesorului/)
    unmount()

    api.getAttemptResult.mockResolvedValueOnce(result({ mode: 'PRACTICE', finalScore: null }))
    renderPage()
    await screen.findByTestId('result-practice')

    expect(api.markResultSeen).not.toHaveBeenCalled()
  })
})
