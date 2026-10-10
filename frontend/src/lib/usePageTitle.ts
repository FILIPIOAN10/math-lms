import { useEffect } from 'react'

const APP = 'Math LMS'

/**
 * Names the browser tab after the page ("Corectură · Math LMS"), so several open tabs - and the history list - can be
 * told apart. Without a title (still loading) the tab just says the app's name.
 */
export function usePageTitle(title: string | null | undefined) {
  useEffect(() => {
    document.title = title ? `${title} · ${APP}` : APP
  }, [title])
}
