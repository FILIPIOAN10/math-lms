export type Role = 'ADMIN' | 'STUDENT' | 'PARENT'

export type AccountStatus =
  | 'PENDING_VERIFICATION'
  | 'PENDING_APPROVAL'
  | 'ACTIVE'
  | 'REJECTED'

export interface User {
  email: string
  fullName: string
  role: Role | null
  status: AccountStatus
}

export class ApiError extends Error {
  status: number
  body: string

  constructor(status: number, body: string) {
    super(`API error: ${status}`)
    this.status = status
    this.body = body
  }
}

function readCookie(name: string): string | null {
  const match = document.cookie.match(new RegExp('(?:^|; )' + name + '=([^;]*)'))
  return match ? decodeURIComponent(match[1]) : null
}

function send(path: string, options: RequestInit): Promise<Response> {
  const method = (options.method ?? 'GET').toUpperCase()
  const headers = new Headers(options.headers)
  // Double-submit CSRF: echo the XSRF-TOKEN cookie on state-changing requests. Read per attempt,
  // so a replay after a refresh echoes whatever token the cookie holds by then.
  if (method !== 'GET' && method !== 'HEAD') {
    const csrfToken = readCookie('XSRF-TOKEN')
    if (csrfToken) {
      headers.set('X-XSRF-TOKEN', csrfToken)
    }
  }
  return fetch(`/api${path}`, {
    credentials: 'include',
    ...options,
    headers,
  })
}

/** Endpoints where a 401 is the real answer, not a stale access token. */
const NO_REFRESH_ON_401 = ['/auth/refresh', '/auth/login', '/auth/logout']

let refreshInFlight: Promise<boolean> | null = null

/**
 * Trades the refresh cookie for a fresh access cookie. Concurrent callers share one in-flight
 * request: the backend rotates (and so invalidates) the refresh token on use, so parallel
 * rotations would race and log the user out.
 */
function refreshSession(): Promise<boolean> {
  if (!refreshInFlight) {
    refreshInFlight = fetch('/api/auth/refresh', { method: 'POST', credentials: 'include' })
      .then((r) => r.ok)
      .catch(() => false)
      .finally(() => {
        refreshInFlight = null
      })
  }
  return refreshInFlight
}

async function apiFetch(path: string, options: RequestInit = {}): Promise<Response> {
  let response = await send(path, options)

  // The access cookie lasts an hour, the refresh session days. Without this a tab left open past
  // that hour keeps rendering the data it already fetched while every new call 401s — the request
  // is rejected by the authorization filter before reaching a controller, so replaying is safe.
  if (response.status === 401 && !NO_REFRESH_ON_401.includes(path)) {
    if (await refreshSession()) {
      response = await send(path, options)
    }
  }

  if (!response.ok) {
    const body = await response.text().catch(() => '')
    throw new ApiError(response.status, body)
  }
  return response
}

async function postJson(path: string, payload: unknown): Promise<Response> {
  return apiFetch(path, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(payload),
  })
}

async function putJson(path: string, payload: unknown): Promise<Response> {
  return apiFetch(path, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(payload),
  })
}

async function del(path: string): Promise<void> {
  await apiFetch(path, { method: 'DELETE' })
}

export async function getCurrentUser(): Promise<User> {
  const response = await apiFetch('/auth/me')
  return response.json()
}

export async function login(email: string, password: string): Promise<User> {
  const response = await postJson('/auth/login', { email, password })
  return response.json()
}

export interface RegisterPayload {
  email: string
  fullName: string
  password: string
  inviteToken: string
}

export async function register(payload: RegisterPayload): Promise<void> {
  await postJson('/auth/register', payload)
}

export async function forgotPassword(email: string): Promise<void> {
  await postJson('/auth/forgot-password', { email })
}

export async function resetPassword(token: string, newPassword: string): Promise<void> {
  await postJson('/auth/reset-password', { token, newPassword })
}

/** What the account became: ACTIVE (it joined through a class/parent link — sign in now) or PENDING_APPROVAL. */
export interface VerifyEmailResult {
  status: AccountStatus
}

export async function verifyEmail(token: string): Promise<VerifyEmailResult> {
  const response = await apiFetch(`/auth/verify-email?token=${encodeURIComponent(token)}`)
  return response.json()
}

/** What a /register?token=… link is for, before the form is filled in. A dead link answers 410 with the reason. */
export interface InvitePreview {
  role: Role
  /** The class a class link enrols into; null for other links. */
  className: string | null
  /** true = a class/parent link: active as soon as the email is confirmed; false = the teacher approves it. */
  direct: boolean
}

