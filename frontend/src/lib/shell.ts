/** Small pure helpers of the app shell (AppShell), kept apart so the component file exports only components. */

/** Whether going from `prev` to `next` is another page, not the same page opening one of its own (a dialog). */
export function isNewPage(prev: string, next: string): boolean {
  if (prev === next) return false
  return !(next.startsWith(`${prev}/`) || prev.startsWith(`${next}/`))
}
