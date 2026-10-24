export type Role = 'ADMIN' | 'TEACHER' | 'STUDENT' | 'PARENT'

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
  kind: 'CLASS' | 'PARENT' | 'ADMIN'
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

export async function listAdminInvites(): Promise<InviteLink[]> {
  const response = await apiFetch(`/admin/invites/admin`)
  return response.json()
}

/** Always single-use, valid for 24 hours; the account it creates waits for an admin's approval. */
export async function createAdminInvite(): Promise<InviteLink> {
  const response = await postJson(`/admin/invites/admin`, {})
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
  /** Who owns the class (a TEACHER sees only theirs); null = only admins see it. */
  teacherId: number | null
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

/** ADMIN only: gives the class (with its students, homework and links) to another teacher. */
export async function assignClassTeacher(id: number, teacherId: number): Promise<SchoolClass> {
  const response = await putJson(`/admin/classes/${id}/teacher`, { teacherId })
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
  /** The picture under the statement: a version token for questionImageUrl(), or null/absent = none. */
  image?: string | null
  /** Imported with "[imagine]" and still waiting for its picture. */
  imageRequired?: boolean
  /** An open problem's requirements a), b)... (EN Subiectul III); empty/absent = none. */
  parts?: ItemPart[]
  /** The EN chapter page the teacher chose; null/absent = automatic (by its place on the paper). */
  enChapterId?: number | null
  /** What "automatic" points at here; null/absent = its place has no default. */
  enChapterAuto?: number | null
}

/** One requirement of an open problem: "(2p) a) Arată că x = 25.", with its barem. */
export interface ItemPart {
  id: number
  position: number
  /** "a", "b"... */
  label: string
  statement: string
  points: number
  solution: string | null
  /** The EN chapter page chosen for this requirement; null/absent = automatic. */
  enChapterId?: number | null
  /** What "automatic" points at for this requirement. */
  enChapterAuto?: number | null
}

/** A requirement when authoring; its letter follows from the order. */
export interface PartInput {
  statement: string
  points: number
  solution: string | null
  /** The EN chapter page for this requirement; null/absent = automatic. */
  enChapterId?: number | null
}

/** A requirement on a paper being graded or graded: its barem and the points given (null = not yet). */
export interface GradedPart {
  id: number
  label: string
  statement: string
  points: number
  barem: string | null
  awardedPoints: number | null
  /** The EN chapter page to open when points were lost here; null/absent = none. */
  enChapter?: EnChapterLink | null
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
  /** The picture under the statement: a version token for questionImageUrl(), or null/absent = none. */
  image?: string | null
  /** Imported with "[imagine]": the question still needs its picture (only read when it is created). */
  imageRequired?: boolean
  /** OPEN only: requirements a), b)... with their own points; the item's points become their sum. */
  parts?: PartInput[] | null
  /** The EN chapter page for this question; null/absent = automatic (by its place on the paper). */
  enChapterId?: number | null
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
  /** The picture under the statement: a version token for questionImageUrl(), or null/absent = none. */
  image?: string | null
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
  /** The picture under the statement: a version token for questionImageUrl(), or null/absent = none. */
  image?: string | null
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

// ---------- Quiz from textbook photos (Faza 3, AI) ----------

/** Whether the "Din poze (AI)" button shows, and how many pages are left today. */
export interface AiImportSettings {
  enabled: boolean
  maxPages: number
  dailyPageLimit: number
  pagesUsedToday: number
}

export type AiImportStatus = 'QUEUED' | 'RUNNING' | 'DONE' | 'FAILED'

/** One question as the AI read it from a page. Never an answer: the teacher marks the correct option. */
export interface ExtractedItem {
  /** The photo it is on, 1-based. */
  page: number
  section: string | null
  type: QuizItemType
  statement: string
  /** A grilă's choices, without letters; empty for an open question. */
  options: string[]
  /** The points printed next to it; null = none printed. */
  points: number | null
  /** It refers to a drawing the teacher adds as the question's picture. */
  hasFigure: boolean
  warnings: string[]
  /** An open problem's requirements a), b)... with their printed points (and proposed chapter); absent on older readings. */
  parts?: { statement: string; points: number | null; enChapter?: string | null; enChapterReason?: string | null }[] | null
  /** The EN chapter the AI proposes ("A1"), or null/absent; the teacher confirms it in the review. */
  enChapter?: string | null
  /** Why, in one sentence. */
  enChapterReason?: string | null
  /** The photo the drawing is on (with figureBox); null/absent = no usable box, the teacher adds the picture. */
  figurePage?: number | null
  /** The drawing's box on that photo: [ymin, xmin, ymax, xmax] on a 0-1000 scale; empty/absent = none. */
  figureBox?: number[] | null
}

export interface AiImport {
  id: number
  status: AiImportStatus
  pageCount: number
  /** False once the photos are deleted (quiz made, or a week passed): no page previews, no retry. */
  pagesAvailable: boolean
  itemCount: number | null
  error: string | null
  schoolClassId: number | null
  chapterId: number | null
  quizId: number | null
  createdAt: string
  finishedAt: string | null
  /** Only on getAiImport (null in the list). */
  items: ExtractedItem[] | null
}

export async function getAiImportSettings(): Promise<AiImportSettings> {
  const response = await apiFetch('/admin/ai-imports/settings')
  return response.json()
}

/** Uploads 1–5 textbook pages to be read; answers at once (QUEUED), poll getAiImport until DONE or FAILED. */
export async function createAiImport(pages: File[], schoolClassId: number | null, chapterId: number | null): Promise<AiImport> {
  const formData = new FormData()
  pages.forEach((page) => formData.append('files', page))
  if (schoolClassId !== null) formData.append('schoolClassId', String(schoolClassId))
  if (chapterId !== null) formData.append('chapterId', String(chapterId))
  const response = await apiFetch('/admin/ai-imports', { method: 'POST', body: formData })
  return response.json()
}

export async function listAiImports(): Promise<AiImport[]> {
  const response = await apiFetch('/admin/ai-imports')
  return response.json()
}

export async function getAiImport(id: number): Promise<AiImport> {
  const response = await apiFetch(`/admin/ai-imports/${id}`)
  return response.json()
}

/** Page `page` (1-based) of an import, for the review screen. */
export function aiImportPageUrl(id: number, page: number): string {
  return `/api/admin/ai-imports/${id}/pages/${page}`
}

export async function retryAiImport(id: number): Promise<AiImport> {
  const response = await postJson(`/admin/ai-imports/${id}/retry`, {})
  return response.json()
}

export async function discardAiImport(id: number): Promise<void> {
  await del(`/admin/ai-imports/${id}`)
}

export interface QuizFromImportInput {
  title: string
  schoolClassId: number | null
  chapterId: number | null
  /** The kept questions in order (positions are assigned by the server). */
  items: ItemInput[]
  /** Drawings to cut out of the page photos, each for items[index]. */
  figures?: FigureInput[]
}

/** The picture of a question: `box` ([ymin, xmin, ymax, xmax], 0-1000) of page `page` (1-based). */
export interface FigureInput {
  index: number
  page: number
  box: number[]
}

/** The reviewed questions become a DRAFT quiz; returns its id. */
export async function createQuizFromAiImport(id: number, input: QuizFromImportInput): Promise<number> {
  const response = await postJson(`/admin/ai-imports/${id}/quiz`, input)
  return (await response.json()).quizId
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

/** Where a question's picture is served from; the version token changes with every new picture (no stale cache). */
export function questionImageUrl(itemId: number, version: string): string {
  return `/api/quiz/items/${itemId}/image?v=${encodeURIComponent(version)}`
}

/** Puts a picture under the question's statement (PNG/JPG/WEBP/GIF), replacing any earlier one. */
export async function uploadQuestionImage(itemId: number, file: File): Promise<void> {
  const formData = new FormData()
  formData.append('file', file)
  // No Content-Type header: the browser sets multipart/form-data with the boundary itself.
  await apiFetch(`/admin/quiz-items/${itemId}/image`, { method: 'PUT', body: formData })
}

export async function removeQuestionImage(itemId: number): Promise<void> {
  await del(`/admin/quiz-items/${itemId}/image`)
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
  /** The picture under the statement: a version token for questionImageUrl(), or null/absent = none. */
  image?: string | null
  /** An open problem's requirements a), b)... (no barem); empty/absent = none. */
  parts?: { id: number; label: string; statement: string; points: number }[]
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
  /** S2: the pages sent for an open question, oldest first. */
  photoIds: number[]
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
  /** S3: a graded test (or a new teacher comment on it) the student has not opened yet. */
  newResult: boolean
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
  /** "a", "b"... as on the paper test; null = no answer / not a grilă (absent in older fixtures). */
  selectedOptionLetter?: string | null
  correctOptionLetter?: string | null
  barem: string | null
  photoUploaded: boolean
  /** S2: the pages of the open answer, oldest first. */
  photoIds: number[]
  /** Practice only (0 in a graded test). */
  hintsUsed: number
  hintsAvailable: number
  /** The teacher's comment on this answer (graded open items), or null. */
  teacherComment: string | null
  /** The heading it sits under (EN style: "SUBIECTUL I" + optional instruction lines); null/absent = none. */
  section?: string | null
  /** The picture under the statement: a version token for questionImageUrl(), or null/absent = none. */
  image?: string | null
  /** Requirements a), b)... with their barem and points; empty/absent = none. */
  parts?: GradedPart[]
  /** The EN chapter page to open when points were lost; null/absent = none. */
  enChapter?: EnChapterLink | null
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
  quizId: number
  /** S4: the quiz is published and open for practice, so "Exersează doar ce ai greșit" can start one. */
  practiceAllowed: boolean
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

/** One page of the student's own answer to an OPEN item (served only to them, never cached). */
export function ownPhotoUrl(attemptId: number, itemId: number, photoId: number): string {
  return `/api/quiz/attempts/${attemptId}/responses/${itemId}/photos/${photoId}`
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

/**
 * Saves a grilă choice. `client` (this browser's id + its counter) lets the server ignore an older retry that arrives
 * after a newer answer (Feature 2); answered 204/200 either way, so the browser stops retrying.
 */
export async function saveQuizAnswer(
  attemptId: number,
  itemId: number,
  optionId: number,
  client?: { clientId: string; clientSeq: number },
): Promise<AnswerFeedback | null> {
  return feedbackOrNull(await putJson(`/quiz/attempts/${attemptId}/responses/${itemId}`, { optionId, ...client }))
}

/** S2: an open answer's pages after one was added or taken off, with the barem in a practice (null in a test). */
export interface AnswerPhotos {
  photoIds: number[]
  feedback: AnswerFeedback | null
}

/** S2: the most pages one open answer may have (the server enforces it too). */
export const MAX_PHOTOS = 5

/** Adds one page (up to MAX_PHOTOS) to an open answer. */
export async function uploadQuizPhoto(attemptId: number, itemId: number, file: File): Promise<AnswerPhotos> {
  const formData = new FormData()
  formData.append('file', file)
  // No Content-Type header: the browser sets multipart/form-data with the boundary itself.
  const response = await apiFetch(`/quiz/attempts/${attemptId}/responses/${itemId}/photo`, {
    method: 'POST',
    body: formData,
  })
  return response.json()
}

/** Takes one page off an open answer while the attempt is still open. */
export async function deleteQuizPhoto(attemptId: number, itemId: number, photoId: number): Promise<AnswerPhotos> {
  const response = await apiFetch(`/quiz/attempts/${attemptId}/responses/${itemId}/photos/${photoId}`, { method: 'DELETE' })
  return response.json()
}

/** Reveals hint `number` (1-based) of an item - practice only, in order; asking again for one already shown is harmless. */
export async function revealQuizHint(attemptId: number, itemId: number, number: number): Promise<HintDto> {
  const response = await putJson(`/quiz/attempts/${attemptId}/items/${itemId}/hints/${number}`, {})
  return response.json()
}

/**
 * One Idempotency-Key per hand-in of an attempt, kept for the tab's life: a retry (after a lost answer, even after a
 * reload) sends the same key, and the server replays the first result instead of "already handed in".
 */
export function submitKey(attemptId: number): string {
  const name = `mathlms:submit-key:${attemptId}`
  try {
    const stored = sessionStorage.getItem(name)
    if (stored) return stored
    const fresh = crypto.randomUUID()
    sessionStorage.setItem(name, fresh)
    return fresh
  } catch {
    return crypto.randomUUID() // no storage: still safe within this one call
  }
}

export async function submitQuizAttempt(attemptId: number): Promise<SubmitResultDto> {
  const response = await apiFetch(`/quiz/attempts/${attemptId}/submit`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', 'Idempotency-Key': submitKey(attemptId) },
    body: '{}',
  })
  return response.json()
}

export async function getAttemptResult(attemptId: number): Promise<AttemptResultViewDto> {
  const response = await apiFetch(`/quiz/attempts/${attemptId}/result`)
  return response.json()
}

/** S3: the student opened this result, so it is no longer "Nou" (idempotent; a result still waiting stays new). */
export async function markResultSeen(attemptId: number): Promise<void> {
  await apiFetch(`/quiz/attempts/${attemptId}/result-seen`, { method: 'PUT' })
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
  /** "a", "b"... as on the paper test; null = no answer / not a grilă (absent in older fixtures). */
  selectedOptionLetter?: string | null
  correctOptionLetter?: string | null
  correct: boolean | null
  awardedPoints: number | null
  photoUploaded: boolean
  /** S2: the pages of the open answer, oldest first. */
  photoIds: number[]
  teacherComment: string | null
  /** The heading it sits under (EN style: "SUBIECTUL I" + optional instruction lines); null/absent = none. */
  section?: string | null
  /** The picture under the statement: a version token for questionImageUrl(), or null/absent = none. */
  image?: string | null
  /** Requirements a), b)... graded one by one; empty/absent = one grade for the whole answer. */
  parts?: GradedPart[]
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

/** Papers graded per page: GRADED only grows, so it comes newest first, a page at a time (F7). */
export const GRADED_PAGE = 50

/**
 * The grading list. SUBMITTED is the whole queue (oldest first); GRADED is one page, newest first — pass the last
 * attemptId shown as `before` for the next one.
 */
export async function listAttemptsForGrading(status: QuizAttemptStatus, before?: number): Promise<AdminAttemptSummary[]> {
  const query = new URLSearchParams({ status })
  if (before !== undefined) query.set('before', String(before))
  const response = await apiFetch(`/admin/quiz/attempts?${query}`)
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

/** A problem with requirements a), b)...: points per requirement (null = not yet), one comment for the whole answer. */
export async function gradeOpenItemParts(
  attemptId: number,
  itemId: number,
  parts: { partId: number; points: number | null }[],
  comment: string | null = null,
): Promise<void> {
  await putJson(`/admin/quiz/attempts/${attemptId}/responses/${itemId}/grade`, { comment, parts })
}

// ---------- Saved grading comments ("comentarii frecvente") ----------

/** One of the teacher's reusable grading comments; the list comes most used first. */
export interface CommentSnippet {
  id: number
  text: string
  useCount: number
}

export async function listCommentSnippets(): Promise<CommentSnippet[]> {
  const response = await apiFetch('/admin/comment-snippets')
  return response.json()
}

/** Saves a comment for reuse; one the teacher already has (whatever the case) comes back as it is. */
export async function createCommentSnippet(text: string): Promise<CommentSnippet> {
  const response = await postJson('/admin/comment-snippets', { text })
  return response.json()
}

export async function deleteCommentSnippet(id: number): Promise<void> {
  await del(`/admin/comment-snippets/${id}`)
}

/** Counts one more use (moves it up the list). */
export async function markCommentSnippetUsed(id: number): Promise<void> {
  await postJson(`/admin/comment-snippets/${id}/use`, {})
}

/** The teacher's comment on the whole paper, while grading or after; blank removes it. */
export async function commentOnAttempt(attemptId: number, comment: string): Promise<void> {
  await putJson(`/admin/quiz/attempts/${attemptId}/comment`, { comment })
}

export async function finalizeGrading(attemptId: number): Promise<void> {
  await postJson(`/admin/quiz/attempts/${attemptId}/mark-graded`, {})
}

/** A plain GET the browser makes itself for an <img>; the auth cookie rides along via the /api proxy. */
export function attemptPhotoUrl(attemptId: number, itemId: number, photoId: number): string {
  return `/api/admin/quiz/attempts/${attemptId}/responses/${itemId}/photos/${photoId}`
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

// --- F1: the teachers' journal ---

export type AuditCategory = 'CONTENT' | 'QUIZ' | 'GRADING' | 'HOMEWORK' | 'PEOPLE'

export type AuditAction =
  | 'CLASS_CREATED' | 'CLASS_UPDATED' | 'CLASS_DELETED'
  | 'BOOK_CREATED' | 'BOOK_UPDATED' | 'BOOK_DELETED'
  | 'CHAPTER_CREATED' | 'CHAPTER_UPDATED' | 'CHAPTER_DELETED'
  | 'EXERCISE_CREATED' | 'EXERCISE_UPDATED' | 'EXERCISE_DELETED'
  | 'EN_CHAPTER_CREATED' | 'EN_CHAPTER_UPDATED' | 'EN_CHAPTER_DELETED' | 'EN_CHAPTER_PHOTO_ADDED' | 'EN_CHAPTER_PHOTO_REMOVED'
  | 'EN_EXAMPLE_ADDED' | 'EN_EXAMPLE_UPDATED' | 'EN_EXAMPLE_DELETED' | 'EN_CHAPTER_SLOTS_UPDATED'
  | 'QUIZ_CREATED' | 'QUIZ_UPDATED' | 'QUIZ_COPIED' | 'QUIZ_PUBLISHED' | 'QUIZ_UNPUBLISHED' | 'QUIZ_DELETED'
  | 'QUESTION_ADDED' | 'QUESTION_UPDATED' | 'QUESTION_DELETED' | 'QUESTION_IMAGE_SET' | 'QUESTION_IMAGE_REMOVED' | 'QUIZ_FROM_PHOTOS'
  | 'ANSWER_GRADED' | 'PAPER_COMMENTED' | 'PAPER_GRADED'
  | 'HOMEWORK_ASSIGNED' | 'HOMEWORK_RESCHEDULED' | 'HOMEWORK_DELETED'
  | 'ACCOUNT_APPROVED' | 'ACCOUNT_REJECTED' | 'PARENT_LINKED' | 'STUDENT_ENROLLED' | 'STUDENT_UNENROLLED'
  | 'INVITE_LINK_CREATED' | 'INVITE_LINK_REVOKED' | 'JOINED_BY_INVITE'

/**
 * One row of the journal. Names are today's (an erased account shows its pseudonym); `actorName` is null when the
 * account no longer exists. `entityId` is the attempt for GRADING rows, the quiz for question rows.
 */
export interface AuditEntry {
  id: number
  occurredAt: string
  action: AuditAction
  category: AuditCategory
  actorName: string | null
  entityId: number | null
  label: string
  details: string | null
  studentId: number | null
  studentName: string | null
  relatedName: string | null
}

/** `nextBefore`: pass it as `before` for the next (older) page; null when this page was the last. */
export interface AuditPage {
  entries: AuditEntry[]
  nextBefore: number | null
}

export interface AuditQuery {
  category?: AuditCategory
  studentId?: number
  /** Days, "2026-10-11", both inclusive (school days in Romania's time). */
  from?: string
  to?: string
  before?: number
  limit?: number
}

export async function getAuditLog(query: AuditQuery = {}): Promise<AuditPage> {
  const params = new URLSearchParams()
  Object.entries(query).forEach(([key, value]) => {
    if (value !== undefined && value !== '') params.set(key, String(value))
  })
  const qs = params.toString()
  const response = await apiFetch(`/admin/audit-log${qs ? `?${qs}` : ''}`)
  return response.json()
}

// --- EN chapter pages: the theory photos and worked examples a student opens after an EN question goes wrong ---

export type EnArea = 'ALGEBRA' | 'GEOMETRY'

/** Where a question sends a student who lost points on it; `ready` = its page has photos or worked examples. */
export interface EnChapterLink {
  id: number
  code: string
  title: string
  ready: boolean
}

/** A chapter in the list ("A1 Ordinea efectuării operațiilor"), with how much the teacher has put on its page. */
export interface EnChapterSummary {
  id: number
  code: string
  title: string
  area: EnArea
  position: number
  /** The chapter (or section) this is a section (or point) of; null = a chapter of its own. */
  parentId: number | null
  /** The page printed in the book where it starts; null = not known. */
  bookPage: number | null
  photoCount: number
  exampleCount: number
}

/** A section or a point under a chapter, with the page of the book it starts on. */
export interface EnSection {
  id: number
  code: string
  title: string
  parentId: number | null
  bookPage: number | null
  /** The written theory of this section (LaTeX between $...$); null = none yet. */
  notes: string | null
}

/** A worked example; `image` is a version token for enExampleImageUrl(), or null when it has no figure. */
export interface EnExample {
  id: number
  statement: string
  solution: string
  image: string | null
}

export interface EnChapterPage {
  id: number
  code: string
  title: string
  area: EnArea
  /** Optional text above the photos (LaTeX allowed). */
  notes: string | null
  parentId: number | null
  /** The chapter whose photos hold this page: itself for a chapter, the chapter above for a section or a point. */
  rootId: number
  bookPage: number | null
  /** Everything under this page (sections, then their points), in book order. */
  sections: EnSection[]
  photos: { id: number }[]
  examples: EnExample[]
}

export interface EnChapterInput {
  code: string
  title: string
  area: EnArea
  notes: string | null
  /** Omitted = keep it a chapter of its own. */
  parentId?: number | null
  bookPage?: number | null
}

export interface EnExampleInput {
  statement: string
  solution: string
}

export async function listEnChapters(): Promise<EnChapterSummary[]> {
  const response = await apiFetch('/en-chapters')
  return response.json()
}

export async function getEnChapter(id: number): Promise<EnChapterPage> {
  const response = await apiFetch(`/en-chapters/${id}`)
  return response.json()
}

/** A photo never changes (a replaced page is a new photo), so its id is enough for the browser cache. */
export function enChapterPhotoUrl(chapterId: number, photoId: number): string {
  return `/api/en-chapters/${chapterId}/photos/${photoId}`
}

export function enExampleImageUrl(chapterId: number, exampleId: number, version: string): string {
  return `/api/en-chapters/${chapterId}/examples/${exampleId}/image?v=${encodeURIComponent(version)}`
}

export async function createEnChapter(input: EnChapterInput): Promise<EnChapterSummary> {
  const response = await postJson('/admin/en-chapters', input)
  return response.json()
}

export async function updateEnChapter(id: number, input: EnChapterInput): Promise<EnChapterSummary> {
  const response = await putJson(`/admin/en-chapters/${id}`, input)
  return response.json()
}

export async function deleteEnChapter(id: number): Promise<void> {
  await del(`/admin/en-chapters/${id}`)
}

export async function moveEnChapter(id: number, direction: 'UP' | 'DOWN'): Promise<void> {
  await apiFetch(`/admin/en-chapters/${id}/move?direction=${direction}`, { method: 'POST' })
}

export async function uploadEnChapterPhoto(chapterId: number, file: File): Promise<{ id: number }> {
  const formData = new FormData()
  formData.append('file', file)
  const response = await apiFetch(`/admin/en-chapters/${chapterId}/photos`, { method: 'POST', body: formData })
  return response.json()
}

export async function removeEnChapterPhoto(chapterId: number, photoId: number): Promise<void> {
  await del(`/admin/en-chapters/${chapterId}/photos/${photoId}`)
}

export async function addEnExample(chapterId: number, input: EnExampleInput): Promise<EnExample> {
  const response = await postJson(`/admin/en-chapters/${chapterId}/examples`, input)
  return response.json()
}

export async function updateEnExample(chapterId: number, exampleId: number, input: EnExampleInput): Promise<EnExample> {
  const response = await putJson(`/admin/en-chapters/${chapterId}/examples/${exampleId}`, input)
  return response.json()
}

export async function deleteEnExample(chapterId: number, exampleId: number): Promise<void> {
  await del(`/admin/en-chapters/${chapterId}/examples/${exampleId}`)
}

export async function uploadEnExampleImage(chapterId: number, exampleId: number, file: File): Promise<void> {
  const formData = new FormData()
  formData.append('file', file)
  await apiFetch(`/admin/en-chapters/${chapterId}/examples/${exampleId}/image`, { method: 'PUT', body: formData })
}

export async function removeEnExampleImage(chapterId: number, exampleId: number): Promise<void> {
  await del(`/admin/en-chapters/${chapterId}/examples/${exampleId}/image`)
}

// --- EN chapter proposals for an existing test (stage 3) ---

export interface PartChapterSuggestion {
  partId: number
  chapterId: number | null
  code: string | null
  reason: string | null
}

/** The AI's proposal for one question (null code = none); nothing is changed until the teacher applies it. */
export interface ItemChapterSuggestion {
  itemId: number
  chapterId: number | null
  code: string | null
  reason: string | null
  parts: PartChapterSuggestion[]
}

export async function suggestEnChapters(quizId: number): Promise<ItemChapterSuggestion[]> {
  const response = await postJson(`/admin/quizzes/${quizId}/en-chapter-suggestions`, {})
  return ((await response.json()) as { items: ItemChapterSuggestion[] }).items
}

/** For each question listed: its EN chapter afterwards (null = automatic); of its requirements, only those listed change. */
export interface EnChapterAssignment {
  itemId: number
  enChapterId: number | null
  parts?: { partId: number; enChapterId: number | null }[]
}

export async function assignEnChapters(quizId: number, items: EnChapterAssignment[]): Promise<void> {
  await putJson(`/admin/quizzes/${quizId}/en-chapters`, { items })
}

// --- The map of EN chapters (stage 4) ---

/** One EN chapter for one student; `percent` null = nothing scored there yet, `ready` = its page has content. */
export interface EnChapterMastery {
  chapterId: number
  code: string
  title: string
  area: EnArea
  earned: number
  possible: number
  percent: number | null
  questions: number
  ready: boolean
}

/** The class grid: students × the EN chapters the class has met (cells in the order of `chapters`). */
export interface EnClassMastery {
  chapters: { id: number; code: string; title: string; area: EnArea }[]
  students: { studentId: number; fullName: string; cells: { chapterId: number; percent: number | null; questions: number }[] }[]
}

export async function getMyEnMastery(): Promise<EnChapterMastery[]> {
  const response = await apiFetch('/quiz/en-mastery')
  return response.json()
}

export async function getChildEnMastery(childId: number): Promise<EnChapterMastery[]> {
  const response = await apiFetch(`/parent/children/${childId}/en-mastery`)
  return response.json()
}

export async function getStudentEnMastery(studentId: number): Promise<EnChapterMastery[]> {
  const response = await apiFetch(`/admin/students/${studentId}/en-mastery`)
  return response.json()
}

export async function getClassEnMastery(classId: number): Promise<EnClassMastery> {
  const response = await apiFetch(`/admin/classes/${classId}/en-mastery`)
  return response.json()
}

// --- The automatic links: which EN chapter each place on the paper points at by default ---

/** "I.1" (SUBIECTUL I, question 1) or "III.5.b" (problem 5, requirement b) → a chapter. */
export interface EnChapterSlot {
  slot: string
  chapterId: number
  code?: string
}

export async function getEnChapterSlots(): Promise<EnChapterSlot[]> {
  const response = await apiFetch('/admin/en-chapter-slots')
  return response.json()
}

/** Replaces the whole list; a place left out has no automatic link any more. */
export async function saveEnChapterSlots(slots: EnChapterSlot[]): Promise<void> {
  await putJson('/admin/en-chapter-slots', { slots })
}
