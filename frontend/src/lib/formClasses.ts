/**
 * A native dropdown, the same everywhere: 44 px tall with 16 px text on a phone (iPhone zooms the page in on anything
 * smaller), compact from 640 px up.
 */
export const selectClass =
  'h-9 rounded-lg border border-border bg-background px-2 text-sm outline-none max-sm:h-11 max-sm:text-base focus-visible:border-ring focus-visible:ring-3 focus-visible:ring-ring/50 disabled:opacity-50'

/**
 * One answer of a grilă, wherever a student reads it: at least 44 px tall, with 16 px text on a phone. Pair it with
 * MathContent's `large`, so the digits of a fraction are full size too.
 */
export const optionRowClass = 'flex min-h-11 items-center gap-3 rounded-lg border p-2 text-sm max-sm:text-base'

/** The radio of such a row, a size bigger on a phone. */
export const optionRadioClass = 'size-4 shrink-0 max-sm:size-5'

/** Where a student reads a question: 16 px on a phone, compact from 640 px up. */
export const readingTextClass = 'max-sm:text-base'
