import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { AdminGradingPage } from '@/pages/AdminGradingPage'
import type { AdminAttemptDetail, AdminAttemptSummary } from '@/lib/api'

const api = vi.hoisted(() => ({
  listAttemptsForGrading: vi.fn(),
  getAttemptForGrading: vi.fn(),
  gradeOpenItem: vi.fn(),
  gradeOpenItemParts: vi.fn(),
  commentOnAttempt: vi.fn(),
  finalizeGrading: vi.fn(),
  listCommentSnippets: vi.fn(),
  createCommentSnippet: vi.fn(),
  deleteCommentSnippet: vi.fn(),
  markCommentSnippetUsed: vi.fn(),
}))
vi.mock('@/lib/api', async (original) => ({ ...(await original<typeof import('@/lib/api')>()), ...api }))

const row = (over: Partial<AdminAttemptSummary> = {}): AdminAttemptSummary => ({
  attemptId: 50, quizId: 1, quizTitle: 'Simulare EN', studentId: 4, studentName: 'Ana', status: 'SUBMITTED',
  submittedAt: '2026-10-09T10:00:00Z', score: null, maxScore: 11, ...over,
})
const detail = (over: Partial<AdminAttemptDetail> = {}): AdminAttemptDetail => ({
  attemptId: 50, quizTitle: 'Simulare EN', studentName: 'Ana', status: 'SUBMITTED', submittedAt: '2026-10-09T10:00:00Z',
  score: null, maxScore: 11, teacherComment: null,
  items: [{
    itemId: 101, position: 1, type: 'OPEN', statement: 'Rezolvă', points: 4, barem: null, selectedOptionText: null,
    correctOptionText: null, correct: null, awardedPoints: null, photoUploaded: false, photoIds: [], teacherComment: null,
  }],
  ...over,
})

function renderPage(path = '/admin/grading') {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <AdminGradingPage />
    </MemoryRouter>,
  )
}

async function openAttempt(d: AdminAttemptDetail = detail()) {
  api.getAttemptForGrading.mockResolvedValue(d)
  renderPage()
  await userEvent.click(await screen.findByTestId('attempt-open'))
  await screen.findByText('Rezolvă')
}

