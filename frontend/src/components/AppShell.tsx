import { Suspense, useEffect, useId, useRef, useState } from 'react'
import { Ellipsis, LogOut, Moon, Search, Sun } from 'lucide-react'
import { Link, Outlet, useLocation } from 'react-router-dom'
import { Button } from '@/components/ui/button'
import { CommandPalette } from '@/components/CommandPalette'
import { useAuth } from '@/context/AuthContext'
import { getMyAttempts, listAttemptsForGrading, listPendingUsers, type Role } from '@/lib/api'
import { roCount } from '@/lib/format'
import {
  hasNavigation,
  isNavActive,
  NAV_COUNTS_CHANGED,
  navItems,
  roleLabel,
  secondaryNavItems,
  tabBarItems,
  type NavCount,
  type NavItem,
} from '@/lib/roles'
import { chooseTheme } from '@/lib/theme'
import { initials, isNewPage } from '@/lib/shell'
import { cn } from '@/lib/utils'
import { LoadingCards } from '@/components/LoadingCards'
import { PageRetryContext } from '@/lib/pageRetry'
import { StudentTutorBox } from '@/components/StudentTutorBox'

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
    <span className={cn('rounded-full px-4 py-0.5 transition-colors', current && 'bg-primary/10')}>
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
              className={cn(TAB_CLASS, current ? 'text-primary' : 'text-muted-foreground')}
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
              className={cn(TAB_CLASS, currentInMore || open ? 'text-primary' : 'text-muted-foreground')}
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
                          current && 'bg-primary/10 text-primary',
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

const SIDE_LINK =
  'inline-flex h-9 shrink-0 items-center gap-1.5 rounded-lg px-3 text-sm font-medium whitespace-nowrap transition-colors outline-none focus-visible:ring-3 focus-visible:ring-ring/50 lg:h-10 lg:gap-3'

/** Light or dark in one press; "follow the system" stays in Contul meu. */
function ThemeToggle({ className }: { className?: string }) {
  const [dark, setDark] = useState(() => document.documentElement.classList.contains('dark'))
  return (
    <Button
      variant="ghost"
      size="icon"
      className={className}
      aria-label="Temă întunecată"
      aria-pressed={dark}
      title={dark ? 'Treci la tema luminoasă' : 'Treci la tema întunecată'}
      onClick={() => {
        chooseTheme(dark ? 'light' : 'dark')
        setDark(!dark)
      }}
    >
      {dark ? <Sun aria-hidden="true" /> : <Moon aria-hidden="true" />}
    </Button>
  )
}

/** Plays the page's short fade-in when the user moves to another page (only when motion is welcome, see index.css). */
function usePageEnter(pathname: string) {
  const ref = useRef<HTMLDivElement>(null)
  const last = useRef(pathname)
  useEffect(() => {
    const el = ref.current
    if (!el || !isNewPage(last.current, pathname)) {
      last.current = pathname
      return
    }
    last.current = pathname
    el.classList.remove('page-in')
    void el.offsetWidth // restart the animation
    el.classList.add('page-in')
  }, [pathname])
  return ref
}

/**
 * The frame around every signed-in page: the app name, the role's main links (the current one marked) and the
 * account with the logout button. Who may see a page is still decided by the route guard inside, so with nobody
 * (or a not-yet-approved account) signed in this renders only the page and lets the guard redirect.
 *
 * One header, three shapes: on a computer (`lg`) it is a side menu - the links in a column, the rarer pages under
 * "Mai mult", the account, theme and "Ieșire" at the bottom; on a tablet a top bar with the links in a row under the
 * name; on a phone a top bar and the tab bar at the bottom (TabBar). Ctrl+K (or the search button) opens the search.
 */
