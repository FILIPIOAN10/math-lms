import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'
import { PhotoPages } from '@/components/PhotoPages'

const urls = ['/p/1', '/p/2', '/p/3']
const alt = (page: number) => `Rezolvarea, pagina ${page}`

describe('PhotoPages', () => {
  it('opens a page large on the same page and goes page to page', async () => {
    const user = userEvent.setup()
    render(<PhotoPages urls={urls} alt={alt} />)

    await user.click(screen.getByRole('button', { name: 'Rezolvarea, pagina 2 — deschide mare' }))
    const viewer = await screen.findByRole('dialog', { name: 'Pagina 2 din 3' })
    expect(within(viewer).getByTestId('photo-viewer-image')).toHaveAttribute('src', '/p/2')
    expect(within(viewer).getByRole('link', { name: 'Tab nou' })).toHaveAttribute('href', '/p/2')

    await user.click(within(viewer).getByRole('button', { name: 'Pagina următoare' }))
    expect(within(viewer).getByTestId('photo-viewer-image')).toHaveAttribute('src', '/p/3')
    expect(within(viewer).getByRole('button', { name: 'Pagina următoare' })).toBeDisabled()
    await user.keyboard('{ArrowLeft}')
    expect(within(viewer).getByTestId('photo-viewer-image')).toHaveAttribute('src', '/p/2')
  })

  it('turns a sideways photo only on screen, and remembers it per page', async () => {
    const user = userEvent.setup()
    render(<PhotoPages urls={urls} alt={alt} />)

    await user.click(screen.getByRole('button', { name: 'Rezolvarea, pagina 1 — deschide mare' }))
    const viewer = await screen.findByRole('dialog')
    await user.click(within(viewer).getByRole('button', { name: 'Rotește 90°' }))
    expect(within(viewer).getByTestId('photo-viewer-image').style.transform).toBe('rotate(90deg)')

    await user.click(within(viewer).getByRole('button', { name: 'Pagina următoare' }))
    expect(within(viewer).getByTestId('photo-viewer-image').style.transform).toBe('rotate(0deg)')
    await user.click(within(viewer).getByRole('button', { name: 'Pagina anterioară' }))
    expect(within(viewer).getByTestId('photo-viewer-image').style.transform).toBe('rotate(90deg)')

    await user.click(within(viewer).getByRole('button', { name: 'Închide' }))
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
  })
})