export async function previewInvite(token: string): Promise<InvitePreview> {
  const response = await apiFetch(`/public/invites/preview?token=${encodeURIComponent(token)}`)
  return response.json()
}

export async function logout(): Promise<void> {
  await apiFetch('/auth/logout', { method: 'POST' })
}

// ---------- "Contul meu": own account, sessions, GDPR ----------

export interface Account {
  email: string
  fullName: string
  role: Role | null
  /** Has a password of its own (Google-only accounts do not). */
  hasPassword: boolean
  googleLinked: boolean
}

export async function getAccount(): Promise<Account> {
  const response = await apiFetch('/me/account')
  return response.json()
}

/** Signs every OTHER device out on success; a wrong current password is a 400 with the reason. */
export async function changePassword(currentPassword: string, newPassword: string): Promise<void> {
  await postJson('/me/password', { currentPassword, newPassword })
}

export interface DeviceSession {
  sessionId: string
  deviceInfo: string
  ipAddress: string
  /** Epoch milliseconds. */
  createdAt: number
  lastUsedAt: number
  /** The browser making this call. */
  current: boolean
}

export async function listSessions(): Promise<DeviceSession[]> {
  const response = await apiFetch('/auth/sessions')
  return response.json()
}

export async function revokeSession(sessionId: string): Promise<void> {
  await del(`/auth/sessions/${encodeURIComponent(sessionId)}`)
}

/** Every device, this one included. */
export async function logoutAll(): Promise<void> {
  await postJson('/auth/logout-all', {})
}

/** GDPR Art. 15: the whole account as a ZIP (profile, classes, attempts, answers, photos). */
export async function downloadMyData(): Promise<Blob> {
  const response = await apiFetch('/users/gdpr/export')
  return response.blob()
}

/**
 * GDPR Art. 17, step 1: emails a confirmation link; nothing is deleted yet. `password` is required for an account that
 * has one (a wrong one answers 401), null for a Google-only account.
 */
export async function requestAccountErasure(password: string | null): Promise<void> {
  await postJson('/users/gdpr/erase', password === null ? {} : { password })
}

/** Step 2, from the emailed link (no session needed): erases the account. A spent or expired token answers 400. */
export async function confirmAccountErasure(token: string): Promise<void> {
  await apiFetch(`/public/gdpr/erase/confirm?token=${encodeURIComponent(token)}`, { method: 'POST' })
}

export interface PendingUser {
  id: number
  email: string
  fullName: string
  requestedRole: Role | null
  emailVerified: boolean
}

export async function listPendingUsers(): Promise<PendingUser[]> {
  const response = await apiFetch('/admin/users/pending')
  return response.json()
}

export async function approveUser(id: number, role: Role): Promise<User> {
  const response = await postJson(`/admin/users/${id}/approve`, { role })
  return response.json()
}

export async function rejectUser(id: number): Promise<User> {
  const response = await postJson(`/admin/users/${id}/reject`, {})
  return response.json()
}

export interface AdminUserSummary {
  id: number
  email: string
  fullName: string
  role: Role
  parentId: number | null
  parentName: string | null
}

export async function listActiveUsers(role: Role): Promise<AdminUserSummary[]> {
  const response = await apiFetch(`/admin/users?role=${role}`)
  return response.json()
}

export async function linkParent(studentId: number, parentId: number): Promise<User> {
  const response = await postJson(`/admin/users/${studentId}/link-parent`, { parentId })
  return response.json()
}

/** A signed registration link (valid 7 days) that grants the invitee the given role. */
export interface Invite {
  role: Role
  url: string
}

export async function createInvite(role: Role): Promise<Invite> {
  const response = await postJson('/admin/invites', { role })
  return response.json()
}

/**
 * A stored invite link (T1): a CLASS link enrols whoever joins in that class, a PARENT link links its one user to the
 * student. Either way the account is active once the email is confirmed — nothing left to approve.
 */
export interface InviteLink {
  id: number
  kind: 'CLASS' | 'PARENT'
  url: string
  uses: number
  maxUses: number
  expiresAt: string
  createdAt: string
  className: string | null
  studentName: string | null
}

/** The class's links that are neither revoked nor expired (full ones included), newest first. */
export async function listClassInvites(classId: number): Promise<InviteLink[]> {
  const response = await apiFetch(`/admin/classes/${classId}/invites`)
  return response.json()
}

/** `maxUses` omitted = the server's default (30). */
export async function createClassInvite(classId: number, maxUses?: number): Promise<InviteLink> {
  const response = await postJson(`/admin/classes/${classId}/invites`, maxUses === undefined ? {} : { maxUses })
  return response.json()
}

