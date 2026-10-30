import { describe, expect, it } from 'vitest'
import { matchesQuery, searchKey } from '@/lib/search'

describe('matchesQuery', () => {
  it('matches everything for an empty query', () => {
    expect(matchesQuery('', 'Ana Pop')).toBe(true)
    expect(matchesQuery('   ', null)).toBe(true)
  })

  it('ignores case and Romanian diacritics, in both directions', () => {
    expect(matchesQuery('stefan', 'Ștefan Ionescu')).toBe(true)
    expect(matchesQuery('ȘTEFAN', 'stefan')).toBe(true)
    expect(matchesQuery('tarta', 'Țarță')).toBe(true) // comma and cedilla forms both fold
    expect(searchKey('Ţară')).toBe('tara')
  })

  it('wants every word, each in any field', () => {
    expect(matchesQuery('ana 7a', 'Ana Pop', 'ana@scoala.ro', '7A')).toBe(true)
    expect(matchesQuery('ana 8b', 'Ana Pop', '7A')).toBe(false)
  })

  it('skips empty fields', () => {
    expect(matchesQuery('pop', null, undefined, 'Ana Pop')).toBe(true)
    expect(matchesQuery('pop', null)).toBe(false)
  })
})
