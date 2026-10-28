import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { TutorHintsPanel } from '@/components/TutorHintsPanel'
import { ApiError, type TutorHints } from '@/lib/api'

const api = vi.hoisted(() => ({ suggestTutorHints: vi.fn() }))
vi.mock('@/lib/api', async (original) => ({ ...(await original<typeof import('@/lib/api')>()), ...api }))

const page = { code: 'AIS5P5', title: 'Ordinea operațiilor', bookPage: 12 }
const threeLevels: TutorHints = {
  answered: true,
  levels: [
    { level: 1, text: 'Întâi paranteza.', sources: [page] },
    { level: 2, text: 'Paranteza dă 5, apoi înmulțirea.', sources: [page] },
    { level: 3, text: 'Rezultatul este 10.', sources: [page, { ...page, bookPage: 13 }] },
  ],
  refusal: null, refusalReason: null, inputTokens: 1500, outputTokens: 400, costUsd: 0.00042,
}

function renderPanel(props: Partial<Parameters<typeof TutorHintsPanel>[0]> = {}) {
  const onAddHint = vi.fn()
  const onUseAsSolution = vi.fn()
  render(<TutorHintsPanel itemId={9} hintsLeft={5} canUseAsSolution onAddHint={onAddHint}
                          onUseAsSolution={onUseAsSolution} {...props} />)
  return { onAddHint, onUseAsSolution }
}

describe('TutorHintsPanel', () => {
  beforeEach(() => {
    api.suggestTutorHints.mockReset()
  })

  it('shows the three levels with their textbook pages and adds the ones the teacher keeps', async () => {
    api.suggestTutorHints.mockResolvedValue(threeLevels)
    const { onAddHint, onUseAsSolution } = renderPanel()

    await userEvent.click(screen.getByRole('button', { name: 'Sugerează indicii (AI)' }))

    expect(api.suggestTutorHints).toHaveBeenCalledWith(9)
    expect(await screen.findByRole('region', { name: 'Indiciu' })).toHaveTextContent('Întâi paranteza.')
    expect(screen.getByRole('region', { name: 'Rezolvare completă' }))
      .toHaveTextContent('Din manual: Ordinea operațiilor, pag. 12; Ordinea operațiilor, pag. 13')
    const addButtons = screen.getAllByRole('button', { name: 'Adaugă ca indiciu' })
    expect(addButtons).toHaveLength(3)

    await userEvent.click(addButtons[0])
    expect(onAddHint).toHaveBeenCalledWith('Întâi paranteza.')
    expect(screen.getByRole('button', { name: 'Adăugat' })).toBeDisabled() // not twice

    await userEvent.click(screen.getByRole('button', { name: 'Pune la rezolvare' }))
    expect(onUseAsSolution).toHaveBeenCalledWith('Rezultatul este 10.')
    expect(screen.getByText(/Elevii văd indiciile doar după ce le adaugi și salvezi/)).toHaveTextContent('Cost: ~0,0004 $.')
  })

  it('never offers the solution box when it is not free, and stops adding when the hints are full', async () => {
    api.suggestTutorHints.mockResolvedValue(threeLevels)
    renderPanel({ canUseAsSolution: false, hintsLeft: 0 })

    await userEvent.click(screen.getByRole('button', { name: 'Sugerează indicii (AI)' }))

    await screen.findByRole('region', { name: 'Indiciu' })
    expect(screen.queryByRole('button', { name: 'Pune la rezolvare' })).not.toBeInTheDocument()
    for (const button of screen.getAllByRole('button', { name: 'Adaugă ca indiciu' })) {
      expect(button).toBeDisabled()
    }
  })

  it('says plainly when the textbook does not cover the question', async () => {
    api.suggestTutorHints.mockResolvedValue({
      answered: false, levels: [], refusal: 'Nu am asta în manual.', refusalReason: 'too_far',
      inputTokens: 0, outputTokens: 0, costUsd: 0,
    } satisfies TutorHints)
    renderPanel()

    await userEvent.click(screen.getByRole('button', { name: 'Sugerează indicii (AI)' }))

    expect(await screen.findByText('Nu am asta în manual.')).toBeInTheDocument()
    expect(screen.getByText('Nicio pagină din manual nu e destul de aproape de ce cere întrebarea.')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Adaugă ca indiciu' })).not.toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Cere din nou (AI)' })).toBeEnabled()
  })

  it('shows the server message when the tutor is not reachable', async () => {
    api.suggestTutorHints.mockRejectedValue(new ApiError(503, 'Tutorul nu răspunde acum. Încearcă din nou peste câteva minute.'))
    renderPanel()

    await userEvent.click(screen.getByRole('button', { name: 'Sugerează indicii (AI)' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('Tutorul nu răspunde acum.')
  })
})