/** Single use; replaces the student's earlier unused parent link. 409 if the student already has a parent. */
export async function createParentInvite(studentId: number): Promise<InviteLink> {
  const response = await postJson(`/admin/users/${studentId}/parent-invite`, {})
  return response.json()
}

export async function revokeInviteLink(id: number): Promise<void> {
  await del(`/admin/invite-links/${id}`)
}

// --- Content hierarchy (Faza 2) ---

export type Difficulty = 'EASY' | 'MEDIUM' | 'HARD'

export interface SchoolClass {
  id: number
  name: string
  description: string | null
}

export interface Book {
  id: number
  schoolClassId: number
  title: string
  description: string | null
}

export interface Chapter {
  id: number
  bookId: number
  title: string
  description: string | null
}

export interface Exercise {
  id: number
  chapterId: number
  statement: string
  solution: string | null
  difficulty: Difficulty | null
  version: number
}

export async function listClasses(): Promise<SchoolClass[]> {
  const response = await apiFetch('/classes')
  return response.json()
}

/** The classes the logged-in STUDENT is enrolled in (everyone else uses listClasses). */
export async function listMyClasses(): Promise<SchoolClass[]> {
  const response = await apiFetch('/me/classes')
  return response.json()
}

export async function listBooks(classId: number): Promise<Book[]> {
  const response = await apiFetch(`/classes/${classId}/books`)
  return response.json()
}

export async function listChapters(bookId: number): Promise<Chapter[]> {
  const response = await apiFetch(`/books/${bookId}/chapters`)
  return response.json()
}

export async function listExercises(chapterId: number): Promise<Exercise[]> {
  const response = await apiFetch(`/chapters/${chapterId}/exercises`)
  return response.json()
}

// --- Content admin writes (Faza 2.5) ---

export async function createClass(name: string, description: string | null): Promise<SchoolClass> {
  const response = await postJson('/admin/classes', { name, description })
  return response.json()
}

export async function updateClass(id: number, name: string, description: string | null): Promise<SchoolClass> {
  const response = await putJson(`/admin/classes/${id}`, { name, description })
  return response.json()
}

export async function deleteClass(id: number): Promise<void> {
  await del(`/admin/classes/${id}`)
}

export async function createBook(classId: number, title: string, description: string | null): Promise<Book> {
  const response = await postJson(`/admin/classes/${classId}/books`, { title, description })
  return response.json()
}

export async function updateBook(id: number, title: string, description: string | null): Promise<Book> {
  const response = await putJson(`/admin/books/${id}`, { title, description })
  return response.json()
}

export async function deleteBook(id: number): Promise<void> {
  await del(`/admin/books/${id}`)
}

export async function createChapter(bookId: number, title: string, description: string | null): Promise<Chapter> {
  const response = await postJson(`/admin/books/${bookId}/chapters`, { title, description })
  return response.json()
}

export async function updateChapter(id: number, title: string, description: string | null): Promise<Chapter> {
  const response = await putJson(`/admin/chapters/${id}`, { title, description })
  return response.json()
}

export async function deleteChapter(id: number): Promise<void> {
  await del(`/admin/chapters/${id}`)
}

export interface ExerciseInput {
  statement: string
  solution: string | null
  difficulty: Difficulty | null
}

export async function createExercise(chapterId: number, input: ExerciseInput): Promise<Exercise> {
  const response = await postJson(`/admin/chapters/${chapterId}/exercises`, input)
  return response.json()
}

export async function updateExercise(
  id: number,
  input: ExerciseInput & { version: number },
): Promise<Exercise> {
  const response = await putJson(`/admin/exercises/${id}`, input)
  return response.json()
}

export async function deleteExercise(id: number): Promise<void> {
  await del(`/admin/exercises/${id}`)
}

// --- Enrollment admin (Faza 2.5) ---

export interface Enrollment {
  id: number
  studentId: number
  studentName: string
  studentEmail: string
}

export async function listRoster(classId: number): Promise<Enrollment[]> {
  const response = await apiFetch(`/admin/classes/${classId}/enrollments`)
  return response.json()
}

export async function enrollStudent(classId: number, studentId: number): Promise<Enrollment> {
  const response = await postJson(`/admin/classes/${classId}/enrollments`, { studentId })
  return response.json()
}

export async function unenroll(enrollmentId: number): Promise<void> {
  await del(`/admin/enrollments/${enrollmentId}`)
}

// --- Quiz builder (Faza Q, admin) ---

export type QuizStatus = 'DRAFT' | 'PUBLISHED'
/** TEST = timed + graded; PRACTICE = immediate feedback, no timer, never graded (the teacher opts a quiz in). */
export type AttemptMode = 'TEST' | 'PRACTICE'
export type QuizItemType = 'SINGLE_CHOICE' | 'OPEN'

