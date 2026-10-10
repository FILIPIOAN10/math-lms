/** "4 oct. 2026, 10:30" in Romanian, or an em dash when there is no date yet. */
export function formatDate(iso: string | null): string {
  return iso ? new Date(iso).toLocaleString('ro-RO', { dateStyle: 'medium', timeStyle: 'short' }) : '—'
}

/** "1 minut", "45 minute" — Romanian singular only for exactly one. */
export function formatMinutes(minutes: number): string {
  return `${minutes} ${minutes === 1 ? 'minut' : 'minute'}`
}

/** "1 subiect", "4 subiecte", "20 de subiecte": Romanian adds "de" when the last two digits are 00 or 20–99. */
export function roCount(n: number, one: string, many: string): string {
  if (n === 1) return `1 ${one}`
  const lastTwo = n % 100
  return `${n}${n !== 0 && (lastTwo === 0 || lastTwo >= 20) ? ' de ' : ' '}${many}`
}
