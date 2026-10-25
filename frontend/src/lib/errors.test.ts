import { describe, expect, it } from 'vitest'
import { ApiError } from '@/lib/api'
import { errorMessage, NOT_FOUND_MESSAGE } from '@/lib/errors'

describe('errorMessage', () => {
  it("shows the server's Romanian sentence as is", () => {
    expect(errorMessage(new ApiError(409, 'Quiz-ul este deja dat ca temă acestei clase — modifică termenul')))
      .toBe('Quiz-ul este deja dat ca temă acestei clase — modifică termenul')
  })

  it('turns a technical "not found" into a plain sentence', () => {
    expect(errorMessage(new ApiError(404, 'No SchoolClass with id 2'))).toBe(NOT_FOUND_MESSAGE)
    expect(errorMessage(new ApiError(404, ''))).toBe(NOT_FOUND_MESSAGE)
  })

  it('keeps a "not found" already written for people', () => {
    expect(errorMessage(new ApiError(404, 'Nu există elevul 4.'))).toBe('Nu există elevul 4.')
  })

  it('F8: explains the two-step sign-in gate instead of showing its code', () => {
    const message = errorMessage(new ApiError(403, 'TWO_FACTOR_SETUP_REQUIRED'))
    expect(message).not.toContain('TWO_FACTOR')
    expect(message).toMatch(/autentificarea în doi pași/)
    expect(message).toMatch(/Contul meu/)
  })

  it('hides JSON and anything unexpected behind a generic line', () => {
    expect(errorMessage(new ApiError(413, '{"status":413}'))).toBe('Operația a eșuat. Reîncearcă.')
    expect(errorMessage(new TypeError('Failed to fetch'))).toBe('Operația a eșuat. Reîncearcă.')
  })
})
