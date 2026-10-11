/** What the user sees instead of a technical "not found" from the server ("No SchoolClass with id 2"). */
export const NOT_FOUND_MESSAGE = 'Nu am găsit ce cauți — poate a fost șters sau nu ai acces la el.'

/**
 * Turns a failed API call into a line for the UI. The *ExceptionHandler classes answer with a plain-text body,
 * usually a Romanian sentence written for the user, shown as is. Two kinds are not: Spring's own errors (e.g. a
 * too-large upload) come back as JSON, and "not found" answers are English and technical ("No Quiz with id 5") -
 * those become a plain sentence instead.
 */
export function errorMessage(e: unknown): string {
  const { body, status } = (e ?? {}) as { body?: string; status?: number }
  if (status === 404 && (!body || /^No\b/.test(body))) {
    return NOT_FOUND_MESSAGE
  }
  if (body && body.length > 0 && body.length < 600 && !body.startsWith('{')) {
    return body
  }
  return 'Operația a eșuat. Reîncearcă.'
}
