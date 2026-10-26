import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { buttonVariants } from '@/components/ui/button'
import { Input } from '@/components/ui/input'

/* On a phone every button and box is at least 44 px tall (Apple / WCAG AAA target size); from 640 px up the compact
 * desktop sizes stay. A box's text is 16 px there, or iPhone zooms the page in when it gets focus. */
describe('touch targets on a phone', () => {
  it('makes the usual buttons 44 px tall below 640 px', () => {
    for (const size of ['default', 'sm', 'lg', 'icon', 'icon-sm', 'icon-lg'] as const) {
      expect(buttonVariants({ size })).toMatch(/max-sm:(h|size)-11/)
    }
  })

  it('makes text boxes 44 px tall with 16 px text below 640 px', () => {
    render(<Input aria-label="Email" />)
    expect(screen.getByLabelText('Email').className).toMatch(/max-sm:h-11/)
    expect(screen.getByLabelText('Email').className).toMatch(/max-sm:text-base/)
  })
})
