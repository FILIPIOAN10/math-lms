/** Small pure helpers of the app shell (AppShell), kept apart so the component file exports only components. */

/** "Ana Popescu" -> "AP": the round mark next to the name. */
export function initials(fullName: string): string {
  const words = fullName.trim().split(/\s+/).filter(Boolean)
  return words.slice(0, 2).map((w) => w[0]?.toLocaleUpperCase('ro') ?? '').join('') || '?'
}

/** Whether going from `prev` to `next` is another page, not the same page opening one of its own (a dialog). */
export function isNewPage(prev: string, next: string): boolean {
  if (prev === next) return false
  return !(next.startsWith(`${prev}/`) || prev.startsWith(`${next}/`))
}
