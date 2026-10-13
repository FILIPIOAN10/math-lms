import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
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
      selectedOptionText: 'Varianta B', correctOptionText: 'Varianta B', barem: null, photoUploaded: false, photoIds: [],
      hintsUsed: 0, hintsAvailable: 0, teacherComment: null,
    },
    {
      itemId: 101, position: 2, type: 'OPEN', statement: 'Rezolvă', points: 10, awardedPoints: 8, correct: null,
      selectedOptionText: null, correctOptionText: null, barem: 'x1 = 2', photoUploaded: true, photoIds: [41, 42],
      hintsUsed: 0, hintsAvailable: 0, teacherComment: 'Ai uitat unitatea de măsură.',
    },
  ],
  teacherComment: 'Lucrare îngrijită, bravo!',
  quizId: 10,
  practiceAllowed: false,
}

describe('AttemptResultView', () => {
  it('shows the student every page of their own photographed solution, in order (S2)', () => {
    render(<AttemptResultView result={graded} />, { wrapper: MemoryRouter })

    expect(screen.getByRole('img', { name: 'Poza ta pentru întrebarea 2, pagina 1' }))
      .toHaveAttribute('src', '/api/quiz/attempts/50/responses/101/photos/41')
    expect(screen.getByRole('img', { name: 'Poza ta pentru întrebarea 2, pagina 2' }))
      .toHaveAttribute('src', '/api/quiz/attempts/50/responses/101/photos/42')
  })

  it('does not try to show the photo to a parent (no endpoint of theirs serves it)', () => {
    render(<AttemptResultView result={graded} audience="parent" />, { wrapper: MemoryRouter })

    expect(screen.queryByRole('img')).not.toBeInTheDocument()
    expect(screen.getByText('Elevul a trimis 2 pagini cu rezolvarea.')).toBeInTheDocument()
  })

  it('shows the big final score once the attempt is graded', () => {
    render(<AttemptResultView result={graded} />, { wrapper: MemoryRouter })

    expect(screen.getByTestId('result-score')).toHaveTextContent('13 / 15 puncte')
    expect(screen.getByTestId('result-grade')).toHaveTextContent('Nota 8,80')
    expect(screen.getByTestId('result-grade')).toHaveTextContent('87%')
  })

  it("shows the teacher's comment on the paper and on each answer", () => {
    render(<AttemptResultView result={graded} />, { wrapper: MemoryRouter })

    expect(screen.getByTestId('teacher-comment')).toHaveTextContent('Lucrare îngrijită, bravo!')
    const itemComments = screen.getAllByTestId('item-teacher-comment')
    expect(itemComments).toHaveLength(1)
    expect(itemComments[0]).toHaveTextContent('Ai uitat unitatea de măsură.')
  })

  it("shows the teacher's comments to a parent too", () => {
    render(<AttemptResultView result={graded} audience="parent" />, { wrapper: MemoryRouter })

    expect(screen.getByTestId('teacher-comment')).toHaveTextContent('Lucrare îngrijită, bravo!')
    expect(screen.getByTestId('item-teacher-comment')).toHaveTextContent('Ai uitat unitatea de măsură.')
  })

  it('shows no comment block when the teacher wrote none', () => {
    render(<AttemptResultView result={{ ...graded, teacherComment: null, items: [graded.items[0]] }} />, { wrapper: MemoryRouter })

    expect(screen.queryByTestId('teacher-comment')).not.toBeInTheDocument()
    expect(screen.queryByTestId('item-teacher-comment')).not.toBeInTheDocument()
  })

  it('says it is waiting for the teacher while the open item is not graded', () => {
    const waiting: AttemptResultViewDto = {
      ...graded,
      status: 'SUBMITTED',
      finalScore: null,
      items: [graded.items[0], { ...graded.items[1], awardedPoints: null }],
    }

    render(<AttemptResultView result={waiting} />, { wrapper: MemoryRouter })

    expect(screen.queryByTestId('result-score')).not.toBeInTheDocument()
    expect(screen.getByText(/Grilele sunt corectate \(5 puncte până acum\)/)).toBeInTheDocument()
    expect(screen.getByText('în corectare')).toBeInTheDocument()
  })

  it('words the student view in the second person and the parent view about the student', () => {
    const { rerender } = render(<AttemptResultView result={graded} audience="student" />, { wrapper: MemoryRouter })
    expect(screen.getByText(/Răspunsul tău:/)).toBeInTheDocument()
    expect(screen.getByText('Ai trimis 2 pagini cu rezolvarea.')).toBeInTheDocument()

    rerender(<AttemptResultView result={graded} audience="parent" />)
    expect(screen.getByText(/Răspunsul elevului:/)).toBeInTheDocument()
    expect(screen.getByText('Elevul a trimis 2 pagini cu rezolvarea.')).toBeInTheDocument()
    expect(screen.queryByText(/Răspunsul tău:/)).not.toBeInTheDocument()
  })

  it('reveals the barem only when asked', async () => {
    render(<AttemptResultView result={graded} />, { wrapper: MemoryRouter })
    expect(screen.queryByText('x1 = 2')).not.toBeInTheDocument()

    await userEvent.click(screen.getByRole('button', { name: 'Arată baremul' }))

    expect(screen.getByText('x1 = 2')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Ascunde baremul' })).toBeInTheDocument()
  })

  it('shows the correct answer next to a wrong one', () => {
    const wrong: AttemptResultViewDto = {
      ...graded,
      items: [{
        ...graded.items[0], correct: false, awardedPoints: 0, selectedOptionText: 'Varianta A',
        selectedOptionLetter: 'a', correctOptionLetter: 'b',
      }],
    }

    render(<AttemptResultView result={wrong} />, { wrapper: MemoryRouter })

    expect(screen.getByText(/Răspunsul tău:/)).toHaveTextContent('✗')
    // the letters as on the paper test, so "b)" means the same thing in the barem and in class
    expect(screen.getByText(/Răspunsul tău:/)).toHaveTextContent('a) Varianta A')
    expect(screen.getByText(/Răspuns corect:/)).toHaveTextContent('b) Varianta B')
  })

  it('says a practice is not marked and shows how many choice questions were right, instead of a score', () => {
    const practice: AttemptResultViewDto = { ...graded, mode: 'PRACTICE', finalScore: null }

    render(<AttemptResultView result={practice} />, { wrapper: MemoryRouter })

    expect(screen.queryByTestId('result-score')).not.toBeInTheDocument()
    expect(screen.getByTestId('result-practice')).toHaveTextContent('fără notă')
    expect(screen.getByTestId('result-practice')).toHaveTextContent('Grile corecte: 1 din 1')
    expect(screen.getByTestId('mode-badge')).toHaveTextContent('Practică')
  })

  it('shows no practice badge on a graded test', () => {
    render(<AttemptResultView result={graded} />, { wrapper: MemoryRouter })

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

    render(<AttemptResultView result={practice} />, { wrapper: MemoryRouter })

    expect(screen.getAllByTestId('hints-used').map((n) => n.textContent)).toEqual([
      'Indicii folosite: 1 din 2',
      'Indicii folosite: 2 din 3',
    ])
    expect(screen.getByTestId('result-practice')).toHaveTextContent('Indicii folosite: 3 din 5')
  })

  it('says nothing about hints when the items had none', () => {
    render(<AttemptResultView result={{ ...graded, mode: 'PRACTICE', finalScore: null }} />, { wrapper: MemoryRouter })

    expect(screen.queryByTestId('hints-used')).not.toBeInTheDocument()
    expect(screen.getByTestId('result-practice')).not.toHaveTextContent('Indicii')
  })

  // --- S4: "Exersează ce ai greșit" ---

  const withMistakes: AttemptResultViewDto = {
    ...graded,
    practiceAllowed: true,
    items: [{ ...graded.items[0], correct: false, awardedPoints: 0, selectedOptionText: 'Varianta A' }, graded.items[1]],
  }

  it('names what to go back to, sends the wrong grilă to the review and offers a practice of just those', () => {
    render(<AttemptResultView result={withMistakes} />, { wrapper: MemoryRouter })

    const card = screen.getByTestId('practice-mistakes')
    expect(card).toHaveTextContent('Întrebările 1, 2 nu au ieșit complet')
    expect(card).toHaveTextContent('1 grilă greșită revine în recapitularea zilnică, începând de mâine.')
    expect(screen.getByRole('link', { name: 'recapitularea zilnică' })).toHaveAttribute('href', '/review')
    expect(screen.getByTestId('practice-mistakes-link')).toHaveAttribute('href', '/quizzes/10/take?mode=practice&only=100,101')
  })

  it('offers no practice link when the teacher did not open the quiz for practice', () => {
    render(<AttemptResultView result={{ ...withMistakes, practiceAllowed: false }} />, { wrapper: MemoryRouter })

    expect(screen.getByTestId('practice-mistakes')).toBeInTheDocument()
    expect(screen.queryByTestId('practice-mistakes-link')).not.toBeInTheDocument()
  })

  it('has nothing to say on a perfect paper, to a parent, or while the teacher is still grading', () => {
    const perfect = { ...graded, items: [graded.items[0], { ...graded.items[1], awardedPoints: 10 }] }
    const { rerender } = render(<AttemptResultView result={perfect} />, { wrapper: MemoryRouter })
    expect(screen.queryByTestId('practice-mistakes')).not.toBeInTheDocument()

    rerender(<AttemptResultView result={withMistakes} audience="parent" />)
    expect(screen.queryByTestId('practice-mistakes')).not.toBeInTheDocument()

    rerender(<AttemptResultView result={{ ...withMistakes, status: 'SUBMITTED', finalScore: null }} />)
    expect(screen.queryByTestId('practice-mistakes')).not.toBeInTheDocument()
  })

  it('sends the student to the chapter page of a question they lost points on (EN chapters)', () => {
    const chapter = { id: 1, code: 'A1', title: 'Ordinea efectuării operațiilor', ready: true }
    const withChapters: AttemptResultViewDto = {
      ...graded,
      items: [
        { ...graded.items[0], enChapter: chapter }, // right: no button
        { ...graded.items[1], enChapter: { ...chapter, id: 17, code: 'G6', title: 'Cercul' } }, // 8 / 10 p
      ],
    }
    render(<AttemptResultView result={withChapters} />, { wrapper: MemoryRouter })

    const links = screen.getAllByTestId('revision-links')
    expect(links).toHaveLength(1)
    expect(screen.getByRole('link', { name: /Vezi modele: G6 Cercul/ })).toHaveAttribute('href', '/capitole/17')
    expect(screen.queryByRole('link', { name: /A1/ })).not.toBeInTheDocument()
  })
})
