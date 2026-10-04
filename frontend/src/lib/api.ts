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

export async function verifyEmail(token: string): Promise<void> {
  await apiFetch(`/auth/verify-email?token=${encodeURIComponent(token)}`)
}

export async function logout(): Promise<void> {
  await apiFetch('/auth/logout', { method: 'POST' })
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
export type QuizItemType = 'SINGLE_CHOICE' | 'OPEN'

export interface QuizSummary {
  id: number
  title: string
  description: string | null
  status: QuizStatus
  /** The one class this quiz is for; null = every student may take it. */
  schoolClassId: number | null
  schoolClassName: string | null
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
}

export interface QuizDetail {
  id: number
  title: string
  description: string | null
  status: QuizStatus
  items: QuizItemDto[]
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

export async function createQuiz(
  title: string,
  description: string | null,
  schoolClassId: number | null,
): Promise<QuizSummary> {
  const response = await postJson('/admin/quizzes', { title, description, schoolClassId })
  return response.json()
}

export async function updateQuiz(
  id: number,
  title: string,
  description: string | null,
  schoolClassId: number | null,
): Promise<QuizSummary> {
  const response = await putJson(`/admin/quizzes/${id}`, { title, description, schoolClassId })
  return response.json()
}

export async function deleteQuiz(id: number): Promise<void> {
  await del(`/admin/quizzes/${id}`)
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
}

export interface StudentQuizDetailDto {
  id: number
  title: string
  description: string | null
  items: StudentItemDto[]
}

/** What the student already saved on a resumed attempt. */
export interface SavedAnswerDto {
  itemId: number
  selectedOptionId: number | null
  photoUploaded: boolean
}

export interface StartedAttemptDto {
  attemptId: number
  status: QuizAttemptStatus
  quiz: StudentQuizDetailDto
  answers: SavedAnswerDto[]
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
}

export interface AttemptResultViewDto {
  attemptId: number
  quizTitle: string
  status: QuizAttemptStatus
  finalScore: number | null
  maxScore: number
  items: ItemResultDto[]
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

export async function getChildAttemptResult(childId: number, attemptId: number): Promise<AttemptResultViewDto> {
  const response = await apiFetch(`/parent/children/${childId}/attempts/${attemptId}/result`)
  return response.json()
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

/** Starts a new attempt, or resumes the one already in progress for this quiz. */
export async function startQuizAttempt(quizId: number): Promise<StartedAttemptDto> {
  const response = await postJson(`/quiz/quizzes/${quizId}/attempts`, {})
  return response.json()
}

export async function saveQuizAnswer(attemptId: number, itemId: number, optionId: number): Promise<void> {
  await putJson(`/quiz/attempts/${attemptId}/responses/${itemId}`, { optionId })
}

export async function uploadQuizPhoto(attemptId: number, itemId: number, file: File): Promise<void> {
  const formData = new FormData()
  formData.append('file', file)
  // No Content-Type header: the browser sets multipart/form-data with the boundary itself.
  await apiFetch(`/quiz/attempts/${attemptId}/responses/${itemId}/photo`, {
    method: 'POST',
    body: formData,
  })
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
}

export async function listAttemptsForGrading(status: QuizAttemptStatus): Promise<AdminAttemptSummary[]> {
  const response = await apiFetch(`/admin/quiz/attempts?status=${status}`)
  return response.json()
}

export async function getAttemptForGrading(attemptId: number): Promise<AdminAttemptDetail> {
  const response = await apiFetch(`/admin/quiz/attempts/${attemptId}`)
  return response.json()
}

export async function gradeOpenItem(attemptId: number, itemId: number, points: number): Promise<void> {
  await putJson(`/admin/quiz/attempts/${attemptId}/responses/${itemId}/grade`, { points })
}

export async function finalizeGrading(attemptId: number): Promise<void> {
  await postJson(`/admin/quiz/attempts/${attemptId}/mark-graded`, {})
}

/** A plain GET the browser makes itself for an <img>; the auth cookie rides along via the /api proxy. */
export function attemptPhotoUrl(attemptId: number, itemId: number): string {
  return `/api/admin/quiz/attempts/${attemptId}/responses/${itemId}/photo`
}
