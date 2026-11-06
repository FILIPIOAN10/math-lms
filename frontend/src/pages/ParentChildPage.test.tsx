import { render, screen, waitFor, within } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ParentChildPage } from '@/pages/ParentChildPage'
import { ApiError, type StudentAssignment } from '@/lib/api'

const api = vi.hoisted(() => ({
  getChildAttempts: vi.fn(),
  getChildProgress: vi.fn(),
  getChildAssignments: vi.fn(),
  listMyChildren: vi.fn(),
  getChildMastery: vi.fn(),
  getChildEnMastery: vi.fn(),
}))
vi.mock('@/lib/api', async (original) => ({ ...(await original<typeof import('@/lib/api')>()), ...api }))

const homework = (id: number, title: string, over: Partial<StudentAssignment> = {}): StudentAssignment => ({
  assignmentId: id, quizId: id, quizTitle: title, schoolClassName: 'Clasa a 8-a',
  dueAt: new Date(Date.now() + 86_400_000).toISOString(), state: 'NOT_STARTED', late: false, overdue: false,
  attemptId: null, timeLimitMinutes: null, ...over,
})

function renderPage() {
  return render(
    <MemoryRouter initialEntries={['/parent/children/4']}>
      <Routes>
        <Route path="/parent/children/:childId" element={<ParentChildPage />} />
      </Routes>
    </MemoryRouter>,
  )
}

