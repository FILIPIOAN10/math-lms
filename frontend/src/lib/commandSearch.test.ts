import { describe, expect, it } from 'vitest'
import { chapterEntries, pageEntries, quizEntries, searchCommands, studentEntries } from '@/lib/commandSearch'
import type { AdminUserSummary, EnChapterSummary, QuizSummary } from '@/lib/api'

const quiz = (id: number, title: string, schoolClassName: string | null = null) =>
  ({ id, title, schoolClassName }) as QuizSummary
const student = (id: number, fullName: string) => ({ id, fullName, email: `e${id}@scoala.ro` }) as AdminUserSummary
const chapter = (id: number, title: string, parentId: number | null = null) =>
  ({ id, code: `A${id}`, title, parentId }) as EnChapterSummary

describe('commandSearch', () => {
  it("lists a teacher's pages once each, with the rare ones, homework and the account", () => {
    const labels = pageEntries('TEACHER').map((e) => e.label)
    expect(labels).toEqual(['Acasă', 'Clase', 'Teste', 'Corectură', 'Elevi', 'Capitole EN', 'Import din poze', 'Teme', 'Contul meu'])
  })

  it('gives a parent only their home and account', () => {
    expect(pageEntries('PARENT').map((e) => e.to)).toEqual(['/', '/account'])
  })

  it('shows the pages while nothing is typed, never the long lists', () => {
    const entries = [...pageEntries('ADMIN'), ...quizEntries([quiz(1, 'Simulare EN')])]
    const sections = searchCommands(entries, '  ')
    expect(sections.map((s) => s.group)).toEqual(['Pagini'])
  })

  it('finds a page by a word its name does not have, and without diacritics', () => {
    const sections = searchCommands(pageEntries('ADMIN'), 'note')
    expect(sections[0].entries.map((e) => e.label)).toContain('Corectură')
    expect(searchCommands(pageEntries('ADMIN'), 'corectura')[0].entries[0].to).toBe('/admin/grading')
  })

  it('groups tests and students in a fixed order, a few of each', () => {
    const entries = [
      ...studentEntries([student(1, 'Ana Pop'), student(2, 'Andrei Ana')]),
      ...quizEntries([quiz(3, 'Ana are mere', '8B')]),
    ]
    const sections = searchCommands(entries, 'ana', 1)
    expect(sections.map((s) => [s.group, s.entries.map((e) => e.label)])).toEqual([
      ['Teste', ['Ana are mere']],
      ['Elevi', ['Ana Pop']],
    ])
  })

  it('searches the hint too (a class, an email)', () => {
    const sections = searchCommands(quizEntries([quiz(3, 'Ecuații', '8B'), quiz(4, 'Funcții', '7A')]), '8b')
    expect(sections[0].entries.map((e) => e.label)).toEqual(['Ecuații'])
  })

  it('opens chapters where the role reads them, sections left out', () => {
    const list = [chapter(1, 'Numere reale'), chapter(2, 'Intervale', 1)]
    expect(chapterEntries(list, 'STUDENT').map((e) => e.to)).toEqual(['/capitole/1'])
    expect(chapterEntries(list, 'ADMIN').map((e) => e.to)).toEqual(['/admin/en-chapters/1'])
  })
})
