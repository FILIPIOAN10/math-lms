import { beforeEach, describe, expect, it } from 'vitest'
import {
  clearSignOutNotice,
  forgetActivity,
  idlePhase,
  noteSignOut,
  readLastActivity,
  readLastHeartbeat,
  readSignOutNotice,
  WARNING_MS,
  writeLastActivity,
  writeLastHeartbeat,
} from '@/lib/idleSession'

const HOUR = 60 * 60_000

describe('idlePhase', () => {
  const start = Date.parse('2026-11-28T08:00:00Z')

  it('is active until five minutes before the limit', () => {
    expect(idlePhase(start + HOUR - WARNING_MS - 1, start, HOUR)).toEqual({ kind: 'active' })
  })

  it('warns in the last five minutes, with the time left', () => {
    expect(idlePhase(start + HOUR - WARNING_MS, start, HOUR)).toEqual({ kind: 'warning', leftMs: WARNING_MS })
    expect(idlePhase(start + HOUR - 1000, start, HOUR)).toEqual({ kind: 'warning', leftMs: 1000 })
  })

  it('has expired once the limit is reached', () => {
    expect(idlePhase(start + HOUR, start, HOUR)).toEqual({ kind: 'expired' })
    expect(idlePhase(start + 3 * HOUR, start, HOUR)).toEqual({ kind: 'expired' })
  })

  it('never warns earlier than the limit allows on a short limit', () => {
    // A 3-minute limit (a test setting): the warning starts right away, not before the session even began.
    expect(idlePhase(start, start, 3 * 60_000)).toEqual({ kind: 'warning', leftMs: 3 * 60_000 })
  })
})

describe('activity shared by the tabs', () => {
  beforeEach(() => {
    localStorage.clear()
  })

  it('keeps the last activity and the last ping until they are forgotten', () => {
    expect(readLastActivity()).toBeNull()
    writeLastActivity(1000)
    writeLastHeartbeat(500)

    expect(readLastActivity()).toBe(1000)
    expect(readLastHeartbeat()).toBe(500)

    forgetActivity()
    expect(readLastActivity()).toBeNull()
    expect(readLastHeartbeat()).toBeNull()
  })

  it('ignores a value that is not a time', () => {
    localStorage.setItem('mathlms-last-activity', 'ieri')
    expect(readLastActivity()).toBeNull()
  })
})

describe('the note left for the sign-in page', () => {
  beforeEach(() => {
    sessionStorage.clear()
  })

  it('says why the session ended until the sign-in page has shown it', () => {
    expect(readSignOutNotice()).toBeNull()
    noteSignOut('idle')

    // Read twice on purpose: React may render the sign-in page twice before it settles.
    expect(readSignOutNotice()).toBe('idle')
    expect(readSignOutNotice()).toBe('idle')

    clearSignOutNotice()
    expect(readSignOutNotice()).toBeNull()
  })

  it('accepts only the reasons it knows', () => {
    sessionStorage.setItem('mathlms-sign-out-notice', '<b>altceva</b>')
    expect(readSignOutNotice()).toBeNull()
  })
})
