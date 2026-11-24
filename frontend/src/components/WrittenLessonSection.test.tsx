import { fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { WrittenLessonSection } from '@/components/WrittenLessonSection'
import type { WrittenLesson } from '@/lib/api'

const api = vi.hoisted(() => ({
  getWrittenLesson: vi.fn(),
  editWrittenLesson: vi.fn(),
  approveWrittenLesson: vi.fn(),
}))
vi.mock('@/lib/api', async (original) => ({ ...(await original<typeof import('@/lib/api')>()), ...api }))

const review = (over: Partial<WrittenLesson> = {}): WrittenLesson => ({
  exists: true,
  markdown: '## Ideea pe scurt\n\nAdunăm numere.',
  status: 'draft',
  editedByTeacher: false,
  redCount: 0,
  yellowCount: 1,
  model: 'gemini-x',
  approvedAt: null,
  updatedAt: '2026-11-24T10:00:00Z',
  claims: [
    { id: 'idea.1', where: 'Ideea pe scurt', text: 'Adunăm numere.', colour: 'green', reasons: [], sourceIds: ['EN-1'] },
    { id: 'formula.1', where: 'Formule și proprietăți', text: 'Comutativitatea.', colour: 'yellow',
      reasons: ['fără sursă (scris de model)'], sourceIds: [] },
  ],
  sources: [{ id: 'EN-1', kind: 'EN', label: 'Culegere EN, pagina 3', text: 'Adunarea numerelor.' }],
  ...over,
})

describe('WrittenLessonSection', () => {
  beforeEach(() => {
    Object.values(api).forEach((fn) => fn.mockReset())
  })

  it('shows a student the approved lesson and nothing when there is none', async () => {
    api.getWrittenLesson.mockResolvedValue({ exists: true, markdown: '## Ideea pe scurt\n\nAdunăm numere.' })
    const { unmount } = render(<WrittenLessonSection chapterId={1} teacher={false} />)

    expect(await screen.findByText('Adunăm numere.')).toBeInTheDocument()
    expect(screen.queryByText(/Ciornă|Aprob/)).not.toBeInTheDocument()
    unmount()

    api.getWrittenLesson.mockResolvedValue({ exists: false })
    render(<WrittenLessonSection chapterId={1} teacher={false} />)
    await waitFor(() => expect(api.getWrittenLesson).toHaveBeenCalledTimes(2))
    expect(screen.queryByRole('heading', { name: 'Lecția' })).not.toBeInTheDocument()
  })

  it('lets the teacher review each claim with its sources and approve once the yellow ones are read', async () => {
    api.getWrittenLesson.mockResolvedValue(review())
    api.approveWrittenLesson.mockResolvedValue(undefined)
    render(<WrittenLessonSection chapterId={1} teacher />)

    expect(await screen.findByText('Ciornă: 0 roșii, 1 de citit. Elevii n-o văd.')).toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: 'Revizuiește afirmațiile' }))
    const green = screen.getByTestId('claim-idea.1')
    expect(green).toHaveAttribute('data-colour', 'green')
    fireEvent.click(within(green).getByRole('button', { name: 'EN-1 · Culegere EN, pagina 3' }))
    expect(within(green).getByText('Adunarea numerelor.')).toBeInTheDocument()
    expect(within(screen.getByTestId('claim-formula.1')).getByText('fără sursă (scris de model)')).toBeInTheDocument()

    const approve = screen.getByRole('button', { name: 'Aprobă lecția' })
    expect(approve).toBeDisabled()
    fireEvent.click(screen.getByRole('checkbox', { name: 'Am citit afirmațiile galbene' }))
    api.getWrittenLesson.mockResolvedValue(review({ status: 'approved', approvedAt: '2026-11-24T11:00:00Z' }))
    fireEvent.click(approve)

    await waitFor(() => expect(api.approveWrittenLesson).toHaveBeenCalledWith(1, true))
    expect(await screen.findByText(/Aprobată\. Elevii o văd după ce arăți capitolul/)).toBeInTheDocument()
  })

  it('keeps a lesson with a red claim from being approved until the teacher corrects the text', async () => {
    api.getWrittenLesson.mockResolvedValue(review({ status: 'flagged', redCount: 1, yellowCount: 0 }))
    api.editWrittenLesson.mockResolvedValue(undefined)
    render(<WrittenLessonSection chapterId={1} teacher />)

    expect(await screen.findByText(/1 roșie/)).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Aprobă lecția' })).toBeDisabled()

    fireEvent.click(screen.getByRole('button', { name: 'Corectează textul' }))
    fireEvent.change(screen.getByRole('textbox', { name: 'Textul lecției' }), { target: { value: 'Corectat.' } })
    api.getWrittenLesson.mockResolvedValue(review({ status: 'flagged', redCount: 1, yellowCount: 0,
      editedByTeacher: true, markdown: 'Corectat.' }))
    fireEvent.click(screen.getByRole('button', { name: 'Salvează' }))

    await waitFor(() => expect(api.editWrittenLesson).toHaveBeenCalledWith(1, 'Corectat.'))
    expect(await screen.findByText(/Textul e corectat de tine/)).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Aprobă lecția' })).toBeEnabled()
  })
})
