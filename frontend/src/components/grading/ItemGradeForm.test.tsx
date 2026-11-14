import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { ItemGradeForm } from '@/components/grading/ItemGradeForm'
import type { AdminItemReview, AiProposal } from '@/lib/api'

/** Subiectul III: a) asks for no result, the student typed b) right (24,0 = 24) and c) wrong. */
const problem: AdminItemReview = {
  itemId: 300, position: 1, type: 'OPEN', statement: 'Se consideră numerele x și y.', points: 7, barem: null,
  selectedOptionText: null, correctOptionText: null, correct: null, awardedPoints: null,
  photoUploaded: true, photoIds: [41], teacherComment: null,
  parts: [
    { id: 31, label: 'a', statement: 'Arată că x = 25.', points: 2, barem: 'x = 25', awardedPoints: null },
    {
      id: 32, label: 'b', statement: 'Calculează aria.', points: 3, barem: 'A = 24', awardedPoints: null,
      typedResult: '24,0', expectedResult: '24', resultCheck: 'RIGHT',
    },
    {
      id: 33, label: 'c', statement: 'Determină y.', points: 2, barem: 'y = 1/2', awardedPoints: null,
      typedResult: '3', expectedResult: '1/2', resultCheck: 'WRONG',
    },
  ],
}

const suggestedIn = (group: string) =>
  within(screen.getByTestId(group)).queryAllByRole('radio').filter((b) => b.hasAttribute('data-suggested'))

describe('ItemGradeForm: the final result typed for a requirement (V72)', () => {
  it('a right result proposes the full points of its requirement; a wrong one proposes nothing', () => {
    render(<ItemGradeForm item={problem} save={vi.fn().mockResolvedValue(undefined)} />)

    const proposed = suggestedIn('grade-part-b')
    expect(proposed).toHaveLength(1)
    expect(proposed[0]).toHaveTextContent('3') // b) is worth 3p
    expect(proposed[0]).toHaveAccessibleDescription('rezultat final corect')
    expect(suggestedIn('grade-part-a')).toHaveLength(0)
    expect(suggestedIn('grade-part-c')).toHaveLength(0)

    const lines = screen.getAllByTestId('result-check')
    expect(lines).toHaveLength(2) // nothing for a), which asks for no result
    expect(lines[0]).toHaveTextContent('Rezultat scris de elev: 24,0 ✓ corect')
    expect(lines[1]).toHaveTextContent('Rezultat scris de elev: 3 ✗ greșit · în barem: 1/2')
  })

  it('nothing is given until the teacher takes the proposal: Enter takes it and saves', async () => {
    const save = vi.fn().mockResolvedValue(undefined)
    render(<ItemGradeForm item={problem} save={save} />)
    expect(screen.getByTestId('part-total')).toHaveTextContent('Total: — / 7 p')
    expect(save).not.toHaveBeenCalled()

    within(screen.getByTestId('grade-part-b')).getByRole('radio', { name: '0' }).focus()
    await userEvent.keyboard('{Enter}')

    await waitFor(() => expect(save).toHaveBeenCalledWith(expect.objectContaining({ parts: { 31: null, 32: 3, 33: null } })))
    expect(within(screen.getByTestId('grade-part-b')).getByRole('radio', { name: '3' })).toBeChecked()
  })

  it('the teacher can still give fewer points than proposed', async () => {
    const save = vi.fn().mockResolvedValue(undefined)
    render(<ItemGradeForm item={problem} save={save} />)

    await userEvent.click(within(screen.getByTestId('grade-part-b')).getByRole('radio', { name: '1' }))

    await waitFor(() => expect(save).toHaveBeenCalledWith(expect.objectContaining({ parts: { 31: null, 32: 1, 33: null } })))
  })

  it('an AI proposal, which has read the photo, comes before the result check', () => {
    const ai: AiProposal = {
      itemId: 300, status: 'DONE', points: 4, reason: null, confidence: 'HIGH', error: null,
      parts: [{ partId: 32, label: 'b', points: 2, reason: 'Lipsește un pas.' }],
    }
    render(<ItemGradeForm item={problem} save={vi.fn().mockResolvedValue(undefined)} aiOn aiProposal={ai} />)

    const proposed = suggestedIn('grade-part-b')
    expect(proposed).toHaveLength(1)
    expect(proposed[0]).toHaveTextContent('2')
    expect(proposed[0]).toHaveAccessibleDescription('propus de AI')
    expect(screen.getAllByTestId('result-check')[0]).toHaveTextContent('✓ corect') // still said, next to the AI's reason
  })
})
