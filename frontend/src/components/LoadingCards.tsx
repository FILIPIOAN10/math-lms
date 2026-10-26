/**
 * What a page shows while its data loads: grey cards roughly the shape of the cards that will replace them, so the page
 * does not jump from one line of text to a screen of content. A screen reader hears "Se încarcă..." once (role
 * status); the pulse stops for anyone who asked their device for less motion.
 */
export function LoadingCards({ cards = 3 }: { cards?: number }) {
  return (
    <div role="status" aria-busy="true" className="space-y-3">
      <span className="sr-only">Se încarcă...</span>
      {Array.from({ length: cards }, (_, i) => (
        <div key={i} data-testid="skeleton-card" aria-hidden="true" className="space-y-3 rounded-xl bg-card p-4 ring-1 ring-foreground/10">
          <div className="h-4 w-2/5 animate-pulse rounded bg-muted motion-reduce:animate-none" />
          <div className="h-3 w-4/5 animate-pulse rounded bg-muted motion-reduce:animate-none" />
          <div className="h-3 w-3/5 animate-pulse rounded bg-muted motion-reduce:animate-none" />
        </div>
      ))}
    </div>
  )
}
