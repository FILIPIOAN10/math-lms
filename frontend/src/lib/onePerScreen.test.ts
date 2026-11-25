import { afterEach, describe, expect, it } from 'vitest'
import { ONE_PER_SCREEN_KEY, readOnePerScreen, saveOnePerScreen, startsInSideScroller, swipeDirection } from '@/lib/onePerScreen'

describe('onePerScreen', () => {
  afterEach(() => localStorage.removeItem(ONE_PER_SCREEN_KEY))

  it('is off until the student turns it on, and remembers it on this device', () => {
    expect(readOnePerScreen()).toBe(false)
    saveOnePerScreen(true)
    expect(readOnePerScreen()).toBe(true)
    saveOnePerScreen(false)
    expect(localStorage.getItem(ONE_PER_SCREEN_KEY)).toBeNull()
  })

  it('reads a long sideways move as a swipe, left = next', () => {
    expect(swipeDirection(-120, 10)).toBe('next')
    expect(swipeDirection(90, -20)).toBe('prev')
  })

  it('ignores a tap, a short move and a scroll that drifted sideways', () => {
    expect(swipeDirection(-30, 0)).toBeNull()
    expect(swipeDirection(-100, 80)).toBeNull()
    expect(swipeDirection(0, 300)).toBeNull()
  })

  it('leaves a swipe that starts in a text box to the text box', () => {
    const container = document.createElement('div')
    const input = document.createElement('input')
    const span = document.createElement('span')
    container.append(input, span)
    expect(startsInSideScroller(input, container)).toBe(true)
    expect(startsInSideScroller(span, container)).toBe(false)
  })
})
