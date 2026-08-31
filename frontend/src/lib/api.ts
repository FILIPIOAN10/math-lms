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

export async function logout(): Promise<void> {
  await apiFetch('/auth/logout', { method: 'POST' })
}
