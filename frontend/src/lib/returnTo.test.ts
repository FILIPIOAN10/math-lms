import { beforeEach, describe, expect, it } from 'vitest'
import { clearReturnTo, forgetReturnOnSignOut, readReturnTo, rememberReturnTo, safeReturnPath } from '@/lib/returnTo'

describe('returnTo', () => {
  beforeEach(() => {
    sessionStorage.clear()
    clearReturnTo()
  })

  it('keeps a page inside the app, with its query and hash', () => {
    rememberReturnTo('/admin/grading?attempt=5#a')
    expect(readReturnTo()).toBe('/admin/grading?attempt=5#a')
  })

  it('refuses anything that could leave the site, and the sign-in pages themselves', () => {
    for (const bad of ['https://evil.ro', '//evil.ro', '/\\evil.ro', 'admin', '', '/login', '/login?step=2fa', '/pending', '/register?token=x', '/']) {
      expect(safeReturnPath(bad)).toBeNull()
    }
    rememberReturnTo('//evil.ro')
    expect(readReturnTo()).toBeNull()
  })

  it('reads the same page twice (React may render twice) until it is cleared', () => {
    rememberReturnTo('/quizzes/3/result')
    expect(readReturnTo()).toBe('/quizzes/3/result')
    expect(readReturnTo()).toBe('/quizzes/3/result')
    clearReturnTo()
    expect(readReturnTo()).toBeNull()
  })

  it('does not hand the page of someone who signed out to the next person', () => {
    forgetReturnOnSignOut()
    rememberReturnTo('/quizzes/3/result') // the guard of the page they were on, right after the sign-out
    expect(readReturnTo()).toBeNull()

    clearReturnTo() // the next sign-in starts afresh
    rememberReturnTo('/quizzes/4/result')
    expect(readReturnTo()).toBe('/quizzes/4/result')
  })
})
