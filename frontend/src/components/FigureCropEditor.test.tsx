import { fireEvent, render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { FigureCropEditor } from '@/components/FigureCropEditor'

const box = { x: 0.2, y: 0.2, w: 0.4, h: 0.4 }

function renderEditor() {
  const onDone = vi.fn()
  render(<FigureCropEditor src="/p/1" box={box} alt="Pagina 1 din manual" onDone={onDone} onCancel={() => {}} />)
  return onDone
}

/** The photo area as a 1000 × 1000 px square at the page's top-left corner, so a pixel is a thousandth of it. */
function sizeArea() {
  const area = screen.getByTestId('figure-box').parentElement!
  area.getBoundingClientRect = () => ({ left: 0, top: 0, width: 1000, height: 1000 }) as DOMRect
  return area
}

describe('FigureCropEditor', () => {
  it('moves the box with the arrows and resizes it with Shift + arrows', async () => {
    const user = userEvent.setup()
    const onDone = renderEditor()

    screen.getByTestId('figure-box').focus()
    await user.keyboard('{ArrowRight}{ArrowRight}{ArrowDown}')
    await user.keyboard('{Shift>}{ArrowRight}{ArrowUp}{/Shift}')
    await user.click(screen.getByRole('button', { name: 'Gata' }))

    const kept = onDone.mock.calls[0][0]
    expect(kept.x).toBeCloseTo(0.21)
    expect(kept.y).toBeCloseTo(0.205)
    expect(kept.w).toBeCloseTo(0.405)
    expect(kept.h).toBeCloseTo(0.395)
  })

  it('pulls a corner grip to resize, keeping the opposite corner in place', async () => {
    const user = userEvent.setup()
    const onDone = renderEditor()
    const area = sizeArea()

    fireEvent.pointerDown(screen.getByTestId('figure-handle-se'), { clientX: 600, clientY: 600 })
    fireEvent.pointerMove(area, { clientX: 900, clientY: 700 })
    fireEvent.pointerUp(area)
    await user.click(screen.getByRole('button', { name: 'Gata' }))

    expect(onDone.mock.calls[0][0]).toEqual({ x: 0.2, y: 0.2, w: expect.closeTo(0.7), h: expect.closeTo(0.5) })
  })

  it('drags the box from its middle to move it, same size', async () => {
    const user = userEvent.setup()
    const onDone = renderEditor()
    const area = sizeArea()

    fireEvent.pointerDown(screen.getByTestId('figure-box'), { clientX: 400, clientY: 400 })
    fireEvent.pointerMove(area, { clientX: 500, clientY: 350 })
    fireEvent.pointerUp(area)
    await user.click(screen.getByRole('button', { name: 'Gata' }))

    expect(onDone.mock.calls[0][0]).toEqual({ x: expect.closeTo(0.3), y: expect.closeTo(0.15), w: 0.4, h: 0.4 })
  })
})
