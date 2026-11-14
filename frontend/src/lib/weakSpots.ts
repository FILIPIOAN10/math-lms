import { type WeakSpot } from '@/lib/api'
import { roCount } from '@/lib/format'

/** From this many days since the last answer, a chapter counts as left aside. */
const STALE_DAYS = 30

/** Why a chapter is on the „La ce stau prost?” list, in a sentence or a few, from the numbers only. */
export function weakSpotReason(spot: WeakSpot, now: Date = new Date()): string {
  const parts = [`Ai ${spot.percent}% din puncte, din ${roCount(spot.questions, 'întrebare', 'întrebări')}.`]
  if (spot.recentWrong > 0) {
    parts.push(`În ultimele 2 săptămâni ai greșit ${spot.recentWrong} din ${spot.recentAnswered}.`)
  }
  if (spot.sureWrong > 0) {
    parts.push(`${spot.sureWrong === 1 ? 'O dată' : `De ${spot.sureWrong} ori`} ai zis „Știu sigur” și era greșit.`)
  }
  if (spot.lastAnsweredAt) {
    const days = Math.floor((now.getTime() - new Date(spot.lastAnsweredAt).getTime()) / 86_400_000)
    if (days >= STALE_DAYS) parts.push(`Nu ai mai lucrat la el de ${roCount(days, 'zi', 'zile')}.`)
  }
  return parts.join(' ')
}
