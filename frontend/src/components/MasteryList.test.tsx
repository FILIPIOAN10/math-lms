import { render, screen, within } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { MasteryList } from '@/components/MasteryList'
import type { ChapterMastery } from '@/lib/api'

const row = (id: number, chapterTitle: string, bookTitle: string, percent: number | null, questions = 3): ChapterMastery => ({
  chapterId: id, chapterTitle, bookTitle, schoolClassName: 'Clasa a 8-a', earned: 0, possible: 0, percent, questions,
})

describe('MasteryList', () => {
  it('groups the chapters by book and gives each its percent and level in words', () => {
    render(
      <MasteryList
        rows={[row(1, 'Fracții', 'Algebra', 85), row(2, 'Ecuații', 'Algebra', 40), row(3, 'Triunghiuri', 'Geometrie', null, 0)]}
      />,
    )

    const algebra = screen.getByRole('group', { name: 'Algebra' })
    const items = within(algebra).getAllByRole('listitem')
    expect(items[0]).toHaveTextContent('Fracții')
    expect(items[0]).toHaveTextContent('85%')
    expect(items[0]).toHaveTextContent('Stăpânit')
    expect(items[1]).toHaveTextContent('De recapitulat')
    expect(within(screen.getByRole('group', { name: 'Geometrie' })).getByRole('listitem')).toHaveTextContent('Fără date')
  })

  it('says how many questions a percent is based on', () => {
    render(<MasteryList rows={[row(1, 'Fracții', 'Algebra', 85, 1)]} />)
    expect(screen.getByRole('listitem')).toHaveTextContent('1 întrebare')
  })

  it('explains itself when there is nothing yet', () => {
    render(<MasteryList rows={[]} />)
    expect(screen.getByText(/Niciun capitol încă/)).toBeInTheDocument()
  })
})
