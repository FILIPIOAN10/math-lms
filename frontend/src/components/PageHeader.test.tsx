import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { describe, expect, it, vi } from 'vitest'
import { PageHeader } from '@/components/PageHeader'

function renderHeader(ui: React.ReactElement) {
  return render(<MemoryRouter>{ui}</MemoryRouter>)
}

describe('PageHeader', () => {
  it('shows the page title as the one heading, with its description and actions', () => {
    renderHeader(
      <PageHeader title="Teme" description="Quiz-uri date claselor, cu termen." actions={<button>Dă o temă</button>} />,
    )

    expect(screen.getByRole('heading', { level: 1, name: 'Teme' })).toBeInTheDocument()
    expect(screen.getByText('Quiz-uri date claselor, cu termen.')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Dă o temă' })).toBeInTheDocument()
  })

  it('names the browser tab after the page', () => {
    renderHeader(<PageHeader title="Corectură" />)
    expect(document.title).toBe('Corectură · Math LMS')
  })

  it('can name the tab differently from the heading', () => {
    renderHeader(<PageHeader title="Simulare EN" documentTitle="Simulare EN — editor" />)
    expect(document.title).toBe('Simulare EN — editor · Math LMS')
  })

  it('shows the way back as links, buttons or plain text, the last step marked as the current page', async () => {
    const back = vi.fn()
    renderHeader(
      <PageHeader
        title="Capitolul 1"
        breadcrumbs={[{ label: 'Quiz-uri', to: '/admin/quizzes' }, { label: 'Clasa a 9-a', onClick: back }, { label: 'Capitolul 1' }]}
      />,
    )

    const trail = screen.getByRole('navigation', { name: 'Unde ești' })
    expect(within(trail).getByRole('link', { name: 'Quiz-uri' })).toHaveAttribute('href', '/admin/quizzes')
    await userEvent.click(within(trail).getByRole('button', { name: 'Clasa a 9-a' }))
    expect(back).toHaveBeenCalled()
    expect(within(trail).getByText('Capitolul 1')).toHaveAttribute('aria-current', 'page')
  })
})
