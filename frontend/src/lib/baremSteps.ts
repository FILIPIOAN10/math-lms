/**
 * A barem written the way the papers do it - "$…$ (2p); $…$ (3p)" - read as steps the teacher can tick. Nothing is
 * stored: the steps are the "(Np)" marks of the barem text, and what is saved stays the requirement's single score
 * (the sum of the ticked steps), so no migration and every barem already imported works.
 */
export interface BaremStep {
  text: string
  points: number
}

const MARK = /\(\s*(\d+)\s*(?:p|pct|puncte?)\s*\)/gi

/**
 * The steps of `barem`, or null when it cannot be ticked reliably: fewer than two marks (the points row is enough),
 * or marks that do not add up to `max` (the teacher's own wording, so the plain points row stays the way to grade).
 */
export function parseBaremSteps(barem: string | null | undefined, max: number): BaremStep[] | null {
  const text = barem?.trim()
  if (!text) return null
  const steps: BaremStep[] = []
  let from = 0
  for (const m of text.matchAll(MARK)) {
    const raw = text.slice(from, m.index).replace(/^\s*R:\s*/i, '').replace(/^[\s;,]+|[\s;,]+$/g, '')
    steps.push({ text: raw, points: Number(m[1]) })
    from = (m.index ?? 0) + m[0].length
  }
  if (steps.length < 2) return null
  return steps.reduce((s, x) => s + x.points, 0) === max ? steps : null
}

export const sumOf = (steps: BaremStep[], ticked: boolean[]): number =>
  steps.reduce((sum, s, i) => sum + (ticked[i] ? s.points : 0), 0)

/**
 * Which steps a saved score stands for: none for no score or 0, all for the full score, else the one subset of steps
 * adding up to it. Two subsets (or none) give the same sum: nothing is ticked, the score still shows in the points row.
 */
export function stepsTicked(steps: BaremStep[], value: number | null): boolean[] {
  const none = steps.map(() => false)
  if (value === null || value === 0) return none
  const found: boolean[][] = []
  for (let mask = 1; mask < 1 << steps.length && found.length < 2; mask++) {
    const ticked = steps.map((_, i) => (mask & (1 << i)) !== 0)
    if (sumOf(steps, ticked) === value) found.push(ticked)
  }
  return found.length === 1 ? found[0] : none
}
