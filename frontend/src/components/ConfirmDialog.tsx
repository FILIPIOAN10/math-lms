import { type ReactNode } from 'react'
import { Button } from '@/components/ui/button'
import { Dialog } from '@/components/ui/dialog'

/**
 * A yes/no question in the app's own dialog instead of `window.confirm`: it is styled, keyboard- and screen-reader
 * friendly, and can offer a third way out (`extraAction`, e.g. "Mergi la subiectul 2"). Escape or the backdrop count as
 * "cancel".
 */
export function ConfirmDialog({
  open,
  title,
  children,
  confirmLabel,
  cancelLabel = 'Anulează',
  destructive = false,
  extraAction,
  finalFocus,
  onConfirm,
  onCancel,
}: {
  open: boolean
  title: string
  children?: ReactNode
  confirmLabel: string
  cancelLabel?: string
  destructive?: boolean
  extraAction?: ReactNode
  finalFocus?: () => HTMLElement | boolean | null
  onConfirm: () => void
  onCancel: () => void
}) {
  return (
    <Dialog open={open} onOpenChange={(o) => !o && onCancel()} title={title} finalFocus={finalFocus}>
      {children && <div className="text-sm text-muted-foreground">{children}</div>}
      <div className="mt-5 flex flex-wrap justify-end gap-2">
        {extraAction}
        <Button variant="outline" onClick={onCancel}>
          {cancelLabel}
        </Button>
        <Button variant={destructive ? 'destructive' : 'default'} onClick={onConfirm}>
          {confirmLabel}
        </Button>
      </div>
    </Dialog>
  )
}
