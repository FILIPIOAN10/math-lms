import { useCallback, useRef, useState, type ReactNode } from 'react'
import { ConfirmDialog } from '@/components/ConfirmDialog'

export interface ConfirmOptions {
  title: string
  body?: ReactNode
  confirmLabel: string
  destructive?: boolean
}

/**
 * `window.confirm` as the app's own dialog, still usable in one line: `if (!(await confirm({...}))) return`.
 * Render the returned `dialog` once anywhere in the page.
 */
export function useConfirm(): { confirm: (options: ConfirmOptions) => Promise<boolean>; dialog: ReactNode } {
  const [options, setOptions] = useState<ConfirmOptions | null>(null)
  const resolver = useRef<((answer: boolean) => void) | null>(null)

  const confirm = useCallback((next: ConfirmOptions) => {
    resolver.current?.(false) // a question still open is answered "no" rather than left hanging
    setOptions(next)
    return new Promise<boolean>((resolve) => {
      resolver.current = resolve
    })
  }, [])

  function answer(yes: boolean) {
    resolver.current?.(yes)
    resolver.current = null
    setOptions(null)
  }

  const dialog = (
    <ConfirmDialog
      open={options !== null}
      title={options?.title ?? ''}
      confirmLabel={options?.confirmLabel ?? ''}
      destructive={options?.destructive}
      onConfirm={() => answer(true)}
      onCancel={() => answer(false)}
    >
      {options?.body}
    </ConfirmDialog>
  )

  return { confirm, dialog }
}
