/** "4 oct. 2026, 10:30" in Romanian, or an em dash when there is no date yet. */
export function formatDate(iso: string | null): string {
  return iso ? new Date(iso).toLocaleString('ro-RO', { dateStyle: 'medium', timeStyle: 'short' }) : '—'
}
