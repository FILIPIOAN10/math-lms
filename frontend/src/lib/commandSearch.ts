import { type AdminUserSummary, type EnChapterSummary, type QuizSummary, type Role } from '@/lib/api'
import { isStaff, navItems, secondaryNavItems } from '@/lib/roles'
import { matchesQuery } from '@/lib/search'

/**
 * The search behind Ctrl+K: every page of the role, and - typed for - the teacher's tests and students, or the EN
 * chapters. Pure functions; the palette (CommandPalette) loads the lists and draws the result.
 */
export type CommandGroup = 'Pagini' | 'Teste' | 'Elevi' | 'Capitole'

export interface CommandEntry {
  /** Unique in the palette: "page:/admin/grading", "quiz:12". */
  id: string
  group: CommandGroup
  label: string
  /** The grey words after the label: a class, an email. Searched too. */
  hint?: string
  /** Other words that find it ("note" finds Corectură). Searched, never shown. */
  keywords?: string
  to: string
}

const GROUP_ORDER: CommandGroup[] = ['Pagini', 'Teste', 'Elevi', 'Capitole']

/** Words a user may type for a page whose name says it differently. */
const PAGE_KEYWORDS: Record<string, string> = {
  '/': 'acasa start inceput',
  '/admin/grading': 'note corectare lucrari punctaje',
  '/admin/students': 'conturi aprobare invitatii parinti',
  '/admin/content': 'clase materie carti lectii',
  '/admin/quizzes': 'quiz simulare',
  '/admin/assignments': 'teme termene predare',
  '/review': 'repetare grile zilnic',
  '/progress': 'note grafic evolutie',
  '/plan': 'tinta nota evaluare nationala saptamani program',
  '/quizzes': 'note rezultate',
  '/account': 'parola tema intunecata email date export',
  '/admin/journal': 'audit istoric cine a schimbat',
  '/admin/quizzes/ai-import': 'ai fotografii poze scanare',
  '/admin/culegere': 'culegere teme recapitulative exercitii aprobare',
}

/** The role's pages: the main links, the rarer ones, homework for the staff, and the account. */
export function pageEntries(role: Role): CommandEntry[] {
  const pages = [
    ...navItems(role),
    ...secondaryNavItems(role),
    ...(isStaff(role) ? [{ to: '/admin/assignments', label: 'Teme' }, { to: '/admin/culegere', label: 'Culegere' }] : []),
    { to: '/account', label: 'Contul meu' },
  ]
  const seen = new Set<string>()
  return pages.flatMap(({ to, label }) => {
    if (seen.has(to)) return []
    seen.add(to)
    return [{ id: `page:${to}`, group: 'Pagini' as const, label, keywords: PAGE_KEYWORDS[to], to }]
  })
}

export function quizEntries(quizzes: QuizSummary[]): CommandEntry[] {
  return quizzes.map((q) => ({
    id: `quiz:${q.id}`,
    group: 'Teste',
    label: q.title,
    hint: q.schoolClassName ?? 'Toate clasele',
    to: `/admin/quizzes/${q.id}`,
  }))
}

export function studentEntries(students: AdminUserSummary[]): CommandEntry[] {
  return students.map((s) => ({
    id: `student:${s.id}`,
    group: 'Elevi',
    label: s.fullName,
    hint: s.email,
    to: `/admin/students/${s.id}`,
  }))
}

/** The chapters (not their sections), on the page this role reads them: an admin edits, everyone else learns. */
export function chapterEntries(chapters: EnChapterSummary[], role: Role): CommandEntry[] {
  const base = role === 'ADMIN' ? '/admin/en-chapters' : '/capitole'
  return chapters
    .filter((c) => c.parentId === null)
    .map((c) => ({ id: `chapter:${c.id}`, group: 'Capitole', label: c.title, hint: c.code, to: `${base}/${c.id}` }))
}

export interface CommandSection {
  group: CommandGroup
  entries: CommandEntry[]
}

/**
 * What the palette lists for `query`: with nothing typed, the pages (where to go); typed, everything that matches,
 * grouped, at most `perGroup` of each so one long list does not push the others away.
 */
export function searchCommands(entries: CommandEntry[], query: string, perGroup = 6): CommandSection[] {
  const typed = query.trim() !== ''
  const found = entries.filter((e) =>
    typed ? matchesQuery(query, e.label, e.hint, e.keywords) : e.group === 'Pagini')
  return GROUP_ORDER.flatMap((group) => {
    const inGroup = found.filter((e) => e.group === group)
    return inGroup.length === 0 ? [] : [{ group, entries: typed ? inGroup.slice(0, perGroup) : inGroup }]
  })
}
