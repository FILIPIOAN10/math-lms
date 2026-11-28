import { useEffect, useRef, useState } from 'react'
import { Button } from '@/components/ui/button'
import { Dialog } from '@/components/ui/dialog'
import { useAuth } from '@/context/AuthContext'
import { ApiError, getCurrentUser, keepSessionAlive } from '@/lib/api'
import {
  DEFAULT_IDLE_LIMIT_MS,
  HEARTBEAT_MS,
  idlePhase,
  isActivityKey,
  noteSignOut,
  readLastActivity,
  readLastHeartbeat,
  writeLastActivity,
  writeLastHeartbeat,
  type IdlePhase,
  type SignOutNotice,
} from '@/lib/idleSession'

/** The person at the keyboard; pointermove covers a mouse, a pen and a finger. */
const ACTIVITY_EVENTS = ['pointerdown', 'pointermove', 'keydown', 'wheel', 'touchstart', 'scroll'] as const
/** Activity is shared with the other tabs at most this often: pointermove fires many times a second. */
const SHARE_EVERY_MS = 5000
/** How often the idle time is checked; every second while the countdown is on screen. */
const CHECK_MS = 15_000
const COUNTDOWN_MS = 1000

/**
 * Closes a session nobody uses for an hour (the server's limit): "Mai ești aici?" five minutes before, then the
 * sign-out, with a note for the sign-in page. A sleeping computer is caught as soon as it wakes. While the person
 * works, the server hears about it once a minute, so it keeps the session open even on pages that make no request
 * (a long lesson) and closes it by itself if this tab never gets to sign out.
 */
export function IdleSessionGuard() {
  const { user } = useAuth()
  return user ? <IdleWatch /> : null
}

function IdleWatch() {
  const { logout, expireSession } = useAuth()
  const [phase, setPhase] = useState<IdlePhase>({ kind: 'active' })
  const [limitMs, setLimitMs] = useState(DEFAULT_IDLE_LIMIT_MS)
  const expire = useRef(expireSession)
  const stay = useRef(() => {})

  useEffect(() => {
    expire.current = expireSession
  }, [expireSession])

  useEffect(() => {
    let alive = true
    let ended = false
    let limit = DEFAULT_IDLE_LIMIT_MS
    // This tab's own times, so it still works with storage blocked; the other tabs' come from localStorage.
    let ownActivity = Date.now()
    let shared = 0
    let ownBeat = 0
    let period = 0
    let timer: number | undefined

    const end = (notice: SignOutNotice) => {
      if (ended || !alive) return
      ended = true
      noteSignOut(notice)
      void expire.current()
    }

    const ping = () => {
      ownBeat = Date.now()
      writeLastHeartbeat(ownBeat)
      keepSessionAlive()
        .then(({ idleTimeoutMinutes }) => {
          if (!alive || !(idleTimeoutMinutes > 0)) return
          limit = idleTimeoutMinutes * 60_000
          setLimitMs(limit)
          check()
        })
        .catch(async (error: unknown) => {
          // Offline, or the server busy: the next minute's ping tries again.
          if (!alive || !(error instanceof ApiError && error.status === 401)) return
          // Another tab may have renewed the refresh cookie just as this ping left with the old one: ask once more the
          // usual way (which refreshes) before calling the session closed.
          try {
            await getCurrentUser()
          } catch (again) {
            if (again instanceof ApiError && again.status === 401) end('ended')
          }
        })
    }

    const schedule = (ms: number) => {
      if (ms === period) return
      period = ms
      window.clearInterval(timer)
      timer = window.setInterval(check, ms)
    }

    function check() {
      if (ended || !alive) return
      const now = Date.now()
      const last = Math.max(ownActivity, readLastActivity() ?? 0)
      const next = idlePhase(now, last, limit)
      if (next.kind === 'expired') {
        end('idle')
        return
      }
      setPhase((current) => (current.kind === 'active' && next.kind === 'active' ? current : next))
      schedule(next.kind === 'warning' ? COUNTDOWN_MS : CHECK_MS)
      // Once a minute at most, and only if the person did something since the last ping.
      const lastBeat = Math.max(ownBeat, readLastHeartbeat() ?? 0)
      if (last > lastBeat && now - lastBeat >= HEARTBEAT_MS) ping()
    }

    const activity = () => {
      const now = Date.now()
      ownActivity = now
      if (now - shared >= SHARE_EVERY_MS) {
        shared = now
        writeLastActivity(now)
      }
      // The countdown closes at once, not at the next check.
      if (period === COUNTDOWN_MS) check()
    }
    const onVisible = () => {
      if (document.visibilityState === 'visible') check()
    }
    const onStorage = (event: StorageEvent) => {
      if (isActivityKey(event.key)) check()
    }

    // The check pings the server too: nothing was sent for the last 55 minutes.
    stay.current = () => {
      activity()
      check()
    }

    // Signing in (or opening the app on a live session) is activity: a time left by an earlier session in this browser
    // must not sign this one out at once.
    shared = ownActivity
    writeLastActivity(ownActivity)
    ping()
    schedule(CHECK_MS)
    const options = { capture: true, passive: true }
    ACTIVITY_EVENTS.forEach((name) => window.addEventListener(name, activity, options))
    document.addEventListener('visibilitychange', onVisible)
    window.addEventListener('focus', check)
    window.addEventListener('storage', onStorage)
    return () => {
      alive = false
      window.clearInterval(timer)
      ACTIVITY_EVENTS.forEach((name) => window.removeEventListener(name, activity, options))
      document.removeEventListener('visibilitychange', onVisible)
      window.removeEventListener('focus', check)
      window.removeEventListener('storage', onStorage)
    }
  }, [])

  return (
    <Dialog
      open={phase.kind === 'warning'}
      onOpenChange={(open) => {
        if (!open) stay.current()
      }}
      title="Mai ești aici?"
    >
      <p className="text-sm text-muted-foreground">
        Nu ai mai folosit aplicația de aproape {spanOf(limitMs)}. Ca nimeni altcineva să nu lucreze în contul tău pe
        acest dispozitiv, te deconectăm în{' '}
        <span className="font-medium tabular-nums text-foreground" data-testid="idle-countdown">
          {phase.kind === 'warning' ? clock(phase.leftMs) : ''}
        </span>
        .
      </p>
      <div className="mt-5 flex flex-wrap justify-end gap-2">
        <Button variant="outline" onClick={() => void logout()}>
          Ieși acum
        </Button>
        <Button onClick={() => stay.current()} data-testid="idle-stay">
          Rămân conectat
        </Button>
      </div>
    </Dialog>
  )
}

/** 4:59 */
function clock(ms: number): string {
  const seconds = Math.max(0, Math.ceil(ms / 1000))
  return `${Math.floor(seconds / 60)}:${String(seconds % 60).padStart(2, '0')}`
}

/** "o oră", "30 de minute", "2 ore": the server's limit in words. */
function spanOf(ms: number): string {
  const minutes = Math.round(ms / 60_000)
  const counted = (n: number, one: string, many: string) =>
    n === 1 ? one : `${n}${n % 100 >= 1 && n % 100 <= 19 ? '' : ' de'} ${many}`
  return minutes % 60 === 0 ? counted(minutes / 60, 'o oră', 'ore') : counted(minutes, 'un minut', 'minute')
}
