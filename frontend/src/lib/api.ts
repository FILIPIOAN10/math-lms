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

async function apiFetch(path: string, options: RequestInit = {}): Promise<Response> {
  const response = await fetch(`/api${path}`, {
    credentials: 'include',
    ...options,
  })
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
