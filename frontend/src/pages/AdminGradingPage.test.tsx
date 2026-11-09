import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, useLocation, useNavigate } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { AdminGradingPage } from '@/pages/AdminGradingPage'
import type { AdminAttemptDetail, AdminAttemptSummary } from '@/lib/api'
import { NAV_COUNTS_CHANGED } from '@/lib/roles'

const api = vi.hoisted(() => ({
  listAttemptsForGrading: vi.fn(),
  getAttemptForGrading: vi.fn(),
  gradeOpenItem: vi.fn(),
  gradeOpenItemParts: vi.fn(),
  commentOnAttempt: vi.fn(),
  finalizeGrading: vi.fn(),
  reopenGrading: vi.fn(),
  listCommentSnippets: vi.fn(),
  createCommentSnippet: vi.fn(),
  deleteCommentSnippet: vi.fn(),
  markCommentSnippetUsed: vi.fn(),
  getAiGradingSettings: vi.fn(),
  getAiProposals: vi.fn(),
  requestAiProposal: vi.fn(),
  getAutoProposals: vi.fn(),
  setAutoProposals: vi.fn(),
  grantExtraAttempt: vi.fn(),
}))
vi.mock('@/lib/api', async (original) => ({ ...(await original<typeof import('@/lib/api')>()), ...api }))

const row = (over: Partial<AdminAttemptSummary> = {}): AdminAttemptSummary => ({
  attemptId: 50, quizId: 1, quizTitle: 'Simulare EN', studentId: 4, studentName: 'Ana', status: 'SUBMITTED',
  submittedAt: '2026-10-09T10:00:00Z', score: null, maxScore: 11, schoolClassName: 'Clasa a 8-a A', ...over,
})
const detail = (over: Partial<AdminAttemptDetail> = {}): AdminAttemptDetail => ({
  attemptId: 50, quizTitle: 'Simulare EN', studentName: 'Ana', status: 'SUBMITTED', submittedAt: '2026-10-09T10:00:00Z',
  score: null, maxScore: 11, teacherComment: null, attemptsAllowed: null, attemptsUsed: 1,
  items: [{
    itemId: 101, position: 1, type: 'OPEN', statement: 'Rezolvă', points: 4, barem: null, selectedOptionText: null,
    correctOptionText: null, correct: null, awardedPoints: null, photoUploaded: false, photoIds: [], teacherComment: null,
  }],
  ...over,
})

function Address() {
  const { pathname, search } = useLocation()
  const navigate = useNavigate()
  return (
    <>
      <p data-testid="address">{pathname + search}</p>
      <button onClick={() => navigate(-1)}>Back</button>
    </>
  )
}

