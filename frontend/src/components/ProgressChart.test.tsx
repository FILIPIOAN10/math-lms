import { render, screen, within } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { ProgressChart } from '@/components/ProgressChart'
import type { ProgressPointDto } from '@/lib/api'

const point = (over: Partial<ProgressPointDto>): ProgressPointDto => ({
  attemptId: 1,
  quizId: 10,
  quizTitle: 'Simulare EN',
  submittedAt: '2026-10-04T10:00:00Z',
  score: 13,
  maxScore: 15,
  percent: 87,
  ...over,
})

describe('ProgressChart', () => {
  it('explains the empty state instead of drawing an empty chart', () => {
    render(<ProgressChart points={[]} />)

    expect(screen.getByTestId('progress-empty')).toHaveTextContent('Nu există încă teste notate')
    expect(screen.queryByRole('img')).not.toBeInTheDocument()
  })

  it('lists every graded attempt with score, max and percent', () => {
    render(
      <ProgressChart
        points={[
          point({ attemptId: 1, quizTitle: 'Test 1', score: 5, maxScore: 10, percent: 50 }),
          point({ attemptId: 2, quizTitle: 'Test 2', score: 13, maxScore: 15, percent: 87 }),
        ]}
      />,
    )

    const rows = screen.getAllByTestId('progress-row')
    expect(rows).toHaveLength(2)
    expect(within(rows[0]).getByText('Test 1')).toBeInTheDocument()
    expect(rows[0]).toHaveTextContent('5 / 10')
    expect(rows[0]).toHaveTextContent('(50%)')
    expect(rows[1]).toHaveTextContent('13 / 15')
    expect(rows[1]).toHaveTextContent('(87%)')
  })

  it('gives the chart an accessible summary with the average and the latest result', () => {
    render(<ProgressChart points={[point({ attemptId: 1, percent: 50 }), point({ attemptId: 2, percent: 90 })]} />)

    expect(screen.getByRole('img')).toHaveAccessibleName('Progres: 2 teste notate, media 70%, ultimul 90%')
  })

  it('draws one marker per attempt and a single attempt still renders', () => {
    const { container, rerender } = render(<ProgressChart points={[point({})]} />)
    expect(container.querySelectorAll('circle')).toHaveLength(1)

    rerender(<ProgressChart points={[point({ attemptId: 1 }), point({ attemptId: 2 }), point({ attemptId: 3 })]} />)
    expect(container.querySelectorAll('circle')).toHaveLength(3)
  })
})