describe('ParentChildPage', () => {
  beforeEach(() => {
    Object.values(api).forEach((fn) => fn.mockReset())
    api.getChildAttempts.mockResolvedValue([])
    api.getChildProgress.mockResolvedValue([])
    api.getChildAssignments.mockResolvedValue([])
    api.listMyChildren.mockResolvedValue([{ id: 4, fullName: 'Ana Student', email: 'ana@x.ro' }])
    api.getChildMastery.mockResolvedValue([])
    api.getChildEnMastery.mockResolvedValue([])
  })

  const chapter = (chapterTitle: string, percent: number | null) => ({
    chapterId: 1, chapterTitle, bookTitle: 'Algebra', schoolClassName: 'Clasa a 8-a', earned: 4, possible: 10, percent, questions: 4,
  })
  const enChapter = (code: string, title: string, percent: number) => ({
    chapterId: 2, code, title, area: 'ALGEBRA', earned: 1, possible: 10, percent, questions: 2, ready: true,
  })

  describe('De lucrat acum', () => {
    it('says what to do next in plain words, before anything else', async () => {
      api.getChildAssignments.mockResolvedValue([homework(1, 'Fracții', { overdue: true }), homework(2, 'Ecuații')])
      api.getChildMastery.mockResolvedValue([chapter('Fracții', 30)])
      api.getChildEnMastery.mockResolvedValue([enChapter('A3', 'Procente și fracții', 10)])
      renderPage()

      const summary = await screen.findByTestId('child-summary')
      expect(summary).toHaveTextContent('2 teme de predat · 1 cu termenul depășit')
      expect(summary).toHaveTextContent('De recapitulat: Procente și fracții, Fracții')
      expect(summary).not.toHaveTextContent('A3')
      expect(summary.compareDocumentPosition(screen.getByRole('heading', { name: 'Teme' })) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy()
    })

    it('says so when nothing is urgent', async () => {
      api.getChildMastery.mockResolvedValue([chapter('Fracții', 90)])
      renderPage()

      expect(await screen.findByTestId('child-summary')).toHaveTextContent('Nimic urgent')
    })

    it('waits for the chapters before it says nothing is urgent', async () => {
      api.getChildEnMastery.mockReturnValue(new Promise(() => undefined))
      renderPage()

      await screen.findByRole('heading', { level: 1, name: 'Ana Student' })
      await screen.findByRole('heading', { name: 'Teme' })
      expect(screen.queryByTestId('child-summary')).not.toBeInTheDocument()
    })
  })

  it('shows how it is going before the homework, and folds the chapters away without codes', async () => {
    api.getChildProgress.mockResolvedValue([])
    api.getChildEnMastery.mockResolvedValue([enChapter('A3', 'Procente și fracții', 60)])
    renderPage()

    const chart = await screen.findByRole('heading', { name: 'Progres în timp' })
    expect(chart.compareDocumentPosition(await screen.findByRole('heading', { name: 'Teme' })) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy()
    const folded = await screen.findByTestId('child-chapters')
    expect(folded).not.toHaveAttribute('open')
    expect(folded).toHaveTextContent('Procente și fracții')
    expect(folded).not.toHaveTextContent('A3')
  })

  const attempt = (attemptId: number, quizTitle: string, over: Record<string, unknown> = {}) => ({
    attemptId, quizId: attemptId, quizTitle, status: 'GRADED', startedAt: '2026-11-06T11:00:00Z',
    submittedAt: '2026-11-06T11:40:00Z', score: 16, mode: 'TEST', maxScore: 30, newResult: false, ...over,
  })

  it('lists the tests once, under a heading, with practice folded away and no second table of grades', async () => {
    api.getChildProgress.mockResolvedValue([
      { attemptId: 1, quizId: 1, quizTitle: 'Calcul algebric', submittedAt: '2026-11-06T11:40:00Z', score: 16, maxScore: 30, percent: 53 },
    ])
    api.getChildAttempts.mockResolvedValue([
      attempt(1, 'Calcul algebric'),
      attempt(2, 'Numere reale', { mode: 'PRACTICE', score: null, status: 'SUBMITTED' }),
    ])
    renderPage()

    const tests = await screen.findByRole('region', { name: 'Testele date' })
    const practice = within(tests).getByTestId('child-practice')
    const unfolded = within(tests).getAllByTestId('child-attempt').filter((card) => !practice.contains(card))
    expect(unfolded).toHaveLength(1)
    expect(unfolded[0]).toHaveTextContent('Calcul algebric')
    expect(practice).not.toHaveAttribute('open')
    expect(practice).toHaveTextContent('Exersări (1)')
    expect(practice).toHaveTextContent('Numere reale')
    // The chart stays; its table would repeat the list above word for word.
    expect(await screen.findByTestId('progress-chart')).toBeInTheDocument()
    expect(screen.queryByTestId('progress-row')).not.toBeInTheDocument()
  })

  it("shows the child's level per chapter", async () => {
    api.getChildMastery.mockResolvedValue([{
      chapterId: 1, chapterTitle: 'Fracții', bookTitle: 'Algebra', schoolClassName: 'Clasa a 8-a',
      earned: 9, possible: 10, percent: 90, questions: 4,
    }])
    renderPage()

    const section = await screen.findByRole('region', { name: 'Pe capitole' })
    expect(section).toHaveTextContent('Fracții')
    expect(section).toHaveTextContent('90%')
    expect(api.getChildMastery).toHaveBeenCalledWith(4)
  })

  it("is titled with the child's name", async () => {
    renderPage()
    expect(await screen.findByRole('heading', { level: 1, name: 'Ana Student' })).toBeInTheDocument()
    await waitFor(() => expect(document.title).toBe('Ana Student · Math LMS')) // set by an effect after the render
  })

  it("lists the child's homework with its state and deadline, without buttons to take it", async () => {
    api.getChildAssignments.mockResolvedValue([
      homework(1, 'Fracții', { overdue: true }),
      homework(2, 'Ecuații', { state: 'IN_PROGRESS' }),
      homework(3, 'Simulare EN', { state: 'GRADED', attemptId: 101 }),
    ])
    api.getChildAttempts.mockResolvedValue([{
      attemptId: 101, quizId: 3, quizTitle: 'Simulare EN', status: 'GRADED', startedAt: '2026-10-04T08:00:00Z',
      submittedAt: '2026-10-04T09:00:00Z', score: 8, mode: 'TEST', maxScore: 11, newResult: false,
    }])
    renderPage()

    const section = await screen.findByRole('region', { name: 'Teme' })
    const cards = within(section).getAllByTestId('assignment-card')
    expect(cards).toHaveLength(3)
    expect(cards[0]).toHaveTextContent('Termen depășit')
    expect(cards[1]).toHaveTextContent('În lucru')
    expect(within(section).queryByRole('link', { name: /Începe|Continuă/ })).not.toBeInTheDocument()
    expect(within(cards[2]).getByRole('link', { name: 'Vezi rezultatul' }))
      .toHaveAttribute('href', '/parent/children/4/attempts/101')
    await waitFor(() => expect(within(cards[2]).getByTestId('assignment-grade')).toHaveTextContent('nota 7,55'))
    expect(api.getChildAssignments).toHaveBeenCalledWith(4)
  })

  it('says so when the child has no homework', async () => {
    renderPage()
    expect(await screen.findByText('Nicio temă dată clasei copilului.')).toBeInTheDocument()
  })

  it("shows the server's refusal for a child that is not yours", async () => {
    api.getChildAssignments.mockRejectedValue(new ApiError(403, 'Acesta nu e copilul tău.'))
    api.getChildAttempts.mockRejectedValue(new ApiError(403, 'Acesta nu e copilul tău.'))
    renderPage()
    expect(await screen.findByText('Acesta nu e copilul tău.')).toBeInTheDocument()
  })
})