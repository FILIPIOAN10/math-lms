import { describe, expect, it } from 'vitest'
import { assignmentBadge, dueCountdown, isoToLocalInput, localInputToIso } from './assignments'

const HOUR = 3_600_000
const DAY = 24 * HOUR
const NOW = Date.parse('2026-10-05T10:00:00Z')

describe('assignmentBadge', () => {
  it('says "De făcut" for something not started', () => {
    expect(assignmentBadge({ state: 'NOT_STARTED', late: false, overdue: false })).toEqual({ text: 'De făcut', tone: 'info' })
  })

  it('says "În lucru" while an attempt is open', () => {
    expect(assignmentBadge({ state: 'IN_PROGRESS', late: false, overdue: false }).text).toBe('În lucru')
  })

  it('flags an unfinished assignment whose deadline passed as overdue, in red', () => {
    expect(assignmentBadge({ state: 'NOT_STARTED', late: false, overdue: true })).toEqual({ text: 'Termen depășit', tone: 'danger' })
    expect(assignmentBadge({ state: 'IN_PROGRESS', late: false, overdue: true }).tone).toBe('danger')
  })

  it('says handed in once it is, graded or not', () => {
    expect(assignmentBadge({ state: 'SUBMITTED', late: false, overdue: false })).toEqual({ text: 'Predată', tone: 'success' })
    expect(assignmentBadge({ state: 'GRADED', late: false, overdue: false }).text).toBe('Predată')
  })

  it('marks a late hand-in as late, not as overdue', () => {
    expect(assignmentBadge({ state: 'SUBMITTED', late: true, overdue: false })).toEqual({
      text: 'Predată cu întârziere', tone: 'warning',
    })
  })
})

describe('dueCountdown', () => {
  it('counts hours inside the last day, with the Romanian "de" from 20 up', () => {
    expect(dueCountdown(new Date(NOW + 5 * HOUR).toISOString(), NOW)).toBe('peste 5 ore')
    expect(dueCountdown(new Date(NOW + HOUR).toISOString(), NOW)).toBe('peste 1 oră')
    expect(dueCountdown(new Date(NOW + 22 * HOUR).toISOString(), NOW)).toBe('peste 22 de ore')
  })

  it('counts days beyond that', () => {
    expect(dueCountdown(new Date(NOW + DAY).toISOString(), NOW)).toBe('peste 1 zi')
    expect(dueCountdown(new Date(NOW + 3 * DAY).toISOString(), NOW)).toBe('peste 3 zile')
    expect(dueCountdown(new Date(NOW + 25 * DAY).toISOString(), NOW)).toBe('peste 25 de zile')
  })

  it('rounds to the nearest unit instead of under-reporting', () => {
    expect(dueCountdown(new Date(NOW + 2 * HOUR - 60_000).toISOString(), NOW)).toBe('peste 2 ore')   // 1 h 59 min
    expect(dueCountdown(new Date(NOW + 2 * DAY + 23 * HOUR).toISOString(), NOW)).toBe('peste 3 zile') // 2 d 23 h
    expect(dueCountdown(new Date(NOW + HOUR + 20 * 60_000).toISOString(), NOW)).toBe('peste 1 oră')   // 1 h 20 min
  })

  it('is gentle about the last minutes', () => {
    expect(dueCountdown(new Date(NOW + 20 * 60_000).toISOString(), NOW)).toBe('în mai puțin de o oră')
  })

  it('says how long ago when the deadline passed', () => {
    expect(dueCountdown(new Date(NOW - 20 * 60_000).toISOString(), NOW)).toBe('depășit')
    expect(dueCountdown(new Date(NOW - 3 * HOUR).toISOString(), NOW)).toBe('depășit cu 3 ore')
    expect(dueCountdown(new Date(NOW - 2 * DAY).toISOString(), NOW)).toBe('depășit cu 2 zile')
  })
})

describe('date input conversion', () => {
  it('round-trips a local date-time through an ISO instant, whatever the time zone', () => {
    expect(isoToLocalInput(localInputToIso('2026-10-12T18:00'))).toBe('2026-10-12T18:00')
    expect(isoToLocalInput(localInputToIso('2027-01-01T00:05'))).toBe('2027-01-01T00:05')
  })

  it('produces an ISO instant the server can parse', () => {
    expect(localInputToIso('2026-10-12T18:00')).toMatch(/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}\.\d{3}Z$/)
  })
})
