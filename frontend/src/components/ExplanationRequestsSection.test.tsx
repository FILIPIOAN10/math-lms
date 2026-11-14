import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ExplanationRequestsSection } from './ExplanationRequestsSection'

const api = vi.hoisted(() => ({ listExplanationRequests: vi.fn() }))
vi.mock('@/lib/api', async (original) => ({ ...(await original<typeof import('@/lib/api')>()), ...api }))

function renderSection() {
  return render(<MemoryRouter><ExplanationRequestsSection /></MemoryRouter>)
}

describe('ExplanationRequestsSection', () => {
  beforeEach(() => {
    api.listExplanationRequests.mockReset()
  })

  it('lists the tests where students asked „De ce am greșit?”, each linked to its Statistici', async () => {
    api.listExplanationRequests.mockResolvedValue([
      { quizId: 4, quizTitle: 'Fracții', options: 2, students: 5, oldest: '2026-11-12T08:00:00Z' },
    ])

    renderSection()

    const link = await screen.findByRole('link', { name: /Fracții/ })
    expect(link).toHaveAttribute('href', '/admin/quizzes/4/stats')
    expect(link).toHaveTextContent('5 elevi')
    expect(link).toHaveTextContent('2 variante')
  })

  it('shows nothing when no one is waiting, or the list cannot be read', async () => {
    api.listExplanationRequests.mockResolvedValueOnce([])
    const first = renderSection()
    await vi.waitFor(() => expect(api.listExplanationRequests).toHaveBeenCalled())
    expect(first.container).toBeEmptyDOMElement()
    first.unmount()

    api.listExplanationRequests.mockRejectedValueOnce(new Error('403'))
    const second = renderSection()
    await vi.waitFor(() => expect(api.listExplanationRequests).toHaveBeenCalledTimes(2))
    expect(second.container).toBeEmptyDOMElement()
  })
})
