import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { AdminAiImportPage } from '@/pages/AdminAiImportPage'
import type { AiImport } from '@/lib/api'

const api = vi.hoisted(() => ({
  getAiImportSettings: vi.fn(),
  listAiImports: vi.fn(),
  listClasses: vi.fn(),
  listChapterOptions: vi.fn(),
  createAiImport: vi.fn(),
  getAiImport: vi.fn(),
  retryAiImport: vi.fn(),
  discardAiImport: vi.fn(),
  createQuizFromAiImport: vi.fn(),
}))
vi.mock('@/lib/api', async (original) => ({ ...(await original<typeof import('@/lib/api')>()), ...api }))
vi.mock('@/lib/image', () => ({ shrinkImage: (file: File) => Promise.resolve(file) }))

const done = (over: Partial<AiImport> = {}): AiImport => ({
  id: 5, status: 'DONE', pageCount: 1, pagesAvailable: true, itemCount: 2, error: null, schoolClassId: 8,
  chapterId: null, quizId: null, createdAt: '2026-10-12T08:00:00Z', finishedAt: '2026-10-12T08:00:20Z',
  items: [
    { page: 1, section: 'SUBIECTUL I', type: 'SINGLE_CHOICE', statement: 'Cât face $2+3$?', options: ['4', '5'],
      points: 5, hasFigure: false, warnings: [] },
    { page: 1, section: 'SUBIECTUL al II-lea', type: 'OPEN', statement: 'În figură, calculează aria.', options: [],
      points: 10, hasFigure: true, warnings: ['Are un desen: adaugă-l ca imagine după ce creezi testul.'] },
  ],
  ...over,
})

function Where() {
  return <p data-testid="where">{useLocation().pathname}</p>
}

function renderAt(path: string) {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <Routes>
        <Route path="/admin/quizzes/ai-import" element={<><AdminAiImportPage /><Where /></>} />
        <Route path="/admin/quizzes/ai-import/:importId" element={<><AdminAiImportPage /><Where /></>} />
        <Route path="/admin/quizzes/:quizId" element={<Where />} />
      </Routes>
    </MemoryRouter>,
  )
}

describe('AdminAiImportPage', () => {
  beforeEach(() => {
    Object.values(api).forEach((fn) => fn.mockReset())
    api.getAiImportSettings.mockResolvedValue({ enabled: true, maxPages: 5, dailyPageLimit: 50, pagesUsedToday: 3 })
    api.listAiImports.mockResolvedValue([])
    api.listClasses.mockResolvedValue([{ id: 8, name: 'Clasa a 8-a', description: null }])
    api.listChapterOptions.mockResolvedValue([])
    api.getAiImport.mockResolvedValue(done())
  })

  it('sends the chosen pages once the teacher confirms they are textbook pages only', async () => {
    const user = userEvent.setup()
    api.createAiImport.mockResolvedValue(done({ status: 'QUEUED', items: null }))
    renderAt('/admin/quizzes/ai-import')

    expect(await screen.findByText('Azi mai poți trimite 47 de pagini (din 50).')).toBeInTheDocument()
    const page = new File(['x'], 'pagina.jpg', { type: 'image/jpeg' })
    await user.upload(screen.getByTestId('ai-gallery'), page)
    await user.selectOptions(screen.getByLabelText('Clasa'), '8')
    const send = screen.getByRole('button', { name: 'Citește 1 pagină' })
    expect(send).toBeDisabled()

    await user.click(screen.getByLabelText(/doar pagini din manual/))
    await user.click(send)

    expect(api.createAiImport).toHaveBeenCalledWith([page], 8, null)
    await waitFor(() => expect(screen.getByTestId('where')).toHaveTextContent('/admin/quizzes/ai-import/5'))
  })

  it('says so where the server does not read photos', async () => {
    api.getAiImportSettings.mockResolvedValue({ enabled: false, maxPages: 0, dailyPageLimit: 0, pagesUsedToday: 0 })
    renderAt('/admin/quizzes/ai-import')

    expect(await screen.findByText(/nu e pornită pe acest server/)).toBeInTheDocument()
  })

  it('waits while the pages are read, then shows the questions', async () => {
    api.getAiImport.mockResolvedValueOnce(done({ status: 'RUNNING', items: null })).mockResolvedValue(done())
    renderAt('/admin/quizzes/ai-import/5')

    expect(await screen.findByText('Se citește pagina...')).toBeInTheDocument()
    expect(await screen.findAllByTestId('ai-draft', {}, { timeout: 4000 })).toHaveLength(2)
  })

  it('makes the quiz only after the grilă answer is marked, with the kept questions', async () => {
    const user = userEvent.setup()
    api.createQuizFromAiImport.mockResolvedValue(9)
    renderAt('/admin/quizzes/ai-import/5')

    const [grila, open] = await screen.findAllByTestId('ai-draft')
    expect(within(open).getByText(/Are un desen/)).toBeInTheDocument()
    expect(screen.getByTestId('ai-problems')).toHaveTextContent('Subiectul 1: marchează varianta corectă.')
    const create = screen.getByRole('button', { name: 'Creează testul (2 subiecte)' })
    expect(create).toBeDisabled()

    await user.click(within(grila).getByLabelText('Varianta b) e corectă'))
    await user.click(within(open).getByLabelText('Îl includ'))
    await user.click(screen.getByRole('button', { name: 'Creează testul (1 subiect)' }))

    expect(api.createQuizFromAiImport).toHaveBeenCalledWith(5, expect.objectContaining({
      schoolClassId: 8,
      items: [expect.objectContaining({
        type: 'SINGLE_CHOICE',
        options: [{ position: 0, text: '4', correct: false }, { position: 1, text: '5', correct: true }],
      })],
    }))
    await waitFor(() => expect(screen.getByTestId('where')).toHaveTextContent('/admin/quizzes/9'))
  })

  it('offers to try a failed read again', async () => {
    const user = userEvent.setup()
    api.getAiImport.mockResolvedValue(done({ status: 'FAILED', error: 'Poza e prea neclară.', items: null }))
    api.retryAiImport.mockResolvedValue(done({ status: 'QUEUED', items: null }))
    renderAt('/admin/quizzes/ai-import/5')

    expect(await screen.findByText('Poza e prea neclară.')).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Încearcă din nou' }))

    expect(api.retryAiImport).toHaveBeenCalledWith(5)
    expect(await screen.findByText('Se citește pagina...')).toBeInTheDocument()
  })
})
