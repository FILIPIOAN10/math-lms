import { useEffect, useId, useState, type FormEvent } from 'react'
import { Button } from '@/components/ui/button'
import { Card, CardContent } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { getEnExamDate, setEnExamDate } from '@/lib/api'
import { errorMessage } from '@/lib/errors'

/**
 * „Data EN" on the EN chapters page: the one exam date every student's plan counts down to, set once for the school
 * by an admin. Empty = no plans yet. A date that cannot be read shows nothing (the page works without it).
 */
export function EnExamDateCard() {
  const inputId = useId()
  const [loaded, setLoaded] = useState(false)
  const [value, setValue] = useState('')
  const [saved, setSaved] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [done, setDone] = useState(false)

  useEffect(() => {
    let cancelled = false
    getEnExamDate()
      .then((date) => {
        if (cancelled) return
        setValue(date ?? '')
        setSaved(date)
        setLoaded(true)
      })
      .catch(() => undefined)
    return () => {
      cancelled = true
    }
  }, [])

  async function save(e: FormEvent) {
    e.preventDefault()
    setBusy(true)
    setError(null)
    setDone(false)
    try {
      const date = await setEnExamDate(value === '' ? null : value)
      setSaved(date)
      setValue(date ?? '')
      setDone(true)
    } catch (err) {
      setError(errorMessage(err))
    } finally {
      setBusy(false)
    }
  }

  if (!loaded) return null

  return (
    <Card data-testid="en-exam-date">
      <CardContent className="space-y-2 py-4">
        <form onSubmit={save} className="flex flex-wrap items-end gap-2">
          <div className="space-y-1">
            <Label htmlFor={inputId}>Data EN</Label>
            <Input id={inputId} type="date" value={value} className="w-44" onChange={(e) => {
              setValue(e.target.value)
              setDone(false)
            }} />
          </div>
          <Button type="submit" variant="outline" disabled={busy || value === (saved ?? '')}>Salvează data</Button>
        </form>
        <p className="text-sm text-muted-foreground">
          {saved
            ? 'Planul fiecărui elev numără săptămânile până la această dată; ultimele trei sunt simulări.'
            : 'Fără dată, elevii nu au plan: alege ziua examenului ca să apară planurile.'}
        </p>
        {done && <p role="status" className="text-sm text-success-foreground">Salvat.</p>}
        {error && <p role="alert" className="text-sm text-destructive">{error}</p>}
      </CardContent>
    </Card>
  )
}
