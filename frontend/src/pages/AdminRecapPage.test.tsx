import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { AdminRecapPage } from '@/pages/AdminRecapPage'
import type { RecapExercise } from '@/lib/api'

const auth = vi.hoisted(() => ({ value: { user: { role: 'ADMIN' }, loading: false } as unknown }))
vi.mock('@/context/AuthContext', () => ({ useAuth: () => auth.value }))

const api = vi.hoisted(() => ({
  listRecapExercises: vi.fn(),
  listEnChapters: vi.fn(),
  listClasses: vi.fn(),
  reviewRecapExercise: vi.fn(),
  createRecapQuiz: vi.fn(),
  importRecap: vi.fn(),
  waiveRecapFigure: vi.fn(),
  attachRecapImage: vi.fn(),
}))
vi.mock('@/lib/api', async (original) => ({ ...(await original<typeof import('@/lib/api')>()), ...api }))

const image = vi.hoisted(() => ({ shrinkImage: vi.fn() }))
vi.mock('@/lib/image', () => image)

const exercise = (id: number, key: string, extra: Partial<RecapExercise> = {}): RecapExercise => ({
  id, key, tema: 3, number: id, statement: `Enunțul ${key}`, solution: 'Pașii', answer: '20 lei', result: '20',
  aiStatus: 'AGREE', verified: true, hasImage: false, figureMissing: false, enChapterId: 12, enChapterCode: 'A3',
  enChapterTitle: 'Procente', review: 'DRAFT', usedIn: 0, parts: [], ...extra,
})

function renderPage() {
  return render(
    <MemoryRouter initialEntries={['/admin/culegere']}>
      <Routes>
        <Route path="/admin/culegere" element={<AdminRecapPage />} />
        <Route path="/admin/quizzes/:id" element={<p>Pagina testului</p>} />
      </Routes>
    </MemoryRouter>,
  )
}

describe('AdminRecapPage', () => {
  beforeEach(() => {
    Object.values(api).forEach((fn) => fn.mockReset())
    auth.value = { user: { role: 'ADMIN' }, loading: false }
    api.listEnChapters.mockResolvedValue([])
    api.listClasses.mockResolvedValue([{ id: 4, name: 'Clasa a 8-a A' }])
    api.listRecapExercises.mockResolvedValue([
      exercise(1, 'T3-1'),
      exercise(2, 'T3-2', { aiStatus: 'PROOF', verified: false, answer: null, result: null }),
      exercise(3, 'T3-3', { review: 'APPROVED' }),
      exercise(4, 'T9-4', { figureMissing: true, enChapterCode: 'G3' }),
      exercise(5, 'T8-1', { figureMissing: true, review: 'APPROVED' }),
    ])
  })

  it('opens on the exercises still to check, saying what the AI checked', async () => {
    renderPage()

    expect(await screen.findByTestId('recap-T3-1')).toBeInTheDocument()
    expect(screen.queryByTestId('recap-T3-3')).not.toBeInTheDocument() // approved: not in "De verificat"
    expect(screen.getByTestId('recap-counts')).toHaveTextContent('2 exerciții aprobate, 3 de verificat.')
    expect(within(screen.getByTestId('recap-T3-1')).getByText('Verificat de AI')).toBeInTheDocument()
    expect(within(screen.getByTestId('recap-T3-2')).getByText('Demonstrație: verifică tu')).toBeInTheDocument()
    expect(within(screen.getByTestId('recap-T3-2')).getByText(/Fără răspuns propus/)).toBeInTheDocument()
    expect(within(screen.getByTestId('recap-T9-4')).getByText(/desen care nu a fost găsit/)).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Încarcă culegerea (.zip)' })).toBeInTheDocument()
  })

  it('approves an exercise, which then leaves the list of those to check', async () => {
    const user = userEvent.setup()
    api.reviewRecapExercise.mockResolvedValue(exercise(1, 'T3-1', { review: 'APPROVED' }))
    renderPage()

    await user.click(within(await screen.findByTestId('recap-T3-1')).getByRole('button', { name: 'Aprobă' }))

    expect(api.reviewRecapExercise).toHaveBeenCalledWith(1, 'APPROVED')
    expect(screen.queryByTestId('recap-T3-1')).not.toBeInTheDocument()
    expect(screen.getByTestId('recap-counts')).toHaveTextContent('3 exerciții aprobate, 2 de verificat.')
  })

  it('makes a draft test of the approved exercises chosen and opens it', async () => {
    const user = userEvent.setup()
    api.createRecapQuiz.mockResolvedValue(55)
    renderPage()

    await user.selectOptions(await screen.findByLabelText('Stare'), 'APPROVED')
    await user.click(screen.getByLabelText('Alege T3-3 pentru test'))
    await user.click(screen.getByRole('button', { name: 'Creează test' }))
    await user.selectOptions(screen.getByLabelText('Clasa'), '4')
    await user.click(screen.getByRole('button', { name: 'Creează ciorna' }))

    expect(api.createRecapQuiz).toHaveBeenCalledWith({ title: 'Recapitulare', exerciseIds: [3], schoolClassId: 4 })
    expect(await screen.findByText('Pagina testului')).toBeInTheDocument()
  })

  it('keeps an approved exercise whose drawing is missing out of a new test', async () => {
    const user = userEvent.setup()
    renderPage()

    await user.selectOptions(await screen.findByLabelText('Stare'), 'APPROVED')

    expect(screen.getByLabelText('Alege T8-1 pentru test')).toBeDisabled()
    expect(within(screen.getByTestId('recap-T8-1')).getByText(/nu intră în niciun test/)).toBeInTheDocument()
    expect(screen.getByLabelText('Alege T3-3 pentru test')).toBeEnabled()
  })

  it('says how many drawings a reload added to the exercises already there', async () => {
    const user = userEvent.setup()
    api.importRecap.mockResolvedValue({ added: 0, skipped: 387, total: 387, figuresAdded: 46 })
    renderPage()

    await user.upload(await screen.findByLabelText('Arhiva culegerii'), new File(['zip'], 'teme-culegere.zip', { type: 'application/zip' }))

    expect(await screen.findByText(/Am adăugat 0 exerciții noi\. La cele existente am adăugat 46 de figuri lipsă/)).toBeInTheDocument()
  })

  it('lets a teacher review but not load the book', async () => {
    auth.value = { user: { role: 'TEACHER' }, loading: false }
    renderPage()

    expect(await screen.findByTestId('recap-T3-1')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Încarcă culegerea (.zip)' })).not.toBeInTheDocument()
  })

  it('converts a picked picture to PNG/JPEG before it goes to the server, which takes only those', async () => {
    const webp = new File(['webp'], 'desen.webp', { type: 'image/webp' })
    const png = new File(['png'], 'desen.png', { type: 'image/png' })
    image.shrinkImage.mockResolvedValue(png)
    api.attachRecapImage.mockResolvedValue(exercise(1, 'T3-1', { hasImage: true }))
    renderPage()

    await userEvent.upload(await screen.findByLabelText('Imagine pentru T3-1'), webp)

    expect(image.shrinkImage).toHaveBeenCalledWith(webp, 1600)
    expect(api.attachRecapImage).toHaveBeenCalledWith(1, png)
  })
})
