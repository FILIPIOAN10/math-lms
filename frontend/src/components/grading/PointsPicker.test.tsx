import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { useState } from 'react'
import { describe, expect, it, vi } from 'vitest'
import { PointsPicker } from '@/components/grading/PointsPicker'

/** A picker that keeps its own value, the way ItemGradeForm does. */
function Controlled({ max, label, onChange }: { max: number; label: string; onChange: (p: number | null, commit: boolean) => void }) {
  const [value, setValue] = useState<number | null>(null)
  return (
    <PointsPicker
      max={max}
      value={value}
      label={label}
      testId={label}
      onChange={(points, commit) => {
        setValue(points)
        onChange(points, commit)
      }}
    />
  )
}

describe('PointsPicker', () => {
  it('picks the points typed on the keyboard and saves them at once', async () => {
    const onChange = vi.fn()
    render(<Controlled max={3} label="a" onChange={onChange} />)
    screen.getByRole('radio', { name: '0' }).focus()

    await userEvent.keyboard('2')

    expect(onChange).toHaveBeenCalledWith(2, true)
    expect(screen.getByRole('radio', { name: '2' })).toHaveAttribute('aria-checked', 'true')
  })

  it('ignores a digit above the maximum', async () => {
    const onChange = vi.fn()
    render(<Controlled max={2} label="a" onChange={onChange} />)
    screen.getByRole('radio', { name: '0' }).focus()

    await userEvent.keyboard('7')

    expect(onChange).not.toHaveBeenCalled()
    expect(screen.getByRole('radio', { name: '0' })).toHaveFocus()
  })

  it('moves on to the next points on the page after a digit, keeping the one just picked', async () => {
    const first = vi.fn()
    const second = vi.fn()
    render(
      <>
        <Controlled max={2} label="a" onChange={first} />
        <Controlled max={3} label="b" onChange={second} />
      </>,
    )
    screen.getAllByRole('radio', { name: '0' })[0].focus()

    await userEvent.keyboard('2')
    await waitFor(() => expect(screen.getByRole('radiogroup', { name: 'b' }).contains(document.activeElement)).toBe(true))
    await userEvent.keyboard('3')

    expect(first).toHaveBeenLastCalledWith(2, true) // leaving the group did not undo the 2
    expect(second).toHaveBeenLastCalledWith(3, true)
  })

  it('is a 44 px target on a phone', () => {
    render(<Controlled max={1} label="a" onChange={vi.fn()} />)
    expect(screen.getByRole('radio', { name: '1' }).className).toMatch(/\bh-11\b/)
  })
})
