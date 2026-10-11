import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { GradeByItem } from '@/components/GradeByItem'
import type { AdminAttemptDetail, AdminItemReview } from '@/lib/api'

const item = (itemId: number, over: Partial<AdminItemReview> = {}): AdminItemReview => ({
  itemId, position: itemId, type: 'OPEN', statement: `Enunț ${itemId}`, points: 5, barem: `Barem ${itemId}`,
  selectedOptionText: null, correctOptionText: null, correct: null, awardedPoints: null, photoUploaded: true,
  teacherComment: null, ...over,
})

// Each paper: a grilă first (auto-graded), then two open questions — shown to students as "Subiectul 2" and "3".
function paper(attemptId: number, studentName: string, points: [number | null, number | null] = [null, null]): AdminAttemptDetail {
  return {
    attemptId, quizTitle: 'Simulare EN', studentName, status: 'SUBMITTED', submittedAt: '2026-10-11T10:00:00Z',
    score: null, maxScore: 12, teacherComment: null,
    items: [
      item(1, { type: 'SINGLE_CHOICE', points: 2, awardedPoints: 2, correct: true, photoUploaded: false }),
      item(2, { awardedPoints: points[0] }),
      item(3, { awardedPoints: points[1] }),
    ],
  }
}

function setup(papers: AdminAttemptDetail[]) {
  const store = new Map(papers.map((p) => [p.attemptId, p]))
  const getAttemptForGrading = vi.fn((id: number) => Promise.resolve(store.get(id)!))
  const gradeOpenItem = vi.fn((attemptId: number, itemId: number, points: number) => {
    const p = store.get(attemptId)!
    store.set(attemptId, { ...p, items: p.items.map((i) => (i.itemId === itemId ? { ...i, awardedPoints: points } : i)) })
    return Promise.resolve()
  })
  const finalizeGrading = vi.fn(() => Promise.resolve())
  const onExit = vi.fn()
  render(
    <GradeByItem
      quizTitle="Simulare EN" attemptIds={papers.map((p) => p.attemptId)} onExit={onExit}
      getAttemptForGrading={getAttemptForGrading} gradeOpenItem={gradeOpenItem} finalizeGrading={finalizeGrading}
      attemptPhotoUrl={(a, i) => `/photo/${a}/${i}`}
    />,
  )
  return { getAttemptForGrading, gradeOpenItem, finalizeGrading, onExit }
}

describe('GradeByItem', () => {
  it('names the open questions as the students saw them, with how many papers have points', async () => {
    setup([paper(50, 'Ana', [4, null]), paper(51, 'Radu')])

    const tabs = within(await screen.findByRole('navigation', { name: 'Subiecte' })).getAllByRole('button')
    expect(tabs.map((t) => t.textContent)).toEqual(['Subiectul 2 (1/2)', 'Subiectul 3 (0/2)'])
  })

  it('starts on the first question still missing points, at the first paper without them', async () => {
    setup([paper(50, 'Ana', [4, null]), paper(51, 'Radu')])

    expect(await screen.findByRole('button', { name: 'Subiectul 2 (1/2)' })).toHaveAttribute('aria-pressed', 'true')
    expect(screen.getByTestId('answer-grader')).toHaveTextContent('Radu · lucrarea 2 din 2')
    expect(screen.getByText('Barem 2')).toBeInTheDocument()
    expect(screen.getByRole('img', { name: 'Rezolvarea lui Radu' })).toHaveAttribute('src', '/photo/51/2')
  })

  it('saves the points and moves on to the next paper of the same question', async () => {
    const user = userEvent.setup()
    const { gradeOpenItem } = setup([paper(50, 'Ana'), paper(51, 'Radu')])

    await user.type(await screen.findByLabelText('Puncte acordate'), '4')
    await user.type(screen.getByLabelText('Comentariu pentru elev (opțional)'), 'Bine')
    await user.click(screen.getByRole('button', { name: 'Salvează și următoarea' }))

    expect(gradeOpenItem).toHaveBeenCalledWith(50, 2, 4, 'Bine')
    expect(await screen.findByText(/Radu/)).toBeInTheDocument()
    expect(screen.getByTestId('answer-grader')).toHaveTextContent('lucrarea 2 din 2')
    expect(screen.getByRole('button', { name: 'Subiectul 2 (1/2)' })).toBeInTheDocument()
  })

  it('refuses points above what the question is worth', async () => {
    const user = userEvent.setup()
    const { gradeOpenItem } = setup([paper(50, 'Ana')])

    await user.type(await screen.findByLabelText('Puncte acordate'), '9')
    await user.click(screen.getByRole('button', { name: 'Salvează' }))

    expect(screen.getByText('Punctajul trebuie să fie un număr întreg între 0 și 5.')).toBeInTheDocument()
    expect(gradeOpenItem).not.toHaveBeenCalled()
  })

  it('finalises only the papers whose every open question has points', async () => {
    const user = userEvent.setup()
    const { finalizeGrading } = setup([paper(50, 'Ana', [4, 5]), paper(51, 'Radu', [3, null])])

    await user.click(await screen.findByRole('button', { name: 'Finalizează lucrările complete (1)' }))
    const dialog = await screen.findByRole('dialog', { name: 'Finalizezi 1 lucrare?' })
    await user.click(within(dialog).getByRole('button', { name: 'Finalizează' }))

    await waitFor(() => expect(finalizeGrading).toHaveBeenCalledTimes(1))
    expect(finalizeGrading).toHaveBeenCalledWith(50)
    expect(await screen.findByRole('status')).toHaveTextContent('1 lucrare finalizată')
    expect(screen.getByRole('button', { name: 'Subiectul 2 (1/1)' })).toBeInTheDocument()
  })

  it('says so when every paper is finalised', async () => {
    const user = userEvent.setup()
    setup([paper(50, 'Ana', [4, 5])])

    await user.click(await screen.findByRole('button', { name: 'Finalizează lucrările complete (1)' }))
    await user.click(within(await screen.findByRole('dialog')).getByRole('button', { name: 'Finalizează' }))

    expect(await screen.findByText('Toate lucrările acestui quiz sunt notate. 🎉')).toBeInTheDocument()
  })
})
