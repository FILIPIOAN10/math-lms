/**
 * Turns a failed API call into a line for the UI. The *ExceptionHandler classes answer with a
 * plain-text body; Spring's own errors (e.g. a too-large upload) come back as JSON, which is not
 * worth showing raw — those fall back to the generic message.
 */
export function errorMessage(e: unknown): string {
  const body = (e as { body?: string })?.body
  if (body && body.length > 0 && body.length < 300 && !body.startsWith('{')) {
    return body
  }
  return 'Operația a eșuat. Reîncearcă.'
}
