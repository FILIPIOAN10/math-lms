import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { defaultRelease } from '@/lib/assignments'

/**
 * "Programează" (Google Classroom "Schedule", Canvas "Available from"): the homework stays hidden from its students
 * until this moment. `value` is a datetime-local string; '' = not scheduled. `testHidden`: the test is published
 * together with this homework, so no student at all sees the test before then either (V66); otherwise the test is
 * already published and stays where students see it - only the homework waits.
 */
export function ReleaseField({
  id,
  value,
  onChange,
  testHidden = false,
}: {
  id: string
  value: string
  onChange: (value: string) => void
  testHidden?: boolean
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
            {testHidden
              ? 'Până atunci niciun elev nu vede testul, din nicio clasă, iar elevii clasei și părinții lor nu văd nici tema.'
              : 'Până atunci elevii și părinții nu văd tema. Testul, deja publicat, rămâne în lista elevilor care îl văd acum.'}{' '}
            O poți da mai devreme din meniul temei: „Dă-o acum”.
          </p>
        </>
      )}
    </div>
  )
}
