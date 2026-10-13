import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
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

function renderPage(query: string) {
  return render(
    <MemoryRouter initialEntries={[`/quizzes/7/take${query}`]}>
      <Routes>
        <Route path="/quizzes/:id/take" element={<TakeQuizPage />} />
        <Route path="/quizzes" element={<p>lista de teste</p>} />
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

  describe('practice mode (?mode=practice)', () => {
    it('S4: with ?only shows just the questions got wrong, keeping the numbers of the test', async () => {
      api.startQuizAttempt.mockResolvedValue({ ...startedThree(), mode: 'PRACTICE' })
      renderPage('?mode=practice&only=101,102')
      await userEvent.click(await screen.findByRole('button', { name: 'Începe practica' }))

      expect(await screen.findByText('Al doilea subiect')).toBeInTheDocument()
      expect(screen.getByText('Al treilea subiect')).toBeInTheDocument()
      expect(screen.queryByText('Primul subiect')).not.toBeInTheDocument()
      expect(screen.getByTestId('practice-only')).toHaveTextContent('Exersezi doar ce ai greșit: 2 subiecte')
      expect(screen.getByRole('link', { name: 'Exersează tot testul' })).toHaveAttribute('href', '/quizzes/7/take?mode=practice')
      expect(screen.getByRole('button', { name: 'Subiectul 2, fără răspuns' })).toBeInTheDocument()
      expect(screen.queryByRole('button', { name: /Subiectul 1,/ })).not.toBeInTheDocument()
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
      await begin('Începe practica')

      expect(api.startQuizAttempt).toHaveBeenCalledWith(7, 'PRACTICE')
      expect(screen.getByTestId('quiz-practice-banner')).toBeInTheDocument()
      expect(screen.queryByTestId('quiz-timer')).not.toBeInTheDocument()
      expect(screen.getByRole('button', { name: 'Termină practica' })).toBeInTheDocument()
    })

    it('shows the verdict and the right option as soon as an answer is given', async () => {
      api.startQuizAttempt.mockResolvedValue(started('PRACTICE'))
      const wrong: AnswerFeedback = { correct: false, correctOptionId: 1000, solution: 'Rezolvare: A' }
      api.saveQuizAnswer.mockResolvedValue(wrong)
      renderPage('?mode=practice')
      await begin('Începe practica')

      await userEvent.click(screen.getByLabelText('b) Varianta B'))

      expect(await screen.findByTestId('item-feedback')).toHaveTextContent('Greșit')
      expect(screen.getByTestId('item-feedback')).toHaveTextContent('Rezolvare: A')
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
      await begin('Începe practica')

      await userEvent.click(screen.getByLabelText('a) Varianta A'))

      expect(await screen.findByTestId('item-feedback')).toHaveTextContent('Corect!')
    })

    it('restores the feedback of earlier answers when a practice is resumed', async () => {
      api.startQuizAttempt.mockResolvedValue(started('PRACTICE', [
        { itemId: 100, selectedOptionId: 1001, photoUploaded: false, photoIds: [],
          feedback: { correct: false, correctOptionId: 1000, solution: null } },
      ]))
      renderPage('?mode=practice')

      await begin('Începe practica')

      expect(screen.getByTestId('item-feedback')).toHaveTextContent('Greșit')
    })

    it('ends the session without the "unanswered" warning of a graded test', async () => {
      api.startQuizAttempt.mockResolvedValue(started('PRACTICE'))
      api.submitQuizAttempt.mockResolvedValue({})
      renderPage('?mode=practice')
      await begin('Începe practica')

      await userEvent.click(screen.getByRole('button', { name: 'Termină practica' }))

      const dialog = await screen.findByRole('dialog', { name: 'Închei sesiunea de practică?' })
      expect(dialog).not.toHaveTextContent('fără răspuns')
      await userEvent.click(within(dialog).getByRole('button', { name: 'Termină practica' }))
      await waitFor(() => expect(api.submitQuizAttempt).toHaveBeenCalledWith(50))
    })

    it('marks the right option and the wrong pick with a sign, not only a colour', async () => {
      api.startQuizAttempt.mockResolvedValue(started('PRACTICE'))
      api.saveQuizAnswer.mockResolvedValue({ correct: false, correctOptionId: 1000, solution: null })
      renderPage('?mode=practice')
      await begin('Începe practica')

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
      await begin('Începe practica')
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

      await begin('Începe practica')

      expect(screen.getByText('Muta termenii')).toBeInTheDocument()
      expect(screen.getByTestId('hint-button')).toHaveTextContent('Încă un indiciu (1/2)')
    })

    it('shows nothing for an item that has no hints', async () => {
      api.startQuizAttempt.mockResolvedValue(started('PRACTICE', [], { hintCount: 0 }))
      renderPage('?mode=practice')

      await begin('Începe practica')

      expect(screen.queryByTestId('item-hints')).not.toBeInTheDocument()
    })

    it('shows the server error and keeps the button when a hint cannot be revealed', async () => {
      api.startQuizAttempt.mockResolvedValue(started('PRACTICE', [], { hintCount: 2 }))
      api.revealQuizHint.mockRejectedValue(new ApiError(400, 'Indiciile se dezvăluie pe rând'))
      renderPage('?mode=practice')
      await begin('Începe practica')

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
      expect(screen.getByTestId('quiz-facts')).toHaveTextContent('3 subiecte · 11 puncte')
      expect(screen.getByTestId('quiz-facts')).toHaveTextContent('Fără limită de timp')
      expect(api.getQuizPreview).toHaveBeenCalledWith(7)
      expect(api.startQuizAttempt).not.toHaveBeenCalled()
    })

    it('warns about the clock of a timed quiz', async () => {
      api.getQuizPreview.mockResolvedValue(preview({ timeLimitMinutes: 30 }))
      renderPage('')
      expect(await screen.findByTestId('quiz-limit-note')).toHaveTextContent('30 minute')
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
      expect(screen.getByRole('heading', { name: '1. · 2 p' })).toBeInTheDocument()
      expect(screen.getByRole('heading', { name: '1. · 4 p' })).toBeInTheDocument() // first under SUBIECTUL al II-lea
      const nav = screen.getByRole('navigation', { name: 'Subiecte' })
      expect(within(nav).getAllByRole('button').map((b) => b.textContent)).toEqual(['I.1', 'I.2', 'II.1'])
    })

    it('numbers every item in the bottom bar and marks the answered ones', async () => {
      api.startQuizAttempt.mockResolvedValue(startedThree())
      api.saveQuizAnswer.mockResolvedValue(null)
      renderPage('')
      await userEvent.click(await screen.findByRole('button', { name: 'Începe testul' }))
      await screen.findByText('Primul subiect')

      const nav = screen.getByRole('navigation', { name: 'Subiecte' })
      expect(within(nav).getByRole('button', { name: 'Subiectul 1, fără răspuns' })).toBeInTheDocument()

      await userEvent.click(screen.getByLabelText('a) A1'))

      expect(await within(nav).findByRole('button', { name: 'Subiectul 1, cu răspuns' })).toBeInTheDocument()
      expect(within(nav).getByRole('button', { name: 'Subiectul 2, fără răspuns' })).toBeInTheDocument()
    })

    it('jumps to an item from the bottom bar', async () => {
      api.startQuizAttempt.mockResolvedValue(startedThree())
      const scroll = vi.fn()
      Element.prototype.scrollIntoView = scroll
      renderPage('')
      await userEvent.click(await screen.findByRole('button', { name: 'Începe testul' }))
      await screen.findByText('Primul subiect')

      await userEvent.click(screen.getByRole('button', { name: 'Subiectul 3, fără răspuns' }))

      expect(scroll).toHaveBeenCalled()
      expect(document.activeElement).toHaveTextContent('Subiectul 3')
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

      const dialog = await screen.findByRole('dialog', { name: 'Ai 2 subiecte fără răspuns' })
      expect(dialog).toHaveTextContent('Subiectele 2, 3')
      await userEvent.click(within(dialog).getByRole('button', { name: 'Mergi la subiectul 2' }))

      await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())
      expect(api.submitQuizAttempt).not.toHaveBeenCalled()
      await waitFor(() => expect(document.activeElement).toHaveTextContent('Subiectul 2'))
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
      expect(screen.getByRole('button', { name: 'Subiectul 1, cu răspuns, netrimis încă' })).toBeInTheDocument()

      window.dispatchEvent(new Event('online'))

      await waitFor(() => expect(screen.queryByTestId('item-queued')).not.toBeInTheDocument())
      expect(api.saveQuizAnswer).toHaveBeenCalledTimes(2)
      expect(screen.queryByTestId('connection-banner')).not.toBeInTheDocument()
      expect(screen.getByRole('button', { name: 'Subiectul 1, cu răspuns' })).toBeInTheDocument()
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
      await begin('Începe practica')

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

      expect(await screen.findByRole('img', { name: 'Pagina 1 a rezolvării, subiectul 3' }))
        .toHaveAttribute('src', '/api/quiz/attempts/50/responses/102/photos/41')
      expect(screen.getByRole('img', { name: 'Pagina 2 a rezolvării, subiectul 3' }))
        .toHaveAttribute('src', '/api/quiz/attempts/50/responses/102/photos/42')
      expect(screen.getByText('Adaugă încă o pagină')).toBeInTheDocument()
      expect(screen.getByRole('status')).toHaveTextContent('Poză încărcată · 2 pagini trimise')
    })

    it('adds a page with each upload and says so (the E2E suite waits for "Poză încărcată")', async () => {
      api.uploadQuizPhoto.mockResolvedValueOnce({ photoIds: [41], feedback: null })
      api.uploadQuizPhoto.mockResolvedValueOnce({ photoIds: [41, 42], feedback: null })
      await begin()
      expect(await screen.findByText('Alege o poză din galerie')).toBeInTheDocument()

      await userEvent.upload(screen.getByTestId('item-photo'), new File(['x'], 'p1.png', { type: 'image/png' }))
      expect(await screen.findByRole('img', { name: 'Pagina 1 a rezolvării, subiectul 3' })).toBeInTheDocument()
      await userEvent.upload(screen.getByTestId('item-photo'), new File(['y'], 'p2.png', { type: 'image/png' }))

      expect(await screen.findByRole('img', { name: 'Pagina 2 a rezolvării, subiectul 3' })).toBeInTheDocument()
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
      expect(screen.getByRole('img', { name: 'Pagina 1 a rezolvării, subiectul 3' }))
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
