import { type AuditAction, type AuditCategory, type AuditEntry } from '@/lib/api'

/** The filter groups, in the order the select lists them. */
export const AUDIT_CATEGORIES: { value: AuditCategory; label: string }[] = [
  { value: 'GRADING', label: 'Corectură' },
  { value: 'QUIZ', label: 'Teste' },
  { value: 'HOMEWORK', label: 'Teme' },
  { value: 'CONTENT', label: 'Conținut' },
  { value: 'PEOPLE', label: 'Elevi și părinți' },
]

/** What happened, as the teacher would say it. */
export const AUDIT_ACTION_LABELS: Record<AuditAction, string> = {
  CLASS_CREATED: 'Clasă creată',
  CLASS_UPDATED: 'Clasă modificată',
  CLASS_DELETED: 'Clasă ștearsă',
  BOOK_CREATED: 'Carte adăugată',
  BOOK_UPDATED: 'Carte modificată',
  BOOK_DELETED: 'Carte ștearsă',
  CHAPTER_CREATED: 'Capitol adăugat',
  CHAPTER_UPDATED: 'Capitol modificat',
  CHAPTER_DELETED: 'Capitol șters',
  EXERCISE_CREATED: 'Exercițiu adăugat',
  EXERCISE_UPDATED: 'Exercițiu modificat',
  EXERCISE_DELETED: 'Exercițiu șters',
  QUIZ_CREATED: 'Test creat',
  QUIZ_UPDATED: 'Test modificat',
  QUIZ_COPIED: 'Test copiat',
  QUIZ_PUBLISHED: 'Test publicat',
  QUIZ_UNPUBLISHED: 'Test depublicat',
  QUIZ_DELETED: 'Test șters',
  QUESTION_ADDED: 'Subiect adăugat',
  QUESTION_UPDATED: 'Subiect modificat',
  QUESTION_DELETED: 'Subiect șters',
  QUESTION_IMAGE_SET: 'Imagine pusă la subiect',
  QUESTION_IMAGE_REMOVED: 'Imagine scoasă de la subiect',
  ANSWER_GRADED: 'Punctaj dat',
  PAPER_COMMENTED: 'Comentariu pe lucrare',
  PAPER_GRADED: 'Lucrare notată',
  HOMEWORK_ASSIGNED: 'Temă dată',
  HOMEWORK_RESCHEDULED: 'Termen mutat',
  HOMEWORK_DELETED: 'Temă ștearsă',
  ACCOUNT_APPROVED: 'Cont aprobat',
  ACCOUNT_REJECTED: 'Cont respins',
  PARENT_LINKED: 'Părinte legat',
  STUDENT_ENROLLED: 'Înscris în clasă',
  STUDENT_UNENROLLED: 'Scos din clasă',
  INVITE_LINK_CREATED: 'Link de invitație creat',
  INVITE_LINK_REVOKED: 'Link de invitație oprit',
  JOINED_BY_INVITE: 'Intrat prin link',
}

export function auditActionLabel(action: AuditAction): string {
  return AUDIT_ACTION_LABELS[action] ?? action
}

/** The row's subject, unless it only repeats the action ("Cont aprobat" / "Cont aprobat"). */
export function auditSubject(entry: AuditEntry): string | null {
  return entry.label === auditActionLabel(entry.action) ? null : entry.label
}

/** Rows whose person is the student whose page exists (an approved account may be a parent's or a teacher's). */
export function linksToStudent(entry: AuditEntry): boolean {
  return entry.studentId !== null && entry.action !== 'ACCOUNT_APPROVED' && entry.action !== 'ACCOUNT_REJECTED'
}

/** Who did it: a name, or what is left when the account is gone. */
export function auditActor(entry: AuditEntry): string {
  return entry.actorName ?? 'cont șters'
}
