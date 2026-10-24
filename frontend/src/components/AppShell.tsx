import { Suspense, useEffect, useState } from 'react'
import { Link, Outlet, useLocation } from 'react-router-dom'
import { Button } from '@/components/ui/button'
import { useAuth } from '@/context/AuthContext'
import { getMyAttempts, listAttemptsForGrading, listPendingUsers, type Role } from '@/lib/api'
import { roCount } from '@/lib/format'
import { isNavActive, NAV_COUNTS_CHANGED, navItems, roleLabel, type NavCount } from '@/lib/roles'
import { cn } from '@/lib/utils'

type Counts = Record<NavCount, number | null>

/**
 * The numbers next to the links: the teacher's work queue sizes, the student's new grades (S3). Re-read on every page
 * change - and when a page says a count changed - so the number drops as soon as the teacher comes back from grading
 * or the student opens the result. Only hints: if a call fails the link simply shows no number.
 */
function useNavCounts(role: Role | null, pathname: string): Counts {
  const [counts, setCounts] = useState<Counts>({ grading: null, pending: null, newResults: null })
  const [changed, setChanged] = useState(0)

  useEffect(() => {
    const bump = () => setChanged((n) => n + 1)
    window.addEventListener(NAV_COUNTS_CHANGED, bump)
    return () => window.removeEventListener(NAV_COUNTS_CHANGED, bump)
  }, [])

  useEffect(() => {
    let cancelled = false
    if (role === 'ADMIN' || role === 'TEACHER') {
      listAttemptsForGrading('SUBMITTED')
        .then((list) => !cancelled && setCounts((c) => ({ ...c, grading: list.length })))
        .catch(() => undefined)
    }
    if (role === 'ADMIN') {
      // Approving accounts is an admin's job: a teacher has no such count.
      listPendingUsers()
        .then((list) => !cancelled && setCounts((c) => ({ ...c, pending: list.length })))
        .catch(() => undefined)
    } else if (role === 'STUDENT') {
      getMyAttempts()
        .then((list) => !cancelled && setCounts((c) => ({ ...c, newResults: list.filter((a) => a.newResult).length })))
        .catch(() => undefined)
    }
    return () => {
      cancelled = true
    }
  }, [role, pathname, changed])

  return counts
}

const COUNT_WORDS: Record<NavCount, (n: number) => string> = {
  grading: (n) => `${roCount(n, 'lucrare', 'lucrări')} de corectat`,
  pending: (n) => `${roCount(n, 'cont', 'conturi')} de aprobat`,
  newResults: (n) => roCount(n, 'notă nouă', 'note noi'),
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
  const counts = useNavCounts(signedIn ? user.role : null, pathname)

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
            {navItems(user.role).map((item) => {
              const active = isNavActive(item, pathname)
              return (
                <Link
                  key={item.to}
                  to={item.to}
                  data-testid={item.testId}
                  aria-current={active ? 'page' : undefined}
                  className={cn(
                    'inline-flex h-9 shrink-0 items-center rounded-lg px-3 text-sm font-medium whitespace-nowrap transition-colors outline-none focus-visible:ring-3 focus-visible:ring-ring/50',
                    active ? 'bg-muted text-foreground' : 'text-muted-foreground hover:bg-muted hover:text-foreground',
                  )}
                >
                  {item.label}
                  {item.count && <CountBadge kind={item.count} n={counts[item.count]} />}
                </Link>
              )
            })}
          </nav>
          <div className="ml-auto flex items-center gap-3">
            <Link
              to="/account"
              title="Contul meu"
              className="rounded-lg px-2 py-1 text-right leading-tight outline-none hover:bg-muted focus-visible:ring-3 focus-visible:ring-ring/50"
            >
              <span className="text-sm font-medium sm:hidden">Contul meu</span>
              <span className="hidden sm:block">
                <span className="sr-only">Contul meu: </span>
                <span className="block text-sm font-medium">{user.fullName}</span>
                <span className="block text-xs text-muted-foreground">{roleLabel(user.role)}</span>
              </span>
            </Link>
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
