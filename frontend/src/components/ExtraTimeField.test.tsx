import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ExtraTimeField } from '@/components/ExtraTimeField'
import { ApiError } from '@/lib/api'

const api = vi.hoisted(() => ({ setStudentExtraTime: vi.fn() }))
vi.mock('@/lib/api', async (original) => ({ ...(await original<typeof import('@/lib/api')>()), ...api }))

describe('ExtraTimeField', () => {
  beforeEach(() => {
    api.setStudentExtraTime.mockReset()
  })

  it('saves the step as soon as it is picked and says from when it counts', async () => {
    api.setStudentExtraTime.mockResolvedValue(undefined)
    render(<ExtraTimeField studentId={7} initial={0} />)

    await userEvent.selectOptions(screen.getByLabelText(/Timp suplimentar/), '50')

    expect(api.setStudentExtraTime).toHaveBeenCalledWith(7, 50)
    expect(await screen.findByRole('status')).toHaveTextContent('Contează de la următorul test început.')
    expect(screen.getByLabelText(/Timp suplimentar/)).toHaveValue('50')
  })

  it('goes back to the saved value when the server refuses', async () => {
    api.setStudentExtraTime.mockImplementation(async () => {
      throw new ApiError(400, 'Timpul suplimentar poate fi doar 0, 25, 50 sau 100 %')
    })
    render(<ExtraTimeField studentId={7} initial={25} />)

    await userEvent.selectOptions(screen.getByLabelText(/Timp suplimentar/), '100')

    expect(await screen.findByRole('status')).toHaveTextContent('Timpul suplimentar poate fi doar')
    expect(screen.getByLabelText(/Timp suplimentar/)).toHaveValue('25')
  })
})
