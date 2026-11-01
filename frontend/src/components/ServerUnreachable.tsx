import { useState } from 'react'
import { AuthLayout } from '@/components/AuthLayout'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'

/**
 * Instead of the app, when the first call to the server gets no answer (no internet, a restart during a deploy). The
 * session may be perfectly fine, so this must never look like being signed out: reloading in the middle of a test
 * lands here, and the test comes back at the same address once the server answers.
 */
export function ServerUnreachable({ onRetry }: { onRetry: () => Promise<void> }) {
  const [retrying, setRetrying] = useState(false)

  async function retry() {
    setRetrying(true)
    await onRetry()
    setRetrying(false)
  }

  return (
    <AuthLayout>
      <Card className="w-full max-w-sm" data-testid="server-unreachable">
        <CardHeader className="text-center">
          <CardTitle className="text-xl">Nu ne putem conecta la server</CardTitle>
          <CardDescription>Nu te-am deconectat, iar ce ai salvat rămâne salvat.</CardDescription>
        </CardHeader>
        <CardContent className="flex flex-col gap-3">
          <p className="text-center text-sm text-muted-foreground">
            Verifică internetul. Încercăm din nou singuri la câteva secunde.
          </p>
          <Button className="w-full" onClick={() => void retry()} disabled={retrying}>
            {retrying ? 'Se reîncearcă…' : 'Reîncearcă'}
          </Button>
        </CardContent>
      </Card>
    </AuthLayout>
  )
}
