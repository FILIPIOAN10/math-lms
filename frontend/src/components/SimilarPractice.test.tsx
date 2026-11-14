import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { type SimilarPractice as SimilarPracticeDto } from '@/lib/api'
import { SimilarPractice } from './SimilarPractice'

const api = vi.hoisted(() => ({ getSimilar: vi.fn(), answerSimilar: vi.fn() }))
vi.mock('@/lib/api', async (original) => ({ ...(await original<typeof import('@/lib/api')>()), ...api }))

const lesson = { chapterId: 3, chapterCode: 'A1', chapterTitle: 'Ordinea operațiilor', sectionId: null, title: 'Ordinea operațiilor', bookPage: 12 }

const found: SimilarPracticeDto = {
  indexed: true,
  entries: [
    {
      kind: 'QUESTION',
      question: { itemId: 21, quizTitle: 'Simulare', statement: 'Calculează cinci plus doi ori trei.', options: [{ id: 1, position: 0, text: '11' }, { id: 2, position: 1, text: '21' }], box: 0, hints: [] },
      example: null,
    },
    {
      kind: 'EXAMPLE',
      question: null,
      example: { id: 7, chapterId: 3, statement: 'Exemplu: patru plus doi ori cinci.', solution: 'Patru plus zece face 14.', image: null, lesson },
    },
  ],
}

function renderPanel() {
  return render(
    <MemoryRouter>
      <SimilarPractice itemId={9} />
    </MemoryRouter>,
  )
}

describe('SimilarPractice', () => {
  beforeEach(() => {
    api.getSimilar.mockReset().mockResolvedValue(found)
    api.answerSimilar.mockReset().mockResolvedValue({ correct: true, correctOptionId: 1, solution: 'Întâi înmulțirea.', mistake: null })
  })

  it('asks for nothing until the student wants one', () => {
    renderPanel()

    expect(screen.getByRole('button', { name: 'Dă-mi una asemănătoare' })).toBeInTheDocument()
    expect(api.getSimilar).not.toHaveBeenCalled()
  })

  it('gives a similar grilă to answer, checked at once', async () => {
    renderPanel()
    await userEvent.click(screen.getByRole('button', { name: 'Dă-mi una asemănătoare' }))

    expect(api.getSimilar).toHaveBeenCalledWith(9)
    expect(await screen.findByText('Calculează cinci plus doi ori trei.')).toBeInTheDocument()
    expect(screen.getByText('Întrebarea 1 din 2')).toBeInTheDocument()
    await userEvent.click(screen.getByLabelText(/11/))
    await userEvent.click(screen.getByRole('button', { name: 'Verifică' }))

    expect(api.answerSimilar).toHaveBeenCalledWith(21, 1)
    expect(await screen.findByText('Întâi înmulțirea.')).toBeInTheDocument()
  })

  it('then a worked example, with its solution on request and its lesson', async () => {
    renderPanel()
    await userEvent.click(screen.getByRole('button', { name: 'Dă-mi una asemănătoare' }))
    await userEvent.click(await screen.findByLabelText(/11/))
    await userEvent.click(screen.getByRole('button', { name: 'Verifică' }))
    await userEvent.click(await screen.findByRole('button', { name: 'Următoarea' }))

    expect(screen.getByText('Exemplu: patru plus doi ori cinci.')).toBeInTheDocument()
    expect(screen.queryByText('Patru plus zece face 14.')).not.toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: 'Arată rezolvarea' }))
    expect(screen.getByText('Patru plus zece face 14.')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: /Deschide lecția A1, pagina 12/ })).toHaveAttribute('href', '/capitole/3')
  })

  it('says when nothing close enough was found', async () => {
    api.getSimilar.mockResolvedValue({ indexed: true, entries: [] })
    renderPanel()

    await userEvent.click(screen.getByRole('button', { name: 'Dă-mi una asemănătoare' }))

    expect(await screen.findByText(/Nu am găsit exerciții destul de apropiate/)).toBeInTheDocument()
  })

  it('says when the question has not been indexed yet', async () => {
    api.getSimilar.mockResolvedValue({ indexed: false, entries: [] })
    renderPanel()

    await userEvent.click(screen.getByRole('button', { name: 'Dă-mi una asemănătoare' }))

    expect(await screen.findByText(/Revino mâine/)).toBeInTheDocument()
  })
})
