/**
 * How this person signed in last, kept in this browser only: the sign-in page opens on it, so whoever uses a password
 * finds the fields there (and their password manager can fill them, which it cannot do behind a closed tab).
 */
export type LoginMethod = 'google' | 'password'

const LOGIN_METHOD_KEY = 'mathlms-login-method'

/** The method saved last; Google when there is none or storage is blocked. */
export function readLoginMethod(): LoginMethod {
  try {
    return localStorage.getItem(LOGIN_METHOD_KEY) === 'password' ? 'password' : 'google'
  } catch {
    return 'google'
  }
}

export function saveLoginMethod(method: LoginMethod) {
  try {
    localStorage.setItem(LOGIN_METHOD_KEY, method)
  } catch {
    // Private mode or blocked storage: the page simply opens on Google next time.
  }
}
