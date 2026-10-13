import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ReviewPage } from '@/pages/ReviewPage'
import { ApiError, type ReviewToday } from '@/lib/api'

const api = vi.hoisted(() => ({ getReviewToday: vi.fn(), answerReview: vi.fn() }))
vi.mock('@/lib/api', async (original) => ({ ...(await original<typeof import('@/lib/api')>()), ...api }))

const question = (itemId: number, statement: string, box = 1) => ({
  itemId, quizTitle: 'Fracții — exersare', statement, box,
  options: [{ id: itemId * 10, position: 0, text: 'corect' }, { id: itemId * 10 + 1, position: 1, text: 'greșit' }],
})
const today = (over: Partial<ReviewToday> = {}): ReviewToday => ({
  limit: 10, doneToday: 0, remainingToday: 2, streak: 3, totalCards: 8, mastered: 1,
  questions: [question(1, 'Cât face 1+1?'), question(2, 'Cât face 2+2?', 2)], ...over,
})

function renderPage() {
  return render(
    <MemoryRouter>
      <ReviewPage />
    </MemoryRouter>,
  )
}

describe('ReviewPage', () => {
  beforeEach(() => {
    api.getReviewToday.mockReset().mockResolvedValue(today())
    api.answerReview.mockReset()
  })

  it('shows today, the streak and the first question with where it is in the boxes', async () => {
    renderPage()

    expect(await screen.findByRole('heading', { level: 1, name: 'Recapitulare' })).toBeInTheDocument()
    expect(await screen.findByTestId('review-stats')).toHaveTextContent('Azi: 0 / 2')
    expect(screen.getByTestId('review-stats')).toHaveTextContent('Seria: 3 zile')
    expect(screen.getByTestId('review-stats')).toHaveTextContent('Stăpânite: 1 din 8')
    expect(screen.getByText('Cât face 1+1?')).toBeInTheDocument()
    expect(screen.getByText('Întrebarea 1 din 2 · cutia 1 din 5')).toBeInTheDocument()
  })

  it('checks an answer, explains it, says where the question goes next, then moves on', async () => {
    api.answerReview.mockResolvedValue({
      correct: false, correctOptionId: 10, solution: 'Doi.', boxBefore: 1, boxAfter: 1, nextDueOn: '2026-10-12',
    })
    renderPage()

    await userEvent.click(await screen.findByLabelText('b) greșit'))
    await userEvent.click(screen.getByRole('button', { name: 'Verifică' }))

    await waitFor(() => expect(api.answerReview).toHaveBeenCalledWith(1, 11))
    const feedback = await screen.findByTestId('review-feedback')
    expect(feedback).toHaveTextContent('Greșit')
    expect(feedback).toHaveTextContent('Doi.')
    expect(feedback).toHaveTextContent('Rămâne în cutia 1 · revine pe 12 oct.')
    expect(screen.getByText('corect').closest('label')).toHaveTextContent('✓')

    await userEvent.click(screen.getByRole('button', { name: 'Următoarea' }))
    expect(screen.getByText('Cât face 2+2?')).toBeInTheDocument()
  })

  it('celebrates when today is done', async () => {
    api.getReviewToday
      .mockResolvedValueOnce(today({ questions: [question(1, 'Cât face 1+1?')], remainingToday: 1 }))
      .mockResolvedValueOnce(today({ questions: [], remainingToday: 0, doneToday: 1, streak: 4 }))
    api.answerReview.mockResolvedValue({
      correct: true, correctOptionId: 10, solution: null, boxBefore: 1, boxAfter: 2, nextDueOn: '2026-10-14',
    })
    renderPage()

    await userEvent.click(await screen.findByLabelText('a) corect'))
    await userEvent.click(screen.getByRole('button', { name: 'Verifică' }))
    expect(await screen.findByTestId('review-feedback')).toHaveTextContent('Urcă în cutia 2')
    await userEvent.click(screen.getByRole('button', { name: 'Termină' }))

    expect(await screen.findByText(/Gata pe azi/)).toBeInTheDocument()
    expect(screen.getByTestId('review-stats')).toHaveTextContent('Seria: 4 zile')
  })

  it('explains where questions come from when there are none yet', async () => {
    api.getReviewToday.mockResolvedValue(today({ questions: [], remainingToday: 0, totalCards: 0, streak: 0, mastered: 0 }))
    renderPage()
    expect(await screen.findByText(/apar aici după ce exersezi sau predai un test/)).toBeInTheDocument()
  })

  it('says nothing is due when the student is up to date', async () => {
    api.getReviewToday.mockResolvedValue(today({ questions: [], remainingToday: 0 }))
    renderPage()
    expect(await screen.findByText(/Nimic de recapitulat azi/)).toBeInTheDocument()
  })

  it("shows the server's reason and reloads when an answer is refused", async () => {
    api.answerReview.mockRejectedValue(new ApiError(409, 'Întrebarea nu e de recapitulat azi.'))
    renderPage()

    await userEvent.click(await screen.findByLabelText('a) corect'))
    await userEvent.click(screen.getByRole('button', { name: 'Verifică' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('Întrebarea nu e de recapitulat azi.')
    await waitFor(() => expect(api.getReviewToday).toHaveBeenCalledTimes(2))
  })

  it('needs a choice before checking', async () => {
    renderPage()
    const card = await screen.findByTestId('review-card')
    expect(within(card).getByRole('button', { name: 'Verifică' })).toBeDisabled()
  })
})
