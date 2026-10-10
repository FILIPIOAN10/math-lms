import { Fragment } from 'react'
import { Menu } from '@base-ui/react/menu'
import { useNavigate } from 'react-router-dom'
import { buttonVariants } from '@/components/ui/button'
import { cn } from '@/lib/utils'

export interface RowMenuItem {
  label: string
  /** Goes to an address... */
  to?: string
  /** ...or runs an action. */
  onClick?: () => void
  /** Deleting and the like: shown in red, after a separator. Put it last. */
  destructive?: boolean
  testId?: string
}

/**
 * The secondary actions of one list row behind a single "⋯" button, so the row shows only its main action. Keyboard
 * and screen-reader support (arrow keys, Escape, focus return) come from Base UI's menu. `label` names the row, e.g.
 * "Mai multe acțiuni pentru Simulare EN", because many rows have the same button.
 */
export function RowMenu({ label, items }: { label: string; items: RowMenuItem[] }) {
  const navigate = useNavigate()

  return (
    <Menu.Root>
      <Menu.Trigger aria-label={label} className={buttonVariants({ variant: 'outline', size: 'icon-sm' })}>
        <span aria-hidden="true">⋯</span>
      </Menu.Trigger>
      <Menu.Portal>
        <Menu.Positioner align="end" sideOffset={4} className="z-50">
          <Menu.Popup className="min-w-44 rounded-lg bg-popover p-1 text-sm text-popover-foreground shadow-lg ring-1 ring-foreground/10 outline-none">
            {items.map((item, i) => (
              <Fragment key={item.label}>
                {item.destructive && i > 0 && <Menu.Separator className="my-1 h-px bg-border" />}
                <Menu.Item
                  data-testid={item.testId}
                  onClick={() => (item.to ? navigate(item.to) : item.onClick?.())}
                  className={cn(
                    'flex cursor-default items-center rounded-md px-2.5 py-1.5 outline-none select-none data-[highlighted]:bg-muted',
                    item.destructive && 'text-destructive',
                  )}
                >
                  {item.label}
                </Menu.Item>
              </Fragment>
            ))}
          </Menu.Popup>
        </Menu.Positioner>
      </Menu.Portal>
    </Menu.Root>
  )
}
