import { render, screen, within } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import { AiUsageCard } from '@/components/AiUsageCard'

describe('AiUsageCard', () => {
  it("lists this month's calls and tokens per feature and model", async () => {
    const getAiUsage = vi.fn().mockResolvedValue({
      from: '2026-10-31T22:00:00Z',
      rows: [
        { feature: 'GRADING', model: 'gemini-4-argon', calls: 150, inputTokens: 600000, outputTokens: 450000 },
        { feature: 'IMPORT', model: 'gemini-3.8-flash', calls: 2, inputTokens: 6650, outputTokens: 3318 },
      ],
    })
    render(<AiUsageCard getAiUsage={getAiUsage} />)

    const table = await screen.findByRole('table', { name: 'Consum AI luna aceasta' })
    const grading = within(table).getByRole('row', { name: /Propuneri de notă/ })
    expect(grading).toHaveTextContent('gemini-4-argon')
    expect(grading).toHaveTextContent('150')
    expect(grading).toHaveTextContent(/600\.000/)
    expect(within(table).getByRole('row', { name: /Import din poze/ })).toHaveTextContent('3318'.replace(/(\d)(?=(\d{3})+$)/g, '$1.'))
  })

  it('shows nothing in a month without AI calls, or when the bill cannot be read', async () => {
    const { container, rerender } = render(<AiUsageCard getAiUsage={vi.fn().mockResolvedValue({ from: 'x', rows: [] })} />)
    await Promise.resolve()
    expect(container).toBeEmptyDOMElement()

    rerender(<AiUsageCard getAiUsage={vi.fn().mockRejectedValue(new Error('403'))} />)
    await Promise.resolve()
    expect(container).toBeEmptyDOMElement()
  })
})