export interface QuizSummary {
  id: number
  title: string
  description: string | null
  status: QuizStatus
  /** The one class this quiz is for; null = every student may take it. */
  schoolClassId: number | null
  schoolClassName: string | null
  /** Minutes a student has once an attempt starts; null = untimed. */
  timeLimitMinutes: number | null
  /** Whether students may also practise this quiz (immediate feedback, no grade). */
  practiceAllowed: boolean
  /** The chapter its questions practise (E6 mastery map); null = untagged. */
  chapterId: number | null
}

export interface QuizOptionDto {
  id: number
  position: number
  text: string
  correct: boolean
}

export interface QuizItemDto {
  id: number
  position: number
  type: QuizItemType
  statement: string
  points: number
  solution: string | null
  options: QuizOptionDto[]
  /** Progressive hints in order (practice mode shows them to students one at a time). */
  hints: string[]
  /** Its own chapter; null = the quiz's. */
  chapterId: number | null
  /** The heading it sits under (EN style: "SUBIECTUL I" + optional instruction lines); null/absent = none. */
  section?: string | null
}

export interface QuizDetail {
  id: number
  title: string
  description: string | null
  status: QuizStatus
  items: QuizItemDto[]
  /** The chapter its items inherit. */
  chapterId: number | null
}

export interface OptionInput {
  position: number
  text: string
  correct: boolean
}

export interface ItemInput {
  type: QuizItemType
  position: number
  statement: string
  points: number
  solution: string | null
  options: OptionInput[] | null
  /** Up to 5 hints, in order; empty = none. */
  hints: string[]
  /** Overrides the quiz's chapter; null/absent = inherit. */
  chapterId?: number | null
  /** The heading it sits under (EN style: "SUBIECTUL I" + optional instruction lines); null/absent = none. */
  section?: string | null
}

// ---------- Chapters + mastery map (E6) ----------

/** A chapter as the teacher picks it when tagging a quiz or a question. Ordered class → book → chapter. */
export interface ChapterOption {
  id: number
  title: string
  bookId: number
  bookTitle: string
  schoolClassId: number
  schoolClassName: string
}

export async function listChapterOptions(): Promise<ChapterOption[]> {
  const response = await apiFetch('/admin/chapters')
  return response.json()
}

/** One chapter for one student. `percent` is null while nothing in it has been scored. */
export interface ChapterMastery {
  chapterId: number
  chapterTitle: string
  bookTitle: string
  schoolClassName: string
  earned: number
  possible: number
  percent: number | null
  questions: number
}

export interface ClassMastery {
  chapters: { id: number; title: string; bookTitle: string }[]
  /** Each student's cells are in the order of `chapters`. */
  students: { studentId: number; fullName: string; cells: { chapterId: number; percent: number | null; questions: number }[] }[]
}

export async function getMyMastery(): Promise<ChapterMastery[]> {
  const response = await apiFetch('/quiz/mastery')
  return response.json()
}

export async function getChildMastery(childId: number): Promise<ChapterMastery[]> {
  const response = await apiFetch(`/parent/children/${childId}/mastery`)
  return response.json()
}

export async function getClassMastery(classId: number): Promise<ClassMastery> {
  const response = await apiFetch(`/admin/classes/${classId}/mastery`)
  return response.json()
}

// ---------- Daily review / spaced repetition (E4) ----------

export interface ReviewQuestion {
  itemId: number
  quizTitle: string
  statement: string
  /** Without which one is right: that comes back with the result. */
  options: { id: number; position: number; text: string }[]
  /** Leitner box 1–5. */
  box: number
}

export interface ReviewToday {
  limit: number
  doneToday: number
  remainingToday: number
  streak: number
  totalCards: number
  /** Questions in the last box. */
  mastered: number
  questions: ReviewQuestion[]
}

export interface ReviewResult {
  correct: boolean
  correctOptionId: number | null
  solution: string | null
  boxBefore: number
  boxAfter: number
  /** A calendar day, "2026-10-14". */
  nextDueOn: string
}

export interface ReviewStatus {
  doneToday: number
  remainingToday: number
  streak: number
  totalCards: number
  mastered: number
  lastReviewOn: string | null
}

export interface StudentReviewStatus extends ReviewStatus {
  studentId: number
  fullName: string
}

export async function getReviewToday(): Promise<ReviewToday> {
  const response = await apiFetch('/quiz/review/today')
  return response.json()
}

/** A question not due today (another tab, a new day) answers 409 with the reason. */
export async function answerReview(itemId: number, optionId: number): Promise<ReviewResult> {
  const response = await postJson(`/quiz/review/items/${itemId}/answer`, { optionId })
  return response.json()
}

