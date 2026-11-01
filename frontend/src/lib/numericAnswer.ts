import type { QuizItemType } from '@/lib/api'

/** Matches NumericAnswer.MAX_LENGTH on the server. */
export const MAX_NUMBER_LENGTH = 40

const NUMBER = /^[+\-−–]?\s*(?:\d+\s+\d+\s*[/:]\s*\d+|\d+\s*[/:]\s*\d+|\d*[.,]\d*(?:\(\d+\))?|\d+)$/

/**
 * Whether the text reads as a number the server can mark: only to warn while typing, the server decides. Same forms as
 * NumericAnswer: 12, 0,5, 1/2, 1 1/2 and the periodic 0,(3).
 */
export function isNumber(text: string): boolean {
  const trimmed = text.trim()
  return /\d/.test(trimmed) && NUMBER.test(trimmed) && !/[/:]\s*0+$/.test(trimmed)
}

/** How each kind of question is named next to its number. */
export function itemTypeLabel(type: QuizItemType): string {
  if (type === 'SINGLE_CHOICE') return 'Grilă'
  return type === 'NUMERIC' ? 'Răspuns numeric' : 'Cu rezolvare'
}
