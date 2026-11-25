import { useSyncExternalStore } from 'react'
import { CircleAlert, CircleCheck, Info, TriangleAlert, X } from 'lucide-react'
import { dismissToast, readToasts, subscribeToasts, type ToastTone } from '@/lib/toast'
import { cn } from '@/lib/utils'

const ICONS: Record<ToastTone, typeof Info> = {
  default: Info,
  success: CircleCheck,
  warning: TriangleAlert,
  danger: CircleAlert,
}

const ICON_CLASS: Record<ToastTone, string> = {
  default: 'text-primary',
  success: 'text-success-foreground',
  warning: 'text-warning-foreground',
  danger: 'text-destructive',
}

/**
 * Where the toasts appear: bottom centre, high enough to clear the phone's tab bar and the test's own bottom bar (its
 * question buttons and "Trimite lucrarea" stay pressable). The region is a
 * polite live region, so a screen reader reads each new one once without interrupting. A toast never takes the focus.
 */
export function Toaster() {
  const toasts = useSyncExternalStore(subscribeToasts, readToasts, readToasts)

  return (
    <section
      aria-label="Notificări"
      aria-live="polite"
      className="pointer-events-none fixed inset-x-0 bottom-[calc(7.5rem+env(safe-area-inset-bottom))] z-50 flex flex-col items-center gap-2 px-3"
    >
      {toasts.map((t) => {
        const Icon = ICONS[t.tone]
        return (
          <div
            key={t.id}
            data-testid="toast"
            data-tone={t.tone}
            aria-hidden={t.announce ? undefined : true}
            className="toast-in pointer-events-auto flex w-full max-w-sm items-center gap-3 rounded-xl bg-popover py-2.5 pr-2 pl-3 text-sm text-popover-foreground shadow-lg ring-1 ring-foreground/10"
          >
            <Icon aria-hidden="true" className={cn('size-4 shrink-0', ICON_CLASS[t.tone])} />
            <p className="min-w-0 flex-1">{t.message}</p>
            {t.action && (
              <button
                type="button"
                onClick={() => {
                  t.action?.onClick()
                  dismissToast(t.id)
                }}
                className="h-8 shrink-0 rounded-lg px-2.5 font-semibold text-primary outline-none hover:bg-primary/10 focus-visible:ring-3 focus-visible:ring-ring/50 max-sm:h-11"
              >
                {t.action.label}
              </button>
            )}
            <button
              type="button"
              aria-label="Închide notificarea"
              onClick={() => dismissToast(t.id)}
              className="flex size-8 shrink-0 items-center justify-center rounded-lg text-muted-foreground outline-none hover:bg-muted focus-visible:ring-3 focus-visible:ring-ring/50 max-sm:size-11"
            >
              <X aria-hidden="true" className="size-4" />
            </button>
          </div>
        )
      })}
    </section>
  )
}
