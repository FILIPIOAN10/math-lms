import { describe, expect, it } from 'vitest'
import { isNumber, itemTypeLabel } from '@/lib/numericAnswer'

describe('isNumber', () => {
  it.each(['12', '0,5', '0.5', ',5', '1/2', '1:2', '-3/4', '−3/4', '+7', '1 1/2', '0,(3)', '1,2(3)', ' 3 / 4 '])('accepts %s', (text) => {
    expect(isNumber(text)).toBe(true)
  })

  it.each(['', '  ', 'abc', '20 cm', 'x=3', '2√3', '1/0', '1/', '/2', '1,2,3', '1 2', '0,(', '3-', ','])('refuses %s', (text) => {
    expect(isNumber(text)).toBe(false)
  })
})

describe('itemTypeLabel', () => {
  it('names each kind of question', () => {
    expect(itemTypeLabel('SINGLE_CHOICE')).toBe('Grilă')
    expect(itemTypeLabel('OPEN')).toBe('Cu rezolvare')
    expect(itemTypeLabel('NUMERIC')).toBe('Răspuns numeric')
  })
})