export async function getChildReview(childId: number): Promise<ReviewStatus> {
  const response = await apiFetch(`/parent/children/${childId}/review`)
  return response.json()
}

export async function getClassReview(classId: number): Promise<StudentReviewStatus[]> {
  const response = await apiFetch(`/admin/classes/${classId}/review`)
  return response.json()
}

export async function listQuizzes(): Promise<QuizSummary[]> {
  const response = await apiFetch('/admin/quizzes')
  return response.json()
}

// ----- Teacher analytics (Phase 5.4) -----

export interface QuizStatsBucket {
  label: string
  count: number
}

export interface QuizItemStat {
  itemId: number
  position: number
  type: QuizItemType
  statement: string
  points: number
  /** 0..1, only for single-choice items; null until something is graded. */
  correctRate: number | null
  averagePoints: number | null
  /** The heading it sits under (EN style: "SUBIECTUL I" + optional instruction lines); null/absent = none. */
  section?: string | null
}

export interface QuizStatsDto {
  quizId: number
  title: string
  gradedAttempts: number
  maxScore: number
  averageScore: number | null
  averagePercent: number | null
  distribution: QuizStatsBucket[]
  items: QuizItemStat[]
}

export async function getQuizStats(quizId: number): Promise<QuizStatsDto> {
  const response = await apiFetch(`/admin/quizzes/${quizId}/stats`)
  return response.json()
}

export async function getQuiz(id: number): Promise<QuizDetail> {
  const response = await apiFetch(`/admin/quizzes/${id}`)
  return response.json()
}

/** The editable fields of a quiz (items are managed separately). */
export interface QuizInput {
  title: string
  description: string | null
  schoolClassId: number | null
  timeLimitMinutes: number | null
  practiceAllowed: boolean
  /** The chapter its questions practise; null = untagged. */
  chapterId?: number | null
}

/** T2: a draft quiz of OPEN questions made from the chapter's ticked exercises, for the chapter's class. */
export interface ChapterQuizInput {
  title: string
  exerciseIds: number[]
  /** Points per question (1–100); single questions can be changed in the editor. */
  points: number
}

export async function createQuizFromChapter(chapterId: number, input: ChapterQuizInput): Promise<QuizSummary> {
  const response = await postJson(`/admin/chapters/${chapterId}/quiz`, input)
  return response.json()
}

export async function createQuiz(input: QuizInput): Promise<QuizSummary> {
  const response = await postJson('/admin/quizzes', input)
  return response.json()
}

export async function updateQuiz(id: number, input: QuizInput): Promise<QuizSummary> {
  const response = await putJson(`/admin/quizzes/${id}`, input)
  return response.json()
}

export async function deleteQuiz(id: number): Promise<void> {
  await del(`/admin/quizzes/${id}`)
}

/** A DRAFT copy with the same settings and items (attempts and homework are not copied). */
export async function copyQuiz(id: number): Promise<QuizSummary> {
  const response = await postJson(`/admin/quizzes/${id}/copy`, {})
  return response.json()
}

export async function setQuizPublished(id: number, published: boolean): Promise<QuizSummary> {
  const response = await postJson(`/admin/quizzes/${id}/${published ? 'publish' : 'unpublish'}`, {})
  return response.json()
}

export async function addQuizItem(quizId: number, input: ItemInput): Promise<QuizItemDto> {
  const response = await postJson(`/admin/quizzes/${quizId}/items`, input)
  return response.json()
}

export async function updateQuizItem(itemId: number, input: ItemInput): Promise<QuizItemDto> {
  const response = await putJson(`/admin/quiz-items/${itemId}`, input)
  return response.json()
}

export async function deleteQuizItem(itemId: number): Promise<void> {
  await del(`/admin/quiz-items/${itemId}`)
}

// ---------- Student quiz (Q5–Q9) ----------

export type QuizAttemptStatus = 'IN_PROGRESS' | 'SUBMITTED' | 'GRADED'

/** An answer choice as the student sees it — no `correct` flag (anti-cheat, see StudentQuizDtos). */
export interface StudentOptionDto {
  id: number
  position: number
  text: string
}

export interface StudentItemDto {
  id: number
  position: number
  type: QuizItemType
  statement: string
  points: number
  options: StudentOptionDto[]
  /** Hints this item has - only ever above 0 in a practice; a graded test hides even their existence. */
  hintCount: number
  /** The heading it sits under (EN style: "SUBIECTUL I" + optional instruction lines); null/absent = none. */
  section?: string | null
}

export interface StudentQuizDetailDto {
  id: number
  title: string
  description: string | null
  /** null = untimed */
  timeLimitMinutes: number | null
  items: StudentItemDto[]
}

