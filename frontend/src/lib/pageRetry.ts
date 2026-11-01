import { createContext } from 'react'

/**
 * Loads the current page again. AppShell remounts it, so a page needs no reload code of its own: every page fetches
 * what it shows when it mounts. Outside AppShell the whole app reloads, which does the same with more waiting.
 */
export const PageRetryContext = createContext<() => void>(() => window.location.reload())
