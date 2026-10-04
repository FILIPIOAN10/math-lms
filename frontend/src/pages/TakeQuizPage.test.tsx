import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { TakeQuizPage } from '@/pages/TakeQuizPage'
import type { AnswerFeedback, AttemptMode, StartedAttemptDto } from '@/lib/api'

const api = vi.hoisted(() => ({
  startQuizAttempt: vi.fn(),
  saveQuizAnswer: vi.fn(),
  uploadQuizPhoto: vi.fn(),
  submitQuizAttempt: vi.fn(),
  getStudentQuizzes: vi.fn(),
}))
vi.mock('@/lib/api', async (importOriginal) => ({ ...(await importOriginal<typeof import('@/lib/api')>()), ...api }))

function started(mode: AttemptMode, answers: StartedAttemptDto['answers'] = []): StartedAttemptDto {
  return {
    attemptId: 50,
    status: 'IN_PROGRESS',
    mode,
    deadlineAt: null,
    serverNow: '2026-10-04T12:00:00Z',
    answers,
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
})
