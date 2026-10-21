import { describe, expect, it } from 'vitest'
import type { ExtractedItem } from '@/lib/api'
import {
  boxFromDrag, boxToPhoto, draftProblems, moveBox, photoToBox, resizeBox, toDrafts, toFigureInputs, toItemInputs,
} from '@/lib/aiImport'

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
    expect(draftProblems([problem])).toEqual(['Întrebarea 1: o cerință nu are enunț.'])
  })

  it('starts every question included, with no answer marked and printed points kept', () => {
    const [grila, open] = toDrafts([read(), read({ type: 'OPEN', options: [], points: null, hasFigure: true, section: null })])

    expect(grila).toMatchObject({ include: true, correct: null, points: 5, options: ['4', '5', '6'], section: 'SUBIECTUL I' })
    expect(open).toMatchObject({ points: 10, imageRequired: true, section: '', options: [] })
  })

  it('will not make the quiz until every kept grilă has its answer marked', () => {
    const drafts = toDrafts([read(), read({ type: 'OPEN', options: [] }), read({ options: ['7'] })])

    expect(draftProblems(drafts)).toEqual([
      'Întrebarea 1: marchează varianta corectă.',
      'Întrebarea 3: o grilă are nevoie de cel puțin 2 variante.',
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
      'Întrebarea 1: enunțul e gol.',
      'Întrebarea 1: punctajul trebuie să fie între 0 și 100.',
    ])
  })

  it('a marked answer whose text was emptied is not an answer', () => {
    const [d] = toDrafts([read()])
    d.correct = 2
    d.options[2] = ' '

    expect(draftProblems([d])).toEqual(['Întrebarea 1: marchează varianta corectă.'])
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
        section: 'SUBIECTUL I', imageRequired: false, parts: null, enChapterId: null,
        options: [{ position: 0, text: '4', correct: false }, { position: 1, text: '5', correct: true }],
      },
      {
        type: 'OPEN', position: 1, statement: 'Rezolvă', points: 5, solution: 'x = 2', hints: [], section: null,
        imageRequired: true, options: null, parts: null, enChapterId: null,
      },
    ])
  })

  it('keeps the EN chapter the AI proposed (or the teacher picked) and sends it as the chapter id', () => {
    const enChapters = [
      { id: 1, code: 'A1', title: 'Ordinea efectuării operațiilor', area: 'ALGEBRA' as const, position: 1, parentId: null, bookPage: null, photoCount: 0, exampleCount: 0 },
      { id: 19, code: 'G8', title: 'Corpuri', area: 'GEOMETRY' as const, position: 19, parentId: null, bookPage: null, photoCount: 0, exampleCount: 0 },
      { id: 20, code: 'G9', title: 'Drepte și plane', area: 'GEOMETRY' as const, position: 20, parentId: null, bookPage: null, photoCount: 0, exampleCount: 0 },
    ]
    const drafts = toDrafts([
      read({ enChapter: 'A1', enChapterReason: 'Un calcul.' }),
      read({
        type: 'OPEN', options: [], enChapter: 'G8', points: null,
        parts: [{ statement: 'Volumul', points: 2, enChapter: 'G8' }, { statement: 'Distanța', points: 3, enChapter: 'G9' }],
      }),
      read({ enChapter: 'X1' }), // a code the list no longer has
    ])
    drafts[0].correct = 0
    drafts[2].correct = 0
    expect(drafts[0].enChapter).toBe('A1')
    expect(drafts[0].enChapterReason).toBe('Un calcul.')
    drafts[1].parts[0].enChapter = '' // the teacher sets a) back to automatic

    const inputs = toItemInputs(drafts, enChapters)
    expect(inputs.map((i) => i.enChapterId)).toEqual([1, 19, null])
    expect(inputs[1].parts?.map((p) => p.enChapterId)).toEqual([null, 20])
  })

  it('keeps the drawing the AI boxed, and nothing for a question without one', () => {
    const [withBox, without] = toDrafts([
      read({ type: 'OPEN', options: [], hasFigure: true, figurePage: 2, figureBox: [200, 300, 500, 500] }),
      read({ hasFigure: true, figurePage: null, figureBox: [] }),
    ])

    expect(withBox.figure).toEqual({ page: 2, box: [200, 300, 500, 500] })
    expect(without.figure).toBeNull()
  })

  it('turns the 0-1000 box into the fractions the picture is drawn with, and back', () => {
    expect(boxToPhoto([200, 300, 500, 500])).toEqual({ x: 0.3, y: 0.2, w: 0.2, h: 0.3 })
    expect(photoToBox({ x: 0.3, y: 0.2, w: 0.2, h: 0.3 })).toEqual([200, 300, 500, 500])
    expect(photoToBox({ x: -0.1, y: 0.95, w: 0.5, h: 0.2 })).toEqual([950, 0, 1000, 400]) // kept on the page
  })

  it('makes a box from a drag in any direction, and none from a click', () => {
    expect(boxFromDrag({ x: 0.6, y: 0.5 }, { x: 0.2, y: 0.1 })).toEqual({ x: 0.2, y: 0.1, w: expect.closeTo(0.4), h: expect.closeTo(0.4) })
    expect(boxFromDrag({ x: 0.5, y: 0.5 }, { x: 0.505, y: 0.6 })).toBeNull() // too thin to be a drawing
  })

  it('resizes a box by one grip, leaving the other sides where they were', () => {
    const box = { x: 0.2, y: 0.2, w: 0.4, h: 0.4 }

    expect(resizeBox(box, 'e', { x: 0.9, y: 0 })).toEqual({ x: 0.2, y: 0.2, w: expect.closeTo(0.7), h: expect.closeTo(0.4) })
    expect(resizeBox(box, 'nw', { x: 0.1, y: 0.05 }))
      .toEqual({ x: 0.1, y: 0.05, w: expect.closeTo(0.5), h: expect.closeTo(0.55) })
  })

  it('never flips a box or lets it leave the photo while resizing', () => {
    const box = { x: 0.2, y: 0.2, w: 0.4, h: 0.4 }

    const pulledPast = resizeBox(box, 'w', { x: 0.95, y: 0.3 }) // the left side dragged past the right one
    expect(pulledPast.x + pulledPast.w).toBeCloseTo(0.6)
    expect(pulledPast.w).toBeCloseTo(0.015)
    expect(resizeBox(box, 'se', { x: 1.3, y: 1.2 })).toEqual({ x: 0.2, y: 0.2, w: expect.closeTo(0.8), h: expect.closeTo(0.8) })
  })

  it('moves a box without changing its size, stopping at the edges', () => {
    const box = { x: 0.2, y: 0.2, w: 0.4, h: 0.4 }

    expect(moveBox(box, 0.1, -0.05)).toEqual({ x: expect.closeTo(0.3), y: expect.closeTo(0.15), w: 0.4, h: 0.4 })
    expect(moveBox(box, 0.9, -0.9)).toEqual({ x: expect.closeTo(0.6), y: 0, w: 0.4, h: 0.4 })
  })

  it('sends the figures of the kept questions only, numbered as they will be in the quiz', () => {
    const drafts = toDrafts([
      read({ hasFigure: true, figurePage: 1, figureBox: [100, 100, 300, 300] }),
      read({ hasFigure: true, figurePage: 1, figureBox: [400, 100, 600, 300] }),
      read({ hasFigure: true, figurePage: 2, figureBox: [100, 500, 300, 900] }),
    ])
    drafts[1].include = false

    expect(toFigureInputs(drafts)).toEqual([
      { index: 0, page: 1, box: [100, 100, 300, 300] },
      { index: 1, page: 2, box: [100, 500, 300, 900] },
    ])
  })
})
