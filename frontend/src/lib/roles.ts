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
}

const NAV: Record<Role, NavItem[]> = {
  STUDENT: [
    { to: '/', label: 'Acasă' },
    { to: '/quizzes', label: 'Testele mele' },
    { to: '/progress', label: 'Progresul meu', testId: 'my-progress' },
    { to: '/content', label: 'Conținut' },
  ],
  PARENT: [
    { to: '/', label: 'Acasă' },
    { to: '/parent', label: 'Copiii mei', testId: 'my-children' },
    { to: '/content', label: 'Conținut' },
  ],
  ADMIN: [
    { to: '/', label: 'Acasă' },
    { to: '/admin/content', label: 'Conținut' },
    { to: '/admin/quizzes', label: 'Quiz-uri' },
    { to: '/admin/assignments', label: 'Teme', testId: 'admin-assignments' },
    { to: '/admin/grading', label: 'Corectură', count: 'grading' },
    { to: '/admin/pending', label: 'Conturi', count: 'pending' },
    { to: '/admin/links', label: 'Invitații' },
  ],
}

/** The main navigation of one role, in the order it is shown. */
export function navItems(role: Role): NavItem[] {
  return NAV[role]
}
