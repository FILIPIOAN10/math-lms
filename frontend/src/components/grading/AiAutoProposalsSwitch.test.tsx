import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { AiAutoProposalsSwitch } from '@/components/grading/AiAutoProposalsSwitch'
import { ApiError } from '@/lib/api'

describe('AiAutoProposalsSwitch', () => {
  it('is off by default and turning it on asks for the papers already waiting', async () => {
    const user = userEvent.setup()
    const setAutoProposals = vi.fn().mockResolvedValue({ auto: true, queued: 3 })
    const onQueued = vi.fn()
    render(<AiAutoProposalsSwitch quizId={7} onQueued={onQueued}
      getAutoProposals={vi.fn().mockResolvedValue({ auto: false, queued: 0 })} setAutoProposals={setAutoProposals} />)

    const box = await screen.findByRole('checkbox', { name: 'Propuneri AI automate la acest test' })
    expect(box).not.toBeChecked()
    expect(screen.getByText(/ceri propunerea doar unde vrei/)).toBeInTheDocument()

    await user.click(box)

    expect(setAutoProposals).toHaveBeenCalledWith(7, true)
    expect(box).toBeChecked()
    expect(await screen.findByRole('status')).toHaveTextContent('Cerute acum pentru 3 răspunsuri care așteaptă nota.')
    expect(onQueued).toHaveBeenCalled()
  })

  it('turns off without asking for anything, and shows a refusal', async () => {
    const user = userEvent.setup()
    const setAutoProposals = vi.fn()
      .mockResolvedValueOnce({ auto: false, queued: 0 })
      .mockRejectedValueOnce(new ApiError(404, 'Testul nu există.'))
    const onQueued = vi.fn()
    render(<AiAutoProposalsSwitch quizId={7} onQueued={onQueued}
      getAutoProposals={vi.fn().mockResolvedValue({ auto: true, queued: 0 })} setAutoProposals={setAutoProposals} />)

    const box = await screen.findByRole('checkbox', { name: 'Propuneri AI automate la acest test' })
    await user.click(box)
    expect(box).not.toBeChecked()
    expect(onQueued).not.toHaveBeenCalled()

    await user.click(box)
    expect(await screen.findByText('Testul nu există.')).toBeInTheDocument()
  })
})