function renderPage(path = '/admin/grading') {
  return render(
    <MemoryRouter initialEntries={['/', path]} initialIndex={1}>
      <AdminGradingPage />
      <Address />
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
    api.reopenGrading.mockResolvedValue(undefined)
    api.listCommentSnippets.mockResolvedValue([])
    api.markCommentSnippetUsed.mockResolvedValue(undefined)
    api.getAiGradingSettings.mockResolvedValue({ enabled: false, baremMaxPages: 0, baremDailyPageLimit: 0, baremPagesUsedToday: 0 })
    api.getAiProposals.mockResolvedValue([])
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

  it('shows how a comment with maths will look to the student, and nothing for plain words', async () => {
    await openAttempt()

    const box = screen.getByLabelText('Comentariu pentru elev (opțional)')
    await userEvent.type(box, 'Bine')
    expect(screen.queryByTestId('comment-preview')).not.toBeInTheDocument()
    await userEvent.type(box, ', dar $x^2-4$ se mai descompune.')

    const preview = await screen.findByTestId('comment-preview')
    expect(preview).toHaveTextContent('Așa îl vede elevul:')
    expect(preview.querySelector('.katex')).not.toBeNull()
  })

  it('shows the same preview under the comment for the whole paper', async () => {
    await openAttempt()

    await userEvent.type(screen.getByLabelText('Comentariu pentru toată lucrarea (opțional)'), 'Revezi $(a-b)^2$.')

    const previews = await screen.findAllByTestId('comment-preview')
    expect(previews).toHaveLength(1)
    expect(previews[0].querySelector('.katex')).not.toBeNull()
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

  it('sends what is still being typed before finalising', async () => {
    const d = detail()
    d.items[0] = { ...d.items[0], awardedPoints: 3 }
    api.finalizeGrading.mockResolvedValue(undefined)
    await openAttempt(d)

    await userEvent.type(screen.getByLabelText('Comentariu pentru elev (opțional)'), 'Bine')
    await userEvent.click(screen.getByTestId('grade-finalize'))

    await waitFor(() => expect(api.finalizeGrading).toHaveBeenCalledWith(50))
    expect(api.gradeOpenItem).toHaveBeenCalledWith(50, 101, 3, 'Bine')
    expect(api.gradeOpenItem.mock.invocationCallOrder[0]).toBeLessThan(api.finalizeGrading.mock.invocationCallOrder[0])
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

  it('finalises without a dialog and offers to take the grade back', async () => {
    const d = detail()
    d.items[0] = { ...d.items[0], awardedPoints: 3 }
    api.finalizeGrading.mockResolvedValue(undefined)
    await openAttempt(d)

    await userEvent.click(screen.getByTestId('grade-finalize'))

    const undo = await screen.findByTestId('grade-undo')
    expect(api.finalizeGrading).toHaveBeenCalledWith(50)
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
    expect(undo).toHaveTextContent('Ana: nota e finalizată')
    expect(await screen.findByRole('heading', { name: 'Corectură' })).toBeInTheDocument()

    await userEvent.click(within(undo).getByRole('button', { name: 'Anulează' }))

    await waitFor(() => expect(api.reopenGrading).toHaveBeenCalledWith(50))
    expect(await screen.findByText('Rezolvă')).toBeInTheDocument() // the paper is open again, to correct
    expect(screen.queryByTestId('grade-undo')).not.toBeInTheDocument()
  })

  it('tells the menu the count changed when a grade is finalised and when it is taken back', async () => {
    const d = detail()
    d.items[0] = { ...d.items[0], awardedPoints: 3 }
    api.finalizeGrading.mockResolvedValue(undefined)
    const changed = vi.fn()
    window.addEventListener(NAV_COUNTS_CHANGED, changed)
    await openAttempt(d)

    await userEvent.click(screen.getByTestId('grade-finalize'))
    await userEvent.click(within(await screen.findByTestId('grade-undo')).getByRole('button', { name: 'Anulează' }))

    await waitFor(() => expect(changed).toHaveBeenCalledTimes(2))
    window.removeEventListener(NAV_COUNTS_CHANGED, changed)
  })

  it('reopens a graded paper only after asking, and says what that does', async () => {
    api.listAttemptsForGrading.mockResolvedValue([row({ status: 'GRADED', score: 8 })])
    const d = detail({ status: 'GRADED', score: 8 })
    d.items[0] = { ...d.items[0], awardedPoints: 3 }
    await openAttempt(d)

    await userEvent.click(screen.getByTestId('grade-reopen'))
    const dialog = await screen.findByRole('dialog', { name: 'Redeschizi lucrarea?' })
    expect(dialog).toHaveTextContent('Nota dispare pentru elev')
    expect(dialog).toHaveTextContent('Jurnal')
    await userEvent.click(within(dialog).getByRole('button', { name: 'Anulează' }))
    expect(api.reopenGrading).not.toHaveBeenCalled()

    api.getAttemptForGrading.mockResolvedValue({ ...d, status: 'SUBMITTED', score: null })
    await userEvent.click(screen.getByTestId('grade-reopen'))
    await userEvent.click(within(await screen.findByRole('dialog', { name: 'Redeschizi lucrarea?' })).getByRole('button', { name: 'Redeschide' }))

    await waitFor(() => expect(api.reopenGrading).toHaveBeenCalledWith(50))
    expect(await screen.findByTestId('grade-finalize')).toBeEnabled() // grading again, its points still there
    expect(screen.queryByTestId('grade-reopen')).not.toBeInTheDocument()
  })

  it('offers no reopening on a paper still to grade', async () => {
    await openAttempt()
    expect(screen.queryByTestId('grade-reopen')).not.toBeInTheDocument()
  })

  it('has nothing to reopen on a graded test of grile only, the server marked it', async () => {
    api.listAttemptsForGrading.mockResolvedValue([row({ status: 'GRADED', score: 4 })])
    const d = detail({ status: 'GRADED', score: 4 })
    d.items[0] = { ...d.items[0], type: 'SINGLE_CHOICE', awardedPoints: 4, correct: true, selectedOptionText: 'x' }
    await openAttempt(d)

    expect(screen.queryByTestId('grade-reopen')).not.toBeInTheDocument()
  })

  it('shows why the grade could not be taken back', async () => {
    api.listAttemptsForGrading.mockResolvedValue([row({ status: 'GRADED', score: 8 })])
    const d = detail({ status: 'GRADED', score: 8 })
    d.items[0] = { ...d.items[0], awardedPoints: 3 }
    api.reopenGrading.mockRejectedValue(Object.assign(new Error('x'), { status: 400, body: 'Se poate redeschide doar o lucrare notată.' }))
    await openAttempt(d)

    await userEvent.click(screen.getByTestId('grade-reopen'))
    await userEvent.click(within(await screen.findByRole('dialog')).getByRole('button', { name: 'Redeschide' }))

    expect(await screen.findByText('Se poate redeschide doar o lucrare notată.')).toBeInTheDocument()
  })

  it("opens the paper named in the address (a link from a student's page)", async () => {
    api.getAttemptForGrading.mockResolvedValue(detail())
    renderPage('/admin/grading?attempt=50')

    expect(await screen.findByText('Rezolvă')).toBeInTheDocument()
    expect(api.getAttemptForGrading).toHaveBeenCalledWith(50)
  })

  it('gives 0 with one tap to an answer with no photo', async () => {
    await openAttempt()

    await userEvent.click(screen.getByTestId('grade-zero'))

    await waitFor(() => expect(api.gradeOpenItem).toHaveBeenCalledWith(50, 101, 0, null))
    expect(await screen.findByText('Salvat ✓')).toBeInTheDocument()
    expect(screen.getByTestId('grade-finalize')).toBeEnabled()
  })

  it('gives 0 to every requirement of a problem with no photo, in one tap', async () => {
    const d = detail()
    d.items[0] = {
      ...d.items[0],
      parts: [
        { id: 1, label: 'a', statement: 'Arată', points: 2, barem: null, awardedPoints: null },
        { id: 2, label: 'b', statement: 'Calculează', points: 3, barem: null, awardedPoints: null },
      ],
    }
    await openAttempt(d)

    await userEvent.click(screen.getByTestId('grade-zero'))

    await waitFor(() => expect(api.gradeOpenItemParts).toHaveBeenCalledWith(50, 101, [
      { partId: 1, points: 0 }, { partId: 2, points: 0 },
    ], null))
    expect(await screen.findByTestId('part-total')).toHaveTextContent('Total: 0 / 4 p')
  })

  it('keeps that shortcut off an answer with a photo', async () => {
    const withPhoto = detail()
    withPhoto.items[0] = { ...withPhoto.items[0], photoIds: [7], photoUploaded: true }
    await openAttempt(withPhoto)
    expect(screen.queryByTestId('grade-zero')).not.toBeInTheDocument()
  })

  it('has no such shortcut on a graded paper', async () => {
    api.listAttemptsForGrading.mockResolvedValue([row({ status: 'GRADED', score: 0 })])
    const d = detail({ status: 'GRADED', score: 0 })
    d.items[0] = { ...d.items[0], awardedPoints: 0 }
    await openAttempt(d)

    expect(screen.queryByTestId('grade-zero')).not.toBeInTheDocument()
  })

  describe('căutare în lucrări', () => {
    const papers = () => [
      row({ attemptId: 1, studentName: 'Ana Pop', quizTitle: 'Simulare EN', quizId: 1 }),
      row({ attemptId: 2, studentName: 'Ștefan Rus', quizTitle: 'Simulare EN', quizId: 1 }),
      row({ attemptId: 3, studentName: 'Radu Ene', quizTitle: 'Fracții', quizId: 2, schoolClassName: 'Clasa a 7-a B' }),
      row({ attemptId: 4, studentName: 'Mara Ilie', quizTitle: 'Fracții', quizId: 2, schoolClassName: 'Clasa a 7-a B' }),
      row({ attemptId: 5, studentName: 'Dan Toma', quizTitle: 'Procente', quizId: 3, schoolClassName: null }),
    ]

    it('names the class of each paper', async () => {
      renderPage()
      expect(await screen.findByTestId('attempt-class')).toHaveTextContent('Clasa a 8-a A')
    })

    it('finds a student, even typed without diacritics', async () => {
      api.listAttemptsForGrading.mockResolvedValue(papers())
      renderPage()

      await userEvent.type(await screen.findByRole('searchbox', { name: 'Caută elev, test sau clasă' }), 'stefan')

      expect(screen.getAllByTestId('attempt-row')).toHaveLength(1)
      expect(screen.getByTestId('attempt-row')).toHaveTextContent('Ștefan Rus')
      expect(within(screen.getByTestId('filter-bar')).getByRole('status')).toHaveTextContent('1 din 5')
    })

    it('narrows the queue to a class, and what "pe întrebare" grades with it', async () => {
      api.listAttemptsForGrading.mockResolvedValue(papers())
      renderPage()

      await userEvent.selectOptions(await screen.findByRole('combobox', { name: 'Clasa' }), 'Clasa a 7-a B')

      expect(screen.getAllByTestId('attempt-row')).toHaveLength(2)
      expect(screen.getByTestId('quiz-group')).toHaveTextContent('Fracții')
    })

    it('says when nothing matches, and clears from there', async () => {
      api.listAttemptsForGrading.mockResolvedValue(papers())
      renderPage()

      await userEvent.type(await screen.findByRole('searchbox', { name: 'Caută elev, test sau clasă' }), 'nimeni')
      expect(screen.getByTestId('no-matches')).toBeInTheDocument()

      await userEvent.click(within(screen.getByTestId('no-matches')).getByRole('button', { name: 'Șterge filtrele' }))
      expect(screen.getAllByTestId('attempt-row')).toHaveLength(5)
    })

    it('offers no search on a short queue', async () => {
      renderPage()
      await screen.findByTestId('attempt-row')
      expect(screen.queryByTestId('filter-bar')).not.toBeInTheDocument()
    })

    it('still goes on to the next paper of the whole queue while it is filtered', async () => {
      api.listAttemptsForGrading.mockResolvedValue(papers())
      const paper = (id: number) => {
        const d = detail({ attemptId: id, studentName: `Elev ${id}` })
        d.items[0] = { ...d.items[0], awardedPoints: 3 }
        return d
      }
      api.getAttemptForGrading.mockImplementation((id: number) => Promise.resolve(paper(id)))
      api.finalizeGrading.mockResolvedValue(undefined)
      renderPage()
      await userEvent.type(await screen.findByRole('searchbox', { name: 'Caută elev, test sau clasă' }), 'ana')
      await userEvent.click(screen.getByTestId('attempt-open'))

      expect(await screen.findByTestId('grade-finalize-next')).toHaveTextContent('(4 rămase)')
    })
  })

  it('offers to try again when the queue could not load', async () => {
    api.listAttemptsForGrading.mockRejectedValue(new TypeError('Failed to fetch'))
    renderPage()

    expect(await screen.findByTestId('error-state')).toHaveTextContent('Nu ne putem conecta la server')
    expect(screen.queryByText('Nicio lucrare de corectat. 🎉')).not.toBeInTheDocument()
  })

  it('offers to try again when the paper in the address could not load', async () => {
    api.getAttemptForGrading.mockRejectedValue(Object.assign(new Error('x'), { status: 500 }))
    renderPage('/admin/grading?attempt=50')

    expect(await screen.findByTestId('error-state')).toBeInTheDocument()
  })

  it('puts the open paper in the address, and Back returns to the queue', async () => {
    await openAttempt()
    expect(screen.getByTestId('address')).toHaveTextContent('/admin/grading?attempt=50')

    await userEvent.click(screen.getByRole('button', { name: 'Back' }))

    expect(await screen.findByRole('heading', { name: 'Corectură' })).toBeInTheDocument()
    expect(screen.getByTestId('address')).toHaveTextContent(/^\/admin\/grading$/)
    expect(screen.queryByText('Rezolvă')).not.toBeInTheDocument()
  })

  it('goes back to the queue from the breadcrumb, leaving the address clean', async () => {
    await openAttempt()

    await userEvent.click(screen.getByRole('button', { name: 'Corectură' }))

    expect(await screen.findByRole('heading', { name: 'Corectură' })).toBeInTheDocument()
    expect(screen.getByTestId('address')).toHaveTextContent(/^\/admin\/grading$/)
  })

  it('keeps the address on the next paper, so a refresh stays on it, and Back goes to the queue', async () => {
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
    await screen.findByRole('heading', { name: 'Ana — Simulare EN' })

    api.listAttemptsForGrading.mockResolvedValue([row({ attemptId: 51, studentName: 'Radu' })])
    api.getAttemptForGrading.mockClear()
    await userEvent.click(await screen.findByTestId('grade-finalize-next'))
    await screen.findByRole('heading', { name: 'Radu — Simulare EN' })

    await waitFor(() => expect(screen.getByTestId('address')).toHaveTextContent('/admin/grading?attempt=51'))
    expect(api.getAttemptForGrading).toHaveBeenCalledTimes(1) // not fetched again when the address caught up

    await userEvent.click(screen.getByRole('button', { name: 'Back' }))
    expect(await screen.findByRole('heading', { name: 'Corectură' })).toBeInTheDocument()
  })

  it('keeps the older paper off screen when a slow answer arrives after the address moved on', async () => {
    let answerOld: (d: AdminAttemptDetail) => void = () => undefined
    api.getAttemptForGrading.mockImplementation((id: number) => (id === 50
      ? new Promise((resolve) => { answerOld = resolve })
      : Promise.resolve(detail({ attemptId: 51, studentName: 'Radu' }))))
    api.listAttemptsForGrading.mockResolvedValue([row({ attemptId: 50 }), row({ attemptId: 51, studentName: 'Radu' })])
    renderPage()

    const [first, second] = await screen.findAllByTestId('attempt-open')
    await userEvent.click(first)
    await userEvent.click(second)
    await screen.findByRole('heading', { name: 'Radu — Simulare EN' })

    answerOld(detail({ attemptId: 50, studentName: 'Ana' }))
    await new Promise((resolve) => setTimeout(resolve, 0))

    expect(screen.getByRole('heading', { name: 'Radu — Simulare EN' })).toBeInTheDocument()
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

    expect(await screen.findByRole('heading', { name: 'Radu — Simulare EN' })).toBeInTheDocument()
    expect(screen.getByTestId('grade-undo')).toHaveTextContent('Ana') // still there, over the next paper
    expect(api.finalizeGrading).toHaveBeenCalledWith(50)
    expect(api.getAttemptForGrading).toHaveBeenLastCalledWith(51)
  })

  it('has one main finish button when more papers wait, the other a quiet "Doar finalizează"', async () => {
    const d = detail()
    d.items[0] = { ...d.items[0], awardedPoints: 3 }
    api.listAttemptsForGrading.mockResolvedValue([row(), row({ attemptId: 51, studentName: 'Radu' })])
    api.getAttemptForGrading.mockResolvedValue(d)
    renderPage()
    await userEvent.click((await screen.findAllByTestId('attempt-open'))[0])
    await screen.findByText('Rezolvă')

    const finish = screen.getByTestId('grade-finalize')
    expect(finish).toHaveTextContent('Doar finalizează')
    expect(finish.className).not.toMatch(/bg-primary/)
    expect(screen.getByTestId('grade-finalize-next').className).toMatch(/bg-primary/)
  })

  it('makes a finish button that cannot be pressed yet look switched off, and says why above it', async () => {
    await openAttempt()

    const finish = screen.getByTestId('grade-finalize')
    expect(finish).toBeDisabled()
    expect(finish.className).toMatch(/disabled:bg-muted/)
    expect(screen.getByTestId('grade-finalize-why')).toHaveTextContent('Notează toate întrebările cu rezolvare ca să poți finaliza.')
  })

  describe('AI proposals', () => {
    const enProblem = () => {
      const d = detail()
      d.items[0] = {
        ...d.items[0], points: 5, photoIds: [41], photoUploaded: true,
        parts: [
          { id: 11, label: 'a', statement: 'Perimetrul.', points: 2, barem: '$P = 130$ ... 2p', awardedPoints: null },
          { id: 12, label: 'b', statement: 'Aria.', points: 3, barem: '$A = 1000$ ... 3p', awardedPoints: null },
        ],
      }
      return d
    }
    const done = {
      itemId: 101, status: 'DONE' as const, points: 4, reason: 'Aproape complet.', confidence: 'MEDIUM' as const, error: null,
      parts: [
        { partId: 11, label: 'a', points: 2, reason: 'Corect.' },
        { partId: 12, label: 'b', points: 2, reason: 'Lipsește concluzia.' },
      ],
    }

    beforeEach(() => {
      api.getAiGradingSettings.mockResolvedValue({ enabled: true, baremMaxPages: 5, baremDailyPageLimit: 20, baremPagesUsedToday: 0 })
      api.getAutoProposals.mockResolvedValue({ auto: false, queued: 0 })
    })

    it("offers automatic proposals for the open paper's test, off by default", async () => {
      api.getAiProposals.mockResolvedValue([])
      api.setAutoProposals.mockResolvedValue({ auto: true, queued: 1 })
      await openAttempt(enProblem())

      const box = await screen.findByRole('checkbox', { name: 'Propuneri AI automate la acest test' })
      expect(api.getAutoProposals).toHaveBeenCalledWith(1)
      expect(box).not.toBeChecked()
      await userEvent.click(box)

      expect(api.setAutoProposals).toHaveBeenCalledWith(1, true)
      await waitFor(() => expect(api.getAiProposals).toHaveBeenCalledTimes(2)) // the paper's proposals read again
    })

    it('shows what the AI proposes next to each requirement, without grading anything', async () => {
      api.getAiProposals.mockResolvedValue([done])
      await openAttempt(enProblem())

      const panel = await screen.findByTestId('ai-proposal')
      expect(panel).toHaveTextContent('Propunere AI: 4 / 5 p')
      expect(panel).toHaveTextContent('încredere medie')
      expect(screen.getByText('Lipsește concluzia.')).toBeInTheDocument()
      expect(api.gradeOpenItemParts).not.toHaveBeenCalled()
    })

    it('saves the whole proposal with one click', async () => {
      api.getAiProposals.mockResolvedValue([done])
      await openAttempt(enProblem())

      await userEvent.click(await screen.findByRole('button', { name: 'Acceptă propunerea AI' }))

      await waitFor(() => expect(api.gradeOpenItemParts).toHaveBeenLastCalledWith(50, 101,
        [{ partId: 11, points: 2 }, { partId: 12, points: 2 }], null))
      expect(screen.getByTestId('part-total')).toHaveTextContent('Total: 4 / 5 p')
    })

    it('takes the proposed points of a requirement with Enter, and moves on', async () => {
      api.getAiProposals.mockResolvedValue([done])
      await openAttempt(enProblem())
      await screen.findByTestId('ai-proposal')
      const a = screen.getByRole('radiogroup', { name: 'Puncte cerința a)' })
      await waitFor(() => expect(a.contains(document.activeElement)).toBe(true))

      await userEvent.keyboard('{Enter}')
      await waitFor(() => expect(screen.getByRole('radiogroup', { name: 'Puncte cerința b)' }).contains(document.activeElement)).toBe(true))
      await userEvent.keyboard('3') // the teacher disagrees on b)

      await waitFor(() => expect(api.gradeOpenItemParts).toHaveBeenLastCalledWith(50, 101,
        [{ partId: 11, points: 2 }, { partId: 12, points: 3 }], null))
    })

    it('says the AI is still working, and shows the proposal once it is ready', async () => {
      api.getAiProposals
        .mockResolvedValueOnce([{ itemId: 101, status: 'RUNNING', points: null, parts: [], reason: null, confidence: null, error: null }])
        .mockResolvedValue([done])
      render(<MemoryRouter initialEntries={['/admin/grading']}><AdminGradingPage aiPollMs={0} /></MemoryRouter>)
      api.getAttemptForGrading.mockResolvedValue(enProblem())
      await userEvent.click(await screen.findByTestId('attempt-open'))

      expect(await screen.findByText(/AI-ul citește rezolvarea/)).toBeInTheDocument()
      expect(await screen.findByText('Propunere AI: 4 / 5 p')).toBeInTheDocument()
      const calls = api.getAiProposals.mock.calls.length
      await new Promise((r) => setTimeout(r, 50))
      expect(api.getAiProposals.mock.calls.length).toBe(calls) // nothing left to wait for: no more asking
    })

    it('says why a proposal failed and asks again', async () => {
      api.getAiProposals.mockResolvedValue([{ itemId: 101, status: 'FAILED', points: null, parts: [], reason: null,
        confidence: null, error: 'Google nu răspunde acum.' }])
      api.requestAiProposal.mockResolvedValue({ itemId: 101, status: 'QUEUED', points: null, parts: [], reason: null, confidence: null, error: null })
      await openAttempt(enProblem())

      expect(await screen.findByTestId('ai-proposal')).toHaveTextContent('Google nu răspunde acum.')
      await userEvent.click(screen.getByRole('button', { name: 'Cere din nou' }))

      expect(api.requestAiProposal).toHaveBeenCalledWith(50, 101)
      expect(await screen.findByText(/AI-ul citește rezolvarea/)).toBeInTheDocument()
    })

    it('offers to ask the AI for a photographed answer with a barem that has no proposal yet', async () => {
      api.requestAiProposal.mockResolvedValue({ itemId: 101, status: 'QUEUED', points: null, parts: [], reason: null, confidence: null, error: null })
      await openAttempt(enProblem())

      await userEvent.click(await screen.findByRole('button', { name: 'Cere propunere AI' }))

      expect(api.requestAiProposal).toHaveBeenCalledWith(50, 101)
    })

    it('shows nothing about the AI while it is off', async () => {
      api.getAiGradingSettings.mockResolvedValue({ enabled: false, baremMaxPages: 0, baremDailyPageLimit: 0, baremPagesUsedToday: 0 })
      await openAttempt(enProblem())

      expect(screen.queryByTestId('ai-proposal')).not.toBeInTheDocument()
      expect(screen.queryByRole('button', { name: 'Cere propunere AI' })).not.toBeInTheDocument()
    })
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

  it('grades a whole EN problem from the keyboard: the first points are ready, each digit moves on', async () => {
    const d = detail()
    d.items[0] = {
      ...d.items[0], points: 5,
      parts: [
        { id: 11, label: 'a', statement: 'Arată că $x=25$.', points: 2, barem: null, awardedPoints: null },
        { id: 12, label: 'b', statement: 'Calculează.', points: 3, barem: null, awardedPoints: null },
      ],
    }
    await openAttempt(d)

    const a = screen.getByRole('radiogroup', { name: 'Puncte cerința a)' })
    await waitFor(() => expect(a.contains(document.activeElement)).toBe(true))
    await userEvent.keyboard('2')
    await waitFor(() => expect(screen.getByRole('radiogroup', { name: 'Puncte cerința b)' }).contains(document.activeElement)).toBe(true))
    await userEvent.keyboard('1')

    await waitFor(() => expect(api.gradeOpenItemParts).toHaveBeenLastCalledWith(50, 101,
      [{ partId: 11, points: 2 }, { partId: 12, points: 1 }], null))
    expect(screen.getByTestId('part-total')).toHaveTextContent('Total: 3 / 5 p')
    expect(screen.getByText(/0–9/)).toBeInTheDocument() // the shortcut is written on the page
  })

  it('starts the keyboard on the first answer still without points', async () => {
    const d = detail()
    d.items = [
      { ...d.items[0], itemId: 101, statement: 'Rezolvă', awardedPoints: 2 },
      { ...d.items[0], itemId: 102, position: 2, statement: 'A doua', awardedPoints: null },
    ]
    await openAttempt(d)

    const groups = screen.getAllByRole('radiogroup', { name: 'Puncte acordate' })
    await waitFor(() => expect(groups[1].contains(document.activeElement)).toBe(true))
  })

  it('puts the photo beside the barem and the points, not above them', async () => {
    await openAttempt(detail({
      items: [{ ...detail().items[0], photoIds: [7], photoUploaded: true, barem: 'x = 3' }],
    }))

    const split = screen.getByTestId('grade-split')
    const [photoColumn, gradeColumn] = Array.from(split.children)
    expect(within(photoColumn as HTMLElement).getByTestId('photo-pages')).toBeInTheDocument()
    expect(within(gradeColumn as HTMLElement).getByText('Barem')).toBeInTheDocument()
    expect(within(gradeColumn as HTMLElement).getByRole('radiogroup', { name: 'Puncte acordate' })).toBeInTheDocument()
    expect(split.className).toMatch(/lg:grid-cols-2/)
  })

  it('gives the student one more attempt at a test with a limit, and says so', async () => {
    api.grantExtraAttempt.mockResolvedValue({ attemptsAllowed: 2 })
    await openAttempt(detail({ attemptsAllowed: 1, attemptsUsed: 1 }))

    expect(screen.getByTestId('attempts-line')).toHaveTextContent('Încercări: 1 din 1')
    await userEvent.click(screen.getByRole('button', { name: '+1 încercare pentru Ana' }))

    expect(api.grantExtraAttempt).toHaveBeenCalledWith(50)
    await waitFor(() => expect(screen.getByTestId('attempts-line')).toHaveTextContent('Încercări: 1 din 2'))
    expect(screen.getByText('Ana mai poate da testul o dată.')).toBeInTheDocument()
  })

  it('offers no extra attempt on a test without a limit', async () => {
    await openAttempt(detail({ attemptsAllowed: null }))

    expect(screen.queryByTestId('attempts-line')).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /\+1 încercare/ })).not.toBeInTheDocument()
  })
})