/**
 * What a PRACTICE answer reveals at once. `correct` is null for an open item (a teacher would mark it); the barem is
 * `solution`. The server sends this only in practice - a graded test reveals nothing until it is submitted.
 */
export interface AnswerFeedback {
  correct: boolean | null
  correctOptionId: number | null
  solution: string | null
}

/** What the student already saved on a resumed attempt. */
export interface SavedAnswerDto {
  itemId: number
  selectedOptionId: number | null
  photoUploaded: boolean
  /** Practice only; null in a graded test. */
  feedback: AnswerFeedback | null
}

/** One revealed hint: its 1-based number, its text and how many hints the item has in all. */
export interface HintDto {
  number: number
  text: string
  total: number
}

/** The hints a resumed practice has already revealed for one item, in order. */
export interface RevealedHintsDto {
  itemId: number
  hints: string[]
}

export interface StartedAttemptDto {
  attemptId: number
  status: QuizAttemptStatus
  quiz: StudentQuizDetailDto
  answers: SavedAnswerDto[]
  /** When time runs out (server's decision); null = untimed. */
  deadlineAt: string | null
  /** The server's clock at the moment it answered — the countdown is measured from this, not from the browser's clock. */
  serverNow: string
  mode: AttemptMode
  /** Practice only; empty in a graded test. */
  revealedHints: RevealedHintsDto[]
}

export interface SubmitResultDto {
  attemptId: number
  status: QuizAttemptStatus
  autoScore: number
  autoMaxScore: number
  finalScore: number | null
}

export interface MyAttemptDto {
  attemptId: number
  quizId: number
  quizTitle: string
  status: QuizAttemptStatus
  startedAt: string
  submittedAt: string | null
  score: number | null
  mode: AttemptMode
  /** The quiz's total points, so a score reads "8 / 11". */
  maxScore: number
}

/** One point of the progress chart: a graded attempt as points and as a percent of the quiz's max. */
export interface ProgressPointDto {
  attemptId: number
  quizId: number
  quizTitle: string
  submittedAt: string
  score: number
  maxScore: number
  percent: number
}

export interface ItemResultDto {
  /** Lets the student's own page fetch their photo of an OPEN item. */
  itemId: number
  position: number
  type: QuizItemType
  statement: string
  points: number
  awardedPoints: number | null
  correct: boolean | null
  selectedOptionText: string | null
  correctOptionText: string | null
  barem: string | null
  photoUploaded: boolean
  /** Practice only (0 in a graded test). */
  hintsUsed: number
  hintsAvailable: number
  /** The teacher's comment on this answer (graded open items), or null. */
  teacherComment: string | null
  /** The heading it sits under (EN style: "SUBIECTUL I" + optional instruction lines); null/absent = none. */
  section?: string | null
}

export interface AttemptResultViewDto {
  attemptId: number
  quizTitle: string
  status: QuizAttemptStatus
  finalScore: number | null
  maxScore: number
  items: ItemResultDto[]
  mode: AttemptMode
  /** The teacher's comment on the whole paper, or null. */
  teacherComment: string | null
}

// ----- Parent (Phase 5): read-only view of one's own children -----

export interface ChildDto {
  id: number
  fullName: string
  email: string
}

export async function listMyChildren(): Promise<ChildDto[]> {
  const response = await apiFetch('/parent/children')
  return response.json()
}

export async function getChildProgress(childId: number): Promise<ProgressPointDto[]> {
  const response = await apiFetch(`/parent/children/${childId}/progress`)
  return response.json()
}

export async function getChildAttempts(childId: number): Promise<MyAttemptDto[]> {
  const response = await apiFetch(`/parent/children/${childId}/attempts`)
  return response.json()
}

/** The child's homework, exactly as the child sees it (a child that is not yours answers 403). */
export async function getChildAssignments(childId: number): Promise<StudentAssignment[]> {
  const response = await apiFetch(`/parent/children/${childId}/assignments`)
  return response.json()
}

// ---------- T5: the teacher's page for one student ----------

export interface StudentProfile {
  id: number
  fullName: string
  email: string
  status: AccountStatus
  classes: { id: number; name: string }[]
  parentId: number | null
  parentName: string | null
  parentEmail: string | null
}

export async function getStudentProfile(studentId: number): Promise<StudentProfile> {
  const response = await apiFetch(`/admin/students/${studentId}`)
  return response.json()
}

export async function getStudentAttempts(studentId: number): Promise<MyAttemptDto[]> {
  const response = await apiFetch(`/admin/students/${studentId}/attempts`)
  return response.json()
}

