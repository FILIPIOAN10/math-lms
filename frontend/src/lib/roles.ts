import { BookOpen, ClipboardList, House, Library, PenLine, RotateCcw, School, TrendingUp, Users, type LucideIcon } from 'lucide-react'
import { type Role } from '@/lib/api'

const LABELS: Record<Role, string> = { ADMIN: 'Administrator', TEACHER: 'Profesor', STUDENT: 'Elev', PARENT: 'Părinte' }

/** Staff: who works in the /admin pages. An ADMIN sees every class; a TEACHER only their own. */
export function isStaff(role: Role | null | undefined): boolean {
  return role === 'ADMIN' || role === 'TEACHER'
}

/** The role as people say it ("Profesor"), never the enum ("ADMIN"). */
export function roleLabel(role: Role | null): string {
  return role ? LABELS[role] : ''
}

/** Which count a link carries: the teacher's work queue, or the student's new grades (S3). */
export type NavCount = 'grading' | 'pending' | 'newResults'

/** Fired after something that changes a count (the student opened a new result), so the links re-read them now. */
export const NAV_COUNTS_CHANGED = 'mathlms:nav-counts-changed'

export interface NavItem {
  to: string
  label: string
  /** The word under the icon in the phone's tab bar, where "Testele mele" does not fit. */
  short?: string
  icon: LucideIcon
  count?: NavCount
  testId?: string
  /** Other addresses that belong to this link (it is shown as the current page there too). */
  also?: string[]
}

const NAV: Record<Role, NavItem[]> = {
  STUDENT: [
    { to: '/', label: 'Acasă', icon: House },
    { to: '/quizzes', label: 'Testele mele', short: 'Teste', icon: ClipboardList, count: 'newResults' },
    { to: '/review', label: 'Recapitulare', icon: RotateCcw },
    { to: '/progress', label: 'Progresul meu', short: 'Progres', icon: TrendingUp, testId: 'my-progress' },
    { to: '/capitole', label: 'Capitole EN', icon: BookOpen, testId: 'en-chapters' },
    { to: '/content', label: 'Conținut', icon: Library },
  ],
  // A parent needs one page: Acasă lists their children, each opening the child's homework, grades and progress.
  PARENT: [{ to: '/', label: 'Acasă', icon: House }],
  // Five links (T6): quizzes and homework are one "Teste" area with tabs; approving, inviting and linking people is
  // one "Elevi" page.
  ADMIN: [
    { to: '/', label: 'Acasă', icon: House },
    // The EN chapter pages are content too: the teacher reaches them from the Clase page and from Acasă.
    { to: '/admin/content', label: 'Clase', icon: School, also: ['/admin/classes', '/admin/en-chapters'] },
    { to: '/admin/quizzes', label: 'Teste', icon: ClipboardList, also: ['/admin/assignments'], testId: 'admin-tests' },
    { to: '/admin/grading', label: 'Corectură', icon: PenLine, count: 'grading' },
    { to: '/admin/students', label: 'Elevi', icon: Users, count: 'pending', also: ['/admin/pending', '/admin/links'] },
  ],
  // The same pages limited to the teacher's own classes; approving accounts stays with the admins (no count).
  TEACHER: [
    { to: '/', label: 'Acasă', icon: House },
    { to: '/admin/content', label: 'Clase', icon: School, also: ['/admin/classes'] },
    { to: '/admin/quizzes', label: 'Teste', icon: ClipboardList, also: ['/admin/assignments'], testId: 'admin-tests' },
    { to: '/admin/grading', label: 'Corectură', icon: PenLine, count: 'grading' },
    { to: '/admin/students', label: 'Elevi', icon: Users },
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

/** Whether the role has more than one page to go between - a single link (the parent's Acasă) needs no menu. */
export function hasNavigation(role: Role): boolean {
  return NAV[role].length > 1
}

/** How many tabs fit across a phone: with more links, the last tab becomes "Mai mult". */
const TAB_BAR_SIZE = 5

/**
 * The phone's tab bar: every link when they all fit, otherwise the first four plus a "Mai mult" menu with the rest
 * (the student's Capitole EN and Conținut - the pages opened least often).
 */
export function tabBarItems(role: Role): { main: NavItem[]; more: NavItem[] } {
  const items = NAV[role]
  if (items.length <= TAB_BAR_SIZE) return { main: items, more: [] }
  return { main: items.slice(0, TAB_BAR_SIZE - 1), more: items.slice(TAB_BAR_SIZE - 1) }
}
