import { afterEach, describe, expect, it, vi } from 'vitest'
import { applyTheme, readThemeChoice, saveThemeChoice, THEME_KEY } from '@/lib/theme'

function systemPrefersDark(dark: boolean) {
  vi.stubGlobal('matchMedia', (query: string) => ({
    matches: dark && query === '(prefers-color-scheme: dark)', media: query,
    addEventListener: () => {}, removeEventListener: () => {},
  }))
}

describe('theme', () => {
  afterEach(() => {
    localStorage.clear()
    document.documentElement.classList.remove('dark')
    vi.unstubAllGlobals()
  })

  it('is light until the user picks something else (the app looked light so far)', () => {
    expect(readThemeChoice()).toBe('light')
    localStorage.setItem(THEME_KEY, 'purple')
    expect(readThemeChoice()).toBe('light')
  })

  it('remembers the choice in this browser', () => {
    saveThemeChoice('dark')
    expect(readThemeChoice()).toBe('dark')
    expect(localStorage.getItem(THEME_KEY)).toBe('dark')
  })

  it('switches the dark class on the page', () => {
    applyTheme('dark')
    expect(document.documentElement).toHaveClass('dark')
    applyTheme('light')
    expect(document.documentElement).not.toHaveClass('dark')
  })

  it('follows the system with "system"', () => {
    systemPrefersDark(true)
    applyTheme('system')
    expect(document.documentElement).toHaveClass('dark')
    systemPrefersDark(false)
    applyTheme('system')
    expect(document.documentElement).not.toHaveClass('dark')
  })
})
