import { useCallback, useEffect, useMemo, useState } from 'react'
import { Button } from '@/components/ui/button'
import { Card, CardContent } from '@/components/ui/card'
import { EnChapterSelect } from '@/components/EnChapterSelect'
import { PageHeader } from '@/components/PageHeader'
import { HomeSection } from '@/components/home/HomeBits'
import {
  getEnChapterSlots,
  listEnChapters,
  saveEnChapterSlots,
  type EnChapterSlot,
  type EnChapterSummary,
} from '@/lib/api'
import { errorMessage } from '@/lib/errors'
import { LoadingCards } from '@/components/LoadingCards'

/** The EN paper: three subjects of six questions; Subiectul III's problems may link a) and b) apart. */
const SUBJECTS: { roman: string; title: string; parts: boolean }[] = [
  { roman: 'I', title: 'Subiectul I (grile)', parts: false },
  { roman: 'II', title: 'Subiectul al II-lea (grile)', parts: false },
  { roman: 'III', title: 'Subiectul al III-lea (probleme)', parts: true },
]
const QUESTIONS = [1, 2, 3, 4, 5, 6]

/** place ("I.1", "III.5.b") → chapter id as a select value ('' = none). */
type Links = Record<string, string>

function toLinks(slots: EnChapterSlot[]): Links {
  return Object.fromEntries(slots.map((s) => [s.slot, String(s.chapterId)]))
}

/**
 * The automatic links: which EN chapter each place on the paper points at when the teacher picked none for the
 * question. Seeded from the official papers (docs/EN-capitole.md); changing one applies at once to every test.
 */
export function AdminEnChapterSlotsPage() {
  const [chapters, setChapters] = useState<EnChapterSummary[]>([])
  const [saved, setSaved] = useState<Links | null>(null)
  const [links, setLinks] = useState<Links>({})
  const [error, setError] = useState<string | null>(null)
  const [notice, setNotice] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  const load = useCallback(() => {
    return getEnChapterSlots()
      .then((slots) => {
        const next = toLinks(slots)
        setSaved(next)
        setLinks(next)
      })
      .catch((e) => setError(errorMessage(e)))
  }, [])

  useEffect(() => {
    listEnChapters().then(setChapters).catch((e) => setError(errorMessage(e)))
    void load()
  }, [load])

  const changed = useMemo(() => {
    if (!saved) return 0
    const places = new Set([...Object.keys(saved), ...Object.keys(links)])
    return [...places].filter((p) => (saved[p] ?? '') !== (links[p] ?? '')).length
  }, [saved, links])

  const set = (place: string, value: string) => {
    setNotice(null)
    setLinks((current) => ({ ...current, [place]: value }))
  }

  async function save() {
    setBusy(true)
    setError(null)
    try {
      await saveEnChapterSlots(Object.entries(links)
        .filter(([, value]) => value !== '')
        .map(([slot, value]) => ({ slot, chapterId: Number(value) })))
      await load()
      setNotice('Salvat. Întrebările fără capitol ales de tine urmează de acum aceste legături.')
    } catch (e) {
      setError(errorMessage(e))
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="p-4">
      <div className="mx-auto max-w-4xl space-y-4">
        <PageHeader
          title="Legături automate"
          description="Capitolul la care e trimis elevul, după locul întrebării în testul EN, când nu ai ales tu altul la întrebare. Se aplică imediat tuturor testelor."
          breadcrumbs={[{ label: 'Capitole EN', to: '/admin/en-chapters' }, { label: 'Legături automate' }]}
          actions={
            <>
              <Button variant="outline" onClick={() => { setLinks(saved ?? {}); setNotice(null) }} disabled={busy || changed === 0}>
                Anulează modificările
              </Button>
              <Button onClick={() => void save()} disabled={busy || changed === 0}>
                {busy ? 'Se salvează…' : changed === 0 ? 'Salvează' : `Salvează (${changed})`}
              </Button>
            </>
          }
        />
        {error && <p role="alert" className="text-sm text-destructive">{error}</p>}
        {notice && <p role="status" className="text-sm text-emerald-700 dark:text-emerald-300">{notice}</p>}
        {!saved && !error && <LoadingCards />}

        {saved && SUBJECTS.map((subject) => (
          <HomeSection key={subject.roman} title={subject.title}>
            <Card>
              <CardContent className="divide-y py-1">
                {QUESTIONS.map((n) => {
                  const place = `${subject.roman}.${n}`
                  return (
                    <div key={place} className="space-y-2 py-2.5" data-testid={`slot-${place}`}>
                      <div className="grid items-center gap-2 sm:grid-cols-[9rem_1fr]">
                        <span className="text-sm font-medium">{subject.parts ? `Problema ${n}` : `Exercițiul ${n}`}</span>
                        <EnChapterSelect id={`slot-${place}`} label={`Capitolul pentru ${place}`} value={links[place] ?? ''}
                                         onChange={(v) => set(place, v)} chapters={chapters} emptyLabel="— fără legătură —" />
                      </div>
                      {subject.parts && (
                        <div className="grid gap-2 sm:ml-36 sm:grid-cols-2">
                          {['a', 'b'].map((part) => {
                            const partPlace = `${place}.${part}`
                            return (
                              <label key={part} className="flex items-center gap-2 text-sm">
                                <span className="w-6 shrink-0 font-semibold">{part})</span>
                                <EnChapterSelect id={`slot-${partPlace}`} label={`Capitolul pentru ${place} ${part})`}
                                                 value={links[partPlace] ?? ''} onChange={(v) => set(partPlace, v)}
                                                 chapters={chapters} emptyLabel="Ca problema" />
                              </label>
                            )
                          })}
                        </div>
                      )}
                    </div>
                  )
                })}
              </CardContent>
            </Card>
          </HomeSection>
        ))}
      </div>
    </div>
  )
}
