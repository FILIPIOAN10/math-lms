import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { AdminQuizzesPage } from '@/pages/AdminQuizzesPage'
import type { QuizDetail, QuizSummary } from '@/lib/api'

const api = vi.hoisted(() => ({
  listQuizzes: vi.fn(),
  listQuizSummaries: vi.fn(),
  listClasses: vi.fn(),
  getQuiz: vi.fn(),
  setQuizPublished: vi.fn(),
  getQuizActivity: vi.fn(),
  copyQuiz: vi.fn(),
  deleteQuiz: vi.fn(),
  deleteQuizItem: vi.fn(),
  moveQuizItem: vi.fn(),
  duplicateQuizItem: vi.fn(),
  listChapterOptions: vi.fn(),
  createQuiz: vi.fn(),
  updateQuiz: vi.fn(),
  getAiImportSettings: vi.fn(),
  getAiGradingSettings: vi.fn(),
}))
vi.mock('@/lib/api', async (original) => ({ ...(await original<typeof import('@/lib/api')>()), ...api }))

const summary = (id: number, title: string, status: QuizSummary['status'] = 'PUBLISHED'): QuizSummary => ({
  id, title, description: null, status, schoolClassId: null, schoolClassName: null, timeLimitMinutes: null,
  practiceAllowed: false, chapterId: null, maxAttempts: null,
})
const detail = (over: Partial<QuizDetail> = {}): QuizDetail => ({
  ...summary(7, 'Simulare EN'),
  items: [{
    id: 70, position: 0, type: 'SINGLE_CHOICE', statement: 'Cât face 2+2?', points: 2, solution: null, hints: [],
    options: [{ id: 700, position: 0, text: '4', correct: true }, { id: 701, position: 1, text: '5', correct: false }],
  }],
  ...over,
} as QuizDetail)

function Where() {
  return <p data-testid="where">{useLocation().pathname}</p>
}

function renderAt(path: string) {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <Routes>
        <Route path="/admin/quizzes" element={<><AdminQuizzesPage /><Where /></>} />
        <Route path="/admin/quizzes/:quizId" element={<><AdminQuizzesPage /><Where /></>} />
        <Route path="/admin/quizzes/:quizId/stats" element={<p>statistici</p>} />
        <Route path="/admin/quizzes/:quizId/preview" element={<p>ca elev</p>} />
        <Route path="/admin/assignments" element={<p>teme</p>} />
      </Routes>
    </MemoryRouter>,
  )
}

const row = async (title: string) =>
  (await screen.findAllByTestId('quiz-row')).find((r) => within(r).queryByText(title) !== null)!

