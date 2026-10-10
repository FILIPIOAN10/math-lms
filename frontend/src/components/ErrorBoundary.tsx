import { Component, type ErrorInfo, type ReactNode } from 'react'
import { Button, buttonVariants } from '@/components/ui/button'

interface State {
  error: Error | null
}

/**
 * Last line of defence: without it a render error anywhere below unmounts the whole app and leaves a white page.
 * Shows what happened and a way out instead. Saved data is on the server, so a reload loses nothing.
 */
export class ErrorBoundary extends Component<{ children: ReactNode }, State> {
  state: State = { error: null }

  static getDerivedStateFromError(error: Error): State {
    return { error }
  }

  componentDidCatch(error: Error, info: ErrorInfo) {
    console.error('Eroare de afișare', error, info.componentStack)
  }

  render() {
    if (!this.state.error) {
      return this.props.children
    }
    return (
      <div role="alert" className="flex min-h-screen items-center justify-center bg-muted p-4">
        <div className="max-w-md space-y-4 rounded-xl border border-border bg-background p-6 text-center">
          <h1 className="text-xl font-semibold">Ceva n-a mers pe această pagină</h1>
          <p className="text-sm text-muted-foreground">
            Ce ai salvat deja nu s-a pierdut. Reîncarcă pagina sau întoarce-te la început.
          </p>
          <div className="flex justify-center gap-2">
            <Button onClick={() => window.location.reload()}>Reîncarcă</Button>
            <a href="/" className={buttonVariants({ variant: 'outline' })}>Acasă</a>
          </div>
        </div>
      </div>
    )
  }
}
