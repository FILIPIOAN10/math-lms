import { fireEvent, render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { EnExamDateCard } from './EnExamDateCard'

const api = vi.hoisted(() => ({ getEnExamDate: vi.fn(), setEnExamDate: vi.fn() }))
vi.mock('@/lib/api', async (original) => ({ ...(await original<typeof import('@/lib/api')>()), ...api }))

describe('EnExamDateCard', () => {
  beforeEach(() => {
    api.getEnExamDate.mockReset()
    api.setEnExamDate.mockReset()
  })

  it('shows the date every plan counts down to and saves a new one', async () => {
    api.getEnExamDate.mockResolvedValue('2027-06-17')
    api.setEnExamDate.mockResolvedValue('2027-06-18')

    render(<EnExamDateCard />)

    const input = await screen.findByLabelText('Data EN')
    expect(input).toHaveValue('2027-06-17')
    fireEvent.change(input, { target: { value: '2027-06-18' } }) // a date field takes a whole date, not keystrokes
    await userEvent.click(screen.getByRole('button', { name: 'Salvează data' }))

    expect(api.setEnExamDate).toHaveBeenCalledWith('2027-06-18')
    expect(await screen.findByRole('status')).toHaveTextContent('Salvat')
  })

  it('says that there are no plans until the date is set', async () => {
    api.getEnExamDate.mockResolvedValue(null)

    render(<EnExamDateCard />)

    expect(await screen.findByText(/Fără dată, elevii nu au plan/)).toBeInTheDocument()
    expect(screen.getByLabelText('Data EN')).toHaveValue('')
  })

  it("shows the server's sentence when the date is refused", async () => {
    api.getEnExamDate.mockResolvedValue('2027-06-17')
    api.setEnExamDate.mockRejectedValue({ status: 400, body: 'Data EN nu poate fi în trecut.' })

    render(<EnExamDateCard />)

    const input = await screen.findByLabelText('Data EN')
    fireEvent.change(input, { target: { value: '2020-01-01' } })
    await userEvent.click(screen.getByRole('button', { name: 'Salvează data' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('Data EN nu poate fi în trecut.')
  })

  it('shows nothing when the date cannot be read', async () => {
    api.getEnExamDate.mockRejectedValue(new Error('500'))

    const { container } = render(<EnExamDateCard />)

    await vi.waitFor(() => expect(api.getEnExamDate).toHaveBeenCalled())
    expect(container).toBeEmptyDOMElement()
  })
})
