import { describe, expect, it } from 'vitest'
import { parseItems } from '@/lib/importItems'

const Q1 = `
1. Soluția ecuației $2x + 5 = 13$ este: (2p)
a) $x = 3$
*b) $x = 4$
c) $x = 9$
d) $x = -4$
R: $2x = 8 \\Rightarrow x = 4$
I: Mută termenul liber în dreapta.
I: Împarte ambii membri la 2.

2. Câte soluții reale are ecuația $x^2 + 4 = 0$? (2p)
*a) 0
b) 1
c) 2

3. Rezolvă ecuația $x^2 - 7x + 12 = 0$.
Scrie toate etapele. (5p)
R: $\\Delta = 1$ (2p);
$x_1 = 4$, $x_2 = 3$ (3p)
`

describe('parseItems', () => {
  it('reads multiple-choice and open items with points, the starred option, solution and hints', () => {
    const { items, errors } = parseItems(Q1)

    expect(errors).toEqual([])
    expect(items.map((i) => [i.type, i.points])).toEqual([['SINGLE_CHOICE', 2], ['SINGLE_CHOICE', 2], ['OPEN', 5]])
    expect(items[0].statement).toBe('Soluția ecuației $2x + 5 = 13$ este:')
    expect(items[0].options?.map((o) => [o.position, o.text, o.correct])).toEqual([
      [0, '$x = 3$', false], [1, '$x = 4$', true], [2, '$x = 9$', false], [3, '$x = -4$', false],
    ])
    expect(items[0].solution).toBe('$2x = 8 \\Rightarrow x = 4$')
    expect(items[0].hints).toEqual(['Mută termenul liber în dreapta.', 'Împarte ambii membri la 2.'])
    expect(items[1].solution).toBeNull()
  })

  it('joins a statement and a solution that run over several lines', () => {
    const open = parseItems(Q1).items[2]

    expect(open.statement).toBe('Rezolvă ecuația $x^2 - 7x + 12 = 0$.\nScrie toate etapele.')
    expect(open.solution).toBe('$\\Delta = 1$ (2p);\n$x_1 = 4$, $x_2 = 3$ (3p)')
    expect(open.options).toBeNull()
  })

  it('gives 1 point when the statement does not say how many', () => {
    expect(parseItems('1. Cât face $1+1$?\n*a) 2\nb) 3').items[0].points).toBe(1)
  })

  it('numbers the items from 0 in the order they appear', () => {
    expect(parseItems(Q1).items.map((i) => i.position)).toEqual([0, 1, 2])
  })

  it('explains every mistake with the item it belongs to', () => {
    const { errors } = parseItems([
      'Text liber înainte de primul subiect',
      '1. Fără variantă corectă',
      'a) unu',
      'b) doi',
      '2. Două corecte',
      '*a) unu',
      '*b) doi',
      '3. O singură variantă',
      '*a) unu',
      'ceva rătăcit după variante',
    ].join('\n'))

    expect(errors).toEqual([
      'Linia 1: textul trebuie să înceapă cu un subiect numerotat („1. …”) sau cu „SUBIECTUL I”.',
      'Subiectul 3, linia 10: nu știu ce e „ceva rătăcit după variante” (variantele încep cu a), b)…, rezolvarea cu R:, indiciile cu I:).',
      'Subiectul 1: marchează cu * exact o variantă corectă (acum: 0).',
      'Subiectul 2: marchează cu * exact o variantă corectă (acum: 2).',
      'Subiectul 3: o grilă are nevoie de cel puțin 2 variante.',
    ])
  })

  it('refuses more than five hints', () => {
    const text = '1. Enunț\n' + Array.from({ length: 6 }, (_, i) => `I: indiciul ${i + 1}`).join('\n')

    expect(parseItems(text).errors).toEqual(['Subiectul 1: cel mult 5 indicii.'])
  })

  it('finds nothing in an empty text', () => {
    expect(parseItems('  \n\n')).toEqual({ items: [], errors: [] })
  })

  describe('Evaluare Națională papers', () => {
    const EN = `
SUBIECTUL I
Încercuiește litera corespunzătoare răspunsului corect. (30 de puncte)
5p 1. Rezultatul calculului $6 - 6 : 2$ este egal cu:
a) 0
*b) 3
c) 6
d) 9
5p 2. Cel mai mic număr natural de două cifre este:
*a) 10
b) 11
SUBIECTUL al II-lea
5p 1. Ana are 12 mere. Câte mere rămân dacă dă 5?
*a) 7
b) 8
SUBIECTUL al III-lea
Scrie rezolvările complete. (30 de puncte)
10p 1. Rezolvă ecuația $2x + 3 = 11$.
R: $x = 4$
`

    it('keeps every question separate, under its heading, with the points written first', () => {
      const { items, errors } = parseItems(EN)

      expect(errors).toEqual([])
      expect(items).toHaveLength(4)
      expect(items.map((i) => i.points)).toEqual([5, 5, 5, 10])
      expect(items.map((i) => i.type)).toEqual(['SINGLE_CHOICE', 'SINGLE_CHOICE', 'SINGLE_CHOICE', 'OPEN'])
      expect(items[0].statement).toBe('Rezultatul calculului $6 - 6 : 2$ este egal cu:')
      expect(items[0].section).toBe('SUBIECTUL I\nÎncercuiește litera corespunzătoare răspunsului corect. (30 de puncte)')
      expect(items[1].section).toBe(items[0].section)
      expect(items[2].section).toBe('SUBIECTUL al II-lea')
      expect(items[3].section).toBe('SUBIECTUL al III-lea\nScrie rezolvările complete. (30 de puncte)')
      expect(items[3].solution).toBe('$x = 4$')
    })

    it('lets "(Np)" at the end win over points written first', () => {
      const { items } = parseItems('SUBIECTUL I\n5p 1. Enunț (3p)\nR: x')
      expect(items[0].points).toBe(3)
    })

    it('leaves a test without headings without sections', () => {
      const { items } = parseItems('1. Enunț (2p)\nR: x')
      expect(items[0].section).toBeNull()
    })

    it('marks a question that needs a figure with "[imagine]"', () => {
      const { items, errors } = parseItems([
        'SUBIECTUL al II-lea',
        '5p 1. În figura alăturată, AB =',
        '[imagine]',
        '*a) 4',
        'b) 6',
        '5p 2. Fără figură',
        '*a) 1',
        'b) 2',
      ].join('\n'))

      expect(errors).toEqual([])
      expect(items.map((i) => i.imageRequired)).toEqual([true, false])
      expect(items[0].statement).toBe('În figura alăturată, AB =')
    })

    it('names the section in an error', () => {
      const { errors } = parseItems('SUBIECTUL al II-lea\n5p 1. Enunț\na) 1\nb) 2')
      expect(errors).toEqual(['SUBIECTUL al II-lea, subiectul 1: marchează cu * exact o variantă corectă (acum: 0).'])
    })
  })
})