describe('AdminQuizzesPage', () => {
  beforeEach(() => {
    Object.values(api).forEach((fn) => fn.mockReset())
    api.listQuizzes.mockResolvedValue([summary(7, 'Simulare EN'), summary(8, 'Ciornă nouă', 'DRAFT')])
    api.listQuizSummaries.mockResolvedValue([])
    api.listClasses.mockResolvedValue([
      { id: 8, name: 'Clasa a 8-a', description: null },
      { id: 9, name: 'Clasa a 9-a', description: null },
    ])
    api.getQuiz.mockResolvedValue(detail())
    api.listChapterOptions.mockResolvedValue([
      { id: 1, title: 'Fracții', bookId: 1, bookTitle: 'Algebra', schoolClassId: 8, schoolClassName: 'Clasa a 8-a' },
      { id: 3, title: 'Funcții', bookId: 2, bookTitle: 'Analiză', schoolClassId: 9, schoolClassName: 'Clasa a 9-a' },
    ])
    api.getAiImportSettings.mockResolvedValue({ enabled: false, maxPages: 0, dailyPageLimit: 0, pagesUsedToday: 0 })
    api.getAiGradingSettings.mockResolvedValue({ enabled: false, baremMaxPages: 0, baremDailyPageLimit: 0, baremPagesUsedToday: 0 })
    api.getQuizActivity.mockResolvedValue({ studentsWriting: 0, homeworkClasses: 0 })
    api.setQuizPublished.mockResolvedValue(summary(7, 'Simulare EN', 'DRAFT'))
  })

  describe('încercări', () => {
    it('starts a new test at one attempt and sends the choice', async () => {
      api.createQuiz.mockResolvedValue(summary(9, 'Test nou', 'DRAFT'))
      renderAt('/admin/quizzes')
      await userEvent.click(await screen.findByRole('button', { name: 'Adaugă test' }))
      const dialog = await screen.findByRole('dialog', { name: 'Adaugă test' })

      await userEvent.type(within(dialog).getByLabelText('Titlu'), 'Test nou')
      const attempts = within(dialog).getByLabelText('Câte încercări are elevul')
      expect(attempts).toHaveValue('1')
      await userEvent.selectOptions(attempts, '3')
      await userEvent.click(within(dialog).getByRole('button', { name: 'Salvează' }))

      await waitFor(() => expect(api.createQuiz).toHaveBeenCalledWith(expect.objectContaining({ maxAttempts: 3 })))
    })

    it('tells the teacher what more attempts mean', async () => {
      renderAt('/admin/quizzes')
      await userEvent.click(await screen.findByRole('button', { name: 'Adaugă test' }))

      const dialog = await screen.findByRole('dialog', { name: 'Adaugă test' })
      expect(dialog).toHaveTextContent('vede răspunsurile corecte și baremul')
      expect(dialog).toHaveTextContent('contează cea mai mare notă')
    })

    const editRow = async () => {
      await userEvent.click(within(await row('Simulare EN')).getByRole('button', { name: 'Mai multe acțiuni pentru Simulare EN' }))
      await userEvent.click(await screen.findByRole('menuitem', { name: 'Editează detaliile' }))
      return screen.findByRole('dialog', { name: 'Editează testul' })
    }

    it('keeps a test without a limit unlimited when it is edited', async () => {
      api.updateQuiz.mockResolvedValue(summary(7, 'Simulare EN'))
      renderAt('/admin/quizzes')
      const dialog = await editRow()

      expect(within(dialog).getByLabelText('Câte încercări are elevul')).toHaveValue('')
      await userEvent.click(within(dialog).getByRole('button', { name: 'Salvează' }))

      await waitFor(() => expect(api.updateQuiz).toHaveBeenCalledWith(7, expect.objectContaining({ maxAttempts: null })))
    })

    it('keeps a limit that is not among the choices', async () => {
      api.listQuizzes.mockResolvedValue([{ ...summary(7, 'Simulare EN'), maxAttempts: 4 }])
      api.updateQuiz.mockResolvedValue(summary(7, 'Simulare EN'))
      renderAt('/admin/quizzes')
      const dialog = await editRow()

      expect(within(dialog).getByLabelText('Câte încercări are elevul')).toHaveValue('4')
      await userEvent.click(within(dialog).getByRole('button', { name: 'Salvează' }))

      await waitFor(() => expect(api.updateQuiz).toHaveBeenCalledWith(7, expect.objectContaining({ maxAttempts: 4 })))
    })

    it('shows the limit on each row', async () => {
      api.listQuizzes.mockResolvedValue([
        { ...summary(7, 'Simulare EN'), maxAttempts: 1 },
        summary(8, 'Fracții'),
      ])
      renderAt('/admin/quizzes')

      expect(within(await row('Simulare EN')).getByTestId('quiz-audience')).toHaveTextContent('1 încercare')
      expect(within(await row('Fracții')).getByTestId('quiz-audience')).toHaveTextContent('încercări nelimitate')
    })
  })

  describe('ordinea întrebărilor', () => {
    const item = (id: number, statement: string, section: string | null = null) => ({
      ...detail().items[0], id, position: id, statement, section,
    })
    const menuOf = async (number: string) => {
      await userEvent.click(await screen.findByRole('button', { name: `Mai multe acțiuni pentru întrebarea ${number}` }))
      return (await screen.findAllByRole('menuitem')).map((i) => i.textContent)
    }

    it('offers only the moves there is room for', async () => {
      api.getQuiz.mockResolvedValue(detail({ items: [item(1, 'prima'), item(2, 'a doua'), item(3, 'a treia')] }))
      renderAt('/admin/quizzes/7')

      expect(await menuOf('1')).toEqual(['Mută mai jos', 'Duplică', 'Șterge întrebarea'])
      await userEvent.keyboard('{Escape}')
      expect(await menuOf('2')).toEqual(['Mută mai sus', 'Mută mai jos', 'Duplică', 'Șterge întrebarea'])
      await userEvent.keyboard('{Escape}')
      expect(await menuOf('3')).toEqual(['Mută mai sus', 'Duplică', 'Șterge întrebarea'])
    })

    it('keeps a question inside its section', async () => {
      api.getQuiz.mockResolvedValue(detail({ items: [item(1, 'prima', 'SUBIECTUL I'), item(2, 'a doua', 'SUBIECTUL al II-lea')] }))
      renderAt('/admin/quizzes/7')

      expect(await menuOf('I.1')).toEqual(['Duplică', 'Șterge întrebarea'])
    })

    it('moves a question and shows the new order', async () => {
      api.getQuiz.mockResolvedValue(detail({ items: [item(1, 'prima'), item(2, 'a doua')] }))
      api.moveQuizItem.mockResolvedValue(undefined)
      renderAt('/admin/quizzes/7')
      await menuOf('2')

      api.getQuiz.mockResolvedValue(detail({ items: [item(2, 'a doua'), item(1, 'prima')] }))
      await userEvent.click(await screen.findByRole('menuitem', { name: 'Mută mai sus' }))

      await waitFor(() => expect(api.moveQuizItem).toHaveBeenCalledWith(2, true))
      await waitFor(() => expect(api.getQuiz).toHaveBeenCalledTimes(2))
    })

    it('duplicates a question and shows the copy', async () => {
      api.duplicateQuizItem.mockResolvedValue(undefined)
      renderAt('/admin/quizzes/7')
      await menuOf('1')

      await userEvent.click(await screen.findByRole('menuitem', { name: 'Duplică' }))

      await waitFor(() => expect(api.duplicateQuizItem).toHaveBeenCalledWith(70))
      await waitFor(() => expect(api.getQuiz).toHaveBeenCalledTimes(2))
    })

    it('shows why a move was refused', async () => {
      api.getQuiz.mockResolvedValue(detail({ items: [item(1, 'prima'), item(2, 'a doua')] }))
      api.moveQuizItem.mockRejectedValue(Object.assign(new Error('x'), { status: 409, body: 'Ordinea nu se mai poate schimba.' }))
      renderAt('/admin/quizzes/7')
      await menuOf('2')

      await userEvent.click(await screen.findByRole('menuitem', { name: 'Mută mai sus' }))

      expect(await screen.findByText('Ordinea nu se mai poate schimba.')).toBeInTheDocument()
    })
  })

  it('opens the test the way a student sees it, published or not', async () => {
    api.getQuiz.mockResolvedValue(detail({ status: 'DRAFT' }))
    renderAt('/admin/quizzes/7')
    await screen.findByText('Cât face 2+2?')

    await userEvent.click(screen.getByRole('button', { name: 'Mai multe acțiuni pentru Simulare EN' }))

    expect(await screen.findAllByRole('menuitem')).toHaveLength(1) // a draft has nothing to unpublish
    await userEvent.click(await screen.findByRole('menuitem', { name: 'Vezi ca elev' }))
    await waitFor(() => expect(screen.queryByText('Cât face 2+2?')).not.toBeInTheDocument())
  })

  describe('un test dat deja de elevi', () => {
    it('says so up front and locks what the server would refuse', async () => {
      api.getQuiz.mockResolvedValue(detail({ taken: true }))
      renderAt('/admin/quizzes/7')

      expect(await screen.findByTestId('quiz-taken')).toHaveTextContent('Elevii au dat deja acest test')
      expect(screen.getByRole('button', { name: 'Adaugă întrebare' })).toBeDisabled()
      expect(screen.getByRole('button', { name: 'Importă din text' })).toBeDisabled()
      expect(screen.queryByRole('button', { name: 'Mai multe acțiuni pentru întrebarea 1' })).not.toBeInTheDocument()
      expect(screen.getByRole('button', { name: 'Editează întrebarea 1' })).toBeEnabled() // the texts can still be corrected
    })

    it('leaves a test nobody took open for editing', async () => {
      renderAt('/admin/quizzes/7')
      await screen.findByText('Cât face 2+2?')

      expect(screen.queryByTestId('quiz-taken')).not.toBeInTheDocument()
      expect(screen.getByRole('button', { name: 'Adaugă întrebare' })).toBeEnabled()
    })

    it('opens a copy to edit from the banner', async () => {
      api.getQuiz.mockImplementation((id: number) => Promise.resolve(detail({ id, taken: id === 7 })))
      api.copyQuiz.mockResolvedValue(summary(9, 'Simulare EN (copie)', 'DRAFT'))
      renderAt('/admin/quizzes/7')

      await userEvent.click(within(await screen.findByTestId('quiz-taken')).getByRole('button', { name: 'Copiază și editează' }))

      await waitFor(() => expect(screen.getByTestId('where')).toHaveTextContent('/admin/quizzes/9'))
      expect(api.copyQuiz).toHaveBeenCalledWith(7)
    })
  })

  describe('Depublică', () => {
    const askFromList = async () => {
      renderAt('/admin/quizzes')
      await userEvent.click(within(await row('Simulare EN')).getByRole('button', { name: 'Mai multe acțiuni pentru Simulare EN' }))
      await userEvent.click(await screen.findByRole('menuitem', { name: 'Depublică' }))
      return screen.findByRole('dialog', { name: 'Depublici „Simulare EN”?' })
    }

    it('says who is writing the test and what happens to its homework, and does nothing on "Anulează"', async () => {
      api.getQuizActivity.mockResolvedValue({ studentsWriting: 3, homeworkClasses: 1 })

      const dialog = await askFromList()

      expect(api.getQuizActivity).toHaveBeenCalledWith(7)
      expect(dialog).toHaveTextContent('3 elevi lucrează acum la el')
      expect(dialog).toHaveTextContent('cine iese nu mai poate reveni')
      expect(dialog).toHaveTextContent('Tema dată pe acest test dispare de la elevi')
      await userEvent.click(within(dialog).getByRole('button', { name: 'Anulează' }))
      expect(api.setQuizPublished).not.toHaveBeenCalled()
    })

    it('unpublishes only on yes', async () => {
      const dialog = await askFromList()

      await userEvent.click(within(dialog).getByRole('button', { name: 'Depublică' }))

      await waitFor(() => expect(api.setQuizPublished).toHaveBeenCalledWith(7, false))
    })

    it('still asks when the count cannot be read', async () => {
      api.getQuizActivity.mockRejectedValue(new Error('offline'))

      const dialog = await askFromList()

      expect(dialog).toHaveTextContent('Elevii nu îl mai pot începe')
      expect(api.setQuizPublished).not.toHaveBeenCalled()
    })

    it('in the editor sits behind the last menu, away from the first place', async () => {
      renderAt('/admin/quizzes/7')
      await screen.findByText('Cât face 2+2?')

      expect(screen.queryByRole('button', { name: 'Depublică' })).not.toBeInTheDocument()
      await userEvent.click(screen.getByRole('button', { name: 'Mai multe acțiuni pentru Simulare EN' }))
      await userEvent.click(await screen.findByRole('menuitem', { name: 'Depublică' }))

      expect(await screen.findByRole('dialog', { name: 'Depublici „Simulare EN”?' })).toBeInTheDocument()
      expect(api.setQuizPublished).not.toHaveBeenCalled()
    })
  })

  it('says which grile still wait for their answer, and where it comes from', async () => {
    api.getQuiz.mockResolvedValue(detail({ items: [
      { ...detail().items[0], options: [{ id: 700, position: 0, text: '4', correct: false }, { id: 701, position: 1, text: '5', correct: false }] },
    ] }))
    api.getAiGradingSettings.mockResolvedValue({ enabled: true, baremMaxPages: 5, baremDailyPageLimit: 20, baremPagesUsedToday: 0 })
    renderAt('/admin/quizzes/7')

    const banner = await screen.findByTestId('unanswered-grile')
    expect(banner).toHaveTextContent('1 grilă nu are încă răspunsul corect: 1.')
    expect(banner).toHaveTextContent('nu se poate publica')
    expect(within(banner).getByRole('button', { name: 'Barem din poze (AI)' })).toBeInTheDocument()
    expect(screen.getByTestId('no-correct-answer')).toHaveTextContent('Fără răspuns corect')
  })

  it('keeps one main action in the editor: adding a question, with the AI tools in their own menu', async () => {
    api.getAiGradingSettings.mockResolvedValue({ enabled: true, baremMaxPages: 5, baremDailyPageLimit: 20, baremPagesUsedToday: 0 })
    renderAt('/admin/quizzes/7')
    await screen.findByText('Cât face 2+2?')

    expect(await screen.findByRole('button', { name: 'Instrumente AI' })).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Barem din poze (AI)' })).not.toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Importă din text' })).toHaveClass('border-border')
    expect(screen.getByRole('button', { name: 'Adaugă întrebare' })).not.toHaveClass('border-border') // the filled one
  })

  it('offers the barem from photos on a test with questions, only where the server reads it', async () => {
    renderAt('/admin/quizzes/7')
    await screen.findByText('Cât face 2+2?')
    expect(screen.queryByRole('button', { name: 'Instrumente AI' })).not.toBeInTheDocument() // no AI on: no empty menu

    api.getAiGradingSettings.mockResolvedValue({ enabled: true, baremMaxPages: 5, baremDailyPageLimit: 20, baremPagesUsedToday: 0 })
    renderAt('/admin/quizzes/7')
    await userEvent.click(await screen.findByRole('button', { name: 'Instrumente AI' }))
    await userEvent.click(await screen.findByRole('menuitem', { name: 'Barem din poze (AI)' }))
    expect(await screen.findByRole('dialog', { name: 'Barem din poze (AI)' })).toBeInTheDocument()
  })

  it('offers a quiz from textbook photos only where the server reads them', async () => {
    renderAt('/admin/quizzes')
    await row('Simulare EN')
    expect(screen.queryByRole('link', { name: 'Din poze (AI)' })).not.toBeInTheDocument()

    api.getAiImportSettings.mockResolvedValue({ enabled: true, maxPages: 5, dailyPageLimit: 50, pagesUsedToday: 0 })
    renderAt('/admin/quizzes')
    expect(await screen.findByRole('link', { name: 'Din poze (AI)' })).toHaveAttribute('href', '/admin/quizzes/ai-import')
  })

  it('names the page and the browser tab', async () => {
    renderAt('/admin/quizzes')
    expect(await screen.findByRole('heading', { level: 1, name: 'Teste' })).toBeInTheDocument()
    expect(document.title).toBe('Teste · Math LMS')
  })

  it('shows one main action per quiz and keeps the rest in a menu', async () => {
    renderAt('/admin/quizzes')

    const published = await row('Simulare EN')
    expect(within(published).getByRole('link', { name: 'Simulare EN' })).toHaveAttribute('href', '/admin/quizzes/7')
    expect(within(published).getByRole('link', { name: 'Dă ca temă' })).toHaveAttribute('href', '/admin/assignments?quizId=7')
    expect(within(published).queryByRole('button', { name: 'Șterge' })).not.toBeInTheDocument()

    const draft = await row('Ciornă nouă')
    expect(within(draft).getByRole('button', { name: 'Publică' })).toBeInTheDocument()
    expect(within(draft).queryByRole('link', { name: 'Dă ca temă' })).not.toBeInTheDocument()
  })

  it('shows the numbers on each row and a visible link to the statistics of a published test', async () => {
    api.listQuizSummaries.mockResolvedValue([
      { quizId: 7, questions: 18, papers: 24, average: 7.42, dueAt: '2026-11-02T18:00:00Z' },
      { quizId: 8, questions: 1, papers: 0, average: null, dueAt: null },
    ])
    renderAt('/admin/quizzes')

    const published = await row('Simulare EN')
    const numbers = await within(published).findByTestId('quiz-numbers')
    expect(numbers).toHaveTextContent('18 întrebări')
    expect(numbers).toHaveTextContent('24 de lucrări')
    expect(numbers).toHaveTextContent('media 7,42')
    expect(numbers).toHaveTextContent('Termen:')
    expect(within(published).getByRole('link', { name: 'Statistici' })).toHaveAttribute('href', '/admin/quizzes/7/stats')

    const draft = await row('Ciornă nouă')
    const draftNumbers = await within(draft).findByTestId('quiz-numbers')
    expect(draftNumbers).toHaveTextContent('1 întrebare')
    expect(draftNumbers).not.toHaveTextContent('media')
    expect(draftNumbers).not.toHaveTextContent('Termen')
    expect(within(draft).queryByRole('link', { name: 'Statistici' })).not.toBeInTheDocument()
  })

  it('searches and filters the list once there are five tests', async () => {
    const user = userEvent.setup()
    api.listQuizzes.mockResolvedValue([
      { ...summary(1, 'Fracții'), schoolClassName: '7A' },
      { ...summary(2, 'Ecuații'), schoolClassName: '7A' },
      { ...summary(3, 'Procente', 'DRAFT'), schoolClassName: '8B' },
      summary(4, 'Geometrie'),
      summary(5, 'Simulare EN', 'DRAFT'),
    ])
    renderAt('/admin/quizzes')
    await screen.findByText('Fracții')

    await user.selectOptions(screen.getByRole('combobox', { name: 'Stare' }), 'DRAFT')
    expect(screen.getAllByTestId('quiz-row')).toHaveLength(2)

    await user.selectOptions(screen.getByRole('combobox', { name: 'Clasa' }), '8B')
    expect(screen.getAllByTestId('quiz-row')).toHaveLength(1)
    expect(screen.getByText('Procente')).toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: 'Șterge filtrele' }))
    await user.type(screen.getByRole('searchbox', { name: /Caută test/ }), 'ecuatii')
    expect(screen.getAllByTestId('quiz-row')).toHaveLength(1)
    expect(within(screen.getByTestId('filter-bar')).getByRole('status')).toHaveTextContent('1 din 5')
  })

  it('offers no search for a short list', async () => {
    renderAt('/admin/quizzes')

    await screen.findByText('Simulare EN')
    expect(screen.queryByTestId('filter-bar')).not.toBeInTheDocument()
  })

  it('offers the other actions in the menu, deleting last', async () => {
    renderAt('/admin/quizzes')
    await userEvent.click(within(await row('Simulare EN')).getByRole('button', { name: 'Mai multe acțiuni pentru Simulare EN' }))

    const items = (await screen.findAllByRole('menuitem')).map((i) => i.textContent)
    expect(items).toEqual(['Editează detaliile', 'Copiază', 'Depublică', 'Șterge'])
  })

  it('offers to try again when the list could not load', async () => {
    api.listQuizzes.mockRejectedValue(new TypeError('Failed to fetch'))
    renderAt('/admin/quizzes')

    expect(await screen.findByTestId('error-state')).toBeInTheDocument()
    expect(screen.queryByText('Niciun test. Adaugă primul.')).not.toBeInTheDocument()
  })

  it('shows a refused action as a plain message: trying again would change nothing', async () => {
    api.deleteQuiz.mockRejectedValue(Object.assign(new Error('x'), { status: 409, body: 'Testul nu poate fi șters.' }))
    renderAt('/admin/quizzes')
    await userEvent.click(within(await row('Simulare EN')).getByRole('button', { name: 'Mai multe acțiuni pentru Simulare EN' }))
    await userEvent.click(await screen.findByRole('menuitem', { name: 'Șterge' }))
    await userEvent.click(within(await screen.findByRole('dialog')).getByRole('button', { name: 'Șterge' }))

    expect(await screen.findByText('Testul nu poate fi șters.')).toBeInTheDocument()
    expect(screen.queryByTestId('error-state')).not.toBeInTheDocument()
  })

  it('asks in a dialog before deleting, and deletes only on yes', async () => {
    api.deleteQuiz.mockResolvedValue(undefined)
    renderAt('/admin/quizzes')
    const open = async () => {
      await userEvent.click(within(await row('Simulare EN')).getByRole('button', { name: 'Mai multe acțiuni pentru Simulare EN' }))
      await userEvent.click(await screen.findByRole('menuitem', { name: 'Șterge' }))
      return screen.findByRole('dialog', { name: 'Ștergi testul „Simulare EN”?' })
    }

    await userEvent.click(within(await open()).getByRole('button', { name: 'Anulează' }))
    expect(api.deleteQuiz).not.toHaveBeenCalled()

    await userEvent.click(within(await open()).getByRole('button', { name: 'Șterge' }))
    await waitFor(() => expect(api.deleteQuiz).toHaveBeenCalledWith(7))
  })

  it('opens the editor at its own address', async () => {
    renderAt('/admin/quizzes')
    await userEvent.click(within(await row('Simulare EN')).getByRole('link', { name: 'Simulare EN' }))

    expect(await screen.findByText('Cât face 2+2?')).toBeInTheDocument()
    expect(screen.getByTestId('where')).toHaveTextContent('/admin/quizzes/7')
  })

  it('loads the editor straight from the address, so refresh and Back work', async () => {
    renderAt('/admin/quizzes/7')

    expect(await screen.findByRole('heading', { level: 1, name: 'Simulare EN' })).toBeInTheDocument()
    expect(api.getQuiz).toHaveBeenCalledWith(7)
    const trail = screen.getByRole('navigation', { name: 'Unde ești' })
    expect(within(trail).getByRole('link', { name: 'Teste' })).toHaveAttribute('href', '/admin/quizzes')
  })

  it('says so when the quiz in the address does not exist', async () => {
    const { ApiError } = await import('@/lib/api')
    api.getQuiz.mockRejectedValue(new ApiError(404, 'Quiz 99 not found'))
    renderAt('/admin/quizzes/99')

    expect(await screen.findByText('Quiz 99 not found')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Înapoi la teste' })).toHaveAttribute('href', '/admin/quizzes')
  })

  it('asks in a dialog before deleting an item', async () => {
    api.deleteQuizItem.mockResolvedValue(undefined)
    renderAt('/admin/quizzes/7')
    await screen.findByText('Cât face 2+2?')

    expect(screen.queryByRole('button', { name: 'Șterge întrebarea 1' })).not.toBeInTheDocument() // not next to Editează
    await userEvent.click(screen.getByRole('button', { name: 'Mai multe acțiuni pentru întrebarea 1' }))
    await userEvent.click(await screen.findByRole('menuitem', { name: 'Șterge întrebarea' }))
    await userEvent.click(within(await screen.findByRole('dialog', { name: 'Ștergi întrebarea 1?' })).getByRole('button', { name: 'Șterge' }))

    await waitFor(() => expect(api.deleteQuizItem).toHaveBeenCalledWith(70))
  })
})

