/** What the user sees instead of a technical "not found" from the server ("No SchoolClass with id 2"). */
export const NOT_FOUND_MESSAGE = 'Nu am găsit ce cauți — poate a fost șters sau nu ai acces la el.'

/** The request never got an answer: said as it is, not as "the operation failed". */
export const CONNECTION_MESSAGE = 'Nu ne putem conecta la server. Verifică internetul.'

/** F8: the staff gate answers with a code, not a sentence (TwoFactorEnrolmentGate.SETUP_REQUIRED). */
const TWO_FACTOR_SETUP_MESSAGE =
  'Activează întâi autentificarea în doi pași din Contul meu — până atunci paginile de profesor nu se deschid.'

/**
 * Turns a failed API call into a line for the UI. The *ExceptionHandler classes answer with a plain-text body,
 * usually a Romanian sentence written for the user, shown as is. Two kinds are not: Spring's own errors (e.g. a
 * too-large upload) come back as JSON, and "not found" answers are English and technical ("No Quiz with id 5") -
 * those become a plain sentence instead, as does the code of the F8 two-step sign-in gate.
 */
export function errorMessage(e: unknown): string {
  if (e instanceof TypeError) {
    return CONNECTION_MESSAGE // what fetch throws when there is no answer at all
  }
  const { body, status } = (e ?? {}) as { body?: string; status?: number }
  if (status === 404 && (!body || /^No\b/.test(body))) {
    return NOT_FOUND_MESSAGE
  }
  if (body === 'TWO_FACTOR_SETUP_REQUIRED') {
    return TWO_FACTOR_SETUP_MESSAGE
  }
  if (body && body.length > 0 && body.length < 600 && !body.startsWith('{')) {
    return body
  }
  return 'Operația a eșuat. Reîncearcă.'
}
