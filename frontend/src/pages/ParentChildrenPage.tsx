import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { buttonVariants } from '@/components/ui/button'
import { Card, CardContent } from '@/components/ui/card'
import { listMyChildren, type ChildDto } from '@/lib/api'
import { errorMessage } from '@/lib/errors'

/** The parent's home: the students an admin linked to this account. */
export function ParentChildrenPage() {
  const [children, setChildren] = useState<ChildDto[] | null>(null)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    let cancelled = false
    listMyChildren()
      .then((c) => !cancelled && setChildren(c))
      .catch((e) => !cancelled && setError(errorMessage(e)))
    return () => {
      cancelled = true
    }
  }, [])

  return (
    <div className="p-4">
      <div className="mx-auto max-w-3xl space-y-4">
        <div className="flex items-center justify-between">
          <h1 className="text-2xl font-semibold">Copiii mei</h1>
        </div>

        {error && <p className="text-sm text-destructive">{error}</p>}
        {!children && !error && <p className="text-muted-foreground">Se încarcă...</p>}

        {children && children.length === 0 && (
          <p className="text-sm text-muted-foreground" data-testid="no-children">
            Niciun elev nu este legat încă de contul tău. Cere profesorului să te lege de copilul tău.
          </p>
        )}

        {children?.map((child) => (
          <Card key={child.id}>
            <CardContent className="flex items-center justify-between gap-3 py-3">
              <div className="min-w-0">
                <p className="truncate font-medium">{child.fullName}</p>
                <p className="truncate text-sm text-muted-foreground">{child.email}</p>
              </div>
              <Link
                to={`/parent/children/${child.id}`}
                data-testid="child-open"
                className={buttonVariants({ size: 'sm' })}
              >
                Vezi testele
              </Link>
            </CardContent>
          </Card>
        ))}
      </div>
    </div>
  )
}
