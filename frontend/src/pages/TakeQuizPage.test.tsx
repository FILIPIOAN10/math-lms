import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { TakeQuizPage } from '@/pages/TakeQuizPage'
import { ApiError, type AnswerFeedback, type AttemptMode, type StartedAttemptDto } from '@/lib/api'

const api = vi.hoisted(() => ({
  startQuizAttempt: vi.fn(),
  saveQuizAnswer: vi.fn(),
  uploadQuizPhoto: vi.fn(),
  submitQuizAttempt: vi.fn(),
  revealQuizHint: vi.fn(),
  getStudentQuizzes: vi.fn(),
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

function renderPage(query: string) {
  return render(
    <MemoryRouter initialEntries={[`/quizzes/7/take${query}`]}>
      <Routes>
        <Route path="/quizzes/:id/take" element={<TakeQuizPage />} />
        <Route path="/quizzes" element={<p>lista de teste</p>} />
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
      expect(screen.getByLabelText('Varianta A').closest('label')).toHaveClass('border-emerald-500')
      expect(screen.getByLabelText('Varianta B').closest('label')).toHaveClass('border-destructive')
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
      const confirm = vi.spyOn(window, 'confirm').mockReturnValue(true)
      renderPage('?mode=practice')
      await begin('Începe practica')

      await userEvent.click(screen.getByRole('button', { name: 'Termină practica' }))

      expect(confirm).toHaveBeenCalledWith('Închei sesiunea de practică?')
      await waitFor(() => expect(api.submitQuizAttempt).toHaveBeenCalledWith(50))
      confirm.mockRestore()
    })
  })

  describe('graded test (default)', () => {
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
})
