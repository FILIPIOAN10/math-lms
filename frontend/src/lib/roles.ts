import { type Role } from '@/lib/api'

const LABELS: Record<Role, string> = { ADMIN: 'Profesor', STUDENT: 'Elev', PARENT: 'Părinte' }

/** The role as people say it ("Profesor"), never the enum ("ADMIN"). */
export function roleLabel(role: Role | null): string {
  return role ? LABELS[role] : ''
}

/** Which count from the teacher's work queue a link carries. */
export type NavCount = 'grading' | 'pending'

export interface NavItem {
  to: string
  label: string
  count?: NavCount
  testId?: string
  /** Other addresses that belong to this link (it is shown as the current page there too). */
  also?: string[]
}

const NAV: Record<Role, NavItem[]> = {
  STUDENT: [
    { to: '/', label: 'Acasă' },
    { to: '/quizzes', label: 'Testele mele' },
    { to: '/review', label: 'Recapitulare' },
    { to: '/progress', label: 'Progresul meu', testId: 'my-progress' },
    { to: '/content', label: 'Conținut' },
  ],
  PARENT: [
    { to: '/', label: 'Acasă' },
    { to: '/parent', label: 'Copiii mei', testId: 'my-children' },
    { to: '/content', label: 'Conținut' },
  ],
  // Five links (T6): quizzes and homework are one "Teste" area with tabs; approving, inviting and linking people is
  // one "Elevi" page.
  ADMIN: [
    { to: '/', label: 'Acasă' },
    { to: '/admin/content', label: 'Clase', also: ['/admin/classes'] },
    { to: '/admin/quizzes', label: 'Teste', also: ['/admin/assignments'], testId: 'admin-tests' },
    { to: '/admin/grading', label: 'Corectură', count: 'grading' },
    { to: '/admin/students', label: 'Elevi', count: 'pending', also: ['/admin/pending', '/admin/links'] },
  ],
}

/** Whether `item` is the link of the page at `pathname` (its own address, a sub-page, or one it groups). */
export function isNavActive(item: NavItem, pathname: string): boolean {
  if (item.to === '/') return pathname === '/'
  return [item.to, ...(item.also ?? [])].some((p) => pathname === p || pathname.startsWith(`${p}/`))
}

/** The main navigation of one role, in the order it is shown. */
export function navItems(role: Role): NavItem[] {
  return NAV[role]
}
