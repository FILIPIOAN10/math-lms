import { describe, expect, it } from 'vitest'
import type { ExtractedItem } from '@/lib/api'
import { draftProblems, toDrafts, toItemInputs } from '@/lib/aiImport'

const read = (over: Partial<ExtractedItem> = {}): ExtractedItem => ({
  page: 1, section: 'SUBIECTUL I', type: 'SINGLE_CHOICE', statement: 'Cât face $2+3$?', options: ['4', '5', '6'],
  points: 5, hasFigure: false, warnings: [], ...over,
})

describe('aiImport', () => {
  it("keeps a problem's requirements a), b) with their points, the total being their sum", () => {
    const [problem] = toDrafts([read({
      type: 'OPEN', options: [], points: 5, statement: 'Se consideră $x$.',
      parts: [{ statement: 'Arată că $x=25$.', points: 2 }, { statement: 'Calculează $x^2$.', points: null }],
    })])
    problem.parts[1].points = 3

    expect(toItemInputs([problem])[0]).toMatchObject({
      points: 5,
      parts: [
        { statement: 'Arată că $x=25$.', points: 2, solution: null },
        { statement: 'Calculează $x^2$.', points: 3, solution: null },
      ],
    })
    problem.parts[0].statement = ' '
    expect(draftProblems([problem])).toEqual(['Subiectul 1: o cerință nu are enunț.'])
  })

  it('starts every question included, with no answer marked and printed points kept', () => {
    const [grila, open] = toDrafts([read(), read({ type: 'OPEN', options: [], points: null, hasFigure: true, section: null })])

    expect(grila).toMatchObject({ include: true, correct: null, points: 5, options: ['4', '5', '6'], section: 'SUBIECTUL I' })
    expect(open).toMatchObject({ points: 10, imageRequired: true, section: '', options: [] })
  })

  it('will not make the quiz until every kept grilă has its answer marked', () => {
    const drafts = toDrafts([read(), read({ type: 'OPEN', options: [] }), read({ options: ['7'] })])

    expect(draftProblems(drafts)).toEqual([
      'Subiectul 1: marchează varianta corectă.',
      'Subiectul 3: o grilă are nevoie de cel puțin 2 variante.',
    ])

    drafts[0].correct = 1
    drafts[2].include = false
    expect(draftProblems(drafts)).toEqual([])
  })

  it('numbers the problems by the questions kept, and checks text and points', () => {
    const drafts = toDrafts([read({ type: 'OPEN' }), read({ type: 'OPEN' })])
    drafts[0].include = false
    drafts[1].statement = '  '
    drafts[1].points = 101

    expect(draftProblems(drafts)).toEqual([
      'Subiectul 1: enunțul e gol.',
      'Subiectul 1: punctajul trebuie să fie între 0 și 100.',
    ])
  })

  it('a marked answer whose text was emptied is not an answer', () => {
    const [d] = toDrafts([read()])
    d.correct = 2
    d.options[2] = ' '

    expect(draftProblems([d])).toEqual(['Subiectul 1: marchează varianta corectă.'])
  })

  it('sends only the kept questions, in order, with empty variants dropped and the marked one correct', () => {
    const drafts = toDrafts([
      read({ options: ['4', '', '5'] }),
      read({ type: 'OPEN', options: [], statement: ' Rezolvă ', hasFigure: true, section: null }),
      read(),
    ])
    drafts[0].correct = 2
    drafts[1].solution = ' x = 2 '
    drafts[2].include = false

    expect(toItemInputs(drafts)).toEqual([
      {
        type: 'SINGLE_CHOICE', position: 0, statement: 'Cât face $2+3$?', points: 5, solution: null, hints: [],
        section: 'SUBIECTUL I', imageRequired: false, parts: null,
        options: [{ position: 0, text: '4', correct: false }, { position: 1, text: '5', correct: true }],
      },
      {
        type: 'OPEN', position: 1, statement: 'Rezolvă', points: 5, solution: 'x = 2', hints: [], section: null,
        imageRequired: true, options: null, parts: null,
      },
    ])
  })
})
