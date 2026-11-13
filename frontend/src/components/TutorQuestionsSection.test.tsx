import { render, screen } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { TutorQuestionsSection } from './TutorQuestionsSection'

const api = vi.hoisted(() => ({ getClassTutorQuestions: vi.fn() }))
vi.mock('@/lib/api', async (original) => ({ ...(await original<typeof import('@/lib/api')>()), ...api }))

describe('TutorQuestionsSection', () => {
  beforeEach(() => api.getClassTutorQuestions.mockReset())

  it('lists what the class asked and the lessons did not answer, without names', async () => {
    api.getClassTutorQuestions.mockResolvedValue([
      { id: 2, question: 'Ce este un logaritm?', askedAt: '2026-11-13T09:15:00Z' },
      { id: 1, question: 'Cum aflu aria cercului?', askedAt: '2026-11-12T16:00:00Z' },
    ])

    render(<TutorQuestionsSection classId={4} />)

    expect(await screen.findByText('Ce este un logaritm?')).toBeInTheDocument()
    expect(screen.getByText('Cum aflu aria cercului?')).toBeInTheDocument()
    expect(api.getClassTutorQuestions).toHaveBeenCalledWith(4)
    expect(screen.getByRole('heading', { name: /fără răspuns în lecții/ })).toBeInTheDocument()
    expect(screen.getByText(/fără numele elevilor/)).toBeInTheDocument()
  })

  it('shows nothing when there is nothing to show or the list cannot be read', async () => {
    api.getClassTutorQuestions.mockResolvedValueOnce([])
    const { container, unmount } = render(<TutorQuestionsSection classId={4} />)
    await vi.waitFor(() => expect(api.getClassTutorQuestions).toHaveBeenCalled())
    expect(container).toBeEmptyDOMElement()
    unmount()

    api.getClassTutorQuestions.mockRejectedValueOnce(new Error('404'))
    const second = render(<TutorQuestionsSection classId={4} />)
    await vi.waitFor(() => expect(api.getClassTutorQuestions).toHaveBeenCalledTimes(2))
    expect(second.container).toBeEmptyDOMElement()
  })
})
