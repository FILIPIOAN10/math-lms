/** A list shows its search and filters from this many rows: a shorter one is read at a glance. */
export const FILTER_FROM = 5

/** Lower case and without diacritics, so "Stefan" finds "Ștefan" and "ana" finds "Ană". */
export function searchKey(text: string): string {
  return text.normalize('NFD').replace(/[\u0300-\u036f]/g, '').toLocaleLowerCase('ro')
}

/**
 * Whether a row matches what was typed: every word of the query must be in one of the row's fields, in any order
 * ("ana 7a" finds the student Ana in class 7A). An empty query matches everything.
 */
export function matchesQuery(query: string, ...fields: (string | null | undefined)[]): boolean {
  const words = searchKey(query.trim()).split(/\s+/).filter(Boolean)
  if (words.length === 0) return true
  const keys = fields.filter((f): f is string => Boolean(f)).map(searchKey)
  return words.every((word) => keys.some((key) => key.includes(word)))
}
