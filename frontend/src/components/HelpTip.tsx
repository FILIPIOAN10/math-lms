import { type ReactNode } from 'react'
import { Popover } from '@base-ui/react/popover'

/**
 * A small "?" that opens an explanation, so a page keeps one sentence on screen and the details for whoever needs them.
 * `label` says what it explains, because a page can have several. Keyboard and focus come from Base UI's popover.
 */
export function HelpTip({ label, children }: { label: string; children: ReactNode }) {
  return (
    <Popover.Root>
      <Popover.Trigger
        aria-label={label}
        className="inline-flex size-5 shrink-0 items-center justify-center rounded-full border border-border text-xs font-medium text-muted-foreground outline-none hover:bg-muted hover:text-foreground focus-visible:ring-3 focus-visible:ring-ring/50"
      >
        ?
      </Popover.Trigger>
      <Popover.Portal>
        <Popover.Positioner sideOffset={6} className="z-50">
          <Popover.Popup className="max-w-xs rounded-lg bg-popover p-3 text-sm font-normal text-popover-foreground shadow-lg ring-1 ring-foreground/10 outline-none">
            {children}
          </Popover.Popup>
        </Popover.Positioner>
      </Popover.Portal>
    </Popover.Root>
  )
}
