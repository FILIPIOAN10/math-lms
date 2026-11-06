import { act, render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { TakeQuizPage } from '@/pages/TakeQuizPage'
import {
  ApiError,
  type AnswerFeedback,
  type AttemptMode,
  type QuizPreview,
  type StartedAttemptDto,
  type StudentAssignment,
} from '@/lib/api'

const api = vi.hoisted(() => ({
  startQuizAttempt: vi.fn(),
  saveQuizAnswer: vi.fn(),
  uploadQuizPhoto: vi.fn(),
  deleteQuizPhoto: vi.fn(),
  submitQuizAttempt: vi.fn(),
  revealQuizHint: vi.fn(),
  getStudentQuizzes: vi.fn(),
  getQuizPreview: vi.fn(),
  getMyAssignments: vi.fn(),
}))
vi.mock('@/lib/api', async (importOriginal) => ({ ...(await importOriginal<typeof import('@/lib/api')>()), ...api }))

function started(
  mode: AttemptMode,
  answers: StartedAttemptDto['answers'] = [],
  { hintCount = 0, revealed = [] as string[] } = {},
): StartedAttemptDto {
  return {
    attemptId: 50,
    status: 'IN_PROGRESS',
    mode,
    deadlineAt: null,
    serverNow: '2026-10-04T12:00:00Z',
    answers,
    revealedHints: revealed.length > 0 ? [{ itemId: 100, hints: revealed }] : [],
    quiz: {
      id: 7,
      title: 'Simulare EN',
      description: null,
      timeLimitMinutes: null,
      items: [
        {
          id: 100, position: 1, type: 'SINGLE_CHOICE', statement: 'Alege varianta corecta', points: 5,
          options: [
            { id: 1000, position: 0, text: 'Varianta A' },
            { id: 1001, position: 1, text: 'Varianta B' },
          ],
          hintCount,
        },
      ],
    },
  }
}

const preview = (over: Partial<QuizPreview> = {}): QuizPreview => ({
  maxAttempts: null, attemptsUsed: 0, blockedBy: null, openTest: null, openPractice: null,
  id: 7, title: 'Simulare EN', description: 'Recapitulare pentru examen', timeLimitMinutes: null, practiceAllowed: true,
  itemCount: 3, maxScore: 11, ...over,
})

/** A test with three items - two grile and one photographed solution - optionally on a clock. */
function startedThree(secondsLeft: number | null = null): StartedAttemptDto {
  const serverNow = '2026-10-04T12:00:00Z'
  return {
    attemptId: 50,
    status: 'IN_PROGRESS',
    mode: 'TEST',
    deadlineAt: secondsLeft === null ? null : new Date(Date.parse(serverNow) + secondsLeft * 1000).toISOString(),
    serverNow,
    answers: [],
    revealedHints: [],
    quiz: {
      id: 7, title: 'Simulare EN', description: null, timeLimitMinutes: secondsLeft === null ? null : 30,
      items: [
        { id: 100, position: 1, type: 'SINGLE_CHOICE', statement: 'Primul subiect', points: 2, hintCount: 0,
          options: [{ id: 1000, position: 0, text: 'A1' }, { id: 1001, position: 1, text: 'B1' }] },
        { id: 101, position: 2, type: 'SINGLE_CHOICE', statement: 'Al doilea subiect', points: 5, hintCount: 0,
          options: [{ id: 1010, position: 0, text: 'A2' }, { id: 1011, position: 1, text: 'B2' }] },
        { id: 102, position: 3, type: 'OPEN', statement: 'Al treilea subiect', points: 4, hintCount: 0, options: [] },
      ],
    },
  }
}

/** A test with one numeric question. */
function startedNumeric(mode: AttemptMode = 'TEST', answers: StartedAttemptDto['answers'] = []): StartedAttemptDto {
  return {
    attemptId: 50, status: 'IN_PROGRESS', mode, deadlineAt: null, serverNow: '2026-10-04T12:00:00Z', answers, revealedHints: [],
    quiz: {
      id: 7, title: 'Simulare EN', description: null, timeLimitMinutes: null,
      items: [{ id: 200, position: 1, type: 'NUMERIC', statement: 'Cât face 1/2 + 1/4?', points: 5, hintCount: 0, options: [] }],
    },
  }
}

async function beginNumeric(answer: StartedAttemptDto, label: string) {
  api.startQuizAttempt.mockResolvedValue(answer)
  await userEvent.click(await screen.findByRole('button', { name: label }))
  return screen.findByTestId('item-number')
}

/** "Testele mele", with what the test page handed it in the navigation state. */
function ListStub() {
  const { state } = useLocation()
  return (
    <>
      <p>lista de teste</p>
      <pre data-testid="list-state">{JSON.stringify(state)}</pre>
    </>
  )
}

function renderPage(query: string) {
  return render(
    <MemoryRouter initialEntries={[`/quizzes/7/take${query}`]}>
      <Routes>
        <Route path="/quizzes/:id/take" element={<TakeQuizPage />} />
        <Route path="/quizzes" element={<ListStub />} />
        <Route path="/quizzes/attempts/:attemptId/result" element={<p>rezultatul</p>} />
      </Routes>
    </MemoryRouter>,
  )
}

async function begin(label: string) {
  await userEvent.click(await screen.findByRole('button', { name: label }))
  await screen.findByText('Alege varianta corecta')
}

describe('TakeQuizPage', () => {
  beforeEach(() => {
    Object.values(api).forEach((fn) => fn.mockReset())
    api.getStudentQuizzes.mockResolvedValue([])
    api.getQuizPreview.mockResolvedValue(preview())
    api.getMyAssignments.mockResolvedValue([])
    localStorage.clear() // the answer queue of attempt 50 must not leak from one test into the next
  })

  describe('numeric question', () => {
    it('saves the number when the student leaves the box and counts the question as answered', async () => {
      api.saveQuizAnswer.mockResolvedValue(null)
      renderPage('')
      const box = await beginNumeric(startedNumeric(), 'Începe testul')

      await userEvent.type(box, '0,75')
      await userEvent.tab()

      await waitFor(() => expect(api.saveQuizAnswer).toHaveBeenCalledWith(50, 200, '0,75', expect.objectContaining({ clientSeq: 1 })))
      expect(screen.getByRole('button', { name: /Întrebarea 1, cu răspuns/ })).toBeInTheDocument()
    })

    it('saves by itself after a pause, without waiting for the student to leave the box', async () => {
      api.saveQuizAnswer.mockResolvedValue(null)
      renderPage('')
      const box = await beginNumeric(startedNumeric(), 'Începe testul')

      await userEvent.type(box, '3/4')

      await waitFor(() => expect(api.saveQuizAnswer).toHaveBeenCalledWith(50, 200, '3/4', expect.anything()), { timeout: 2000 })
      expect(api.saveQuizAnswer).toHaveBeenCalledTimes(1)
    })

    it('warns about what is not a number while there is still time to fix it', async () => {
      api.saveQuizAnswer.mockResolvedValue(null)
      renderPage('')
      const box = await beginNumeric(startedNumeric(), 'Începe testul')

      await userEvent.type(box, '20 cm')

      expect(screen.getByText(/Nu pare un număr/)).toBeInTheDocument()
    })

    it('sends a number still in its pause when the student leaves the page, and leaves nothing ticking', async () => {
      api.saveQuizAnswer.mockResolvedValue(null)
      const { unmount } = renderPage('')
      const box = await beginNumeric(startedNumeric(), 'Începe testul')

      await userEvent.type(box, '3/4')
      unmount()

      expect(api.saveQuizAnswer).toHaveBeenCalledTimes(1)
      expect(api.saveQuizAnswer).toHaveBeenCalledWith(50, 200, '3/4', expect.anything())
      await new Promise((resolve) => setTimeout(resolve, 900)) // past the pause: nothing fires a second time
      expect(api.saveQuizAnswer).toHaveBeenCalledTimes(1)
    })

    it('hands in a number still waiting for its pause', async () => {
      api.saveQuizAnswer.mockResolvedValue(null)
      api.submitQuizAttempt.mockResolvedValue({ attemptId: 50, status: 'GRADED', autoScore: 5, autoMaxScore: 5, finalScore: 5 })
      renderPage('')
      const box = await beginNumeric(startedNumeric(), 'Începe testul')

      await userEvent.type(box, '1')
      await userEvent.click(screen.getByTestId('quiz-submit'))
      await userEvent.click(await screen.findByRole('button', { name: 'Trimite' }))

      await screen.findByText('rezultatul')
      expect(api.saveQuizAnswer).toHaveBeenCalledWith(50, 200, '1', expect.anything())
    })

    it('gets the saved number back when the test is resumed', async () => {
      renderPage('')

      const box = await beginNumeric(startedNumeric('TEST', [
        { itemId: 200, selectedOptionId: null, photoUploaded: false, feedback: null, photoIds: [], numericAnswer: '3/4' },
      ]), 'Începe testul')

      expect(box).toHaveValue('3/4')
    })

    it('in a practice waits for "Verifică", then says what is right', async () => {
      api.saveQuizAnswer.mockResolvedValue({ correct: false, correctOptionId: null, solution: 'Numitorul comun e 4.', correctNumber: '3/4' })
      renderPage('?mode=practice')
      const box = await beginNumeric(startedNumeric('PRACTICE'), 'Începe exersarea')

      await userEvent.type(box, '1')
      await userEvent.tab()
      expect(api.saveQuizAnswer).not.toHaveBeenCalled()
      await userEvent.click(screen.getByTestId('item-number-check'))

      expect(await screen.findByTestId('item-feedback')).toHaveTextContent('Greșit — răspunsul corect e 3/4')
      expect(screen.getByTestId('item-feedback')).toHaveTextContent('Numitorul comun e 4.')
      expect(screen.queryByTestId('revision-note')).not.toBeInTheDocument()
    })

    it('after a wrong practice answer says what to revise and opens the lesson in another tab', async () => {
      api.saveQuizAnswer.mockResolvedValue({
        correct: false, correctOptionId: null, solution: 'Numitorul comun e 4.', correctNumber: '3/4',
        enChapter: { id: 16, code: 'G1', title: 'Segmente', ready: true, revisionTopic: 'simetrie și segmente' },
      })
      renderPage('?mode=practice')
      const box = await beginNumeric(startedNumeric('PRACTICE'), 'Începe exersarea')

      await userEvent.type(box, '1')
      await userEvent.click(screen.getByTestId('item-number-check'))

      expect(await screen.findByTestId('revision-note')).toHaveTextContent('Dacă vrei să recapitulezi noțiunile despre simetrie și segmente:')
      const lesson = screen.getByRole('link', { name: /Vezi lecția: G1 Segmente/ })
      expect(lesson).toHaveAttribute('href', '/capitole/16')
      expect(lesson).toHaveAttribute('target', '_blank')
    })

    it('has no lesson to send a student to while its page is empty', async () => {
      api.saveQuizAnswer.mockResolvedValue({
        correct: false, correctOptionId: null, solution: 'Numitorul comun e 4.', correctNumber: '3/4',
        enChapter: { id: 16, code: 'G1', title: 'Segmente', ready: false, revisionTopic: 'simetrie și segmente' },
      })
      renderPage('?mode=practice')
      const box = await beginNumeric(startedNumeric('PRACTICE'), 'Începe exersarea')

      await userEvent.type(box, '1')
      await userEvent.click(screen.getByTestId('item-number-check'))

      expect(await screen.findByTestId('item-feedback')).toBeInTheDocument()
      expect(screen.queryByTestId('revision-note')).not.toBeInTheDocument()
    })
  })

  describe('practice mode (?mode=practice)', () => {
    it('S4: with ?only shows just the questions got wrong, keeping the numbers of the test', async () => {
      api.startQuizAttempt.mockResolvedValue({ ...startedThree(), mode: 'PRACTICE' })
      renderPage('?mode=practice&only=101,102')
      await userEvent.click(await screen.findByRole('button', { name: 'Începe exersarea' }))

      expect(await screen.findByText('Al doilea subiect')).toBeInTheDocument()
      expect(screen.getByText('Al treilea subiect')).toBeInTheDocument()
      expect(screen.queryByText('Primul subiect')).not.toBeInTheDocument()
      expect(screen.getByTestId('practice-only')).toHaveTextContent('Exersezi doar ce ai greșit: 2 întrebări')
      expect(screen.getByRole('link', { name: 'Exersează tot testul' })).toHaveAttribute('href', '/quizzes/7/take?mode=practice')
      expect(screen.getByRole('button', { name: 'Întrebarea 2, fără răspuns' })).toBeInTheDocument()
      expect(screen.queryByRole('button', { name: /Întrebarea 1,/ })).not.toBeInTheDocument()
    })

    it('a graded test ignores ?only: it is always taken whole', async () => {
      api.startQuizAttempt.mockResolvedValue(startedThree())
      renderPage('?only=101')
      await userEvent.click(await screen.findByRole('button', { name: /Începe/ }))

      expect(await screen.findByText('Primul subiect')).toBeInTheDocument()
      expect(screen.queryByTestId('practice-only')).not.toBeInTheDocument()
    })

    it('starts the attempt in PRACTICE mode and says so, with no countdown', async () => {
      api.startQuizAttempt.mockResolvedValue(started('PRACTICE'))
      renderPage('?mode=practice')

      expect(await screen.findByTestId('quiz-practice-note')).toHaveTextContent('Nu se acordă notă')
      await begin('Începe exersarea')

      expect(api.startQuizAttempt).toHaveBeenCalledWith(7, 'PRACTICE')
      expect(screen.getByTestId('quiz-practice-banner')).toBeInTheDocument()
      expect(screen.queryByTestId('quiz-timer')).not.toBeInTheDocument()
      expect(screen.getByRole('button', { name: 'Termină exersarea' })).toBeInTheDocument()
    })

    it('shows the verdict and the right option as soon as an answer is given', async () => {
      api.startQuizAttempt.mockResolvedValue(started('PRACTICE'))
      const wrong: AnswerFeedback = { correct: false, correctOptionId: 1000, solution: 'Rezolvare: A', mistake: 'Ai adunat numitorii.' }
      api.saveQuizAnswer.mockResolvedValue(wrong)
      renderPage('?mode=practice')
      await begin('Începe exersarea')

      await userEvent.click(screen.getByLabelText('b) Varianta B'))

      expect(await screen.findByTestId('item-feedback')).toHaveTextContent('Greșit')
      expect(screen.getByTestId('item-feedback')).toHaveTextContent('Rezolvare: A')
      expect(screen.getByTestId('item-mistake')).toHaveTextContent('Ai adunat numitorii.')
      expect(api.saveQuizAnswer).toHaveBeenCalledWith(50, 100, 1001, { clientId: expect.any(String), clientSeq: 1 })
      // the right option is outlined green, the wrong pick red
      expect(screen.getByText('Varianta A').closest('label')).toHaveClass('border-emerald-500')
      expect(screen.getByText('Varianta B').closest('label')).toHaveClass('border-destructive')
      // and the verdict names the right option by its letter, as the barem does
      expect(screen.getByTestId('item-feedback')).toHaveTextContent('răspunsul corect e a)')
    })

    it('congratulates a correct answer', async () => {
      api.startQuizAttempt.mockResolvedValue(started('PRACTICE'))
      api.saveQuizAnswer.mockResolvedValue({ correct: true, correctOptionId: 1000, solution: null })
      renderPage('?mode=practice')
      await begin('Începe exersarea')

      await userEvent.click(screen.getByLabelText('a) Varianta A'))

      expect(await screen.findByTestId('item-feedback')).toHaveTextContent('Corect!')
    })

    it('restores the feedback of earlier answers when a practice is resumed', async () => {
      api.startQuizAttempt.mockResolvedValue(started('PRACTICE', [
        { itemId: 100, selectedOptionId: 1001, photoUploaded: false, photoIds: [],
          feedback: { correct: false, correctOptionId: 1000, solution: null } },
      ]))
      renderPage('?mode=practice')

      await begin('Începe exersarea')

      expect(screen.getByTestId('item-feedback')).toHaveTextContent('Greșit')
    })

    it('ends the session without the "unanswered" warning of a graded test', async () => {
      api.startQuizAttempt.mockResolvedValue(started('PRACTICE'))
      api.submitQuizAttempt.mockResolvedValue({})
      renderPage('?mode=practice')
      await begin('Începe exersarea')

      await userEvent.click(screen.getByRole('button', { name: 'Termină exersarea' }))

      const dialog = await screen.findByRole('dialog', { name: 'Termini exersarea?' })
      expect(dialog).not.toHaveTextContent('fără răspuns')
      await userEvent.click(within(dialog).getByRole('button', { name: 'Termină exersarea' }))
      await waitFor(() => expect(api.submitQuizAttempt).toHaveBeenCalledWith(50))
    })

    it('marks the right option and the wrong pick with a sign, not only a colour', async () => {
      api.startQuizAttempt.mockResolvedValue(started('PRACTICE'))
      api.saveQuizAnswer.mockResolvedValue({ correct: false, correctOptionId: 1000, solution: null })
      renderPage('?mode=practice')
      await begin('Începe exersarea')

      await userEvent.click(screen.getByLabelText('b) Varianta B'))

      await screen.findByTestId('item-feedback')
      const right = screen.getByText('Varianta A').closest('label')
      const wrong = screen.getByText('Varianta B').closest('label')
      expect(right).toHaveTextContent('✓')
      expect(right).toHaveTextContent('răspunsul corect')
      expect(wrong).toHaveTextContent('✗')
      expect(wrong).toHaveTextContent('alegerea ta, greșită')
    })
  })

  describe('graded test (default)', () => {
    it('shows a conflict on an untimed test instead of handing the paper in', async () => {
      api.startQuizAttempt.mockResolvedValue(started('TEST'))
      api.saveQuizAnswer.mockRejectedValue(new ApiError(409, 'Elementul e încă folosit sau intră în conflict cu unul existent.'))
      renderPage('')
      await begin('Începe testul')

      await userEvent.click(screen.getByLabelText('a) Varianta A'))

      expect(await screen.findByText(/încă folosit/)).toBeInTheDocument()
      expect(api.submitQuizAttempt).not.toHaveBeenCalled()
    })

    it('starts in TEST mode and never shows feedback: the server answers 204', async () => {
      api.startQuizAttempt.mockResolvedValue(started('TEST'))
      api.saveQuizAnswer.mockResolvedValue(null)
      renderPage('')
      await begin('Începe testul')

      await userEvent.click(screen.getByLabelText('a) Varianta A'))

      await waitFor(() => expect(api.saveQuizAnswer).toHaveBeenCalled())
      expect(api.startQuizAttempt).toHaveBeenCalledWith(7, 'TEST')
      expect(screen.queryByTestId('item-feedback')).not.toBeInTheDocument()
      expect(screen.queryByTestId('quiz-practice-banner')).not.toBeInTheDocument()
      expect(screen.getByRole('button', { name: 'Trimite lucrarea' })).toBeInTheDocument()
    })
  })

  describe('hints (practice only)', () => {
    const hint = (number: number, text: string) => ({ number, text, total: 2 })

    it('offers a hint button with the count, and reveals the hints one at a time', async () => {
      api.startQuizAttempt.mockResolvedValue(started('PRACTICE', [], { hintCount: 2 }))
      api.revealQuizHint.mockResolvedValueOnce(hint(1, 'Muta termenii')).mockResolvedValueOnce(hint(2, 'Imparte la coeficient'))
      renderPage('?mode=practice')
      await begin('Începe exersarea')
      expect(screen.queryAllByTestId('hint')).toHaveLength(0)
      expect(screen.getByTestId('hint-button')).toHaveTextContent('Vrei un indiciu? (0/2)')

      await userEvent.click(screen.getByTestId('hint-button'))

      expect(await screen.findByText('Muta termenii')).toBeInTheDocument()
      expect(api.revealQuizHint).toHaveBeenLastCalledWith(50, 100, 1)
      expect(screen.getByTestId('hint-button')).toHaveTextContent('Încă un indiciu (1/2)')

      await userEvent.click(screen.getByTestId('hint-button'))

      expect(await screen.findByText('Imparte la coeficient')).toBeInTheDocument()
      expect(api.revealQuizHint).toHaveBeenLastCalledWith(50, 100, 2)
      expect(screen.getAllByTestId('hint')).toHaveLength(2)
      expect(screen.queryByTestId('hint-button')).not.toBeInTheDocument() // all revealed
    })

    it('restores the hints already revealed when a practice is resumed', async () => {
      api.startQuizAttempt.mockResolvedValue(started('PRACTICE', [], { hintCount: 2, revealed: ['Muta termenii'] }))
      renderPage('?mode=practice')

      await begin('Începe exersarea')

      expect(screen.getByText('Muta termenii')).toBeInTheDocument()
      expect(screen.getByTestId('hint-button')).toHaveTextContent('Încă un indiciu (1/2)')
    })

    it('shows nothing for an item that has no hints', async () => {
      api.startQuizAttempt.mockResolvedValue(started('PRACTICE', [], { hintCount: 0 }))
      renderPage('?mode=practice')

      await begin('Începe exersarea')

      expect(screen.queryByTestId('item-hints')).not.toBeInTheDocument()
    })

    it('shows the server error and keeps the button when a hint cannot be revealed', async () => {
      api.startQuizAttempt.mockResolvedValue(started('PRACTICE', [], { hintCount: 2 }))
      api.revealQuizHint.mockRejectedValue(new ApiError(400, 'Indiciile se dezvăluie pe rând'))
      renderPage('?mode=practice')
      await begin('Începe exersarea')

      await userEvent.click(screen.getByTestId('hint-button'))

      expect(await screen.findByText(/Indiciile se dezvăluie pe rând/)).toBeInTheDocument()
      expect(screen.getByTestId('hint-button')).toBeEnabled()
    })

    it('never shows a hint button in a graded test: its items report no hints', async () => {
      api.startQuizAttempt.mockResolvedValue(started('TEST')) // hintCount 0 - the server hides hints in a test
      api.getStudentQuizzes.mockResolvedValue([])
      renderPage('')

      await begin('Începe testul')

      expect(screen.queryByTestId('item-hints')).not.toBeInTheDocument()
      expect(screen.queryByTestId('hint-button')).not.toBeInTheDocument()
    })

    it('does not show hints in a test even if a (buggy) response carried a hint count', async () => {
      api.startQuizAttempt.mockResolvedValue(started('TEST', [], { hintCount: 3 }))
      renderPage('')

      await begin('Începe testul')

      expect(screen.queryByTestId('hint-button')).not.toBeInTheDocument() // the page itself also gates on practice mode
    })
  })

  describe('before starting', () => {
    it('names the quiz and says how many items and points it has, before anything is created', async () => {
      renderPage('')

      expect(await screen.findByRole('heading', { name: 'Simulare EN' })).toBeInTheDocument()
      expect(screen.getByText('Recapitulare pentru examen')).toBeInTheDocument()
      expect(screen.getByTestId('quiz-facts')).toHaveTextContent('3 întrebări · 11 puncte')
      expect(screen.getByTestId('quiz-facts')).toHaveTextContent('Fără limită de timp')
      expect(api.getQuizPreview).toHaveBeenCalledWith(7)
      expect(api.startQuizAttempt).not.toHaveBeenCalled()
    })

    it('keeps the bottom bar clear of the iPhone home bar, and short on a phone', async () => {
      api.startQuizAttempt.mockResolvedValue(startedThree())
      renderPage('')
      await userEvent.click(await screen.findByTestId('quiz-start'))

      expect(await screen.findByTestId('quiz-bar')).toHaveClass('pb-[max(0.5rem,env(safe-area-inset-bottom))]')
      expect(screen.getByTestId('quiz-answered')).toHaveTextContent('0 / 3 răspunsuri')
      expect(within(screen.getByTestId('quiz-answered')).getByText('răspunsuri')).toHaveClass('max-sm:sr-only')
    })

    it('warns about the clock of a timed quiz', async () => {
      api.getQuizPreview.mockResolvedValue(preview({ timeLimitMinutes: 30 }))
      renderPage('')
      expect(await screen.findByTestId('quiz-limit-note')).toHaveTextContent('30 de minute')
    })

    it('puts the clock before everything else it explains, in left-aligned text', async () => {
      api.getQuizPreview.mockResolvedValue(preview({ timeLimitMinutes: 120, maxAttempts: 1, photoItemCount: 3 }))
      renderPage('')

      const limit = await screen.findByTestId('quiz-limit-note')
      for (const later of ['quiz-photo-note', 'quiz-attempt-rule', 'quiz-autosave-note']) {
        expect(limit.compareDocumentPosition(screen.getByTestId(later)) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy()
      }
      expect(screen.getByTestId('quiz-notes')).toHaveClass('text-left')
    })

    it('tells the student to have paper and a phone ready when questions are solved on paper', async () => {
      api.getQuizPreview.mockResolvedValue(preview({ photoItemCount: 3 }))
      renderPage('')
      expect(await screen.findByTestId('quiz-photo-note')).toHaveTextContent(
        'Pregătește foi și telefonul: la 3 probleme scrii rezolvarea pe hârtie și o fotografiezi.',
      )
    })

    it('says nothing about paper when every question is answered on the screen', async () => {
      api.getQuizPreview.mockResolvedValue(preview({ photoItemCount: 0 }))
      renderPage('')
      await screen.findByTestId('quiz-facts')
      expect(screen.queryByTestId('quiz-photo-note')).not.toBeInTheDocument()
    })

    it('says the student is coming back to a test they started, and how far they got', async () => {
      api.getQuizPreview.mockResolvedValue(preview({ openTest: { answered: 2, deadlineAt: null, serverNow: '2026-10-04T12:00:00Z' } }))
      renderPage('')

      expect(await screen.findByTestId('quiz-resume')).toHaveTextContent('Ai început deja testul: ai răspuns la 2 din 3 întrebări.')
      expect(screen.getByTestId('quiz-start')).toHaveTextContent('Continuă testul')
    })

    it('tells a timed test taken up again that its clock already runs, and how long is left', async () => {
      api.getQuizPreview.mockResolvedValue(preview({
        timeLimitMinutes: 30,
        openTest: { answered: 0, deadlineAt: '2026-10-04T12:12:00Z', serverNow: '2026-10-04T12:00:00Z' },
      }))
      renderPage('')

      const note = await screen.findByTestId('quiz-limit-note')
      expect(note).toHaveTextContent('Cronometrul a pornit când ai început testul')
      expect(note).toHaveTextContent('mai ai 12 minute')
      expect(note).not.toHaveTextContent('pornește când apeși')
      expect(screen.getByTestId('quiz-resume')).toHaveTextContent('Ai început deja testul, încă fără răspunsuri.')
    })

    it('resumes the practice or the test the page is for, never the other one', async () => {
      api.getQuizPreview.mockResolvedValue(preview({ openTest: { answered: 2, deadlineAt: null, serverNow: '2026-10-04T12:00:00Z' } }))
      renderPage('?mode=practice')

      expect(await screen.findByTestId('quiz-start')).toHaveTextContent('Începe exersarea')
      expect(screen.queryByTestId('quiz-resume')).not.toBeInTheDocument()
    })

    it('offers to go on with a practice already started', async () => {
      api.getQuizPreview.mockResolvedValue(preview({ openPractice: { answered: 1, deadlineAt: null, serverNow: '2026-10-04T12:00:00Z' } }))
      renderPage('?mode=practice')

      expect(await screen.findByTestId('quiz-resume')).toHaveTextContent('Ai început deja exersarea: ai răspuns la 1 din 3 întrebări.')
      expect(screen.getByTestId('quiz-start')).toHaveTextContent('Continuă exersarea')
    })

    it('says how many tries the student has and which grade counts', async () => {
      api.getQuizPreview.mockResolvedValue(preview({ maxAttempts: 3, attemptsUsed: 1 }))
      renderPage('')
      expect(await screen.findByTestId('quiz-attempt-rule')).toHaveTextContent('Încercarea 2 din 3. Contează cea mai mare notă.')
    })

    it('says plainly when there is one try', async () => {
      api.getQuizPreview.mockResolvedValue(preview({ maxAttempts: 1 }))
      renderPage('')
      expect(await screen.findByTestId('quiz-attempt-rule')).toHaveTextContent('Ai o singură încercare.')
    })

    it('offers no start on a test with no attempt left, and says why', async () => {
      api.getQuizPreview.mockResolvedValue(preview({ maxAttempts: 1, attemptsUsed: 1, blockedBy: 'NO_ATTEMPTS_LEFT' }))
      renderPage('')

      expect(await screen.findByTestId('quiz-blocked')).toHaveTextContent('Ai folosit toate încercările la acest test.')
      expect(screen.queryByTestId('quiz-start')).not.toBeInTheDocument()
      expect(screen.queryByTestId('quiz-attempt-rule')).not.toBeInTheDocument()
    })

    it('says a retake waits for the grade', async () => {
      api.getQuizPreview.mockResolvedValue(preview({ blockedBy: 'WAITING_FOR_GRADE' }))
      renderPage('')

      expect(await screen.findByTestId('quiz-blocked')).toHaveTextContent('Lucrarea ta se corectează.')
      expect(screen.queryByTestId('quiz-start')).not.toBeInTheDocument()
    })

    it('shows the refusal when the limit is reached between opening the page and pressing "Începe"', async () => {
      api.startQuizAttempt.mockRejectedValue(new ApiError(400, 'Ai folosit toate încercările la acest test.'))
      renderPage('')

      await userEvent.click(await screen.findByTestId('quiz-start'))

      expect(await screen.findByText('Ai folosit toate încercările la acest test.')).toBeInTheDocument()
    })

    it('leaves a practice open whatever happened to the test', async () => {
      api.getQuizPreview.mockResolvedValue(preview({ maxAttempts: 1, attemptsUsed: 1, blockedBy: 'NO_ATTEMPTS_LEFT' }))
      renderPage('?mode=practice')

      expect(await screen.findByTestId('quiz-start')).toBeEnabled()
      expect(screen.queryByTestId('quiz-blocked')).not.toBeInTheDocument()
    })

    it('shows the homework deadline when the quiz is homework still to hand in', async () => {
      const homework: StudentAssignment = {
        assignmentId: 1, quizId: 7, quizTitle: 'Simulare EN', schoolClassName: 'Clasa a 8-a',
        dueAt: new Date(Date.now() + 3 * 3_600_000).toISOString(), state: 'NOT_STARTED', late: false, overdue: false,
        attemptId: null, timeLimitMinutes: null,
      }
      api.getMyAssignments.mockResolvedValue([homework, { ...homework, assignmentId: 2, quizId: 8 }])
      renderPage('')
      expect(await screen.findByTestId('quiz-homework-due')).toHaveTextContent('peste 3 ore')
    })

    it('still lets the student start when the summary cannot be loaded', async () => {
      api.getQuizPreview.mockRejectedValue(new ApiError(500, 'boom'))
      api.startQuizAttempt.mockResolvedValue(started('TEST'))
      renderPage('')
      await begin('Începe testul')
    })
  })

  describe('finding your way around a test', () => {
    it("shows a question's picture under its statement", async () => {
      const attempt = startedThree()
      attempt.quiz.items[1].image = 'fig-v1.png'
      api.startQuizAttempt.mockResolvedValue(attempt)
      renderPage('')
      await userEvent.click(await screen.findByRole('button', { name: 'Începe testul' }))
      await screen.findByText('Primul subiect')

      const pictures = screen.getAllByTestId('question-image')
      expect(pictures).toHaveLength(1)
      expect(pictures[0]).toHaveAttribute('src', '/api/quiz/items/101/image?v=fig-v1.png')
    })

    it('groups an EN test under its headings and numbers again from 1 in each', async () => {
      const attempt = startedThree()
      attempt.quiz.items[0].section = 'SUBIECTUL I\nÎncercuiește litera corespunzătoare răspunsului corect.'
      attempt.quiz.items[1].section = 'SUBIECTUL I\nÎncercuiește litera corespunzătoare răspunsului corect.'
      attempt.quiz.items[2].section = 'SUBIECTUL al II-lea'
      api.startQuizAttempt.mockResolvedValue(attempt)
      renderPage('')
      await userEvent.click(await screen.findByRole('button', { name: 'Începe testul' }))
      await screen.findByText('Primul subiect')

      expect(screen.getAllByTestId('section-heading').map((h) => h.textContent)).toEqual([
        'SUBIECTUL I(7 puncte)Încercuiește litera corespunzătoare răspunsului corect.', // the section's points, as on the paper
        'SUBIECTUL al II-lea(4 puncte)',
      ])
      expect(screen.getByRole('heading', { name: '1. (2 p)' })).toBeInTheDocument()
      expect(screen.getByRole('heading', { name: '1. (4 p)' })).toBeInTheDocument() // first under SUBIECTUL al II-lea
      const nav = screen.getByRole('navigation', { name: 'Întrebări' })
      expect(within(nav).getAllByRole('button').map((b) => b.textContent)).toEqual(['I.1', 'I.2', 'II.1'])
    })

    it('numbers every item in the bottom bar and marks the answered ones', async () => {
      api.startQuizAttempt.mockResolvedValue(startedThree())
      api.saveQuizAnswer.mockResolvedValue(null)
      renderPage('')
      await userEvent.click(await screen.findByRole('button', { name: 'Începe testul' }))
      await screen.findByText('Primul subiect')

      const nav = screen.getByRole('navigation', { name: 'Întrebări' })
      expect(within(nav).getByRole('button', { name: 'Întrebarea 1, fără răspuns' })).toBeInTheDocument()

      await userEvent.click(screen.getByLabelText('a) A1'))

      expect(await within(nav).findByRole('button', { name: 'Întrebarea 1, cu răspuns' })).toBeInTheDocument()
      expect(within(nav).getByRole('button', { name: 'Întrebarea 2, fără răspuns' })).toBeInTheDocument()
    })

    it('jumps to an item from the bottom bar', async () => {
      api.startQuizAttempt.mockResolvedValue(startedThree())
      const scroll = vi.fn()
      Element.prototype.scrollIntoView = scroll
      renderPage('')
      await userEvent.click(await screen.findByRole('button', { name: 'Începe testul' }))
      await screen.findByText('Primul subiect')

      await userEvent.click(screen.getByRole('button', { name: 'Întrebarea 3, fără răspuns' }))

      expect(scroll).toHaveBeenCalled()
      expect(document.activeElement).toHaveTextContent('Întrebarea 3')
    })

    describe('the question on screen', () => {
      /** Stands in for the browser's IntersectionObserver: the test says which card scrolled into the reading band. */
      class FakeObserver {
        static all: FakeObserver[] = []
        observed: Element[] = []
        readonly callback: IntersectionObserverCallback
        constructor(callback: IntersectionObserverCallback) {
          this.callback = callback
          FakeObserver.all.push(this)
        }
        observe(el: Element) { this.observed.push(el) }
        unobserve() {}
        disconnect() { this.observed = [] }
        takeRecords() { return [] }
      }
      function scrollTo(statement: string) {
        const card = screen.getByText(statement).closest('[data-item-id]')!
        const observer = FakeObserver.all.find((o) => o.observed.includes(card))!
        act(() => observer.callback([{ target: card, isIntersecting: true } as unknown as IntersectionObserverEntry], observer as unknown as IntersectionObserver))
      }
      const current = (nav: HTMLElement) => within(nav).getAllByRole('button').filter((b) => b.getAttribute('aria-current') === 'step')

      beforeEach(() => {
        FakeObserver.all = []
        vi.stubGlobal('IntersectionObserver', FakeObserver)
        Element.prototype.scrollIntoView = vi.fn()
      })
      afterEach(() => vi.unstubAllGlobals())

      it('marks the first question as the current one when the test starts, then follows the scroll', async () => {
        api.startQuizAttempt.mockResolvedValue(startedThree())
        renderPage('')
        await userEvent.click(await screen.findByRole('button', { name: 'Începe testul' }))
        await screen.findByText('Primul subiect')
        const nav = screen.getByRole('navigation', { name: 'Întrebări' })

        expect(current(nav).map((b) => b.textContent)).toEqual(['1'])

        scrollTo('Al doilea subiect')

        expect(current(nav).map((b) => b.textContent)).toEqual(['2'])
        expect(current(nav)[0].className).toMatch(/ring-2/) // seen, not only announced
      })

      it('keeps "current" apart from "answered": an answered question that is not on screen is not current', async () => {
        api.startQuizAttempt.mockResolvedValue(startedThree())
        api.saveQuizAnswer.mockResolvedValue(null)
        renderPage('')
        await userEvent.click(await screen.findByRole('button', { name: 'Începe testul' }))
        await screen.findByText('Primul subiect')
        const nav = screen.getByRole('navigation', { name: 'Întrebări' })

        await userEvent.click(screen.getByLabelText('a) A1'))
        scrollTo('Al treilea subiect')

        const answered = await within(nav).findByRole('button', { name: 'Întrebarea 1, cu răspuns' })
        expect(answered).not.toHaveAttribute('aria-current')
        expect(within(nav).getByRole('button', { name: 'Întrebarea 3, fără răspuns' })).toHaveAttribute('aria-current', 'step')
      })

      it('makes the last question current at the very bottom of the page, where it can never reach the reading band', async () => {
        api.startQuizAttempt.mockResolvedValue(startedThree())
        renderPage('')
        await userEvent.click(await screen.findByRole('button', { name: 'Începe testul' }))
        await screen.findByText('Primul subiect')
        const nav = screen.getByRole('navigation', { name: 'Întrebări' })
        Object.defineProperty(document.documentElement, 'scrollHeight', { configurable: true, value: 2000 })
        Object.defineProperty(window, 'scrollY', { configurable: true, value: 2000 - window.innerHeight })

        try {
          act(() => {
            window.dispatchEvent(new Event('scroll'))
          })
          expect(current(nav).map((b) => b.textContent)).toEqual(['3'])

          scrollTo('Al doilea subiect') // the band still sees 2 down there: the end of the page wins
          expect(current(nav).map((b) => b.textContent)).toEqual(['3'])
        } finally {
          Object.defineProperty(window, 'scrollY', { configurable: true, value: 0 })
          Reflect.deleteProperty(document.documentElement, 'scrollHeight')
        }
      })

      it('makes a question current as soon as it is picked in the bar', async () => {
        api.startQuizAttempt.mockResolvedValue(startedThree())
        renderPage('')
        await userEvent.click(await screen.findByRole('button', { name: 'Începe testul' }))
        await screen.findByText('Primul subiect')

        await userEvent.click(screen.getByRole('button', { name: 'Întrebarea 3, fără răspuns' }))

        expect(screen.getByRole('button', { name: 'Întrebarea 3, fără răspuns' })).toHaveAttribute('aria-current', 'step')
      })

      it('has 44 px buttons in the bar on a phone', async () => {
        api.startQuizAttempt.mockResolvedValue(startedThree())
        renderPage('')
        await userEvent.click(await screen.findByRole('button', { name: 'Începe testul' }))
        await screen.findByText('Primul subiect')

        const nav = screen.getByRole('navigation', { name: 'Întrebări' })
        for (const button of within(nav).getAllByRole('button')) expect(button.className).toMatch(/\bh-11\b.*\bsm:h-9\b/)
        expect(screen.getByTestId('quiz-submit').className).toMatch(/\bh-11\b/)
      })
    })

    it('lists the unanswered items before handing in, and can take the student to the first one', async () => {
      api.startQuizAttempt.mockResolvedValue(startedThree())
      api.saveQuizAnswer.mockResolvedValue(null)
      Element.prototype.scrollIntoView = vi.fn()
      renderPage('')
      await userEvent.click(await screen.findByRole('button', { name: 'Începe testul' }))
      await userEvent.click(await screen.findByLabelText('a) A1'))
      await waitFor(() => expect(api.saveQuizAnswer).toHaveBeenCalled())

      await userEvent.click(screen.getByRole('button', { name: 'Trimite lucrarea' }))

      const dialog = await screen.findByRole('dialog', { name: 'Ai 2 întrebări fără răspuns' })
      expect(dialog).toHaveTextContent('Întrebările 2, 3')
      await userEvent.click(within(dialog).getByRole('button', { name: 'Mergi la întrebarea 2' }))

      await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())
      expect(api.submitQuizAttempt).not.toHaveBeenCalled()
      await waitFor(() => expect(document.activeElement).toHaveTextContent('Întrebarea 2'))
    })

    it('makes going back to the unanswered questions the main button, and handing in anyway the quiet one', async () => {
      api.startQuizAttempt.mockResolvedValue(startedThree())
      renderPage('')
      await userEvent.click(await screen.findByRole('button', { name: 'Începe testul' }))
      await screen.findByText('Primul subiect')

      await userEvent.click(screen.getByRole('button', { name: 'Trimite lucrarea' }))

      const dialog = await screen.findByRole('dialog', { name: 'Ai 3 întrebări fără răspuns' })
      expect(within(dialog).getByRole('button', { name: 'Mergi la întrebarea 1' })).toHaveClass('bg-primary')
      expect(within(dialog).getByRole('button', { name: 'Trimite oricum' })).not.toHaveClass('bg-primary')
    })

    it('says before an incomplete hand-in that it is the last try', async () => {
      api.getQuizPreview.mockResolvedValue(preview({ maxAttempts: 1 }))
      api.startQuizAttempt.mockResolvedValue(startedThree())
      renderPage('')
      await userEvent.click(await screen.findByRole('button', { name: 'Începe testul' }))
      await screen.findByText('Primul subiect')

      await userEvent.click(screen.getByRole('button', { name: 'Trimite lucrarea' }))

      expect(await screen.findByRole('dialog')).toHaveTextContent('Ai o singură încercare: după ce trimiți, nu mai poți da testul din nou.')
    })

    it('keeps "Trimite" the main button once every question has an answer', async () => {
      api.startQuizAttempt.mockResolvedValue(started('TEST', [
        { itemId: 100, selectedOptionId: 1000, photoUploaded: false, feedback: null, photoIds: [] },
      ]))
      renderPage('')
      await begin('Începe testul')
      await userEvent.click(screen.getByRole('button', { name: 'Trimite lucrarea' }))

      const dialog = await screen.findByRole('dialog', { name: 'Trimiți lucrarea?' })
      expect(within(dialog).getByRole('button', { name: 'Trimite' })).toHaveClass('bg-primary')
    })

    it('hands in only after the student confirms in the dialog', async () => {
      api.startQuizAttempt.mockResolvedValue(started('TEST', [
        { itemId: 100, selectedOptionId: 1000, photoUploaded: false, feedback: null, photoIds: [] },
      ]))
      api.submitQuizAttempt.mockResolvedValue({})
      renderPage('')
      await begin('Începe testul')

      await userEvent.click(screen.getByRole('button', { name: 'Trimite lucrarea' }))
      const dialog = await screen.findByRole('dialog', { name: 'Trimiți lucrarea?' })
      await userEvent.click(within(dialog).getByRole('button', { name: 'Anulează' }))
      expect(api.submitQuizAttempt).not.toHaveBeenCalled()

      await userEvent.click(screen.getByRole('button', { name: 'Trimite lucrarea' }))
      await userEvent.click(within(await screen.findByRole('dialog')).getByRole('button', { name: 'Trimite' }))
      await waitFor(() => expect(api.submitQuizAttempt).toHaveBeenCalledWith(50))
      expect(await screen.findByText('rezultatul')).toBeInTheDocument()
    })
  })

  describe('a bad connection (Feature 2)', () => {
    const offline = () => new TypeError('Failed to fetch')

    it('keeps the choice when the network fails, says it is waiting, and sends it when the connection is back', async () => {
      api.startQuizAttempt.mockResolvedValue(started('TEST'))
      api.saveQuizAnswer.mockRejectedValueOnce(offline()).mockResolvedValue(null)
      renderPage('')
      await begin('Începe testul')

      await userEvent.click(screen.getByLabelText('b) Varianta B'))

      expect(await screen.findByTestId('item-queued')).toHaveTextContent('Încă netrimis')
      expect(screen.getByLabelText('b) Varianta B')).toBeChecked() // not undone
      expect(screen.getByTestId('connection-banner')).toHaveTextContent('Se reîncearcă trimiterea: 1 răspuns netrimis.')
      expect(screen.getByRole('button', { name: 'Întrebarea 1, cu răspuns, netrimis încă' })).toBeInTheDocument()

      window.dispatchEvent(new Event('online'))

      await waitFor(() => expect(screen.queryByTestId('item-queued')).not.toBeInTheDocument())
      expect(api.saveQuizAnswer).toHaveBeenCalledTimes(2)
      expect(screen.queryByTestId('connection-banner')).not.toBeInTheDocument()
      expect(screen.getByRole('button', { name: 'Întrebarea 1, cu răspuns' })).toBeInTheDocument()
    })

    it('undoes the choice only when the server really refuses it', async () => {
      api.startQuizAttempt.mockResolvedValue(started('TEST'))
      api.saveQuizAnswer.mockRejectedValue(new ApiError(400, 'Varianta nu ține de acest subiect.'))
      renderPage('')
      await begin('Începe testul')

      await userEvent.click(screen.getByLabelText('b) Varianta B'))

      expect(await screen.findByText('Varianta nu ține de acest subiect.')).toBeInTheDocument()
      expect(screen.getByLabelText('b) Varianta B')).not.toBeChecked()
      expect(screen.queryByTestId('item-queued')).not.toBeInTheDocument()
    })

    it('a newer choice wins over one still waiting', async () => {
      api.startQuizAttempt.mockResolvedValue(started('TEST'))
      api.saveQuizAnswer.mockRejectedValueOnce(offline()).mockResolvedValue(null)
      renderPage('')
      await begin('Începe testul')

      await userEvent.click(screen.getByLabelText('b) Varianta B'))
      await screen.findByTestId('item-queued')
      await userEvent.click(screen.getByLabelText('a) Varianta A'))

      await waitFor(() => expect(screen.queryByTestId('item-queued')).not.toBeInTheDocument())
      expect(api.saveQuizAnswer).toHaveBeenLastCalledWith(50, 100, 1000, { clientId: expect.any(String), clientSeq: 2 })
    })

    it('before handing in, says which answers never reached the server and lets the student wait', async () => {
      api.startQuizAttempt.mockResolvedValue(started('TEST'))
      api.saveQuizAnswer.mockRejectedValue(offline())
      api.submitQuizAttempt.mockResolvedValue({})
      renderPage('')
      await begin('Începe testul')
      await userEvent.click(screen.getByLabelText('b) Varianta B'))
      await screen.findByTestId('item-queued')

      await userEvent.click(screen.getByRole('button', { name: 'Trimite lucrarea' }))
      await userEvent.click(within(await screen.findByRole('dialog')).getByRole('button', { name: 'Trimite' }))

      const warning = await screen.findByRole('dialog', { name: '1 răspuns n-a ajuns la server' })
      expect(api.submitQuizAttempt).not.toHaveBeenCalled()

      api.saveQuizAnswer.mockResolvedValue(null) // the connection is back
      await userEvent.click(within(warning).getByRole('button', { name: 'Mai așteaptă' }))

      await waitFor(() => expect(api.submitQuizAttempt).toHaveBeenCalledWith(50))
      expect(localStorage.getItem('mathlms:pending:50')).toBeNull() // nothing left after hand-in
    })

    it('a reload while offline shows the waiting answer and sends it again', async () => {
      localStorage.setItem('mathlms:pending:50', JSON.stringify({
        clientId: '7f6a0f3e-1111-4b2c-9d0e-123456789abc', nextSeq: 4, pending: [{ itemId: 100, optionId: 1001, seq: 3 }],
      }))
      api.startQuizAttempt.mockResolvedValue(started('TEST'))
      api.saveQuizAnswer.mockResolvedValue(null)
      renderPage('')
      await begin('Începe testul')

      expect(screen.getByLabelText('b) Varianta B')).toBeChecked()
      await waitFor(() => expect(api.saveQuizAnswer).toHaveBeenCalledWith(50, 100, 1001, {
        clientId: '7f6a0f3e-1111-4b2c-9d0e-123456789abc', clientSeq: 3,
      }))
    })
  })

  describe('"Salvează și ieși" - leaving to come back later', () => {
    it('goes back to "Testele mele" without handing anything in, once every answer is on the server', async () => {
      api.startQuizAttempt.mockResolvedValue(started('TEST'))
      api.saveQuizAnswer.mockResolvedValue(null)
      renderPage('')
      await begin('Începe testul')
      await userEvent.click(screen.getByLabelText('b) Varianta B'))
      await waitFor(() => expect(api.saveQuizAnswer).toHaveBeenCalled())

      await userEvent.click(screen.getByRole('button', { name: 'Salvează și ieși' }))

      expect(await screen.findByText('lista de teste')).toBeInTheDocument()
      expect(api.submitQuizAttempt).not.toHaveBeenCalled()
    })

    it('is there in a practice too', async () => {
      api.startQuizAttempt.mockResolvedValue(started('PRACTICE'))
      renderPage('?mode=practice')
      await begin('Începe exersarea')

      await userEvent.click(screen.getByRole('button', { name: 'Salvează și ieși' }))

      expect(await screen.findByText('lista de teste')).toBeInTheDocument()
    })

    it('on a clock, first says the time keeps running - "Rămân" stays, "Ieși" leaves', async () => {
      api.startQuizAttempt.mockResolvedValue(startedThree(20 * 60))
      renderPage('')
      await userEvent.click(await screen.findByRole('button', { name: 'Începe testul' }))
      await screen.findByTestId('quiz-timer')

      await userEvent.click(screen.getByRole('button', { name: 'Salvează și ieși' }))
      const dialog = await screen.findByRole('dialog', { name: 'Ieși din test?' })
      expect(dialog).toHaveTextContent('Cronometrul nu se oprește')
      expect(dialog).toHaveTextContent('se trimite singură')

      await userEvent.click(within(dialog).getByRole('button', { name: 'Rămân' }))
      expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
      expect(screen.getByText('Primul subiect')).toBeInTheDocument()

      await userEvent.click(screen.getByRole('button', { name: 'Salvează și ieși' }))
      await userEvent.click(within(await screen.findByRole('dialog')).getByRole('button', { name: 'Ieși' }))

      expect(await screen.findByText('lista de teste')).toBeInTheDocument()
      expect(api.submitQuizAttempt).not.toHaveBeenCalled()
      // "Testele mele" must not tell the student they can continue "oricând": it shows the clock instead.
      expect(JSON.parse(screen.getByTestId('list-state').textContent!)).toEqual({
        savedQuizTitle: 'Simulare EN', savedAttemptId: 50, savedTimed: true,
      })
    })

    it('without internet, says what is still on this device and keeps it there when the student leaves anyway', async () => {
      api.startQuizAttempt.mockResolvedValue(started('TEST'))
      api.saveQuizAnswer.mockRejectedValue(new TypeError('Failed to fetch'))
      renderPage('')
      await begin('Începe testul')
      await userEvent.click(screen.getByLabelText('b) Varianta B'))
      await screen.findByTestId('item-queued')

      await userEvent.click(screen.getByRole('button', { name: 'Salvează și ieși' }))
      const warning = await screen.findByRole('dialog', { name: '1 răspuns n-a ajuns încă la server' })
      expect(warning).toHaveTextContent('rămâne pe acest dispozitiv')

      await userEvent.click(within(warning).getByRole('button', { name: 'Ieși oricum' }))

      expect(await screen.findByText('lista de teste')).toBeInTheDocument()
      // still waiting in this browser: reopening the test sends it
      expect(localStorage.getItem('mathlms:pending:50')).toContain('"optionId":1001')
    })

    it('waits for a photo still uploading', async () => {
      api.startQuizAttempt.mockResolvedValue(startedThree())
      api.uploadQuizPhoto.mockReturnValue(new Promise(() => undefined)) // never finishes
      renderPage('')
      await userEvent.click(await screen.findByRole('button', { name: 'Începe testul' }))

      await userEvent.upload(await screen.findByTestId('item-photo'), new File(['x'], 'p1.png', { type: 'image/png' }))

      await waitFor(() => expect(screen.getByRole('button', { name: 'Salvează și ieși' })).toBeDisabled())
    })
  })

  describe('the clock', () => {
    async function startTimed(secondsLeft: number) {
      api.startQuizAttempt.mockResolvedValue(startedThree(secondsLeft))
      renderPage('')
      await userEvent.click(await screen.findByRole('button', { name: 'Începe testul' }))
      return screen.findByTestId('quiz-timer')
    }

    it('stays calm with plenty of time left', async () => {
      const timer = await startTimed(20 * 60)
      expect(timer).toHaveAttribute('data-tone', 'normal')
      expect(screen.getByTestId('quiz-timer-announcement')).toBeEmptyDOMElement()
    })

    it('turns amber and tells screen readers in the last five minutes', async () => {
      const timer = await startTimed(4 * 60)
      expect(timer).toHaveAttribute('data-tone', 'warning')
      expect(screen.getByTestId('quiz-timer-announcement')).toHaveTextContent('mai puțin de 5 minute')
    })

    it('turns red in the last minute', async () => {
      const timer = await startTimed(45)
      expect(timer).toHaveAttribute('data-tone', 'danger')
      expect(screen.getByTestId('quiz-timer-announcement')).toHaveTextContent('mai puțin de un minut')
    })
  })

  describe('photographed solutions', () => {
    const begin = async (resumed = startedThree()) => {
      api.startQuizAttempt.mockResolvedValue(resumed)
      renderPage('')
      await userEvent.click(await screen.findByRole('button', { name: 'Începe testul' }))
    }

    it('shows every page the server has, also when the attempt is resumed (S2)', async () => {
      const resumed = startedThree()
      resumed.answers = [{ itemId: 102, selectedOptionId: null, photoUploaded: true, feedback: null, photoIds: [41, 42] }]
      await begin(resumed)

      expect(await screen.findByRole('img', { name: 'Pagina 1 a rezolvării, întrebarea 3' }))
        .toHaveAttribute('src', '/api/quiz/attempts/50/responses/102/photos/41')
      expect(screen.getByRole('img', { name: 'Pagina 2 a rezolvării, întrebarea 3' }))
        .toHaveAttribute('src', '/api/quiz/attempts/50/responses/102/photos/42')
      expect(screen.getByText('Adaugă încă o pagină')).toBeInTheDocument()
      expect(screen.getByRole('status')).toHaveTextContent('Poză încărcată · 2 pagini trimise')
    })

    it('in a practice puts the lesson of each requirement under the barem, to open in another tab', async () => {
      const lesson = (id: number, code: string, title: string, revisionTopic: string) =>
        ({ id, code, title, ready: true, revisionTopic })
      api.uploadQuizPhoto.mockResolvedValue({
        photoIds: [41],
        feedback: {
          correct: null, correctOptionId: null, solution: 'a) 2p, b) 3p',
          partLessons: [
            { label: 'a', enChapter: lesson(19, 'G8', 'Corpuri geometrice', 'corpuri geometrice, arii și volume') },
            { label: 'b', enChapter: lesson(20, 'G9', 'Drepte și plane', 'drepte și plane, unghiuri și distanțe în spațiu') },
          ],
        },
      })
      api.startQuizAttempt.mockResolvedValue({ ...startedThree(), mode: 'PRACTICE' })
      renderPage('?mode=practice')
      await userEvent.click(await screen.findByRole('button', { name: 'Începe exersarea' }))

      await userEvent.upload(await screen.findByTestId('item-photo'), new File(['x'], 'p1.png', { type: 'image/png' }))

      const notes = await screen.findAllByTestId('revision-note')
      expect(notes).toHaveLength(2)
      expect(notes[0]).toHaveTextContent('La a), dacă vrei să recapitulezi noțiunile despre corpuri geometrice, arii și volume:')
      expect(notes[1]).toHaveTextContent('La b), dacă vrei să recapitulezi noțiunile despre drepte și plane')
      expect(screen.getByRole('link', { name: /Vezi lecția: G9 Drepte și plane/ })).toHaveAttribute('target', '_blank')
    })

    it('adds a page with each upload and says so (the E2E suite waits for "Poză încărcată")', async () => {
      api.uploadQuizPhoto.mockResolvedValueOnce({ photoIds: [41], feedback: null })
      api.uploadQuizPhoto.mockResolvedValueOnce({ photoIds: [41, 42], feedback: null })
      await begin()
      expect(await screen.findByText('Alege o poză din galerie')).toBeInTheDocument()

      await userEvent.upload(screen.getByTestId('item-photo'), new File(['x'], 'p1.png', { type: 'image/png' }))
      expect(await screen.findByRole('img', { name: 'Pagina 1 a rezolvării, întrebarea 3' })).toBeInTheDocument()
      await userEvent.upload(screen.getByTestId('item-photo'), new File(['y'], 'p2.png', { type: 'image/png' }))

      expect(await screen.findByRole('img', { name: 'Pagina 2 a rezolvării, întrebarea 3' })).toBeInTheDocument()
      expect(screen.getByRole('status')).toHaveTextContent('Poză încărcată · 2 pagini trimise')
      expect(api.uploadQuizPhoto).toHaveBeenCalledTimes(2)
    })

    it('"Fă o poză" asks a phone for its back camera', async () => {
      await begin()

      expect(await screen.findByTestId('item-camera')).toHaveAttribute('capture', 'environment')
      expect(screen.getByTestId('item-photo')).not.toHaveAttribute('capture')
    })

    it('a page can be taken off, and the others stay', async () => {
      const resumed = startedThree()
      resumed.answers = [{ itemId: 102, selectedOptionId: null, photoUploaded: true, feedback: null, photoIds: [41, 42] }]
      api.deleteQuizPhoto.mockResolvedValue({ photoIds: [42], feedback: null })
      await begin(resumed)

      await userEvent.click(await screen.findByRole('button', { name: 'Șterge pagina 1' }))

      expect(api.deleteQuizPhoto).toHaveBeenCalledWith(50, 102, 41)
      await waitFor(() => expect(screen.queryByRole('img', { name: /Pagina 2/ })).not.toBeInTheDocument())
      expect(screen.getByRole('img', { name: 'Pagina 1 a rezolvării, întrebarea 3' }))
        .toHaveAttribute('src', '/api/quiz/attempts/50/responses/102/photos/42')
    })

    it('at five pages there is nothing more to pick, only to take off', async () => {
      const resumed = startedThree()
      resumed.answers = [{ itemId: 102, selectedOptionId: null, photoUploaded: true, feedback: null, photoIds: [1, 2, 3, 4, 5] }]
      await begin(resumed)

      expect(await screen.findByText(/Ai 5 pagini, cât se poate trimite/)).toBeInTheDocument()
      expect(screen.queryByTestId('item-photo')).not.toBeInTheDocument()
      expect(screen.queryByTestId('item-camera')).not.toBeInTheDocument()
    })
  })
})