describe('AdminQuizzesPage — chapter tag', () => {
  beforeEach(() => {
    Object.values(api).forEach((fn) => fn.mockReset())
    api.listQuizzes.mockResolvedValue([])
    api.listQuizSummaries.mockResolvedValue([])
    api.listClasses.mockResolvedValue([
      { id: 8, name: 'Clasa a 8-a', description: null },
      { id: 9, name: 'Clasa a 9-a', description: null },
    ])
    api.listChapterOptions.mockResolvedValue([
      { id: 1, title: 'Fracții', bookId: 1, bookTitle: 'Algebra', schoolClassId: 8, schoolClassName: 'Clasa a 8-a' },
      { id: 3, title: 'Funcții', bookId: 2, bookTitle: 'Analiză', schoolClassId: 9, schoolClassName: 'Clasa a 9-a' },
    ])
    api.createQuiz.mockResolvedValue({ ...summary(20, 'Nou'), status: 'DRAFT' })
    api.getQuiz.mockResolvedValue(detail({ id: 20, title: 'Nou', items: [] }))
    api.getAiImportSettings.mockResolvedValue({ enabled: false, maxPages: 0, dailyPageLimit: 0, pagesUsedToday: 0 })
    api.getAiGradingSettings.mockResolvedValue({ enabled: false, baremMaxPages: 0, baremDailyPageLimit: 0, baremPagesUsedToday: 0 })
  })

  it("offers only the chosen class's chapters and sends the one picked", async () => {
    renderAt('/admin/quizzes')
    await userEvent.click(await screen.findByRole('button', { name: 'Adaugă test' }))
    const dialog = await screen.findByRole('dialog')

    await userEvent.type(within(dialog).getByLabelText('Titlu'), 'Nou')
    await userEvent.selectOptions(within(dialog).getByLabelText('Pentru clasa'), '8')
    const chapter = within(dialog).getByLabelText('Capitol (pentru harta pe capitole)')
    const offered = within(chapter).getAllByRole('option').map((o) => o.textContent)
    expect(offered).toContain('Fracții')
    expect(offered).not.toContain('Funcții')
    await userEvent.selectOptions(chapter, '1')
    await userEvent.click(within(dialog).getByRole('button', { name: 'Salvează' }))

    await waitFor(() => expect(api.createQuiz).toHaveBeenCalledWith(expect.objectContaining({ schoolClassId: 8, chapterId: 1 })))
  })
})
