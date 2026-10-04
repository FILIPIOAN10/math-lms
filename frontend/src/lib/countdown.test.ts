import { describe, expect, it } from 'vitest'
import { formatClock, secondsLeft } from './countdown'

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