export async function getStudentAssignments(studentId: number): Promise<StudentAssignment[]> {
  const response = await apiFetch(`/admin/students/${studentId}/assignments`)
  return response.json()
}

export async function getStudentProgress(studentId: number): Promise<ProgressPointDto[]> {
  const response = await apiFetch(`/admin/students/${studentId}/progress`)
  return response.json()
}

export async function getStudentMastery(studentId: number): Promise<ChapterMastery[]> {
  const response = await apiFetch(`/admin/students/${studentId}/mastery`)
  return response.json()
}

export async function getStudentReview(studentId: number): Promise<ReviewStatus> {
  const response = await apiFetch(`/admin/students/${studentId}/review`)
  return response.json()
}

export async function getChildAttemptResult(childId: number, attemptId: number): Promise<AttemptResultViewDto> {
  const response = await apiFetch(`/parent/children/${childId}/attempts/${attemptId}/result`)
  return response.json()
}

/** What a student sees before starting a quiz - starts nothing and reveals no statement. */
export interface QuizPreview {
  id: number
  title: string
  description: string | null
  /** null = untimed */
  timeLimitMinutes: number | null
  practiceAllowed: boolean
  itemCount: number
  maxScore: number
}

export async function getQuizPreview(quizId: number): Promise<QuizPreview> {
  const response = await apiFetch(`/quiz/quizzes/${quizId}`)
  return response.json()
}

/** The student's own photo of an OPEN item (served only to them; never cached, so add `?v=` after a re-upload). */
export function ownPhotoUrl(attemptId: number, itemId: number): string {
  return `/api/quiz/attempts/${attemptId}/responses/${itemId}/photo`
}

export async function getStudentQuizzes(): Promise<QuizSummary[]> {
  const response = await apiFetch('/quiz/quizzes')
  return response.json()
}

export async function getMyProgress(): Promise<ProgressPointDto[]> {
  const response = await apiFetch('/quiz/progress')
  return response.json()
}

export async function getMyAttempts(): Promise<MyAttemptDto[]> {
  const response = await apiFetch('/quiz/attempts')
  return response.json()
}

/** Starts a new attempt, or resumes the one already in progress for this quiz in the given mode. */
export async function startQuizAttempt(quizId: number, mode: AttemptMode = 'TEST'): Promise<StartedAttemptDto> {
  const response = await postJson(`/quiz/quizzes/${quizId}/attempts?mode=${mode}`, {})
  return response.json()
}

/** 200 + feedback in a practice, 204 (null) in a graded test. */
async function feedbackOrNull(response: Response): Promise<AnswerFeedback | null> {
  return response.status === 204 ? null : response.json()
}

export async function saveQuizAnswer(
  attemptId: number,
  itemId: number,
  optionId: number,
): Promise<AnswerFeedback | null> {
  return feedbackOrNull(await putJson(`/quiz/attempts/${attemptId}/responses/${itemId}`, { optionId }))
}

export async function uploadQuizPhoto(attemptId: number, itemId: number, file: File): Promise<AnswerFeedback | null> {
  const formData = new FormData()
  formData.append('file', file)
  // No Content-Type header: the browser sets multipart/form-data with the boundary itself.
  return feedbackOrNull(
    await apiFetch(`/quiz/attempts/${attemptId}/responses/${itemId}/photo`, {
      method: 'POST',
      body: formData,
    }),
  )
}

/** Reveals hint `number` (1-based) of an item - practice only, in order; asking again for one already shown is harmless. */
export async function revealQuizHint(attemptId: number, itemId: number, number: number): Promise<HintDto> {
  const response = await putJson(`/quiz/attempts/${attemptId}/items/${itemId}/hints/${number}`, {})
  return response.json()
}

export async function submitQuizAttempt(attemptId: number): Promise<SubmitResultDto> {
  const response = await postJson(`/quiz/attempts/${attemptId}/submit`, {})
  return response.json()
}

export async function getAttemptResult(attemptId: number): Promise<AttemptResultViewDto> {
  const response = await apiFetch(`/quiz/attempts/${attemptId}/result`)
  return response.json()
}

// ---------- Teacher grading (Q10) ----------

export interface AdminAttemptSummary {
  attemptId: number
  quizId: number
  quizTitle: string
  studentId: number
  studentName: string
  status: QuizAttemptStatus
  submittedAt: string | null
  score: number | null
  maxScore: number
}

export interface AdminItemReview {
  itemId: number
  position: number
  type: QuizItemType
  statement: string
  points: number
  barem: string | null
  selectedOptionText: string | null
  correctOptionText: string | null
  correct: boolean | null
  awardedPoints: number | null
  photoUploaded: boolean
  teacherComment: string | null
  /** The heading it sits under (EN style: "SUBIECTUL I" + optional instruction lines); null/absent = none. */
  section?: string | null
}

