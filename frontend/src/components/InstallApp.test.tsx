import { act, render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { InstallAppSection, InstallBanner } from '@/components/InstallApp'
import { captureInstallPrompt, isAppleMobile, registerServiceWorker, resetInstallStateForTests } from '@/lib/pwa'

/** A fake window: an event target, so the test can fire Chrome's install events at the captured listeners. */
const fakeWindow = new EventTarget() as unknown as Window
captureInstallPrompt(fakeWindow)

function offerInstall(outcome: 'accepted' | 'dismissed' = 'accepted') {
  const event = Object.assign(new Event('beforeinstallprompt', { cancelable: true }), {
    prompt: vi.fn(async () => undefined),
    userChoice: Promise.resolve({ outcome }),
  })
  act(() => void fakeWindow.dispatchEvent(event))
  return event
}

describe('install the app (E11)', () => {
  beforeEach(() => {
    act(() => resetInstallStateForTests())
    localStorage.clear()
  })
  afterEach(() => vi.unstubAllGlobals())

  it("offers the browser's own install dialog once the browser says the app can be installed", async () => {
    render(<InstallAppSection />)
    expect(screen.queryByRole('button', { name: 'Instalează aplicația' })).not.toBeInTheDocument()

    const event = offerInstall('accepted')
    expect(event.defaultPrevented).toBe(true) // no mini-infobar of the browser's own
    await userEvent.click(await screen.findByRole('button', { name: 'Instalează aplicația' }))

    expect(event.prompt).toHaveBeenCalled()
    expect(await screen.findByText('✓ Math LMS e instalată pe acest dispozitiv.')).toBeInTheDocument()
  })

  it('says so when the person closes the dialog without installing', async () => {
    render(<InstallAppSection />)
    offerInstall('dismissed')

    await userEvent.click(await screen.findByRole('button', { name: 'Instalează aplicația' }))

    expect(await screen.findByText(/Nu s-a instalat/)).toBeInTheDocument()
  })

  it('explains the menu where the browser offers no dialog', () => {
    render(<InstallAppSection />)
    expect(screen.getByTestId('install-app')).toHaveTextContent('Instalează aplicația” sau')
  })

  it('the home banner appears only when installing is possible, and "Nu acum" keeps it closed', async () => {
    const { unmount } = render(<InstallBanner />)
    expect(screen.queryByTestId('install-banner')).not.toBeInTheDocument()

    offerInstall()
    await userEvent.click(await screen.findByRole('button', { name: 'Nu acum' }))
    expect(screen.queryByTestId('install-banner')).not.toBeInTheDocument()
    unmount()

    render(<InstallBanner />)
    expect(screen.queryByTestId('install-banner')).not.toBeInTheDocument() // remembered on this device
  })

  it('recognises an iPhone and an iPad that reports itself as a Mac', () => {
    expect(isAppleMobile({ userAgent: 'Mozilla/5.0 (iPhone; CPU iPhone OS 18_0 like Mac OS X)', maxTouchPoints: 5 } as Navigator)).toBe(true)
    expect(isAppleMobile({ userAgent: 'Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7)', maxTouchPoints: 5 } as Navigator)).toBe(true)
    expect(isAppleMobile({ userAgent: 'Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7)', maxTouchPoints: 0 } as Navigator)).toBe(false)
    expect(isAppleMobile({ userAgent: 'Mozilla/5.0 (Linux; Android 15)', maxTouchPoints: 5 } as Navigator)).toBe(false)
  })

  it('registers the service worker only in a production build', () => {
    const register = vi.fn(async () => undefined)
    const nav = { serviceWorker: { register } } as unknown as Navigator

    registerServiceWorker(nav, false)
    window.dispatchEvent(new Event('load'))
    expect(register).not.toHaveBeenCalled()

    registerServiceWorker(nav, true)
    window.dispatchEvent(new Event('load'))
    expect(register).toHaveBeenCalledWith('/sw.js', { scope: '/' })
  })
})
