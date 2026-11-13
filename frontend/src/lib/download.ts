/** A file name every system accepts: what Windows forbids (\ / : * ? " < > |) becomes a dash. */
export function safeFileName(name: string): string {
  return name.replace(/[\\/:*?"<>|]+/g, '-').trim()
}

/**
 * Hands a file we already hold in memory to the browser's own download: a temporary link to it, one click, and the
 * memory is released. (A plain <a href="/api/..."> cannot renew an expired session; fetching through the API client
 * first can.)
 */
export function saveBlob(blob: Blob, filename: string): void {
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = safeFileName(filename)
  document.body.appendChild(link)
  link.click()
  link.remove()
  URL.revokeObjectURL(url)
}
