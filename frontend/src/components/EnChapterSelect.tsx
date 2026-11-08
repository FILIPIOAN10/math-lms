import { type EnChapterSummary } from '@/lib/api'
import { EN_AREAS } from '@/lib/enChapters'
import { selectClass } from '@/lib/formClasses'

/**
 * Where a question (or a requirement) sends a student who loses points on it: "Automat" follows what its statement is
 * about, else its place on the EN paper (and says where that is, when known), or one chapter picked from the list. The value is the chapter's id, or
 * its code with {@code by="code"} (the AI import review, which knows proposals by code); '' = automatic.
 */
export function EnChapterSelect({ id, label, value, onChange, chapters, autoId, by = 'id', disabled, emptyLabel }: {
  id: string
  label?: string
  value: string
  onChange: (value: string) => void
  chapters: EnChapterSummary[]
  autoId?: number | null
  by?: 'id' | 'code'
  disabled?: boolean
  /** What the empty choice says, when it is not "automatic" (e.g. "— fără legătură —"). */
  emptyLabel?: string
}) {
  const auto = autoId == null ? null : chapters.find((c) => c.id === autoId)
  return (
    <select id={id} aria-label={label} className={selectClass} value={value} disabled={disabled}
            onChange={(e) => onChange(e.target.value)}>
      <option value="">{emptyLabel ?? (auto ? `Automat: ${auto.code} ${auto.title}` : 'Automat (după enunț, apoi după locul din test)')}</option>
      {EN_AREAS.map((area) => (
        <optgroup key={area.value} label={area.label}>
          {chapters.filter((c) => c.area === area.value).map((c) => (
            <option key={c.id} value={by === 'id' ? String(c.id) : c.code}>{c.code} {c.title}</option>
          ))}
        </optgroup>
      ))}
    </select>
  )
}
