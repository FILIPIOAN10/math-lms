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
  })

  describe('practice mode (?mode=practice)', () => {
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

      await userEvent.click(screen.getByLabelText('Varianta B'))

      expect(await screen.findByTestId('item-feedback')).toHaveTextContent('Greșit')
      expect(screen.getByTestId('item-feedback')).toHaveTextContent('Rezolvare: A')
      expect(api.saveQuizAnswer).toHaveBeenCalledWith(50, 100, 1001)
      // the right option is outlined green, the wrong pick red
      expect(screen.getByText('Varianta A').closest('label')).toHaveClass('border-emerald-500')
      expect(screen.getByText('Varianta B').closest('label')).toHaveClass('border-destructive')
    })

    it('congratulates a correct answer', async () => {
      api.startQuizAttempt.mockResolvedValue(started('PRACTICE'))
      api.saveQuizAnswer.mockResolvedValue({ correct: true, correctOptionId: 1000, solution: null })
      renderPage('?mode=practice')
      await begin('Începe practica')

      await userEvent.click(screen.getByLabelText('Varianta A'))

      expect(await screen.findByTestId('item-feedback')).toHaveTextContent('Corect!')
    })

    it('restores the feedback of earlier answers when a practice is resumed', async () => {
      api.startQuizAttempt.mockResolvedValue(started('PRACTICE', [
        { itemId: 100, selectedOptionId: 1001, photoUploaded: false,
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

      await userEvent.click(screen.getByLabelText('Varianta B'))

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

      await userEvent.click(screen.getByLabelText('Varianta A'))

      expect(await screen.findByText(/încă folosit/)).toBeInTheDocument()
      expect(api.submitQuizAttempt).not.toHaveBeenCalled()
    })

    it('starts in TEST mode and never shows feedback: the server answers 204', async () => {
      api.startQuizAttempt.mockResolvedValue(started('TEST'))
      api.saveQuizAnswer.mockResolvedValue(null)
      renderPage('')
      await begin('Începe testul')

      await userEvent.click(screen.getByLabelText('Varianta A'))

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
    it('numbers every item in the bottom bar and marks the answered ones', async () => {
      api.startQuizAttempt.mockResolvedValue(startedThree())
      api.saveQuizAnswer.mockResolvedValue(null)
      renderPage('')
      await userEvent.click(await screen.findByRole('button', { name: 'Începe testul' }))
      await screen.findByText('Primul subiect')

      const nav = screen.getByRole('navigation', { name: 'Subiecte' })
      expect(within(nav).getByRole('button', { name: 'Subiectul 1, fără răspuns' })).toBeInTheDocument()

      await userEvent.click(screen.getByLabelText('A1'))

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
      await userEvent.click(await screen.findByLabelText('A1'))
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
        { itemId: 100, selectedOptionId: 1000, photoUploaded: false, feedback: null },
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
    it('shows a thumbnail of the photo the server has, also when the attempt is resumed', async () => {
      const resumed = startedThree()
      resumed.answers = [{ itemId: 102, selectedOptionId: null, photoUploaded: true, feedback: null }]
      api.startQuizAttempt.mockResolvedValue(resumed)
      renderPage('')
      await userEvent.click(await screen.findByRole('button', { name: 'Începe testul' }))

      const img = await screen.findByRole('img', { name: 'Poza ta pentru subiectul 3' })
      expect(img.getAttribute('src')).toMatch(/^\/api\/quiz\/attempts\/50\/responses\/102\/photo/)
      expect(screen.getByText('Înlocuiește poza')).toBeInTheDocument()
    })

    it('refreshes the thumbnail after a new upload', async () => {
      api.startQuizAttempt.mockResolvedValue(startedThree())
      api.uploadQuizPhoto.mockResolvedValue(null)
      renderPage('')
      await userEvent.click(await screen.findByRole('button', { name: 'Începe testul' }))
      expect(await screen.findByText('Încarcă poza rezolvării')).toBeInTheDocument()

      const file = new File(['x'], 'rezolvare.png', { type: 'image/png' })
      await userEvent.upload(screen.getByTestId('item-photo'), file)

      const img = await screen.findByRole('img', { name: 'Poza ta pentru subiectul 3' })
      expect(img.getAttribute('src')).toContain('v=1')
    })
  })
})