export function AppShell() {
  const { user, logout } = useAuth()
  const { pathname } = useLocation()
  // "Reîncearcă" on a page that could not load: a new key remounts it, and it fetches what it shows again.
  const [retries, setRetries] = useState(0)
  const [searching, setSearching] = useState(false)
  const pageRef = usePageEnter(pathname)
  const signedIn = user !== null && user.status === 'ACTIVE' && user.role !== null
  // F8: before two-step sign-in is set up every staff count would be a 403, so none are asked for.
  const counts = useNavCounts(signedIn && !user.twoFactorSetupRequired ? user.role : null, pathname)

  useEffect(() => {
    if (!signedIn) return
    const onKey = (e: KeyboardEvent) => {
      if ((e.ctrlKey || e.metaKey) && e.key.toLowerCase() === 'k') {
        e.preventDefault()
        setSearching(true)
      }
    }
    document.addEventListener('keydown', onKey)
    return () => document.removeEventListener('keydown', onKey)
  }, [signedIn])

  if (!signedIn || !user.role) {
    return <Outlet />
  }
  const withNav = hasNavigation(user.role)
  const more = secondaryNavItems(user.role)
  // On a rarer page its own link is the current one, not the main link that groups it (Clase for Capitole EN).
  const moreActive = more.some((item) => isNavActive(item, pathname))

  return (
    <div className="min-h-screen bg-muted">
      <header className="sticky top-0 z-20 border-b bg-background lg:fixed lg:inset-y-0 lg:left-0 lg:w-60 lg:border-r lg:border-b-0">
        <div aria-hidden="true" className="hero-surface h-1" />
        <div className="mx-auto flex max-w-6xl flex-wrap items-center gap-x-2 gap-y-1 px-4 py-2 sm:gap-x-3 lg:h-[calc(100%-0.25rem)] lg:max-w-none lg:flex-col lg:flex-nowrap lg:items-stretch lg:gap-1 lg:overflow-y-auto lg:p-3">
          <Link to="/" className="flex items-center gap-2 text-base font-semibold lg:px-2 lg:pt-1 lg:pb-3">
            <span aria-hidden="true" className="hero-surface flex size-7 items-center justify-center rounded-lg bg-primary text-xs font-bold text-white">
              x²
            </span>
            Math LMS
          </Link>
          <button
            type="button"
            onClick={() => setSearching(true)}
            aria-label="Caută"
            aria-keyshortcuts="Control+K"
            data-testid="open-search"
            className="ml-auto flex size-11 items-center justify-center rounded-lg text-muted-foreground outline-none hover:bg-muted hover:text-foreground focus-visible:ring-3 focus-visible:ring-ring/50 md:size-9 lg:order-none lg:mb-2 lg:ml-0 lg:h-9 lg:w-full lg:justify-start lg:gap-2 lg:bg-muted lg:px-3 lg:text-sm"
          >
            <Search aria-hidden="true" className="size-4 shrink-0" />
            <span aria-hidden="true" className="hidden flex-1 text-left lg:inline">Caută…</span>
            <kbd aria-hidden="true" className="hidden rounded border bg-background px-1.5 text-xs lg:inline">Ctrl K</kbd>
          </button>
          {withNav && (
            <nav
              aria-label="Navigare principală"
              className="order-last -mx-1 hidden w-full gap-1 overflow-x-auto [scrollbar-width:none] md:flex lg:order-none lg:mx-0 lg:flex-col lg:overflow-visible"
            >
              {navItems(user.role).map((item) => {
                const active = !moreActive && isNavActive(item, pathname)
                return (
                  <Link
                    key={item.to}
                    to={item.to}
                    data-testid={item.testId}
                    aria-current={active ? 'page' : undefined}
                    className={cn(SIDE_LINK, active ? 'bg-primary/10 text-primary' : 'text-muted-foreground hover:bg-muted hover:text-foreground')}
                  >
                    <item.icon aria-hidden="true" className="size-4" />
                    {item.label}
                    {item.count && <CountBadge kind={item.count} n={counts[item.count]} className="lg:ml-auto" />}
                  </Link>
                )
              })}
            </nav>
          )}
          {more.length > 0 && (
            <nav aria-label="Mai mult" className="hidden lg:mt-4 lg:flex lg:flex-col lg:gap-1">
              <p aria-hidden="true" className="px-3 pb-1 text-xs font-medium text-muted-foreground">Mai mult</p>
              {more.map((item) => {
                const active = isNavActive(item, pathname)
                return (
                  <Link
                    key={item.to}
                    to={item.to}
                    aria-current={active ? 'page' : undefined}
                    className={cn(SIDE_LINK, active ? 'bg-primary/10 text-primary' : 'text-muted-foreground hover:bg-muted hover:text-foreground')}
                  >
                    <item.icon aria-hidden="true" className="size-4" />
                    {item.label}
                  </Link>
                )
              })}
            </nav>
          )}
          <div className="flex items-center gap-2 lg:mt-auto lg:flex-col lg:items-stretch lg:gap-2 lg:border-t lg:pt-3">
            <Link
              to="/account"
              title="Contul meu"
              className="flex items-center gap-2 rounded-lg px-2 py-1 leading-tight outline-none hover:bg-muted focus-visible:ring-3 focus-visible:ring-ring/50"
            >
              <span aria-hidden="true" className="flex size-8 shrink-0 items-center justify-center rounded-full bg-primary/10 text-xs font-semibold text-primary">
                {initials(user.fullName)}
              </span>
              {/* On a phone the round initials stand for the account: the words would push the row onto two lines. */}
              <span className="sr-only sm:hidden">Contul meu</span>
              <span className="hidden min-w-0 text-right sm:block lg:text-left">
                <span className="sr-only">Contul meu: </span>
                <span className="block truncate text-sm font-medium">{user.fullName}</span>
                <span className="block text-xs text-muted-foreground">{roleLabel(user.role)}</span>
              </span>
            </Link>
            <div className="flex items-center gap-1 lg:justify-between">
              {/* On a phone the theme is in Contul meu: the top bar keeps room for the search and "Ieșire". */}
              <ThemeToggle className="max-sm:hidden" />
              <Button variant="outline" size="sm" onClick={logout} data-testid="logout">
                <LogOut aria-hidden="true" className="hidden lg:block" />
                Ieșire
              </Button>
            </div>
          </div>
        </div>
      </header>
      {/* On a phone the bottom padding keeps the end of every page clear of the tab bar; on a computer the left one
          keeps it clear of the side menu. */}
      <main className={cn('lg:pl-60', withNav && 'pb-[calc(3.5rem+env(safe-area-inset-bottom))] md:pb-0')}>
        {/* The header stays put while the next page's code downloads. */}
        <Suspense fallback={<div className="mx-auto max-w-3xl p-4"><LoadingCards /></div>}>
          <PageRetryContext.Provider value={() => setRetries((n) => n + 1)}>
            <div ref={pageRef}>
              <Outlet key={retries} />
            </div>
          </PageRetryContext.Provider>
        </Suspense>
      </main>
      <CommandPalette role={user.role} open={searching} onClose={() => setSearching(false)} />
      {withNav && <TabBar role={user.role} counts={counts} pathname={pathname} />}
      {/* The student's AI box ("Nu înțeleg…"): renders nothing while the server has it switched off. */}
      {user.role === 'STUDENT' && !pathname.endsWith('/take') && <StudentTutorBox />}
    </div>
  )
}
