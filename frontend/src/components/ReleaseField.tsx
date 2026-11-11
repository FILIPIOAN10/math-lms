import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { defaultRelease } from '@/lib/assignments'

/**
 * "Programează" (Google Classroom "Schedule", Canvas "Available from"): the homework, and a test given only by it,
 * stays hidden from the students until this moment. `value` is a datetime-local string; '' = not scheduled.
 */
export function ReleaseField({
  id,
  value,
  onChange,
}: {
  id: string
  value: string
  onChange: (value: string) => void
}) {
  const scheduled = value !== ''
  return (
    <div className="flex flex-col gap-2">
      <label className="flex min-h-11 items-center gap-2 text-sm">
        <input
          type="checkbox"
          className="size-4"
          checked={scheduled}
          onChange={(e) => onChange(e.target.checked ? defaultRelease(Date.now()) : '')}
        />
        Programează: apare elevilor mai târziu
      </label>
      {scheduled && (
        <>
          <Label htmlFor={id}>Apare elevilor la</Label>
          <Input id={id} type="datetime-local" value={value} onChange={(e) => onChange(e.target.value)} />
          <p className="text-xs text-muted-foreground">
            Până atunci elevii și părinții nu văd nici tema, nici testul (dacă nu l-au primit deja altfel). Îl poți da
            mai devreme din meniul temei: „Dă-o acum”.
          </p>
        </>
      )}
    </div>
  )
}
