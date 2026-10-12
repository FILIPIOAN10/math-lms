import { render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { AttemptResultPage } from '@/pages/AttemptResultPage'
import { type AttemptResultViewDto } from '@/lib/api'
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
