import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ChapterPracticePage } from '@/pages/ChapterPracticePage'
import { type ChapterPractice, type ReviewQuestion } from '@/lib/api'

const api = vi.hoisted(() => ({
  getChapterPractice: vi.fn(),
  answerChapterPractice: vi.fn(),
}))
vi.mock('@/lib/api', async (original) => ({ ...(await original<typeof import('@/lib/api')>()), ...api }))

const question = (itemId: number): ReviewQuestion => ({
  itemId, quizTitle: 'Simulare EN', statement: `Cât face ${itemId} + 1?`, box: 0, image: null,
  options: [{ id: itemId * 10, position: 0, text: `${itemId + 1}` }, { id: itemId * 10 + 1, position: 1, text: '0' }],
})

const practice = (questions: ReviewQuestion[], over: Partial<ChapterPractice> = {}): ChapterPractice => ({
  chapterId: 4, code: 'A4', title: 'Fracții', available: questions.length, questions, ...over,
})

function renderPage(url = '/capitole/4/exersare') {
  return render(
    <MemoryRouter initialEntries={[url]}>
      <Routes>
        <Route path="/capitole/:id/exersare" element={<ChapterPracticePage />} />
        <Route path="/quizzes/:id/take" element={<p>Pagina testului</p>} />
      </Routes>
    </MemoryRouter>,
  )
}

describe('ChapterPracticePage', () => {
  beforeEach(() => {
    Object.values(api).forEach((fn) => fn.mockReset())
  })

  it('always has the way back to the lesson, and to the test when it came from one', async () => {
    const user = userEvent.setup()
    api.getChapterPractice.mockResolvedValue(practice([question(1)]))
    renderPage('/capitole/4/exersare?test=%2Fquizzes%2F7%2Ftake')

    const back = await screen.findByRole('navigation', { name: 'Înapoi' })
    expect(within(back).getByRole('link', { name: '← Înapoi la lecție' })).toHaveAttribute('href', '/capitole/4?test=%2Fquizzes%2F7%2Ftake')
    await user.click(within(back).getByRole('button', { name: '← Înapoi la test' }))
    expect(await screen.findByText('Pagina testului')).toBeInTheDocument()
  })

  it('has no "Înapoi la test" when opened from the lesson list', async () => {
    api.getChapterPractice.mockResolvedValue(practice([question(1)]))
    renderPage()

    const back = await screen.findByRole('navigation', { name: 'Înapoi' })
    expect(within(back).getByRole('link', { name: '← Înapoi la lecție' })).toHaveAttribute('href', '/capitole/4')
    expect(screen.queryByTestId('back-to-test')).not.toBeInTheDocument()
  })

  it('checks each grilă at once, with the solution, and ends with how it went', async () => {
    const user = userEvent.setup()
    api.getChapterPractice.mockResolvedValue(practice([question(1), question(2)]))
    api.answerChapterPractice
      .mockResolvedValueOnce({ correct: false, correctOptionId: 10, solution: '1 + 1 = 2', mistake: null })
      .mockResolvedValueOnce({ correct: true, correctOptionId: 20, solution: null, mistake: null })
    renderPage()

    expect(await screen.findByRole('heading', { name: 'Exersează: A4 · Fracții' })).toBeInTheDocument()
    expect(screen.getByText('Întrebarea 1 din 2')).toBeInTheDocument()

    await user.click(screen.getByRole('radio', { name: /0/ }))
    await user.click(screen.getByRole('button', { name: 'Verifică' }))
    expect(api.answerChapterPractice).toHaveBeenCalledWith(4, 1, 11)
    // Wrong: one more try before the answer is shown; the right option is not marked yet.
    expect(await screen.findByTestId('practice-retry')).toHaveTextContent('Mai încearcă o dată')
    expect(screen.queryByTestId('practice-feedback')).not.toBeInTheDocument()
    expect(screen.queryByText('(răspunsul corect)')).not.toBeInTheDocument()
    expect(screen.getByRole('radio', { name: /0/ })).toBeDisabled()
    await user.click(screen.getByRole('radio', { name: /2/ }))
    await user.click(screen.getByRole('button', { name: 'Verifică din nou' }))
    const feedback = await screen.findByTestId('practice-feedback')
    expect(feedback).toHaveTextContent('Corect, din a doua încercare')
    expect(feedback).toHaveTextContent('Rezolvare')
    expect(api.answerChapterPractice).toHaveBeenCalledTimes(1) // the second try is checked here, never sent

    await user.click(screen.getByRole('button', { name: 'Următoarea' }))
    await user.click(screen.getByRole('radio', { name: /3/ }))
    await user.click(screen.getByRole('button', { name: 'Verifică' }))
    await user.click(await screen.findByRole('button', { name: 'Vezi cum ai mers' }))

    const done = screen.getByTestId('practice-done')
    expect(done).toHaveTextContent('Ai răspuns corect la 1 din 2.')
    expect(within(done).getByRole('link', { name: 'Înapoi la capitol' })).toHaveAttribute('href', '/capitole/4')
  })

  it('says when the chapter has nothing to practise yet, and why', async () => {
    api.getChapterPractice.mockResolvedValue(practice([]))
    renderPage()

    const empty = await screen.findByTestId('practice-empty')
    expect(empty).toHaveTextContent('după ce predai un test cu întrebări din el')
    expect(within(empty).getByRole('link', { name: 'Înapoi la capitol' })).toHaveAttribute('href', '/capitole/4')
  })
})
