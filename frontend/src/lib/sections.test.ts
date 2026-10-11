import { describe, expect, it } from 'vitest'
import { numberItems, parseSection } from '@/lib/sections'

describe('numberItems', () => {
  it('keeps 1, 2, 3 for a test without headings', () => {
    const numbered = numberItems([{ section: null }, {}, { section: null }])
    expect(numbered.map((n) => n.label)).toEqual(['1', '2', '3'])
    expect(numbered.every((n) => n.heading === null)).toBe(true)
  })

  it('starts again at 1 under each heading and labels by the roman numeral', () => {
    const I = 'SUBIECTUL I\nÎncercuiește litera corespunzătoare răspunsului corect.'
    const II = 'SUBIECTUL al II-lea'
    const numbered = numberItems([{ section: I }, { section: I }, { section: II }, { section: II }, { section: II }])

    expect(numbered.map((n) => n.label)).toEqual(['I.1', 'I.2', 'II.1', 'II.2', 'II.3'])
    expect(numbered.map((n) => n.local)).toEqual([1, 2, 1, 2, 3])
    expect(numbered[0].heading).toEqual({ title: 'SUBIECTUL I', note: 'Încercuiește litera corespunzătoare răspunsului corect.' })
    expect(numbered[1].heading).toBeNull()
    expect(numbered[2].heading).toEqual({ title: 'SUBIECTUL al II-lea', note: null })
  })

  it('numbers a heading without a roman numeral by its place', () => {
    const numbered = numberItems([{ section: 'Partea A' }, { section: 'Partea B' }])
    expect(numbered.map((n) => n.label)).toEqual(['1.1', '2.1'])
  })
})

describe('parseSection', () => {
  it('splits the title from the instruction', () => {
    expect(parseSection('SUBIECTUL al III-lea\nScrie rezolvările complete.\n(30 de puncte)')).toEqual({
      title: 'SUBIECTUL al III-lea',
      note: 'Scrie rezolvările complete.\n(30 de puncte)',
    })
  })
})
