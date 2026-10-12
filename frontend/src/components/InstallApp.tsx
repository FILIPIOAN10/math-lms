import { useState } from 'react'
import { Button } from '@/components/ui/button'
import { Card, CardContent } from '@/components/ui/card'
import { HomeSection } from '@/components/home/HomeBits'
import { promptInstall, useInstallState } from '@/lib/pwa'

const DISMISSED_KEY = 'mathlms.install-banner-dismissed'

function readDismissed(): boolean {
  try {
    return localStorage.getItem(DISMISSED_KEY) === '1'
  } catch {
    return false // private mode / blocked storage: just show it
  }
}

/**
 * E11 on "Contul meu": how to put the app on this device - the browser's own install dialog where there is one
 * (Chrome, Edge, Android), the Share-menu steps on iPhone/iPad, a plain hint elsewhere, and a calm "already installed".
 */
export function InstallAppSection() {
  const state = useInstallState()
  const [declined, setDeclined] = useState(false)

  return (
    <HomeSection title="Aplicația pe telefon">
      <Card>
        <CardContent className="space-y-2 py-4 text-sm" data-testid="install-app">
          {state === 'installed' && <p>✓ Math LMS e instalată pe acest dispozitiv.</p>}
          {state === 'available' && (
            <>
              <p>Pune Math LMS pe ecranul principal: se deschide ca o aplicație, fără bara browserului.</p>
              <Button size="sm" onClick={() => void promptInstall().then((ok) => setDeclined(!ok))}>
                Instalează aplicația
              </Button>
            </>
          )}
          {state === 'apple' && (
            <p>
              Pe iPhone sau iPad: în Safari apasă <strong>Partajează</strong> (pătratul cu săgeata în sus), apoi{' '}
              <strong>Adaugă pe ecranul principal</strong>.
            </p>
          )}
          {state === 'unsupported' && (
            <p className="text-muted-foreground">
              Deschide Math LMS în Chrome (Android) sau Safari (iPhone) și alege din meniu „Instalează aplicația” sau
              „Adaugă pe ecranul principal”.
            </p>
          )}
          {declined && state !== 'installed' && (
            <p className="text-muted-foreground">Nu s-a instalat. O poți face oricând din meniul browserului.</p>
          )}
        </CardContent>
      </Card>
    </HomeSection>
  )
}

/** E11 on the home page: a one-line offer, only where the browser can install it, until installed or closed. */
export function InstallBanner() {
  const state = useInstallState()
  const [dismissed, setDismissed] = useState(readDismissed)
  if (state !== 'available' || dismissed) return null

  function dismiss() {
    setDismissed(true)
    try {
      localStorage.setItem(DISMISSED_KEY, '1')
    } catch {
      // not remembered: it simply comes back next time
    }
  }

  return (
    <div
      role="region"
      aria-label="Instalează aplicația"
      data-testid="install-banner"
      className="flex flex-wrap items-center justify-between gap-2 rounded-lg border bg-background px-3 py-2 text-sm"
    >
      <span>Pune Math LMS pe ecranul telefonului, ca pe o aplicație.</span>
      <span className="flex items-center gap-2">
        <Button size="sm" onClick={() => void promptInstall()}>Instalează</Button>
        <Button size="sm" variant="ghost" onClick={dismiss} aria-label="Nu acum">Nu acum</Button>
      </span>
    </div>
  )
}
