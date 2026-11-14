import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { buttonVariants } from '@/components/ui/button'
import { Card, CardContent } from '@/components/ui/card'
import { HomeSection } from '@/components/home/HomeBits'
import { getWeakSpots, type WeakSpot } from '@/lib/api'
import { weakSpotReason } from '@/lib/weakSpots'

/**
 * „La ce stau prost?” on the student's home: the (at most three) EN chapters where they stand worst, from their own
 * answers only - each with why, in numbers, and what to do: read the lesson, practise the chapter. No AI involved.
 * Nothing at all when the list cannot be read.
 */
export function WeakSpotsSection() {
  const [spots, setSpots] = useState<WeakSpot[] | null>(null)

  useEffect(() => {
    let cancelled = false
    getWeakSpots()
      .then((list) => !cancelled && setSpots(list))
      .catch(() => !cancelled && setSpots(null))
    return () => {
      cancelled = true
    }
  }, [])

  if (spots === null) return null
  return (
    <HomeSection title="La ce stau prost?">
      <Card>
        <CardContent className="space-y-3 py-4 text-sm">
          {spots.length === 0 ? (
            <p className="text-emerald-700 dark:text-emerald-300">Nu ai niciun capitol sub 70%. Ține-o tot așa!</p>
          ) : (
            spots.map((spot) => (
              <div key={spot.chapterId} data-testid="weak-spot" className="space-y-1.5 border-b pb-3 last:border-b-0 last:pb-0">
                <p className="flex flex-wrap items-baseline justify-between gap-2">
                  <span className="font-medium">{spot.code} {spot.title}</span>
                  <span className="tabular-nums text-destructive">{spot.percent}%</span>
                </p>
                <p className="text-muted-foreground">{weakSpotReason(spot)}</p>
                <div className="flex flex-wrap gap-2">
                  {spot.ready && (
                    <Link
                      to={spot.lessonSectionId === null
                        ? `/capitole/${spot.lessonChapterId}`
                        : `/capitole/${spot.lessonChapterId}?sectiune=${spot.lessonSectionId}`}
                      className={buttonVariants({ size: 'sm', variant: 'outline' })}
                    >
                      Deschide lecția
                    </Link>
                  )}
                  <Link to={`/capitole/${spot.chapterId}/exersare`} className={buttonVariants({ size: 'sm' })}>
                    Exersează capitolul
                  </Link>
                </div>
              </div>
            ))
          )}
        </CardContent>
      </Card>
    </HomeSection>
  )
}
