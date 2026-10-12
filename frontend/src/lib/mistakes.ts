import { type ItemResultDto } from '@/lib/api'
import { numberItems } from '@/lib/sections'

/** S4: what a graded test says the student should go back to. */
export interface Mistakes {
  /** The questions not fully right, in order: "2", "I.3"… as numbered on screen. */
  labels: string[]
  itemIds: number[]
  /** Grile answered wrong: these (and only these) enter the daily review - an unanswered one has no answer to replay. */
  wrongChoices: number
}

/** A grilă not answered right (wrong or empty), or a graded open question that did not get all its points. */
function missed(item: ItemResultDto): boolean {
  if (item.type === 'SINGLE_CHOICE') return item.correct !== true
  return item.awardedPoints !== null && item.awardedPoints < item.points
}

export function mistakesOf(items: ItemResultDto[]): Mistakes {
  const numbered = numberItems(items).filter((n) => missed(n.item))
  return {
    labels: numbered.map((n) => n.label),
    itemIds: numbered.map((n) => n.item.itemId),
    wrongChoices: items.filter((i) => i.type === 'SINGLE_CHOICE' && i.correct === false).length,
  }
}

/** A practice of the quiz showing only these questions (the practice page reads `only`). */
export function practiceMistakesHref(quizId: number, itemIds: number[]): string {
  return `/quizzes/${quizId}/take?mode=practice&only=${itemIds.join(',')}`
}

/** The `only` filter of a practice: the item ids to show, or null for the whole quiz. */
export function parseOnly(value: string | null): Set<number> | null {
  if (!value) return null
  const ids = value.split(',').map(Number).filter((n) => Number.isInteger(n) && n > 0)
  return ids.length > 0 ? new Set(ids) : null
}
