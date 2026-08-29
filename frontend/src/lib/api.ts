export type Role = 'ADMIN' | 'STUDENT' | 'PARENT'

export interface User {
  email: string
  fullName: string
  role: Role
}

export class ApiError extends Error {
  status: number

  constructor(status: number) {
    super(`API error: ${status}`)
    this.status = status
  }
}

async function apiFetch(path: string, options: RequestInit = {}): Promise<Response> {
  const response = await fetch(`/api${path}`, {
    credentials: 'include',
    ...options,
  })
  if (!response.ok) {
    throw new ApiError(response.status)
  }
  return response
}

export async function getCurrentUser(): Promise<User> {
  const response = await apiFetch('/auth/me')
  return response.json()
}

export async function logout(): Promise<void> {
  await apiFetch('/auth/logout', { method: 'POST' })
}