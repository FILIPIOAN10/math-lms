import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { AdminGradingPage } from '@/pages/AdminGradingPage'
import type { AdminAttemptDetail, AdminAttemptSummary } from '@/lib/api'

const api = vi.hoisted(() => ({
  listAttemptsForGrading: vi.fn(),
  getAttemptForGrading: vi.fn(),
  gradeOpenItem: vi.fn(),
  commentOnAttempt: vi.fn(),
  finalizeGrading: vi.fn(),
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
    correctOptionText: null, correct: null, awardedPoints: null, photoUploaded: false, teacherComment: null,
  }],
  ...over,
})

async function openAttempt(d: AdminAttemptDetail = detail()) {
  api.getAttemptForGrading.mockResolvedValue(d)
  render(<AdminGradingPage />)
  await userEvent.click(await screen.findByTestId('attempt-open'))
  await screen.findByText('Rezolvă')
}

describe('AdminGradingPage', () => {
  beforeEach(() => {
    Object.values(api).forEach((fn) => fn.mockReset())
    api.listAttemptsForGrading.mockResolvedValue([row()])
    api.gradeOpenItem.mockResolvedValue(undefined)
    api.commentOnAttempt.mockResolvedValue(undefined)
  })

  it('shows graded papers as "x / max p · nota"', async () => {
    api.listAttemptsForGrading.mockResolvedValue([row({ status: 'GRADED', score: 8 })])
    render(<AdminGradingPage />)
    expect(await screen.findByText('8 / 11 p · nota 7,55')).toBeInTheDocument()
  })

  it('saves a comment for the student together with the points', async () => {
    await openAttempt()

    await userEvent.type(screen.getByTestId('grade-points'), '3')
    await userEvent.type(screen.getByLabelText('Comentariu pentru elev (opțional)'), 'Ai uitat unitatea.')
    await userEvent.click(screen.getByTestId('grade-save'))

    await waitFor(() => expect(api.gradeOpenItem).toHaveBeenCalledWith(50, 101, 3, 'Ai uitat unitatea.'))
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
    await userEvent.click(screen.getByRole('button', { name: 'Salvează comentariul' }))

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
})