describe('AdminGradingPage', () => {
  beforeEach(() => {
    Object.values(api).forEach((fn) => fn.mockReset())
    api.listAttemptsForGrading.mockResolvedValue([row()])
    api.gradeOpenItem.mockResolvedValue(undefined)
    api.gradeOpenItemParts.mockResolvedValue(undefined)
    api.commentOnAttempt.mockResolvedValue(undefined)
    api.listCommentSnippets.mockResolvedValue([])
    api.markCommentSnippetUsed.mockResolvedValue(undefined)
  })

  it('shows graded papers as "x / max p · nota"', async () => {
    api.listAttemptsForGrading.mockResolvedValue([row({ status: 'GRADED', score: 8 })])
    renderPage()
    expect(await screen.findByText('8 / 11 p · nota 7,55')).toBeInTheDocument()
  })

  it('shows graded papers a page at a time, older ones on "Mai vechi"', async () => {
    const page = Array.from({ length: 50 }, (_, i) => row({ attemptId: 200 - i, status: 'GRADED', score: 5 }))
    api.listAttemptsForGrading.mockImplementation((status: string, before?: number) =>
      Promise.resolve(status === 'SUBMITTED' ? [] : before === undefined ? page : [row({ attemptId: 9, status: 'GRADED', score: 5 })]))
    renderPage()

    await userEvent.selectOptions(await screen.findByRole('combobox', { name: 'Ce lucrări arăți' }), 'GRADED')
    await userEvent.click(await screen.findByTestId('graded-older'))

    await waitFor(() => expect(api.listAttemptsForGrading).toHaveBeenCalledWith('GRADED', 151))
    expect(await screen.findAllByTestId('attempt-row')).toHaveLength(51)
    expect(screen.queryByTestId('graded-older')).not.toBeInTheDocument() // the last page was short
  })

  it('saves a comment for the student together with the points, with no Save button', async () => {
    await openAttempt()

    expect(screen.queryByTestId('grade-save')).not.toBeInTheDocument()
    await userEvent.type(screen.getByLabelText('Comentariu pentru elev (opțional)'), 'Ai uitat unitatea.')
    expect(api.gradeOpenItem).not.toHaveBeenCalled() // no points yet: nothing to save
    await userEvent.click(screen.getByRole('radio', { name: '3' }))

    await waitFor(() => expect(api.gradeOpenItem).toHaveBeenCalledWith(50, 101, 3, 'Ai uitat unitatea.'))
    expect(await screen.findByText('Salvat ✓')).toBeInTheDocument()
    expect(screen.getByRole('radio', { name: '3' })).toHaveAttribute('aria-checked', 'true')
  })

  it('saves a comment written after the points when the teacher leaves the box', async () => {
    await openAttempt()

    await userEvent.click(screen.getByRole('radio', { name: '4' }))
    await waitFor(() => expect(api.gradeOpenItem).toHaveBeenCalledWith(50, 101, 4, null))
    await userEvent.type(screen.getByLabelText('Comentariu pentru elev (opțional)'), 'Perfect')
    expect(api.gradeOpenItem).toHaveBeenCalledTimes(1) // not on every key
    await userEvent.tab()

    await waitFor(() => expect(api.gradeOpenItem).toHaveBeenLastCalledWith(50, 101, 4, 'Perfect'))
  })

  it('sends what is still being typed before asking to finalise', async () => {
    const d = detail()
    d.items[0] = { ...d.items[0], awardedPoints: 3 }
    api.finalizeGrading.mockResolvedValue(undefined)
    await openAttempt(d)

    await userEvent.type(screen.getByLabelText('Comentariu pentru elev (opțional)'), 'Bine')
    await userEvent.click(screen.getByTestId('grade-finalize'))

    await waitFor(() => expect(api.gradeOpenItem).toHaveBeenCalledWith(50, 101, 3, 'Bine'))
    expect(await screen.findByRole('dialog', { name: 'Finalizezi nota?' })).toBeInTheDocument()
  })

  it('keeps a failed save on screen and retries it', async () => {
    api.gradeOpenItem.mockRejectedValueOnce(Object.assign(new Error('x'), { status: 500, body: 'Serverul nu răspunde.' }))
    await openAttempt()

    await userEvent.click(screen.getByRole('radio', { name: '2' }))
    expect(await screen.findByText(/Nesalvat: Serverul nu răspunde/)).toBeInTheDocument()
    expect(screen.getByTestId('grade-finalize')).toBeDisabled()
    await userEvent.click(screen.getByRole('button', { name: 'Reîncearcă' }))

    expect(await screen.findByText('Salvat ✓')).toBeInTheDocument()
    expect(api.gradeOpenItem).toHaveBeenCalledTimes(2)
    expect(screen.getByTestId('grade-finalize')).toBeEnabled()
  })

  it('adds a saved comment with one tap and keeps a new one for next time', async () => {
    api.listCommentSnippets.mockResolvedValue([{ id: 7, text: 'Atenție la semn.', useCount: 2 }])
    api.createCommentSnippet.mockResolvedValue({ id: 8, text: 'Lipsește unitatea.', useCount: 0 })
    await openAttempt()

    await userEvent.click(await screen.findByRole('button', { name: 'Atenție la semn.' }))
    const box = screen.getByLabelText('Comentariu pentru elev (opțional)')
    expect(box).toHaveValue('Atenție la semn.')
    expect(api.markCommentSnippetUsed).toHaveBeenCalledWith(7)

    await userEvent.clear(box)
    await userEvent.type(box, 'Lipsește unitatea.')
    await userEvent.click(screen.getByRole('button', { name: '⭐ Salvează ca frecvent' }))
    expect(api.createCommentSnippet).toHaveBeenCalledWith('Lipsește unitatea.')
    expect(await screen.findByRole('button', { name: 'Lipsește unitatea.' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '⭐ E în comentariile frecvente' })).toBeDisabled()
  })

  it('starts from the comment already written', async () => {
    const d = detail()
    d.items[0] = { ...d.items[0], awardedPoints: 3, teacherComment: 'Scris deja' }
    await openAttempt(d)

    expect(screen.getByLabelText('Comentariu pentru elev (opțional)')).toHaveValue('Scris deja')
  })

  it('saves an overall comment on the paper', async () => {
    await openAttempt()

    await userEvent.type(screen.getByLabelText('Comentariu pentru toată lucrarea (opțional)'), 'Lucrare bună.')
    await userEvent.tab()

    await waitFor(() => expect(api.commentOnAttempt).toHaveBeenCalledWith(50, 'Lucrare bună.'))
    expect(await screen.findByText('✓ Comentariu salvat')).toBeInTheDocument()
  })

  it('lets the teacher comment on a paper that is already graded', async () => {
    api.listAttemptsForGrading.mockResolvedValue([row({ status: 'GRADED', score: 8 })])
    await openAttempt(detail({ status: 'GRADED', score: 8, teacherComment: 'Vechi' }))

    const box = screen.getByLabelText('Comentariu pentru toată lucrarea (opțional)')
    expect(box).toHaveValue('Vechi')
    expect(box).toBeEnabled()
  })

  it('asks in a dialog before finalising the grade', async () => {
    const d = detail()
    d.items[0] = { ...d.items[0], awardedPoints: 3 }
    api.finalizeGrading.mockResolvedValue(undefined)
    await openAttempt(d)

    await userEvent.click(screen.getByTestId('grade-finalize'))
    const dialog = await screen.findByRole('dialog', { name: 'Finalizezi nota?' })
    await userEvent.click(within(dialog).getByRole('button', { name: 'Finalizează' }))

    await waitFor(() => expect(api.finalizeGrading).toHaveBeenCalledWith(50))
  })

  it("opens the paper named in the address (a link from a student's page)", async () => {
    api.getAttemptForGrading.mockResolvedValue(detail())
    renderPage('/admin/grading?attempt=50')

    expect(await screen.findByText('Rezolvă')).toBeInTheDocument()
    expect(api.getAttemptForGrading).toHaveBeenCalledWith(50)
  })

  it('goes straight on to the next paper waiting, without the list', async () => {
    const graded = (id: number, name: string) => {
      const d = detail({ attemptId: id, studentName: name })
      d.items[0] = { ...d.items[0], awardedPoints: 3 }
      return d
    }
    api.listAttemptsForGrading.mockResolvedValue([row({ attemptId: 50 }), row({ attemptId: 51, studentName: 'Radu' })])
    api.getAttemptForGrading.mockImplementation((id: number) => Promise.resolve(graded(id, id === 50 ? 'Ana' : 'Radu')))
    api.finalizeGrading.mockResolvedValue(undefined)
    renderPage()
    await userEvent.click((await screen.findAllByTestId('attempt-open'))[0])

    api.listAttemptsForGrading.mockResolvedValue([row({ attemptId: 51, studentName: 'Radu' })])
    await userEvent.click(await screen.findByRole('button', { name: 'Finalizează și treci la următoarea (1 rămasă)' }))
    const dialog = await screen.findByRole('dialog', { name: 'Finalizezi nota?' })
    await userEvent.click(within(dialog).getByRole('button', { name: 'Finalizează și treci mai departe' }))

    expect(await screen.findByRole('heading', { name: 'Radu — Simulare EN' })).toBeInTheDocument()
    expect(api.finalizeGrading).toHaveBeenCalledWith(50)
    expect(api.getAttemptForGrading).toHaveBeenLastCalledWith(51)
  })

  it('offers no "next" button on the last paper waiting', async () => {
    const d = detail()
    d.items[0] = { ...d.items[0], awardedPoints: 3 }
    await openAttempt(d)

    expect(screen.getByTestId('grade-finalize')).toBeInTheDocument()
    expect(screen.queryByTestId('grade-finalize-next')).not.toBeInTheDocument()
  })

  it('offers to grade each quiz question by question', async () => {
    api.listAttemptsForGrading.mockResolvedValue([
      row({ attemptId: 50 }), row({ attemptId: 51, studentName: 'Radu' }), row({ attemptId: 60, quizId: 2, quizTitle: 'Fracții' }),
    ])
    renderPage()

    const groups = await screen.findAllByTestId('quiz-group')
    expect(groups.map((g) => g.textContent)).toEqual([
      expect.stringContaining('Simulare EN · 2 lucrări'),
      expect.stringContaining('Fracții · 1 lucrare'),
    ])
    api.getAttemptForGrading.mockImplementation((id: number) => Promise.resolve(detail({ attemptId: id })))
    await userEvent.click(within(groups[0]).getByRole('button', { name: 'Corectează pe întrebare' }))

    expect(await screen.findByRole('heading', { name: 'Simulare EN — pe întrebare' })).toBeInTheDocument()
    expect(api.getAttemptForGrading).toHaveBeenCalledWith(50)
    expect(api.getAttemptForGrading).toHaveBeenCalledWith(51)
  })

  it('grades an EN problem requirement by requirement and finalises only when each has points', async () => {
    const d = detail()
    d.items[0] = {
      ...d.items[0], points: 5,
      parts: [
        { id: 11, label: 'a', statement: 'Arată că $x=25$.', points: 2, barem: 'x = 25', awardedPoints: null },
        { id: 12, label: 'b', statement: 'Calculează.', points: 3, barem: null, awardedPoints: null },
      ],
    }
    await openAttempt(d)

    expect(screen.getByText('Barem a):')).toBeInTheDocument()
    const a = screen.getByRole('radiogroup', { name: 'Puncte cerința a)' })
    expect(within(a).getAllByRole('radio')).toHaveLength(3) // 0, 1, 2
    await userEvent.click(within(a).getByRole('radio', { name: '2' }))

    await waitFor(() => expect(api.gradeOpenItemParts).toHaveBeenCalledWith(50, 101,
      [{ partId: 11, points: 2 }, { partId: 12, points: null }], null))
    expect(api.gradeOpenItem).not.toHaveBeenCalled()
    expect(screen.getByTestId('part-total')).toHaveTextContent('Total: — / 5 p')
    expect(screen.getByText(/lipsește punctajul la b\)/)).toBeInTheDocument()
    expect(screen.getByTestId('grade-finalize')).toBeDisabled()

    const b = screen.getByRole('radiogroup', { name: 'Puncte cerința b)' })
    await userEvent.click(within(b).getByRole('radio', { name: '1' }))

    await waitFor(() => expect(screen.getByTestId('part-total')).toHaveTextContent('Total: 3 / 5 p'))
    expect(api.gradeOpenItemParts).toHaveBeenLastCalledWith(50, 101, [{ partId: 11, points: 2 }, { partId: 12, points: 1 }], null)
    expect(screen.getByTestId('grade-finalize')).toBeEnabled()
  })
})
