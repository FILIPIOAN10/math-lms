import { render, screen, within } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { describe, expect, it } from 'vitest'
import { EnMasteryList } from '@/components/EnMasteryList'
import { EnMasteryGrid } from '@/components/EnMasteryGrid'
import type { EnChapterMastery, EnClassMastery } from '@/lib/api'

const row = (over: Partial<EnChapterMastery>): EnChapterMastery => ({
  chapterId: 1, code: 'A1', title: 'Ordinea efectuării operațiilor', area: 'ALGEBRA', earned: 5, possible: 5, percent: 100,
  questions: 1, ready: true, ...over,
})

describe('EnMasteryList', () => {
  it('puts the weakest chapters first, then algebra and geometry, and leaves out what was not met', () => {
    render(
      <EnMasteryList rows={[
        row({}),
        row({ chapterId: 3, code: 'A3', title: 'Procente', earned: 0, possible: 5, percent: 0 }),
        row({ chapterId: 4, code: 'A4', title: 'Numere întregi', percent: null, questions: 0, earned: 0, possible: 0 }),
        row({ chapterId: 20, code: 'G9', title: 'Drepte și plane', area: 'GEOMETRY', earned: 1, possible: 3, percent: 33, ready: false }),
      ]} />,
      { wrapper: MemoryRouter },
    )

    const weakest = screen.getByTestId('en-weakest')
    expect(within(weakest).getAllByRole('listitem').map((li) => li.textContent)).toEqual(['A3 Procente0%', 'G9 Drepte și plane33%'])
    expect(within(weakest).getByRole('link', { name: 'A3 Procente' })).toHaveAttribute('href', '/capitole/3')
    expect(within(weakest).queryByRole('link', { name: /G9/ })).not.toBeInTheDocument() // its page is still empty

    expect(within(screen.getByRole('group', { name: 'Algebră' })).getAllByTestId('en-mastery-row')).toHaveLength(2)
    expect(within(screen.getByRole('group', { name: 'Geometrie' })).getByText('1 / 3 p · 1 întrebare')).toBeInTheDocument()
    expect(screen.queryByText(/Numere întregi/)).not.toBeInTheDocument()
  })

  it('says so when nothing has been graded yet', () => {
    render(<EnMasteryList rows={[row({ percent: null, questions: 0, earned: 0, possible: 0 })]} />, { wrapper: MemoryRouter })
    expect(screen.getByText(/Încă nicio întrebare de EN notată/)).toBeInTheDocument()
  })
})

describe('EnMasteryGrid', () => {
  const grid: EnClassMastery = {
    chapters: [
      { id: 1, code: 'A1', title: 'Ordinea efectuării operațiilor', area: 'ALGEBRA' },
      { id: 20, code: 'G9', title: 'Drepte și plane', area: 'GEOMETRY' },
    ],
    students: [
      { studentId: 7, fullName: 'Ana', cells: [{ chapterId: 1, percent: 100, questions: 1 }, { chapterId: 20, percent: 33, questions: 1 }] },
      { studentId: 8, fullName: 'Radu', cells: [{ chapterId: 1, percent: 60, questions: 1 }, { chapterId: 20, percent: null, questions: 0 }] },
    ],
  }

  it('shows each student in each chapter, with the class average', () => {
    render(<EnMasteryGrid grid={grid} />, { wrapper: MemoryRouter })

    expect(screen.getByRole('columnheader', { name: 'A1' })).toHaveAttribute('title', 'Ordinea efectuării operațiilor')
    expect(screen.getByLabelText('Ana, G9 Drepte și plane: 33%, De recapitulat')).toHaveTextContent('33%')
    expect(screen.getByLabelText('Radu, G9 Drepte și plane: Fără date')).toHaveTextContent('—')
    expect(screen.getByTestId('en-average-A1')).toHaveTextContent('80%')
    expect(screen.getByTestId('en-average-G9')).toHaveTextContent('33%')
    expect(screen.getByRole('link', { name: 'Ana' })).toHaveAttribute('href', '/admin/students/7')
  })
})
