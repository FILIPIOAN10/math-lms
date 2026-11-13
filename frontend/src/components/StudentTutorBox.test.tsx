import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ApiError, type StudentTutorStatus, type TutorExplanation } from '@/lib/api'
import { StudentTutorBox } from './StudentTutorBox'

const api = vi.hoisted(() => ({ getStudentTutor: vi.fn(), askStudentTutor: vi.fn() }))
vi.mock('@/lib/api', async (original) => ({ ...(await original<typeof import('@/lib/api')>()), ...api }))

const open: StudentTutorStatus = { enabled: true, dailyLimit: 20, remainingToday: 20, openTest: null }

const explained: TutorExplanation = {
  answered: true,
  text: 'Scrii factorul comun în fața parantezei.',
  lessons: [{ chapterId: 7, chapterCode: 'A7', chapterTitle: 'Calcul algebric', sectionId: 9, title: 'Factor comun', bookPage: 34 }],
  sentToTeacher: false,
  remainingToday: 19,
}

function renderBox() {
  return render(
    <MemoryRouter>
      <StudentTutorBox />
    </MemoryRouter>,
  )
}

async function openBox() {
  await userEvent.click(await screen.findByRole('button', { name: /Nu înțeleg/ }))
  return screen.getByRole('dialog')
}

async function ask(text: string) {
  await userEvent.type(screen.getByRole('textbox', { name: /Ce nu înțelegi/ }), text)
  await userEvent.click(screen.getByRole('button', { name: 'Întreabă' }))
}

describe('StudentTutorBox', () => {
  beforeEach(() => {
    api.getStudentTutor.mockReset().mockResolvedValue(open)
    api.askStudentTutor.mockReset().mockResolvedValue(explained)
  })

  it('is not there while the box is switched off', async () => {
    api.getStudentTutor.mockResolvedValue({ enabled: false, dailyLimit: 0, remainingToday: 0, openTest: null })

    renderBox()

    await vi.waitFor(() => expect(api.getStudentTutor).toHaveBeenCalled())
    expect(screen.queryByRole('button', { name: /Nu înțeleg/ })).not.toBeInTheDocument()
  })

  it('says plainly that the answers come from an AI, before anything is asked', async () => {
    renderBox()

    const dialog = await openBox()

    expect(within(dialog).getByTestId('tutor-ai-notice')).toHaveTextContent(/inteligență artificială/)
    expect(within(dialog).getByTestId('tutor-ai-notice')).toHaveTextContent(/doar din lecțiile/)
    expect(within(dialog).getByTestId('tutor-ai-notice')).toHaveTextContent(/Poate greși/)
  })

  it('answers with a link to the lesson at its page and to the practice of the chapter', async () => {
    renderBox()
    await openBox()

    await ask('cum scot factor comun?')

    expect(api.askStudentTutor).toHaveBeenCalledWith('cum scot factor comun?', [])
    expect(await screen.findByText('Scrii factorul comun în fața parantezei.')).toBeInTheDocument()
    expect(screen.getByTestId('tutor-answer')).toHaveTextContent('Răspuns AI')
    expect(screen.getByRole('link', { name: /Deschide lecția A7, pagina 34/ })).toHaveAttribute('href', '/capitole/7?sectiune=9')
    expect(screen.getByRole('link', { name: /Exersează capitolul/ })).toHaveAttribute('href', '/capitole/7/exersare')
    expect(screen.getByText(/Îți mai rămân 19 întrebări azi/)).toBeInTheDocument()
  })

  it('links to the chapter itself when the page is not a section', async () => {
    api.askStudentTutor.mockResolvedValue({ ...explained, lessons: [{ ...explained.lessons[0], sectionId: null }] })
    renderBox()
    await openBox()

    await ask('ce este calculul algebric?')

    expect(await screen.findByRole('link', { name: /Deschide lecția A7/ })).toHaveAttribute('href', '/capitole/7')
  })

  it('sends what was said before with a follow-up', async () => {
    renderBox()
    await openBox()
    await ask('cum scot factor comun?')
    await screen.findByText('Scrii factorul comun în fața parantezei.')

    await ask('și la 2x + 4?')

    expect(api.askStudentTutor).toHaveBeenLastCalledWith('și la 2x + 4?', [
      { role: 'user', text: 'cum scot factor comun?' },
      { role: 'bot', text: 'Scrii factorul comun în fața parantezei.' },
    ])
  })

  it('clips a long answer in the history it sends back', async () => {
    api.askStudentTutor.mockResolvedValueOnce({ ...explained, text: 'a'.repeat(1500) })
    renderBox()
    await openBox()
    await ask('cum scot factor comun?')
    await screen.findByTestId('tutor-answer')

    await ask('și la 2x + 4?')

    expect(api.askStudentTutor.mock.lastCall?.[1][1].text).toHaveLength(1000)
  })

  it('says when the lessons have nothing, and that the teacher will see the question', async () => {
    api.askStudentTutor.mockResolvedValue({ answered: false, text: null, lessons: [], sentToTeacher: true, remainingToday: 19 })
    renderBox()
    await openBox()

    await ask('ce e logaritmul?')

    const answer = await screen.findByTestId('tutor-answer')
    expect(answer).toHaveTextContent('Nu am asta în manual.')
    expect(answer).toHaveTextContent(/profesorului tău/)
    expect(within(answer).queryByRole('link')).not.toBeInTheDocument()
  })

  it('is closed while a graded test is open', async () => {
    api.getStudentTutor.mockResolvedValue({ ...open, openTest: 'Simulare EN' })
    renderBox()

    const dialog = await openBox()

    expect(within(dialog).getByText(/Simulare EN/)).toBeInTheDocument()
    expect(within(dialog).queryByRole('textbox')).not.toBeInTheDocument()
  })

  it('shows the server\'s reason when a question is refused', async () => {
    api.askStudentTutor.mockRejectedValue(new ApiError(429, 'Ai folosit toate cele 20 de întrebări de azi.'))
    renderBox()
    await openBox()

    await ask('încă una')

    expect(await screen.findByRole('alert')).toHaveTextContent('Ai folosit toate cele 20 de întrebări de azi.')
  })

  it('cannot ask once the day\'s questions are used up', async () => {
    api.getStudentTutor.mockResolvedValue({ ...open, remainingToday: 0 })
    renderBox()
    await openBox()

    expect(screen.getByRole('button', { name: 'Întreabă' })).toBeDisabled()
    expect(screen.getByText(/Ai folosit toate întrebările de azi/)).toBeInTheDocument()
  })
})
