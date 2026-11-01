import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { Button, buttonVariants } from '@/components/ui/button'

describe('buttonVariants', () => {
  it('keeps the border of an outline button used on a link', () => {
    const classes = buttonVariants({ variant: 'outline', size: 'sm' }).split(' ')

    expect(classes).toContain('border-border')
    expect(classes).not.toContain('border-transparent')
  })

  it('gives a link and a real button the same classes', () => {
    render(<Button variant="outline" size="sm">Buton</Button>)

    expect(screen.getByRole('button', { name: 'Buton' }).className).toContain(buttonVariants({ variant: 'outline', size: 'sm' }))
  })

  it('lets a class passed in win over the variant', () => {
    expect(buttonVariants({ size: 'sm', className: 'h-11' }).split(' ')).toContain('h-11')
    expect(buttonVariants({ size: 'sm', className: 'h-11' }).split(' ')).not.toContain('h-7')
  })

  it('works with no options at all', () => {
    expect(buttonVariants()).toContain('bg-primary')
  })
})
