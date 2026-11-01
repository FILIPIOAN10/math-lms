import { useContext } from 'react'
import { Button } from '@/components/ui/button'
import { PageRetryContext } from '@/lib/pageRetry'

/**
 * What a page shows when it could not load: the reason, and a way to try again without reloading the app. For a load
 * only: a refused action (a test that cannot be deleted) stays a plain message, since trying it again changes nothing
 * and the button that caused it is still on screen.
 */
export function ErrorState({ message }: { message: string }) {
  const retry = useContext(PageRetryContext)

  return (
    <div
      role="alert"
      data-testid="error-state"
      className="flex flex-wrap items-center justify-between gap-2 rounded-lg border border-destructive/30 bg-destructive/5 px-3 py-2 text-sm"
    >
      <p className="min-w-0 text-destructive">{message}</p>
      <Button size="sm" variant="outline" onClick={retry}>Reîncearcă</Button>
    </div>
  )
}
