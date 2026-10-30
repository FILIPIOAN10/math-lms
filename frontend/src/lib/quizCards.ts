import { type MyAttemptDto, type QuizSummary, type StudentAssignment } from '@/lib/api'
import { todoAssignments } from '@/lib/home'

/**
 * Where one quiz stands for the student: being worked on, still to do, handed in and waiting for the teacher, graded,
 * or (a quiz no longer offered) only practised.
 */
export type QuizCardState = 'in-progress' | 'todo' | 'waiting' | 'graded' | 'practised'

/** One quiz on "Testele mele", with everything the student has to do with it. */
export interface QuizCard {
  quizId: number
  title: string
  description: string | null
  timeLimitMinutes: number | null
  practiceAllowed: boolean
  /** Still offered, so it can be retaken. Homework and history can outlive the offer. */
  available: boolean
  assignment: StudentAssignment | null
  state: QuizCardState
  /** The handed-in test the card shows: a grade not opened yet, else the latest one. */
  shown: MyAttemptDto | null
  /** Every attempt at this quiz, newest first. */
  attempts: MyAttemptDto[]
  practiceOpen: boolean
  newResult: boolean
}

function stateOf(
  attempts: MyAttemptDto[],
  homeworkToDo: boolean,
  available: boolean,
  shown: MyAttemptDto | null,
): QuizCardState {
  if (attempts.some((a) => a.mode === 'TEST' && a.status === 'IN_PROGRESS')) return 'in-progress'
  if (homeworkToDo) return 'todo'
  if (shown) return shown.status === 'GRADED' ? 'graded' : 'waiting'
  return available ? 'todo' : 'practised'
}

/** Most urgent first: what is open, then homework (in the server's order, soonest first), then the free tests. */
const urgency = (card: QuizCard) => (card.state === 'in-progress' ? 0 : card.assignment ? 1 : 2)

const handedInAt = (card: QuizCard) => Date.parse(card.shown?.submittedAt ?? card.attempts[0]?.startedAt ?? '') || 0

/**
 * One card per quiz, from the three lists the server gives: quizzes offered, homework and attempts. `todo` is what the
 * student still has to do; `done` is what they already handed in, a grade not opened yet first, then newest first.
 * A quiz in any of the lists gets a card, so a test the teacher withdrew keeps its history.
 */
export function buildQuizCards(
  quizzes: QuizSummary[],
  assignments: StudentAssignment[],
  attempts: MyAttemptDto[],
): { todo: QuizCard[]; done: QuizCard[] } {
  const homeworkToDo = new Set(todoAssignments(assignments).map((a) => a.quizId))
  const ids = [...new Set([...assignments.map((a) => a.quizId), ...quizzes.map((q) => q.id), ...attempts.map((a) => a.quizId)])]

  const cards = ids.map((quizId): QuizCard => {
    const quiz = quizzes.find((q) => q.id === quizId) ?? null
    const assignment = assignments.find((a) => a.quizId === quizId) ?? null
    const own = attempts.filter((a) => a.quizId === quizId)
    const handedIn = own.filter((a) => a.mode === 'TEST' && a.status !== 'IN_PROGRESS')
    const shown = handedIn.find((a) => a.newResult) ?? handedIn[0] ?? null
    return {
      quizId,
      title: quiz?.title ?? assignment?.quizTitle ?? own[0].quizTitle,
      description: quiz?.description ?? null,
      timeLimitMinutes: quiz?.timeLimitMinutes ?? assignment?.timeLimitMinutes ?? null,
      practiceAllowed: quiz?.practiceAllowed ?? false,
      available: quiz !== null,
      assignment,
      state: stateOf(own, homeworkToDo.has(quizId), quiz !== null, shown),
      shown,
      attempts: own,
      practiceOpen: own.some((a) => a.mode === 'PRACTICE' && a.status === 'IN_PROGRESS'),
      newResult: own.some((a) => a.newResult),
    }
  })

  const isTodo = (card: QuizCard) => card.state === 'in-progress' || card.state === 'todo'
  return {
    todo: cards.filter(isTodo).sort((a, b) => urgency(a) - urgency(b)),
    done: cards
      .filter((card) => !isTodo(card))
      .sort((a, b) => Number(b.newResult) - Number(a.newResult) || handedInAt(b) - handedInAt(a)),
  }
}
