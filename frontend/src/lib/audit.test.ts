import { describe, expect, it } from 'vitest'
import { type AuditAction, type AuditEntry } from '@/lib/api'
import { AUDIT_ACTION_LABELS, auditSubject, linksToStudent } from '@/lib/audit'

const entry = (action: AuditAction, label: string, studentId: number | null = 4): AuditEntry => ({
  id: 1, occurredAt: '2026-10-11T08:30:00Z', action, category: 'PEOPLE', actorName: 'Prof', entityId: null, label,
  details: null, studentId, studentName: studentId ? 'Ana' : null, relatedName: null,
})

describe('audit', () => {
  it('has a Romanian label for every action the server writes', () => {
    // The backend's AuditAction enum, in its order: a new constant there needs a label here.
    const server: AuditAction[] = [
      'CLASS_CREATED', 'CLASS_UPDATED', 'CLASS_DELETED', 'BOOK_CREATED', 'BOOK_UPDATED', 'BOOK_DELETED',
      'CHAPTER_CREATED', 'CHAPTER_UPDATED', 'CHAPTER_DELETED', 'EXERCISE_CREATED', 'EXERCISE_UPDATED',
      'EXERCISE_DELETED', 'EN_CHAPTER_CREATED', 'EN_CHAPTER_UPDATED', 'EN_CHAPTER_DELETED', 'EN_CHAPTER_PHOTO_ADDED',
      'EN_CHAPTER_PHOTO_REMOVED', 'EN_EXAMPLE_ADDED', 'EN_EXAMPLE_UPDATED', 'EN_EXAMPLE_DELETED', 'QUIZ_CREATED', 'QUIZ_UPDATED', 'QUIZ_COPIED', 'QUIZ_PUBLISHED', 'QUIZ_UNPUBLISHED',
      'QUIZ_DELETED', 'QUESTION_ADDED', 'QUESTION_UPDATED', 'QUESTION_DELETED', 'QUESTION_IMAGE_SET',
      'QUESTION_IMAGE_REMOVED', 'QUIZ_FROM_PHOTOS', 'ANSWER_GRADED', 'PAPER_COMMENTED', 'PAPER_GRADED', 'HOMEWORK_ASSIGNED',
      'HOMEWORK_RESCHEDULED', 'HOMEWORK_DELETED', 'ACCOUNT_APPROVED', 'ACCOUNT_REJECTED', 'PARENT_LINKED',
      'STUDENT_ENROLLED', 'STUDENT_UNENROLLED', 'INVITE_LINK_CREATED', 'INVITE_LINK_REVOKED', 'JOINED_BY_INVITE',
    ]
    expect(Object.keys(AUDIT_ACTION_LABELS).sort()).toEqual([...server].sort())
  })

  it('does not repeat a subject that only says the action again', () => {
    expect(auditSubject(entry('ACCOUNT_APPROVED', 'Cont aprobat'))).toBeNull()
    expect(auditSubject(entry('STUDENT_ENROLLED', 'Clasa a 8-a'))).toBe('Clasa a 8-a')
  })

  it("links the person to a student page except for account decisions (a parent's or a teacher's account)", () => {
    expect(linksToStudent(entry('STUDENT_ENROLLED', 'Clasa a 8-a'))).toBe(true)
    expect(linksToStudent(entry('ACCOUNT_APPROVED', 'Cont aprobat'))).toBe(false)
    expect(linksToStudent(entry('CLASS_CREATED', 'Clasa a 8-a', null))).toBe(false)
  })
})
