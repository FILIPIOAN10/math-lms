import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { MistakeEditor } from '@/components/MistakeEditor'
import type { OptionStat } from '@/lib/api'

const api = vi.hoisted(() => ({ saveOptionMistake: vi.fn(), suggestOptionMistake: vi.fn() }))
vi.mock('@/lib/api', async (original) => ({ ...(await original<typeof import('@/lib/api')>()), ...api }))

const option = (mistake: string | null = null): OptionStat => ({ id: 7, letter: 'b', text: '2/6', correct: false, chosen: 3, mistake })

describe('MistakeEditor', () => {
  beforeEach(() => {
    api.saveOptionMistake.mockReset().mockResolvedValue(undefined)
    api.suggestOptionMistake.mockReset()
  })

  it('saves what the teacher writes', async () => {
    const onSaved = vi.fn()
    render(<MistakeEditor option={option()} aiOn={false} onSaved={onSaved} />)

    await userEvent.click(screen.getByRole('button', { name: 'Ce greșeală ascunde?' }))
    await userEvent.type(screen.getByLabelText('Greșeala din spatele variantei b)'), 'Ai adunat numitorii.')
    await userEvent.click(screen.getByRole('button', { name: 'Salvează' }))

    expect(api.saveOptionMistake).toHaveBeenCalledWith(7, 'Ai adunat numitorii.')
    expect(onSaved).toHaveBeenCalled()
  })

  it('offers the AI only where the server has it', async () => {
    render(<MistakeEditor option={option()} aiOn={false} onSaved={vi.fn()} />)

    await userEvent.click(screen.getByRole('button', { name: 'Ce greșeală ascunde?' }))

    expect(screen.queryByRole('button', { name: 'Propune cu AI' })).not.toBeInTheDocument()
  })

  it('puts the proposal in the box and saves nothing until the teacher says so', async () => {
    api.suggestOptionMistake.mockResolvedValue('Propunerea AI.')
    render(<MistakeEditor option={option()} aiOn onSaved={vi.fn()} />)

    await userEvent.click(screen.getByRole('button', { name: 'Ce greșeală ascunde?' }))
    await userEvent.click(screen.getByRole('button', { name: 'Propune cu AI' }))

    expect(await screen.findByDisplayValue('Propunerea AI.')).toBeInTheDocument()
    expect(api.saveOptionMistake).not.toHaveBeenCalled()
  })

  it('says what went wrong with the AI and keeps the box open', async () => {
    api.suggestOptionMistake.mockRejectedValue(new Error('Gemini nu răspunde acum.'))
    render(<MistakeEditor option={option()} aiOn onSaved={vi.fn()} />)

    await userEvent.click(screen.getByRole('button', { name: 'Ce greșeală ascunde?' }))
    await userEvent.click(screen.getByRole('button', { name: 'Propune cu AI' }))

    expect(await screen.findByRole('alert')).toBeInTheDocument()
    expect(screen.getByLabelText('Greșeala din spatele variantei b)')).toBeInTheDocument()
  })

  it('shows what was written, ready to change', async () => {
    render(<MistakeEditor option={option('Ai adunat numitorii.')} aiOn={false} onSaved={vi.fn()} />)

    expect(screen.getByTestId('option-mistake')).toHaveTextContent('Ai adunat numitorii.')
    await userEvent.click(screen.getByRole('button', { name: 'Schimbă explicația' }))

    expect(screen.getByDisplayValue('Ai adunat numitorii.')).toBeInTheDocument()
  })
})
