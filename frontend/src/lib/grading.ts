import type { AdminItemReview } from '@/lib/api'

/**
 * The teacher's grade for one open answer while it is being edited: one score, or - for a problem with requirements
 * a), b)... (EN Subiectul III) - a score per requirement, plus the comment on the whole answer.
 */
export interface GradeValue {
  points: number | null
  /** By part id; null = not graded yet. Empty for an answer without requirements. */
  parts: Record<number, number | null>
  comment: string
}

export const hasParts = (item: AdminItemReview) => (item.parts?.length ?? 0) > 0

export function initialGrade(item: AdminItemReview): GradeValue {
  return {
    points: item.awardedPoints,
    parts: Object.fromEntries((item.parts ?? []).map((p) => [p.id, p.awardedPoints])),
    comment: item.teacherComment ?? '',
  }
}

export function sameGrade(a: GradeValue, b: GradeValue): boolean {
  if (a.points !== b.points || a.comment.trim() !== b.comment.trim()) return false
  const keys = new Set([...Object.keys(a.parts), ...Object.keys(b.parts)])
  return [...keys].every((k) => (a.parts[Number(k)] ?? null) === (b.parts[Number(k)] ?? null))
}

/** Something the server can store: a score, or - with requirements - any state of them (some may still be missing). */
export function gradeReady(item: AdminItemReview, g: GradeValue): boolean {
  return hasParts(item) || g.points !== null
}

/** The answer's points: the sum once every requirement has some, null before; the single score otherwise. */
export function totalOf(item: AdminItemReview, g: GradeValue): number | null {
  if (!hasParts(item)) return g.points
  const given = (item.parts ?? []).map((p) => g.parts[p.id] ?? null)
  return given.every((v) => v !== null) ? given.reduce<number>((sum, v) => sum + (v ?? 0), 0) : null
}

/** The item as the page shows it once `g` is saved (no need to fetch the paper again). */
export function gradedItem(item: AdminItemReview, g: GradeValue): AdminItemReview {
  const comment = g.comment.trim() === '' ? null : g.comment.trim()
  return {
    ...item,
    awardedPoints: totalOf(item, g),
    teacherComment: comment,
    parts: item.parts?.map((p) => ({ ...p, awardedPoints: g.parts[p.id] ?? null })),
  }
}

/** What the grade request carries for a problem with requirements. */
export function partsPayload(item: AdminItemReview, g: GradeValue): { partId: number; points: number | null }[] {
  return (item.parts ?? []).map((p) => ({ partId: p.id, points: g.parts[p.id] ?? null }))
}