export interface AdminAttemptDetail {
  attemptId: number
  quizTitle: string
  studentName: string
  status: QuizAttemptStatus
  submittedAt: string | null
  score: number | null
  maxScore: number
  items: AdminItemReview[]
  /** On the whole paper. */
  teacherComment: string | null
}

export async function listAttemptsForGrading(status: QuizAttemptStatus): Promise<AdminAttemptSummary[]> {
  const response = await apiFetch(`/admin/quiz/attempts?status=${status}`)
  return response.json()
}

export async function getAttemptForGrading(attemptId: number): Promise<AdminAttemptDetail> {
  const response = await apiFetch(`/admin/quiz/attempts/${attemptId}`)
  return response.json()
}

/** Points for one open answer, with an optional comment for the student (blank = none). */
export async function gradeOpenItem(attemptId: number, itemId: number, points: number, comment: string | null = null): Promise<void> {
  await putJson(`/admin/quiz/attempts/${attemptId}/responses/${itemId}/grade`, { points, comment })
}

/** The teacher's comment on the whole paper, while grading or after; blank removes it. */
export async function commentOnAttempt(attemptId: number, comment: string): Promise<void> {
  await putJson(`/admin/quiz/attempts/${attemptId}/comment`, { comment })
}

export async function finalizeGrading(attemptId: number): Promise<void> {
  await postJson(`/admin/quiz/attempts/${attemptId}/mark-graded`, {})
}

/** A plain GET the browser makes itself for an <img>; the auth cookie rides along via the /api proxy. */
export function attemptPhotoUrl(attemptId: number, itemId: number): string {
  return `/api/admin/quiz/attempts/${attemptId}/responses/${itemId}/photo`
}

// ---------- Homework (E7) ----------

export type AssignmentState = 'NOT_STARTED' | 'IN_PROGRESS' | 'SUBMITTED' | 'GRADED'

/** One assignment as the teacher's list shows it, with how the class is doing. */
export interface AssignmentSummary {
  id: number
  quizId: number
  quizTitle: string
  schoolClassId: number
  schoolClassName: string
  dueAt: string
  overdue: boolean
  enrolled: number
  /** Handed in, on time or late. */
  done: number
  /** Of those, handed in after the deadline. */
  late: number
  inProgress: number
  notStarted: number
}

/** One student's row in the teacher's per-assignment status. */
export interface AssignmentStudentStatus {
  studentId: number
  fullName: string
  state: AssignmentState
  submittedAt: string | null
  late: boolean
  score: number | null
  attemptId: number | null
}

/** One assignment as a student sees it. */
export interface StudentAssignment {
  assignmentId: number
  quizId: number
  quizTitle: string
  schoolClassName: string
  dueAt: string
  state: AssignmentState
  /** Handed in after the deadline. */
  late: boolean
  /** Deadline passed and not handed in yet. */
  overdue: boolean
  /** The open attempt to continue, or the handed-in one whose result to read. */
  attemptId: number | null
  timeLimitMinutes: number | null
}

export interface AssignmentInput {
  quizId: number
  schoolClassId: number
  /** ISO instant. */
  dueAt: string
}

export async function listAssignments(): Promise<AssignmentSummary[]> {
  const response = await apiFetch('/admin/assignments')
  return response.json()
}

/**
 * T3: publishes a draft quiz and gives it as homework in one step. All or nothing: if the homework is refused (past
 * deadline, another class's quiz) the quiz stays a draft.
 */
export async function publishAndAssign(
  quizId: number,
  input: { schoolClassId: number; dueAt: string },
): Promise<AssignmentSummary> {
  const response = await postJson(`/admin/quizzes/${quizId}/publish-and-assign`, input)
  return response.json()
}

export async function createAssignment(input: AssignmentInput): Promise<AssignmentSummary> {
  const response = await postJson('/admin/assignments', input)
  return response.json()
}

/** Moves the deadline; the quiz and class of an assignment never change. */
export async function rescheduleAssignment(id: number, dueAt: string): Promise<AssignmentSummary> {
  const response = await putJson(`/admin/assignments/${id}`, { dueAt })
  return response.json()
}

export async function deleteAssignment(id: number): Promise<void> {
  await del(`/admin/assignments/${id}`)
}

export async function getAssignmentStatus(id: number): Promise<AssignmentStudentStatus[]> {
  const response = await apiFetch(`/admin/assignments/${id}/status`)
  return response.json()
}

export async function getMyAssignments(): Promise<StudentAssignment[]> {
  const response = await apiFetch('/quiz/assignments')
  return response.json()
}
