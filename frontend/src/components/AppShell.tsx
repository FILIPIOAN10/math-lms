import { Suspense, useEffect, useId, useRef, useState } from 'react'
import { Ellipsis } from 'lucide-react'
import { Link, Outlet, useLocation } from 'react-router-dom'
import { Button } from '@/components/ui/button'
import { useAuth } from '@/context/AuthContext'
import { getMyAttempts, listAttemptsForGrading, listPendingUsers, type Role } from '@/lib/api'
import { roCount } from '@/lib/format'
import { isNavActive, NAV_COUNTS_CHANGED, navItems, roleLabel, tabBarItems, type NavCount, type NavItem } from '@/lib/roles'
import { cn } from '@/lib/utils'
import { LoadingCards } from '@/components/LoadingCards'

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
function CountBadge({ kind, n, className }: { kind: NavCount; n: number | null; className?: string }) {
  if (!n) return null
  return (
    <>
      <span
        aria-hidden="true"
        className={cn('ml-1.5 rounded-full bg-destructive px-1.5 text-xs font-semibold text-white', className)}
      >
        {n}
      </span>
      <span className="sr-only">, {COUNT_WORDS[kind](n)}</span>
    </>
  )
}

const TAB_CLASS =
  'relative flex h-14 min-w-0 flex-1 flex-col items-center justify-center gap-0.5 text-[11px] leading-tight font-medium outline-none focus-visible:ring-3 focus-visible:ring-ring/50 focus-visible:ring-inset'

/** The icon of a tab, on a pill when it is the current page (the colour alone would be easy to miss). */
function TabIcon({ icon: Icon, current }: { icon: NavItem['icon']; current: boolean }) {
  return (
    <span className={cn('rounded-full px-4 py-0.5 transition-colors', current && 'bg-muted')}>
      <Icon aria-hidden="true" className="size-5" strokeWidth={current ? 2.25 : 1.75} />
    </span>
  )
}

/**
 * The phone's navigation: a tab bar at the bottom, where the thumb is, instead of a row of links under the name that
 * scrolls sideways and hides the last ones. With more links than fit, the last tab opens "Mai mult" with the rest;
 * it closes on Escape, on a tap outside it, and once a page is picked.
 */
function TabBar({ role, counts, pathname }: { role: Role; counts: Counts; pathname: string }) {
  const { main, more } = tabBarItems(role)
  const [open, setOpen] = useState(false)
  const menuId = useId()
  const bar = useRef<HTMLDivElement>(null)
  const moreButton = useRef<HTMLButtonElement>(null)
  const currentInMore = more.some((item) => isNavActive(item, pathname))

  useEffect(() => {
    if (!open) return
    const onKey = (e: KeyboardEvent) => {
      if (e.key !== 'Escape') return
      setOpen(false)
      moreButton.current?.focus()
    }
    const onPointer = (e: PointerEvent) => {
      if (!bar.current?.contains(e.target as Node)) setOpen(false)
    }
    document.addEventListener('keydown', onKey)
    document.addEventListener('pointerdown', onPointer)
    return () => {
      document.removeEventListener('keydown', onKey)
      document.removeEventListener('pointerdown', onPointer)
    }
  }, [open])

  return (
    <nav
      aria-label="Meniu principal"
      className="fixed inset-x-0 bottom-0 z-20 border-t bg-background pb-[env(safe-area-inset-bottom)] md:hidden"
    >
      <div ref={bar} className="relative mx-auto flex max-w-lg">
        {main.map((item) => {
          const current = isNavActive(item, pathname)
          return (
            <Link
              key={item.to}
              to={item.to}
              aria-current={current ? 'page' : undefined}
              className={cn(TAB_CLASS, current ? 'text-foreground' : 'text-muted-foreground')}
            >
              <TabIcon icon={item.icon} current={current} />
              <span className="max-w-full truncate px-0.5">{item.short ?? item.label}</span>
              {item.count && (
                <CountBadge
                  kind={item.count}
                  n={counts[item.count]}
                  className="absolute top-1 left-1/2 ml-2 min-w-4 px-1 text-center text-[10px] leading-4"
                />
              )}
            </Link>
          )
        })}
        {more.length > 0 && (
          <>
            <button
              ref={moreButton}
              type="button"
              aria-expanded={open}
              aria-controls={menuId}
              data-current={currentInMore ? 'true' : undefined}
              onClick={() => setOpen((o) => !o)}
              className={cn(TAB_CLASS, currentInMore || open ? 'text-foreground' : 'text-muted-foreground')}
            >
              <TabIcon icon={Ellipsis} current={currentInMore} />
              <span>Mai mult</span>
            </button>
            {open && (
              <ul
                id={menuId}
                aria-label="Mai mult"
                className="absolute right-2 bottom-full mb-2 min-w-48 rounded-xl border bg-popover p-1 text-popover-foreground shadow-lg"
              >
                {more.map((item) => {
                  const current = isNavActive(item, pathname)
                  return (
                    <li key={item.to}>
                      <Link
                        to={item.to}
                        aria-current={current ? 'page' : undefined}
                        onClick={() => setOpen(false)}
                        className={cn(
                          'flex h-11 items-center gap-3 rounded-lg px-3 text-sm font-medium outline-none hover:bg-muted focus-visible:ring-3 focus-visible:ring-ring/50',
                          current && 'bg-muted',
                        )}
                      >
                        <item.icon aria-hidden="true" className="size-5 text-muted-foreground" />
                        {item.label}
                      </Link>
                    </li>
                  )
                })}
              </ul>
            )}
          </>
        )}
      </div>
    </nav>
  )
}

/**
 * The frame around every signed-in page: the app name, the role's main links (the current one marked) and the
 * account with the logout button. Who may see a page is still decided by the route guard inside, so with nobody
 * (or a not-yet-approved account) signed in this renders only the page and lets the guard redirect.
 *
 * On a phone the links move to a tab bar at the bottom (TabBar); between phone and desktop widths they take their own
 * row under the name.
 */
export function AppShell() {
  const { user, logout } = useAuth()
  const { pathname } = useLocation()
  const signedIn = user !== null && user.status === 'ACTIVE' && user.role !== null
  // F8: before two-step sign-in is set up every staff count would be a 403, so none are asked for.
  const counts = useNavCounts(signedIn && !user.twoFactorSetupRequired ? user.role : null, pathname)

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
            className="order-last -mx-1 hidden w-full gap-1 overflow-x-auto [scrollbar-width:none] md:flex lg:order-none lg:mx-0 lg:w-auto"
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
      {/* On a phone the bottom padding keeps the end of every page clear of the tab bar. */}
      <main className="pb-[calc(3.5rem+env(safe-area-inset-bottom))] md:pb-0">
        {/* The header stays put while the next page's code downloads. */}
        <Suspense fallback={<div className="mx-auto max-w-3xl p-4"><LoadingCards /></div>}>
          <Outlet />
        </Suspense>
      </main>
      <TabBar role={user.role} counts={counts} pathname={pathname} />
    </div>
  )
}
