import * as React from 'react'
import { cva, type VariantProps } from 'class-variance-authority'
import { cn } from '@/lib/utils'

const badgeVariants = cva(
  'inline-flex shrink-0 items-center gap-1 rounded-md px-2 py-0.5 text-xs font-medium whitespace-nowrap [&_svg]:size-3.5 [&_svg]:shrink-0',
  {
    variants: {
      variant: {
        default: 'bg-primary/10 text-primary',
        secondary: 'bg-muted text-muted-foreground',
        outline: 'ring-1 ring-border text-foreground',
        success: 'bg-success/15 text-success-foreground',
        warning: 'bg-warning/15 text-warning-foreground',
        destructive: 'bg-destructive/15 text-destructive',
      },
    },
    defaultVariants: { variant: 'default' },
  },
)

/** A short status word on a row ("azi", "Termen depășit", "Notat"), in the status colours of index.css. */
function Badge({ className, variant, ...props }: React.ComponentProps<'span'> & VariantProps<typeof badgeVariants>) {
  return <span data-slot="badge" data-variant={variant ?? 'default'} className={cn(badgeVariants({ variant }), className)} {...props} />
}

export { Badge }
