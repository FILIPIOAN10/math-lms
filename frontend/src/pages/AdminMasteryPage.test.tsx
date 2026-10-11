import { render, screen, within } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { AdminMasteryPage } from '@/pages/AdminMasteryPage'
import type { ClassMastery } from '@/lib/api'

const api = vi.hoisted(() => ({ getClassMastery: vi.fn(), listClasses: vi.fn() }))
vi.mock('@/lib/api', async (original) => ({ ...(await original<typeof import('@/lib/api')>()), ...api }))

const grid: ClassMastery = {
  chapters: [
    { id: 1, title: 'Fracții', bookTitle: 'Algebra' },
    { id: 2, title: 'Ecuații', bookTitle: 'Algebra' },
    { id: 3, title: 'Triunghiuri', bookTitle: 'Geometrie' },
  ],
  students: [
    { studentId: 4, fullName: 'Ana Student', cells: [
      { chapterId: 1, percent: 90, questions: 4 }, { chapterId: 2, percent: 40, questions: 2 }, { chapterId: 3, percent: null, questions: 0 },
    ] },
    { studentId: 5, fullName: 'Radu Nou', cells: [
      { chapterId: 1, percent: 60, questions: 4 }, { chapterId: 2, percent: null, questions: 0 }, { chapterId: 3, percent: null, questions: 0 },
    ] },
  ],
}

function renderPage() {
  return render(
    <MemoryRouter initialEntries={['/admin/classes/8/mastery']}>
      <Routes>
        <Route path="/admin/classes/:classId/mastery" element={<AdminMasteryPage />} />
      </Routes>
    </MemoryRouter>,
  )
}

describe('AdminMasteryPage', () => {
  beforeEach(() => {
    api.getClassMastery.mockReset().mockResolvedValue(grid)
    api.listClasses.mockReset().mockResolvedValue([{ id: 8, name: 'Clasa a 8-a', description: null }])
  })

  it('is titled with the class and loads its grid', async () => {
    renderPage()
    expect(await screen.findByRole('heading', { level: 1, name: 'Pe capitole — Clasa a 8-a' })).toBeInTheDocument()
    expect(api.getClassMastery).toHaveBeenCalledWith(8)
  })

  it('shows students against chapters, books as column groups, each cell with its percent', async () => {
    renderPage()
    const table = await screen.findByRole('table')

    expect(within(table).getByRole('columnheader', { name: 'Algebra' })).toHaveAttribute('colspan', '2')
    expect(within(table).getByRole('columnheader', { name: 'Triunghiuri' })).toBeInTheDocument()
    const ana = within(table).getByRole('row', { name: /Ana Student/ })
    expect(within(ana).getByRole('cell', { name: 'Ana Student, Fracții: 90%, Stăpânit' })).toHaveTextContent('90%')
    expect(within(ana).getByRole('cell', { name: 'Ana Student, Triunghiuri: Fără date' })).toHaveTextContent('—')
  })

  it("adds the class average under each chapter, from the students who have data", async () => {
    renderPage()
    const average = within(await screen.findByRole('table')).getByRole('row', { name: /Media clasei/ })
    const cells = within(average).getAllByRole('cell')
    expect(cells.map((c) => c.textContent)).toEqual(['75%', '40%', '—'])
  })

  it('explains what to do when the class has no chapters', async () => {
    api.getClassMastery.mockResolvedValue({ chapters: [], students: [] })
    renderPage()
    expect(await screen.findByText(/Clasa nu are încă capitole/)).toBeInTheDocument()
  })

  it('reminds the teacher that only tagged quizzes count when nothing has data yet', async () => {
    api.getClassMastery.mockResolvedValue({
      chapters: grid.chapters,
      students: grid.students.map((s) => ({ ...s, cells: s.cells.map((c) => ({ ...c, percent: null, questions: 0 })) })),
    })
    renderPage()
    expect(await screen.findByText(/Leagă quiz-urile de capitole/)).toBeInTheDocument()
  })
})
