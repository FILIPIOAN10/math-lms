import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'
import { PhotoPages } from '@/components/PhotoPages'

const alt = (page: number) => `Poza ${page}`

describe('PhotoPages, as the teacher grades (stacked)', () => {
  it('turns a sideways photo in place, without opening a window over the form', async () => {
    render(<PhotoPages stacked urls={['/p/1']} alt={alt} />)
    const image = screen.getByRole('img', { name: 'Poza 1' })
    expect(image).toHaveStyle({ transform: 'rotate(0deg)' })

    await userEvent.click(screen.getByRole('button', { name: 'Rotește poza 90°' }))

    expect(image).toHaveStyle({ transform: 'rotate(90deg)' })
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
  })

  it('switches to real size and back', async () => {
    render(<PhotoPages stacked urls={['/p/1']} alt={alt} />)
    const toggle = screen.getByRole('button', { name: '100%' })

    await userEvent.click(toggle)

    expect(screen.getByRole('button', { name: 'Potrivește' })).toHaveAttribute('aria-pressed', 'true')
  })

  it('has a link that opens the photo in a new tab', () => {
    render(<PhotoPages stacked urls={['/p/1']} alt={alt} />)
    const link = screen.getByRole('link', { name: 'Tab nou' })
    expect(link).toHaveAttribute('href', '/p/1')
    expect(link).toHaveAttribute('target', '_blank')
  })

  it('each page has its own turn', async () => {
    render(<PhotoPages stacked urls={['/p/1', '/p/2']} alt={alt} />)

    await userEvent.click(screen.getAllByRole('button', { name: 'Rotește poza 90°' })[1])

    expect(screen.getByRole('img', { name: 'Poza 1' })).toHaveStyle({ transform: 'rotate(0deg)' })
    expect(screen.getByRole('img', { name: 'Poza 2' })).toHaveStyle({ transform: 'rotate(90deg)' })
  })

  it('still opens the large viewer on a click on the photo', async () => {
    render(<PhotoPages stacked urls={['/p/1']} alt={alt} />)
    await userEvent.click(screen.getByRole('button', { name: 'Poza 1 — deschide mare' }))
    expect(screen.getByRole('dialog')).toBeInTheDocument()
  })
})

describe('PhotoPages as thumbnails', () => {
  it('has no controls', () => {
    render(<PhotoPages urls={['/p/1']} alt={alt} />)
    expect(screen.queryByRole('button', { name: 'Rotește poza 90°' })).not.toBeInTheDocument()
  })
})
