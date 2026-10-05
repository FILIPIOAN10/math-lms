import { render, screen, within } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { describe, expect, it } from 'vitest'
import { AssignmentsSection } from '@/components/AssignmentsSection'
import type { StudentAssignment } from '@/lib/api'

const base: StudentAssignment = {
  assignmentId: 1, quizId: 10, quizTitle: 'Simulare EN', schoolClassName: 'Clasa a 9-a',
  dueAt: '2026-10-08T15:00:00Z', state: 'NOT_STARTED', late: false, overdue: false, attemptId: null, timeLimitMinutes: null,
}
const NOW = Date.parse('2026-10-05T10:00:00Z')

function renderSection(assignments: StudentAssignment[]) {
  return render(
    <MemoryRouter>
      <AssignmentsSection assignments={assignments} nowMs={NOW} />
    </MemoryRouter>,
  )
}

describe('AssignmentsSection', () => {
  it('renders nothing when there is no homework', () => {
    const { container } = renderSection([])

    expect(container).toBeEmptyDOMElement()
  })

  it('shows the quiz, the class, the deadline in words and a start link', () => {
    renderSection([base])

    const card = screen.getByTestId('assignment-card')
    expect(within(card).getByText('Simulare EN')).toBeInTheDocument()
    expect(card).toHaveTextContent('Clasa a 9-a')
    expect(card).toHaveTextContent('peste 3 zile')
    expect(within(card).getByText('De făcut')).toBeInTheDocument()
    expect(within(card).getByRole('link', { name: 'Începe' })).toHaveAttribute('href', '/quizzes/10/take')
  })

  it('offers to continue an attempt in progress', () => {
    renderSection([{ ...base, state: 'IN_PROGRESS', attemptId: 7 }])

    expect(screen.getByRole('link', { name: 'Continuă' })).toHaveAttribute('href', '/quizzes/10/take')
    expect(screen.getByText('În lucru')).toBeInTheDocument()
  })

  it('flags an overdue assignment and still lets the student do it (the deadline is soft)', () => {
    renderSection([{ ...base, dueAt: '2026-10-04T10:00:00Z', overdue: true }])

    expect(screen.getByText('Termen depășit')).toBeInTheDocument()
    expect(screen.getByTestId('assignment-card')).toHaveTextContent('depășit cu 1 zi')
    expect(screen.getByRole('link', { name: 'Începe' })).toBeInTheDocument()
  })

  it('links a handed-in assignment to its result, and says when it was late', () => {
    renderSection([{ ...base, state: 'SUBMITTED', late: true, attemptId: 9 }])

    expect(screen.getByText('Predată cu întârziere')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Vezi rezultatul' })).toHaveAttribute('href', '/quizzes/attempts/9/result')
  })

  it('shows the homework not yet done before the finished ones, as the server ordered it', () => {
    renderSection([base, { ...base, assignmentId: 2, quizTitle: 'Gata', state: 'GRADED', attemptId: 4 }])

    expect(screen.getAllByTestId('assignment-card').map((c) => c.textContent)).toEqual([
      expect.stringContaining('Simulare EN'), expect.stringContaining('Gata'),
    ])
  })
})
