import { Suspense, useEffect, useState } from 'react'
import { Link, NavLink, Outlet, useLocation } from 'react-router-dom'
import { Button } from '@/components/ui/button'
import { useAuth } from '@/context/AuthContext'
import { listAttemptsForGrading, listPendingUsers } from '@/lib/api'
import { roCount } from '@/lib/format'
import { navItems, roleLabel, type NavCount } from '@/lib/roles'
import { cn } from '@/lib/utils'

type Counts = Record<NavCount, number | null>

/**
 * The teacher's work queue sizes, shown next to their links. Re-read on every page change, so the number drops as
 * soon as the teacher comes back from grading. Only hints: if a call fails the link simply shows no number.
 */
function useTeacherCounts(enabled: boolean, pathname: string): Counts {
  const [counts, setCounts] = useState<Counts>({ grading: null, pending: null })

  useEffect(() => {
    if (!enabled) return
    let cancelled = false
    listAttemptsForGrading('SUBMITTED')
      .then((list) => !cancelled && setCounts((c) => ({ ...c, grading: list.length })))
      .catch(() => undefined)
    listPendingUsers()
      .then((list) => !cancelled && setCounts((c) => ({ ...c, pending: list.length })))
      .catch(() => undefined)
    return () => {
      cancelled = true
    }
  }, [enabled, pathname])

  return counts
}

const COUNT_WORDS: Record<NavCount, (n: number) => string> = {
  grading: (n) => `${roCount(n, 'lucrare', 'lucrări')} de corectat`,
  pending: (n) => `${roCount(n, 'cont', 'conturi')} de aprobat`,
}

/** The number on a link: seen as a badge, heard as words ("Corectură, 3 lucrări de corectat"). */
function CountBadge({ kind, n }: { kind: NavCount; n: number | null }) {
  if (!n) return null
  return (
    <>
      <span aria-hidden="true" className="ml-1.5 rounded-full bg-destructive px-1.5 text-xs font-semibold text-white">
        {n}
      </span>
      <span className="sr-only">, {COUNT_WORDS[kind](n)}</span>
    </>
  )
}

/**
 * The frame around every signed-in page: the app name, the role's main links (the current one marked) and the
 * account with the logout button. Who may see a page is still decided by the route guard inside, so with nobody
 * (or a not-yet-approved account) signed in this renders only the page and lets the guard redirect.
 *
 * On a phone the links drop to their own row under the name and scroll sideways instead of wrapping.
 */
export function AppShell() {
  const { user, logout } = useAuth()
  const { pathname } = useLocation()
  const signedIn = user !== null && user.status === 'ACTIVE' && user.role !== null
  const counts = useTeacherCounts(signedIn && user?.role === 'ADMIN', pathname)

  if (!signedIn || !user.role) {
    return <Outlet />
  }

  return (
    <div className="min-h-screen bg-muted">
      <header className="sticky top-0 z-20 border-b bg-background">
        <div className="mx-auto flex max-w-6xl flex-wrap items-center gap-x-4 gap-y-1 px-4 py-2">
          <Link to="/" className="text-base font-semibold">
            Math LMS
          </Link>
          <nav
            aria-label="Navigare principală"
            className="order-last -mx-1 flex w-full gap-1 overflow-x-auto [scrollbar-width:none] lg:order-none lg:mx-0 lg:w-auto"
          >
            {navItems(user.role).map((item) => (
              <NavLink
                key={item.to}
                to={item.to}
                end={item.to === '/'}
                data-testid={item.testId}
                className={({ isActive }) =>
                  cn(
                    'inline-flex h-9 shrink-0 items-center rounded-lg px-3 text-sm font-medium whitespace-nowrap transition-colors outline-none focus-visible:ring-3 focus-visible:ring-ring/50',
                    isActive ? 'bg-muted text-foreground' : 'text-muted-foreground hover:bg-muted hover:text-foreground',
                  )
                }
              >
                {item.label}
                {item.count && <CountBadge kind={item.count} n={counts[item.count]} />}
              </NavLink>
            ))}
          </nav>
          <div className="ml-auto flex items-center gap-3">
            <p className="hidden text-right leading-tight sm:block">
              <span className="block text-sm font-medium">{user.fullName}</span>
              <span className="block text-xs text-muted-foreground">{roleLabel(user.role)}</span>
            </p>
            <Button variant="outline" size="sm" onClick={logout} data-testid="logout">
              Ieșire
            </Button>
          </div>
        </div>
      </header>
      <main>
        {/* The header stays put while the next page's code downloads. */}
        <Suspense fallback={<p className="p-4 text-muted-foreground">Se încarcă...</p>}>
          <Outlet />
        </Suspense>
      </main>
    </div>
  )
}
