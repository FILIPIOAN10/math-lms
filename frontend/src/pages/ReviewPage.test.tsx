import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ReviewPage } from '@/pages/ReviewPage'
import { ApiError, type ReviewToday } from '@/lib/api'

const api = vi.hoisted(() => ({
  getReviewToday: vi.fn(), answerReview: vi.fn(), getReviewExtra: vi.fn(), answerReviewExtra: vi.fn(),
}))
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
    api.getReviewExtra.mockReset()
    api.answerReviewExtra.mockReset()
  })

  it('shows today, the streak and the first question with where it is in the boxes', async () => {
    renderPage()

    expect(await screen.findByRole('heading', { level: 1, name: 'Recapitulare' })).toBeInTheDocument()
    expect(await screen.findByTestId('review-stats')).toHaveTextContent('Azi: 0 / 2')
    expect(screen.getByTestId('review-stats')).toHaveTextContent('Seria: 3 zile')
    expect(screen.getByTestId('review-stats')).toHaveTextContent('Stăpânite: 1 din 8')
    expect(screen.getByText('Cât face 1+1?')).toBeInTheDocument()
    expect(screen.getByText('Întrebarea 1 din 2')).toBeInTheDocument()
  })

  it('checks an answer, explains it, says where the question goes next, then moves on', async () => {
    api.answerReview.mockResolvedValue({
      correct: false, correctOptionId: 10, solution: 'Doi.', boxBefore: 1, boxAfter: 1, nextDueOn: '2026-10-12',
    })
    renderPage()

    await userEvent.click(await screen.findByLabelText('b) greșit'))
    await userEvent.click(screen.getByRole('button', { name: 'Verifică' }))

    await waitFor(() => expect(api.answerReview).toHaveBeenCalledWith(1, 11))
    // A second try is offered first; giving up shows the answer at once.
    await userEvent.click(await screen.findByRole('button', { name: 'Arată răspunsul' }))
    const feedback = await screen.findByTestId('review-feedback')
    expect(feedback).toHaveTextContent('Greșit')
    expect(feedback).toHaveTextContent('Doi.')
    expect(feedback).toHaveTextContent('Revine pe 12 oct.')
    expect(screen.getByText('corect').closest('label')).toHaveTextContent('✓')

    await userEvent.click(screen.getByRole('button', { name: 'Următoarea' }))
    expect(screen.getByText('Cât face 2+2?')).toBeInTheDocument()
  })

  it('after a wrong answer says what to revise and opens the lesson in another tab', async () => {
    api.answerReview.mockResolvedValue({
      correct: false, correctOptionId: 10, solution: 'Doi.', boxBefore: 1, boxAfter: 1, nextDueOn: '2026-10-12',
      enChapter: { id: 16, code: 'G1', title: 'Segmente', ready: true, revisionTopic: 'simetrie și segmente' },
    })
    renderPage()

    await userEvent.click(await screen.findByLabelText('b) greșit'))
    await userEvent.click(screen.getByRole('button', { name: 'Verifică' }))
    await userEvent.click(await screen.findByRole('button', { name: 'Arată răspunsul' }))

    expect(await screen.findByTestId('revision-note')).toHaveTextContent('Dacă vrei să recapitulezi noțiunile despre simetrie și segmente:')
    const lesson = screen.getByRole('link', { name: /Vezi lecția: G1 Segmente/ })
    expect(lesson).toHaveAttribute('href', '/capitole/16')
    expect(lesson).toHaveAttribute('target', '_blank')
  })

  it('reveals the teacher hints one at a time, and none once the answer is shown', async () => {
    api.getReviewToday.mockResolvedValue(today({
      questions: [{ ...question(1, 'Cât face 1+1?'), hints: ['Numără pe degete.', 'Unu și încă unu.'] }],
    }))
    api.answerReview.mockResolvedValue({
      correct: true, correctOptionId: 10, solution: null, boxBefore: 1, boxAfter: 2, nextDueOn: '2026-10-14',
    })
    renderPage()

    await userEvent.click(await screen.findByRole('button', { name: 'Arată indiciul 1 din 2' }))
    expect(screen.getByText('Numără pe degete.')).toBeInTheDocument()
    expect(screen.queryByText('Unu și încă unu.')).not.toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: 'Arată indiciul 2 din 2' }))
    expect(screen.getByText('Unu și încă unu.')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /Arată indiciul/ })).not.toBeInTheDocument()

    await userEvent.click(screen.getByLabelText('a) corect'))
    await userEvent.click(screen.getByRole('button', { name: 'Verifică' }))
    expect(await screen.findByTestId('review-feedback')).toHaveTextContent('Corect!')
    expect(screen.queryByTestId('review-hints')).not.toBeInTheDocument()
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
    expect(await screen.findByTestId('review-feedback')).toHaveTextContent('Revine pe')
    await userEvent.click(screen.getByRole('button', { name: 'Termină' }))

    expect(await screen.findByText(/Gata pe azi/)).toBeInTheDocument()
    expect(screen.getByTestId('review-stats')).toHaveTextContent('Seria: 4 zile')
  })

  it('offers five more once today is done, answered without moving anything, until none are left', async () => {
    api.getReviewToday.mockResolvedValue(today({ questions: [], remainingToday: 0, doneToday: 10 }))
    api.getReviewExtra
      .mockResolvedValueOnce([question(7, 'Cât face 3+3?', 2)])
      .mockResolvedValueOnce([])
    api.answerReviewExtra.mockResolvedValue({
      correct: true, correctOptionId: 70, solution: null, boxBefore: 2, boxAfter: 2, nextDueOn: '2026-10-14',
    })
    renderPage()

    await userEvent.click(await screen.findByRole('button', { name: 'Încă 5 întrebări' }))
    expect(api.getReviewExtra).toHaveBeenCalledWith([])
    expect(await screen.findByText('Cât face 3+3?')).toBeInTheDocument()
    await userEvent.click(screen.getByLabelText('a) corect'))
    await userEvent.click(screen.getByRole('button', { name: 'Verifică' }))

    expect(api.answerReviewExtra).toHaveBeenCalledWith(7, 70)
    expect(api.answerReview).not.toHaveBeenCalled()
    expect(await screen.findByTestId('review-feedback')).toHaveTextContent('Exersare în plus')
    expect(screen.getByTestId('review-stats')).toHaveTextContent('Azi: 10 / 10') // not counted as today's
    await userEvent.click(screen.getByRole('button', { name: 'Termină' }))

    await userEvent.click(await screen.findByRole('button', { name: 'Încă 5' }))
    expect(api.getReviewExtra).toHaveBeenLastCalledWith([7])
    expect(await screen.findByTestId('review-extra-none')).toHaveTextContent('Ai trecut prin toate întrebările tale')
    expect(screen.queryByTestId('review-extra')).not.toBeInTheDocument()
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
