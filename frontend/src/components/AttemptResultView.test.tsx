import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'
import { AttemptResultView } from '@/components/AttemptResultView'
import type { AttemptResultViewDto } from '@/lib/api'

const graded: AttemptResultViewDto = {
  attemptId: 50,
  quizTitle: 'Simulare EN',
  status: 'GRADED',
  mode: 'TEST',
  finalScore: 13,
  maxScore: 15,
  items: [
    {
      itemId: 100, position: 1, type: 'SINGLE_CHOICE', statement: 'Alege B', points: 5, awardedPoints: 5, correct: true,
      selectedOptionText: 'Varianta B', correctOptionText: 'Varianta B', barem: null, photoUploaded: false,
      hintsUsed: 0, hintsAvailable: 0,
    },
    {
      itemId: 101, position: 2, type: 'OPEN', statement: 'Rezolvă', points: 10, awardedPoints: 8, correct: null,
      selectedOptionText: null, correctOptionText: null, barem: 'x1 = 2', photoUploaded: true,
      hintsUsed: 0, hintsAvailable: 0,
    },
  ],
}

describe('AttemptResultView', () => {
  it('shows the student their own photo next to the open item', () => {
    render(<AttemptResultView result={graded} />)

    const img = screen.getByRole('img', { name: 'Poza ta pentru subiectul 2' })
    expect(img).toHaveAttribute('src', '/api/quiz/attempts/50/responses/101/photo')
  })

  it('does not try to show the photo to a parent (no endpoint of theirs serves it)', () => {
    render(<AttemptResultView result={graded} audience="parent" />)

    expect(screen.queryByRole('img')).not.toBeInTheDocument()
    expect(screen.getByText('Elevul a trimis o poză cu rezolvarea.')).toBeInTheDocument()
  })

  it('shows the big final score once the attempt is graded', () => {
    render(<AttemptResultView result={graded} />)

    expect(screen.getByTestId('result-score')).toHaveTextContent('13 / 15 puncte')
  })

  it('says it is waiting for the teacher while the open item is not graded', () => {
    const waiting: AttemptResultViewDto = {
      ...graded,
      status: 'SUBMITTED',
      finalScore: null,
      items: [graded.items[0], { ...graded.items[1], awardedPoints: null }],
    }

    render(<AttemptResultView result={waiting} />)

    expect(screen.queryByTestId('result-score')).not.toBeInTheDocument()
    expect(screen.getByText(/Grilele sunt corectate \(5 puncte până acum\)/)).toBeInTheDocument()
    expect(screen.getByText('în corectare')).toBeInTheDocument()
  })

  it('words the student view in the second person and the parent view about the student', () => {
    const { rerender } = render(<AttemptResultView result={graded} audience="student" />)
    expect(screen.getByText(/Răspunsul tău:/)).toBeInTheDocument()
    expect(screen.getByText('Ai trimis o poză cu rezolvarea.')).toBeInTheDocument()

    rerender(<AttemptResultView result={graded} audience="parent" />)
    expect(screen.getByText(/Răspunsul elevului:/)).toBeInTheDocument()
    expect(screen.getByText('Elevul a trimis o poză cu rezolvarea.')).toBeInTheDocument()
    expect(screen.queryByText(/Răspunsul tău:/)).not.toBeInTheDocument()
  })

  it('reveals the barem only when asked', async () => {
    render(<AttemptResultView result={graded} />)
    expect(screen.queryByText('x1 = 2')).not.toBeInTheDocument()

    await userEvent.click(screen.getByRole('button', { name: 'Arată baremul' }))

    expect(screen.getByText('x1 = 2')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Ascunde baremul' })).toBeInTheDocument()
  })

  it('shows the correct answer next to a wrong one', () => {
    const wrong: AttemptResultViewDto = {
      ...graded,
      items: [{ ...graded.items[0], correct: false, awardedPoints: 0, selectedOptionText: 'Varianta A' }],
    }

    render(<AttemptResultView result={wrong} />)

    expect(screen.getByText(/Răspunsul tău:/)).toHaveTextContent('✗')
    expect(screen.getByText(/Răspuns corect:/)).toHaveTextContent('Varianta B')
  })

  it('says a practice is not marked and shows how many choice questions were right, instead of a score', () => {
    const practice: AttemptResultViewDto = { ...graded, mode: 'PRACTICE', finalScore: null }

    render(<AttemptResultView result={practice} />)

    expect(screen.queryByTestId('result-score')).not.toBeInTheDocument()
    expect(screen.getByTestId('result-practice')).toHaveTextContent('fără notă')
    expect(screen.getByTestId('result-practice')).toHaveTextContent('Grile corecte: 1 din 1')
    expect(screen.getByTestId('mode-badge')).toHaveTextContent('Practică')
  })

  it('shows no practice badge on a graded test', () => {
    render(<AttemptResultView result={graded} />)

    expect(screen.queryByTestId('mode-badge')).not.toBeInTheDocument()
  })

  it('shows how many hints were used per item and in total on a practice result', () => {
    const practice: AttemptResultViewDto = {
      ...graded,
      mode: 'PRACTICE',
      finalScore: null,
      items: [
        { ...graded.items[0], hintsUsed: 1, hintsAvailable: 2 },
        { ...graded.items[1], hintsUsed: 2, hintsAvailable: 3 },
      ],
    }

    render(<AttemptResultView result={practice} />)

    expect(screen.getAllByTestId('hints-used').map((n) => n.textContent)).toEqual([
      'Indicii folosite: 1 din 2',
      'Indicii folosite: 2 din 3',
    ])
    expect(screen.getByTestId('result-practice')).toHaveTextContent('Indicii folosite: 3 din 5')
  })

  it('says nothing about hints when the items had none', () => {
    render(<AttemptResultView result={{ ...graded, mode: 'PRACTICE', finalScore: null }} />)

    expect(screen.queryByTestId('hints-used')).not.toBeInTheDocument()
    expect(screen.getByTestId('result-practice')).not.toHaveTextContent('Indicii')
  })
})
