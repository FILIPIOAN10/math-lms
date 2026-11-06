import { describe, expect, it } from 'vitest'
import { formatClock, formatTimeLeft, runningClock, secondsLeft } from './countdown'

const DEADLINE = '2026-10-04T13:00:00Z'
const SERVER_NOW = '2026-10-04T12:30:00Z' // 30 minutes before the deadline

describe('secondsLeft', () => {
  it('is the server-side distance to the deadline at the moment the page received it', () => {
    expect(secondsLeft(DEADLINE, SERVER_NOW, 1_000_000, 1_000_000)).toBe(1800)
  })

  it('counts down with the browser clock elapsed since then', () => {
    expect(secondsLeft(DEADLINE, SERVER_NOW, 1_000_000, 1_000_000 + 65_000)).toBe(1800 - 65)
  })

  it('does not care what time the browser thinks it is (a skewed device clock changes nothing)', () => {
    // received at "browser time" 5 and 9_999_999_999 — only the elapsed duration matters
    expect(secondsLeft(DEADLINE, SERVER_NOW, 5, 5 + 60_000)).toBe(1740)
    expect(secondsLeft(DEADLINE, SERVER_NOW, 9_999_999_999, 9_999_999_999 + 60_000)).toBe(1740)
  })

  it('never goes below zero', () => {
    expect(secondsLeft(DEADLINE, SERVER_NOW, 0, 99 * 60 * 1000)).toBe(0)
  })

  it('rounds up, so the last partial second still shows 0:01, not 0:00', () => {
    expect(secondsLeft(DEADLINE, SERVER_NOW, 0, 1_799_500)).toBe(1) // 0.5 s left
  })
})

describe('formatClock', () => {
  it('shows minutes and seconds under an hour', () => {
    expect(formatClock(0)).toBe('0:00')
    expect(formatClock(9)).toBe('0:09')
    expect(formatClock(65)).toBe('1:05')
    expect(formatClock(45 * 60)).toBe('45:00')
  })

  it('adds hours from an hour up', () => {
    expect(formatClock(3600)).toBe('1:00:00')
    expect(formatClock(3600 + 61)).toBe('1:01:01')
  })

  it('clamps a negative value to zero', () => {
    expect(formatClock(-5)).toBe('0:00')
  })
})

describe('formatTimeLeft', () => {
  it('says hours and minutes from an hour up', () => {
    expect(formatTimeLeft(95 * 60)).toBe('1 h 35 min')
    expect(formatTimeLeft(2 * 3600)).toBe('2 h')
    expect(formatTimeLeft(3600 + 61)).toBe('1 h 1 min')
  })

  it('says the minutes under an hour the way Romanian counts them', () => {
    expect(formatTimeLeft(42 * 60)).toBe('42 de minute')
    expect(formatTimeLeft(5 * 60)).toBe('5 minute')
    expect(formatTimeLeft(60)).toBe('1 minut')
  })

  it('never promises time that is not there: a started minute does not count', () => {
    expect(formatTimeLeft(3599)).toBe('59 de minute')
    expect(formatTimeLeft(119)).toBe('1 minut')
    expect(formatTimeLeft(59)).toBe('mai puțin de un minut')
  })
})

describe('runningClock', () => {
  const open = { mode: 'TEST', status: 'IN_PROGRESS', deadlineAt: DEADLINE, serverNow: SERVER_NOW } as const

  it('counts an open timed test down from the server clock', () => {
    expect(runningClock(open, 1_000, 61_000)).toBe(1740)
  })

  it('has no clock for an untimed test, a practice or a handed-in test', () => {
    expect(runningClock({ ...open, deadlineAt: null }, 0, 0)).toBeNull()
    expect(runningClock({ ...open, deadlineAt: undefined, serverNow: undefined }, 0, 0)).toBeNull()
    expect(runningClock({ ...open, mode: 'PRACTICE' }, 0, 0)).toBeNull()
    expect(runningClock({ ...open, status: 'SUBMITTED' }, 0, 0)).toBeNull()
  })

  it('is zero once the time is up and the server has not handed the paper in yet', () => {
    expect(runningClock(open, 0, 31 * 60_000)).toBe(0)
  })
})
