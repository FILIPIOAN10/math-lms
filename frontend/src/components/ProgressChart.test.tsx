import { render, screen, within } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
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

  it('writes the 1–10 grade on every point and leads each row with it (the column is "Nota")', () => {
    const { container } = render(
      <ProgressChart points={[
        point({ attemptId: 1, quizTitle: 'Test 1', score: 5, maxScore: 10, percent: 50 }),
        point({ attemptId: 2, quizTitle: 'Test 2', score: 13, maxScore: 15, percent: 87 }),
      ]} />,
    )

    expect([...container.querySelectorAll('[data-testid="point-grade"]')].map((t) => t.textContent)).toEqual(['5,50', '8,80'])
    expect(container.querySelectorAll('circle title')[1].textContent).toContain('nota 8,80')
    expect(screen.getAllByTestId('progress-row')[1]).toHaveTextContent(/8,80\s*13 \/ 15 \(87%\)/)
  })

  it('gives the chart an accessible summary in grades: the average and the latest', () => {
    render(<ProgressChart points={[
      point({ attemptId: 1, score: 5, maxScore: 10, percent: 50 }),
      point({ attemptId: 2, score: 9, maxScore: 10, percent: 90 }),
    ]} />)

    expect(screen.getByRole('img')).toHaveAccessibleName('Progres: 2 teste notate, media notelor 7,30, ultima notă 9,10')
  })

  it('reads in grades: the axis marks 1, 5, 7, 9 and 10, the bands of the grades, not percents', () => {
    const { container } = render(<ProgressChart points={[point({})]} />)

    const axis = [...container.querySelectorAll('[data-testid="axis-grade"]')].map((t) => t.textContent)
    expect(axis).toEqual(['1', '5', '7', '9', '10'])
    // No percent is drawn on the chart (the hover tooltip may still give it, with the points).
    expect([...container.querySelectorAll('svg text')].map((t) => t.textContent).join(' ')).not.toContain('%')
  })

  it('is drawn at the width it gets, so its labels keep their size on a phone instead of shrinking to 5 px', () => {
    class FakeResizeObserver {
      constructor(private readonly callback: ResizeObserverCallback) {}
      observe(target: Element) {
        this.callback([{ contentRect: { width: 311 } } as ResizeObserverEntry], this as unknown as ResizeObserver)
        void target
      }
      disconnect() {}
      unobserve() {}
    }
    vi.stubGlobal('ResizeObserver', FakeResizeObserver)
    try {
      const { container } = render(<ProgressChart points={[point({ attemptId: 1 }), point({ attemptId: 2 })]} />)

      expect(container.querySelector('svg')).toHaveAttribute('viewBox', '0 0 311 260')
      expect(container.querySelector('[data-testid="axis-grade"]')).toHaveClass('text-xs')
    } finally {
      vi.unstubAllGlobals()
    }
  })

  it('leaves its table out where the page lists the same tests with their grades already', () => {
    render(<ProgressChart points={[point({})]} table={false} />)

    expect(screen.getByRole('img')).toBeInTheDocument()
    expect(screen.queryByTestId('progress-row')).not.toBeInTheDocument()
  })

  it('draws one marker per attempt and a single attempt still renders', () => {
    const { container, rerender } = render(<ProgressChart points={[point({})]} />)
    expect(container.querySelectorAll('circle')).toHaveLength(1)

    rerender(<ProgressChart points={[point({ attemptId: 1 }), point({ attemptId: 2 }), point({ attemptId: 3 })]} />)
    expect(container.querySelectorAll('circle')).toHaveLength(3)
  })
})
