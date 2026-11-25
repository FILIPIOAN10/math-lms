import { useEffect, useId, useMemo, useRef, useState, type KeyboardEvent } from 'react'
import { Dialog as DialogPrimitive } from '@base-ui/react/dialog'
import { CornerDownLeft, Search } from 'lucide-react'
import { useNavigate } from 'react-router-dom'
import { listActiveUsers, listEnChapters, listQuizzes, type Role } from '@/lib/api'
import {
  chapterEntries,
  pageEntries,
  quizEntries,
  searchCommands,
  studentEntries,
  type CommandEntry,
} from '@/lib/commandSearch'
import { isStaff } from '@/lib/roles'
import { cn } from '@/lib/utils'

/** The lists behind the search, asked for once, the first time the palette opens. A list that fails is left out. */
async function loadEntries(role: Role): Promise<CommandEntry[]> {
  const staff = isStaff(role)
  const [quizzes, students, chapters] = await Promise.allSettled([
    staff ? listQuizzes() : Promise.resolve([]),
    staff ? listActiveUsers('STUDENT') : Promise.resolve([]),
    role === 'PARENT' ? Promise.resolve([]) : listEnChapters(),
  ])
  return [
    ...(quizzes.status === 'fulfilled' ? quizEntries(quizzes.value) : []),
    ...(students.status === 'fulfilled' ? studentEntries(students.value) : []),
    ...(chapters.status === 'fulfilled' ? chapterEntries(chapters.value, role) : []),
  ]
}

/**
 * Ctrl+K (⌘K): type where to go - a page, a test, a student, a chapter - and Enter. The input is a combobox over a
 * listbox: ↑ ↓ move, Enter opens, Escape closes; the mouse works too. Opened from the shell's search button as well.
 */
export function CommandPalette({ role, open, onClose }: { role: Role; open: boolean; onClose: () => void }) {
  const navigate = useNavigate()
  const [query, setQuery] = useState('')
  const [active, setActive] = useState(0)
  const [loaded, setLoaded] = useState<CommandEntry[] | null>(null)
  const asked = useRef(false)
  const listId = useId()
  const optionId = (i: number) => `${listId}-o${i}`

  useEffect(() => {
    if (!open || asked.current) return
    asked.current = true
    void loadEntries(role).then(setLoaded)
  }, [open, role])

  const sections = useMemo(
    () => searchCommands([...pageEntries(role), ...(loaded ?? [])], query),
    [role, loaded, query],
  )
  const flat = sections.flatMap((s) => s.entries)
  const indexOf = new Map(flat.map((entry, i) => [entry.id, i]))
  const current = Math.min(active, Math.max(0, flat.length - 1))

  function close() {
    setQuery('')
    setActive(0)
    onClose()
  }

  function go(entry: CommandEntry | undefined) {
    if (!entry) return
    close()
    navigate(entry.to)
  }

  function onKeyDown(e: KeyboardEvent<HTMLInputElement>) {
    if (flat.length === 0) return
    const step = e.key === 'ArrowDown' ? 1 : e.key === 'ArrowUp' ? -1 : 0
    if (step !== 0) {
      e.preventDefault()
      const next = (current + step + flat.length) % flat.length
      setActive(next)
      document.getElementById(optionId(next))?.scrollIntoView?.({ block: 'nearest' })
    } else if (e.key === 'Enter') {
      e.preventDefault()
      go(flat[current])
    }
  }

  return (
    <DialogPrimitive.Root open={open} onOpenChange={(o) => !o && close()}>
      <DialogPrimitive.Portal>
        <DialogPrimitive.Backdrop className="dialog-backdrop fixed inset-0 z-40 bg-black/40" />
        <DialogPrimitive.Popup
          data-testid="command-palette"
          className="dialog-popup fixed top-[10vh] left-1/2 z-50 flex max-h-[70dvh] w-[calc(100%-2rem)] max-w-lg -translate-x-1/2 flex-col overflow-hidden rounded-xl bg-popover text-popover-foreground shadow-lg ring-1 ring-foreground/10 outline-none"
        >
          <DialogPrimitive.Title className="sr-only">Caută în aplicație</DialogPrimitive.Title>
          <div className="flex items-center gap-2 border-b px-3">
            <Search aria-hidden="true" className="size-4 shrink-0 text-muted-foreground" />
            <input
              autoFocus
              role="combobox"
              aria-expanded="true"
              aria-controls={listId}
              aria-autocomplete="list"
              aria-activedescendant={flat.length > 0 ? optionId(current) : undefined}
              aria-label="Caută o pagină, un test, un elev"
              placeholder={isStaff(role) ? 'Caută o pagină, un test, un elev…' : 'Caută o pagină sau un capitol…'}
              value={query}
              onChange={(e) => {
                setQuery(e.target.value)
                setActive(0)
              }}
              onKeyDown={onKeyDown}
              className="h-12 min-w-0 flex-1 bg-transparent text-base outline-none placeholder:text-muted-foreground"
            />
            <kbd className="hidden rounded border px-1.5 text-xs text-muted-foreground sm:inline">Esc</kbd>
          </div>
          <div id={listId} role="listbox" aria-label="Rezultate" className="min-h-0 flex-1 overflow-y-auto p-1.5">
            {flat.length === 0 && (
              <p className="px-3 py-6 text-center text-sm text-muted-foreground">
                {loaded === null && query.trim() ? 'Se caută…' : 'Nimic găsit.'}
              </p>
            )}
            {sections.map((section) => (
              <div key={section.group} role="group" aria-labelledby={`${listId}-${section.group}`} className="py-1">
                <p id={`${listId}-${section.group}`} className="px-2.5 pb-1 text-xs font-medium text-muted-foreground">
                  {section.group}
                </p>
                {section.entries.map((entry) => {
                  const i = indexOf.get(entry.id) ?? 0
                  const selected = i === current
                  return (
                    <div
                      key={entry.id}
                      id={optionId(i)}
                      role="option"
                      aria-selected={selected}
                      onMouseMove={() => setActive(i)}
                      onClick={() => go(entry)}
                      className={cn(
                        'flex min-h-10 cursor-pointer items-center gap-2 rounded-lg px-2.5 py-1.5 text-sm max-sm:min-h-11',
                        selected && 'bg-primary/10 text-primary',
                      )}
                    >
                      <span className="min-w-0 flex-1 truncate font-medium">{entry.label}</span>
                      {entry.hint && <span className="shrink-0 truncate text-xs text-muted-foreground">{entry.hint}</span>}
                      {selected && <CornerDownLeft aria-hidden="true" className="size-3.5 shrink-0" />}
                    </div>
                  )
                })}
              </div>
            ))}
          </div>
        </DialogPrimitive.Popup>
      </DialogPrimitive.Portal>
    </DialogPrimitive.Root>
  )
}
