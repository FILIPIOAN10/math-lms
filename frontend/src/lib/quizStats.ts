import type { OptionStat, QuizItemStat } from '@/lib/api'

/** Matches QuizOption.MAX_MISTAKE on the server. */
export const MAX_MISTAKE = 500

/** How many wrong options "Greșeli frecvente" lists: what is worth going over in class, not every slip. */
const SHOWN = 3
/** One student is a slip; at least two, and a fifth of the class, is a pattern. */
const MIN_STUDENTS = 2
const MIN_SHARE = 0.2

export interface CommonMistake {
  label: string
  option: OptionStat
}

/**
 * The wrong options most students chose, most chosen first. The option the strongest students agree on is left out:
 * it is probably the right answer, and listing it would ask the teacher to explain why it is wrong.
 */
export function commonMistakes(numbered: { item: QuizItemStat; label: string }[], graded: number): CommonMistake[] {
  return numbered
    .flatMap(({ item, label }) => item.options
      .filter((o) => !o.correct && o.letter !== item.suspect?.letter)
      .map((option) => ({ label, option })))
    .filter(({ option }) => option.chosen >= MIN_STUDENTS && option.chosen >= graded * MIN_SHARE)
    .sort((a, b) => b.option.chosen - a.option.chosen)
    .slice(0, SHOWN)
}
