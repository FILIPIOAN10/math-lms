import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ApiError } from '@/lib/api'
import { WhyWrongButton } from './WhyWrongButton'
import { MistakeNote } from './MistakeNote'

const api = vi.hoisted(() => ({ askWhyWrong: vi.fn() }))
vi.mock('@/lib/api', async (original) => ({ ...(await original<typeof import('@/lib/api')>()), ...api }))

describe('WhyWrongButton', () => {
  beforeEach(() => {
    api.askWhyWrong.mockReset().mockResolvedValue(undefined)
  })

  it('asks the teacher and says the explanation will appear here', async () => {
    render(<WhyWrongButton attemptId={5} itemId={9} requested={false} />)

    await userEvent.click(screen.getByRole('button', { name: 'De ce am greșit?' }))

    expect(api.askWhyWrong).toHaveBeenCalledWith(5, 9)
    expect(await screen.findByTestId('why-wrong-requested')).toHaveTextContent(/profesorul/i)
    expect(screen.queryByRole('button', { name: 'De ce am greșit?' })).not.toBeInTheDocument()
  })

  it('remembers a request made before', () => {
    render(<WhyWrongButton attemptId={5} itemId={9} requested />)

    expect(screen.getByTestId('why-wrong-requested')).toBeInTheDocument()
    expect(screen.queryByRole('button')).not.toBeInTheDocument()
  })

  it('shows the server\'s reason when the request is refused', async () => {
    api.askWhyWrong.mockImplementation(() => Promise.reject(new ApiError(409, 'Explicațiile apar după ultima încercare la acest test.')))
    render(<WhyWrongButton attemptId={5} itemId={9} requested={false} />)

    await userEvent.click(screen.getByRole('button', { name: 'De ce am greșit?' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('Explicațiile apar după ultima încercare')
    expect(screen.getByRole('button', { name: 'De ce am greșit?' })).toBeInTheDocument()
  })
})

describe('MistakeNote', () => {
  it('links to the lesson the explanation comes from, at its page', () => {
    render(
      <MemoryRouter>
        <MistakeNote text="Ai adunat numitorii." lesson={{ chapterId: 3, chapterCode: 'A3', chapterTitle: 'Fracții', sectionId: 8, title: 'Adunarea fracțiilor', bookPage: 21 }} />
      </MemoryRouter>,
    )

    expect(screen.getByText('Ai adunat numitorii.')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: /Deschide lecția A3, pagina 21/ })).toHaveAttribute('href', '/capitole/3?sectiune=8')
  })

  it('has no link without a lesson', () => {
    render(<MistakeNote text="Ai adunat numitorii." />)

    expect(screen.queryByRole('link')).not.toBeInTheDocument()
  })
})
