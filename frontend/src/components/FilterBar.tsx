import { type ReactNode } from 'react'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'

const SELECT_CLASS =
  'h-9 rounded-lg border border-border bg-background px-2 text-sm outline-none focus-visible:border-ring focus-visible:ring-3 focus-visible:ring-ring/50'

/** A dropdown filter: the first option (value "") is "everything". `label` is what a screen reader calls it. */
export function FilterSelect({ label, value, onChange, options }: {
  label: string
  value: string
  onChange: (value: string) => void
  options: { value: string; label: string }[]
}) {
  return (
    <select aria-label={label} className={SELECT_CLASS} value={value} onChange={(e) => onChange(e.target.value)}>
      {options.map((o) => <option key={o.value} value={o.value}>{o.label}</option>)}
    </select>
  )
}

/**
 * The search box and filters above a list. While something is filtered it says how many rows are left, and one button
 * clears everything. `children` are the FilterSelects.
 */
export function FilterBar({ label, search, onSearch, active, onClear, shown, total, children }: {
  /** What is searched, e.g. "Caută elev". */
  label: string
  search: string
  onSearch: (value: string) => void
  active: boolean
  onClear: () => void
  shown: number
  total: number
  children?: ReactNode
}) {
  return (
    <div role="search" data-testid="filter-bar" className="flex flex-wrap items-center gap-2">
      <Input
        type="search"
        value={search}
        onChange={(e) => onSearch(e.target.value)}
        placeholder={label}
        aria-label={label}
        className="w-full sm:w-64"
      />
      {children}
      {active && <Button type="button" variant="ghost" size="sm" onClick={onClear}>Șterge filtrele</Button>}
      <span role="status" className="text-sm text-muted-foreground sm:ml-auto">
        {active ? `${shown} din ${total}` : ''}
      </span>
    </div>
  )
}

/** What a filtered list says when nothing is left: not "nothing yet", but "nothing like this". */
export function NoMatches({ onClear }: { onClear: () => void }) {
  return (
    <p data-testid="no-matches" className="text-sm text-muted-foreground">
      Niciun rezultat pentru filtrele alese.{' '}
      <button type="button" className="font-medium text-foreground underline underline-offset-2" onClick={onClear}>
        Șterge filtrele
      </button>
    </p>
  )
}
