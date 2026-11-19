import { describe, expect, it } from 'vitest'
import { lessonHref } from '@/lib/lessons'

const link = (sectionId: number | null, bookPage: number) => ({
  chapterId: 3, chapterCode: 'A3', chapterTitle: 'Fracții', sectionId, title: 'Adunarea fracțiilor', bookPage,
})

describe('lessonHref', () => {
  it('names the section and the page of the book, so the chapter can open its written-out page', () => {
    expect(lessonHref(link(8, 21))).toBe('/capitole/3?sectiune=8&pagina=21')
    expect(lessonHref(link(null, 21))).toBe('/capitole/3?pagina=21')
  })

  it('leaves the page out when the link has none', () => {
    expect(lessonHref(link(8, 0))).toBe('/capitole/3?sectiune=8')
    expect(lessonHref(link(null, 0))).toBe('/capitole/3')
  })
})
