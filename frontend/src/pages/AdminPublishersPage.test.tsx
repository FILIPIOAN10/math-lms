import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { AdminPublishersPage } from '@/pages/AdminPublishersPage'
import type { Publisher } from '@/lib/api'

const api = vi.hoisted(() => ({
  listPublishers: vi.fn(),
  createPublisher: vi.fn(),
  updatePublisher: vi.fn(),
  deletePublisher: vi.fn(),
  setPublisherLogo: vi.fn(),
  removePublisherLogo: vi.fn(),
}))
vi.mock('@/lib/api', async (original) => ({ ...(await original<typeof import('@/lib/api')>()), ...api }))
vi.mock('@/lib/image', () => ({ shrinkImage: (file: File) => Promise.resolve(file) }))

const artKlett: Publisher = {
  id: 1, name: 'Art Klett', description: 'Manuale și culegeri', hasLogo: true, logoVersion: 5, quizCount: 2, books: [],
}
const litera: Publisher = { id: 2, name: 'Litera', description: null, hasLogo: false, logoVersion: 1, quizCount: 0, books: [] }

function renderPage() {
  return render(<MemoryRouter><AdminPublishersPage /></MemoryRouter>)
}

describe('AdminPublishersPage', () => {
  beforeEach(() => {
    Object.values(api).forEach((fn) => fn.mockReset())
    api.listPublishers.mockResolvedValue([artKlett, litera])
    URL.createObjectURL = vi.fn(() => 'blob:logo')
    URL.revokeObjectURL = vi.fn()
  })

  it('starts empty: no publisher is built in', async () => {
    api.listPublishers.mockResolvedValue([])
    renderPage()
    expect(await screen.findByText(/Nicio editură încă/)).toBeInTheDocument()
  })

  it('lists each publisher with its logo, description and how many tests use it', async () => {
    renderPage()
    const rows = await screen.findAllByTestId('publisher')
    expect(rows).toHaveLength(2)
    expect(within(rows[0]).getByText('Art Klett')).toBeInTheDocument()
    expect(within(rows[0]).getByText('Manuale și culegeri')).toBeInTheDocument()
    expect(within(rows[0]).getByText('nicio carte · 2 teste')).toBeInTheDocument()
    expect(within(rows[0]).getByRole('link', { name: 'Art Klett' })).toHaveAttribute('href', '/admin/publishers/1')
    expect(within(rows[0]).getByRole('img', { name: 'Sigla Art Klett' })).toHaveAttribute('src', '/api/admin/publishers/1/logo?v=5')
    expect(within(rows[1]).getByText('nicio carte · niciun test')).toBeInTheDocument()
  })

  it('adds a publisher with a description and a logo', async () => {
    const user = userEvent.setup()
    api.createPublisher.mockResolvedValue({ ...litera, id: 3, name: 'Intuitext' })
    renderPage()

    await user.click(await screen.findByRole('button', { name: 'Adaugă editură' }))
    await user.type(screen.getByLabelText('Numele editurii'), '  Intuitext ')
    await user.type(screen.getByLabelText('Descriere (opțional)'), 'Auxiliare EN')
    await user.upload(screen.getByLabelText('Siglă (opțional)'), new File(['x'], 'sigla.png', { type: 'image/png' }))
    await user.click(screen.getByRole('button', { name: 'Salvează' }))

    await waitFor(() => expect(api.createPublisher).toHaveBeenCalledWith({ name: 'Intuitext', description: 'Auxiliare EN' }))
    expect(api.setPublisherLogo).toHaveBeenCalledWith(3, expect.objectContaining({ name: 'sigla.png' }))
    expect(api.listPublishers).toHaveBeenCalledTimes(2) // reloaded
  })

  it('edits a publisher and can take its logo off', async () => {
    const user = userEvent.setup()
    api.updatePublisher.mockResolvedValue(artKlett)
    renderPage()

    const [row] = await screen.findAllByTestId('publisher')
    await user.click(within(row).getByRole('button', { name: 'Editează' }))
    const name = screen.getByLabelText('Numele editurii')
    expect(name).toHaveValue('Art Klett')
    await user.clear(name)
    await user.type(name, 'Art Klett România')
    await user.click(screen.getByRole('button', { name: 'Scoate sigla' }))
    await user.click(screen.getByRole('button', { name: 'Salvează' }))

    await waitFor(() => expect(api.updatePublisher).toHaveBeenCalledWith(1, {
      name: 'Art Klett România', description: 'Manuale și culegeri',
    }))
    expect(api.removePublisherLogo).toHaveBeenCalledWith(1)
  })

  it('says what deleting does to the tests that use it, and deletes only on yes', async () => {
    const user = userEvent.setup()
    renderPage()

    const [row] = await screen.findAllByTestId('publisher')
    await user.click(within(row).getByRole('button', { name: 'Șterge' }))
    expect(await screen.findByText(/Cele 2 teste rămân, fără editură/)).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Șterge editura' }))

    await waitFor(() => expect(api.deletePublisher).toHaveBeenCalledWith(1))
  })

  it('shows why a save was refused', async () => {
    const user = userEvent.setup()
    api.createPublisher.mockRejectedValue({ status: 409, body: 'Există deja editura „Litera”.' })
    renderPage()

    await user.click(await screen.findByRole('button', { name: 'Adaugă editură' }))
    await user.type(screen.getByLabelText('Numele editurii'), 'litera')
    await user.click(screen.getByRole('button', { name: 'Salvează' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('Există deja editura „Litera”.')
  })
})
