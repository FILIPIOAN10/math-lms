/**
 * The page a signed-out visitor asked for (the "rezultatul e gata" email link, a paper to grade), kept until they have
 * signed in so the sign-in takes them there instead of to Acasă. In sessionStorage because the Google sign-in leaves
 * the app and comes back in the same tab; one tab's wish does not spill into another.
 */
const RETURN_TO_KEY = 'mathlms-return-to'

/** Pages that are the sign-in itself: going "back" to them after signing in makes no sense. */
const NOT_A_DESTINATION = ['/', '/login', '/pending', '/register', '/forgot-password', '/reset-password', '/verify-email', '/erase-account']

// Set by a sign-out: the guard of the page the person was on then sends to /login too, and that page is theirs, not
// the next person's on this computer.
let signedOut = false

/** The path when it is a page of this app worth returning to; null for other sites (`//x`, `https:`) and sign-in pages. */
export function safeReturnPath(path: string | null | undefined): string | null {
  if (!path || !path.startsWith('/') || path.startsWith('//') || path.startsWith('/\\')) return null
  const bare = path.split(/[?#]/)[0]
  return NOT_A_DESTINATION.includes(bare) ? null : path
}

export function rememberReturnTo(path: string) {
  if (signedOut || safeReturnPath(path) === null) return
  try {
    sessionStorage.setItem(RETURN_TO_KEY, path)
  } catch {
    // Blocked storage: the sign-in lands on Acasă, as before.
  }
}

/** The remembered page, without forgetting it: React may render the sign-in redirect twice. */
export function readReturnTo(): string | null {
  try {
    return safeReturnPath(sessionStorage.getItem(RETURN_TO_KEY))
  } catch {
    return null
  }
}

/** Once the signed-in person has been sent on; also ends the "just signed out" pause. */
export function clearReturnTo() {
  signedOut = false
  try {
    sessionStorage.removeItem(RETURN_TO_KEY)
  } catch {
    // Nothing kept, nothing to clear.
  }
}

/** Called by the sign-out: forget the page and do not remember the one the guard is about to leave. */
export function forgetReturnOnSignOut() {
  clearReturnTo()
  signedOut = true
}
