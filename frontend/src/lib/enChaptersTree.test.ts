import { describe, expect, it } from 'vitest'
import type { EnChapterSummary } from '@/lib/api'
import { photoIndexOf, rootsOf, sectionPageStatus, treeRows } from '@/lib/enChapters'

function page(id: number, code: string, parentId: number | null, bookPage: number | null = null,
              area: EnChapterSummary['area'] = 'ALGEBRA'): EnChapterSummary {
  return { id, code, title: `Titlu ${code}`, area, position: id, parentId, bookPage, photoCount: 0, exampleCount: 0 }
}

const LIST = [
  page(1, 'AI', null, 9),
  page(2, 'AIS1', 1, 9),
  page(3, 'AIS1P1', 2, 9),
  page(4, 'AIS2', 1, 12),
  page(5, 'AII', null, 22),
  page(6, 'GI', null, 113, 'GEOMETRY'),
]

describe('rootsOf', () => {
  it('keeps the chapters and drops their sections and points', () => {
    expect(rootsOf(LIST).map((c) => c.code)).toEqual(['AI', 'AII', 'GI'])
  })

  it('treats a page whose parent is not in the list as a chapter, so nothing is hidden', () => {
    expect(rootsOf([page(7, 'X1', 999)]).map((c) => c.code)).toEqual(['X1'])
  })
})

describe('treeRows', () => {
  it('lists chapters closed: only the chapters, each with how many sections it has', () => {
    const rows = treeRows(LIST, 'ALGEBRA', new Set())

    expect(rows.map((r) => r.chapter.code)).toEqual(['AI', 'AII'])
    expect(rows[0].childCount).toBe(2)
    expect(rows[1].childCount).toBe(0)
  })

  it('opens a chapter into its sections and their points, in book order, with depth', () => {
    const rows = treeRows(LIST, 'ALGEBRA', new Set([1]))

    expect(rows.map((r) => [r.chapter.code, r.depth])).toEqual([['AI', 0], ['AIS1', 1], ['AIS1P1', 2], ['AIS2', 1], ['AII', 0]])
  })

  it('knows the first and the last among siblings, which is what up and down act on', () => {
    const rows = treeRows(LIST, 'ALGEBRA', new Set([1]))
    const flags = Object.fromEntries(rows.map((r) => [r.chapter.code, [r.first, r.last]]))

    expect(flags.AI).toEqual([true, false])
    expect(flags.AII).toEqual([false, true])
    expect(flags.AIS1).toEqual([true, false])
    expect(flags.AIS2).toEqual([false, true])
  })

  it('shows one part at a time', () => {
    expect(treeRows(LIST, 'GEOMETRY', new Set()).map((r) => r.chapter.code)).toEqual(['GI'])
  })
})

describe('sectionPageStatus', () => {
  const chapters = [
    { ...page(1, 'AI', null, 9), photoCount: 5 }, // pages 9..13 are uploaded
    page(2, 'AIS1', 1, 10),
    page(3, 'AIS1P1', 2, 12),
    page(4, 'AIS9', 1, 40),
    page(5, 'AIS0', 1, null),
  ]

  it('says a section is in the material when its page is among the uploaded ones', () => {
    expect(sectionPageStatus(chapters[1], chapters)).toBe('in-materia')
    expect(sectionPageStatus(chapters[2], chapters)).toBe('in-materia') // a point looks up through its section
  })

  it('says a page is missing when the chapter has not been uploaded that far', () => {
    expect(sectionPageStatus(chapters[3], chapters)).toBe('missing')
  })

  it('says so when the section has no page number at all', () => {
    expect(sectionPageStatus(chapters[4], chapters)).toBe('no-page')
  })
})

describe('photoIndexOf', () => {
  it('counts pages from the first page of the chapter', () => {
    expect(photoIndexOf(14, 9, 13)).toBe(5)
    expect(photoIndexOf(9, 9, 13)).toBe(0)
  })

  it('is null when a page is unknown or not uploaded', () => {
    expect(photoIndexOf(null, 9, 13)).toBeNull()
    expect(photoIndexOf(12, null, 13)).toBeNull()
    expect(photoIndexOf(30, 9, 13)).toBeNull() // beyond the uploaded pages
    expect(photoIndexOf(5, 9, 13)).toBeNull() // before the chapter
  })
})
