import { useEffect } from 'react'
import { useLocation, useNavigationType } from 'react-router-dom'

/**
 * A new page starts at its top; Back and Forward are left alone, so the browser returns to the place the student was
 * reading. Above the routes, so it also covers the test screen, which has no AppShell. Only a new path counts: a change
 * in the query (a paper opened, a filter) is not a new page.
 */
export function ScrollToTop() {
  const { pathname } = useLocation()
  const navigationType = useNavigationType()

  useEffect(() => {
    if (navigationType !== 'POP') window.scrollTo(0, 0)
    // eslint-disable-next-line react-hooks/exhaustive-deps -- a new path, not a new kind of navigation
  }, [pathname])

  return null
}
