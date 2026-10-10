import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { Button, buttonVariants } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { useAuth } from '@/context/AuthContext'
import { listAttemptsForGrading, listPendingUsers } from '@/lib/api'

/** A small count on a dashboard button ("Corectură 3"); nothing when there is nothing to do. */
function Count({ n, label }: { n: number | null; label: string }) {
  if (!n) return null
  return (
    <span aria-label={label} className="ml-1.5 rounded-full bg-destructive px-1.5 text-xs font-semibold text-white">
      {n}
    </span>
  )
}

export function DashboardPage() {
  const { user, logout } = useAuth()
  const [pending, setPending] = useState<number | null>(null)
  const [toGrade, setToGrade] = useState<number | null>(null)
  const isAdmin = user?.role === 'ADMIN'

  useEffect(() => {
    if (!isAdmin) return
    let cancelled = false
    // Only hints on the buttons: if a call fails the dashboard still works, just without the number.
    listPendingUsers().then((list) => !cancelled && setPending(list.length)).catch(() => undefined)
    listAttemptsForGrading('SUBMITTED').then((list) => !cancelled && setToGrade(list.length)).catch(() => undefined)
    return () => {
      cancelled = true
    }
  }, [isAdmin])

  if (!user) {
    return null
  }

  return (
    <div className="min-h-screen bg-muted p-4">
      <div className="mx-auto max-w-2xl">
        <Card>
          <CardHeader>
            <CardTitle data-testid="welcome" className="text-2xl">Bine ai venit, {user.fullName}</CardTitle>
            <CardDescription>
              Ești autentificat ca <strong>{user.role}</strong> ({user.email})
            </CardDescription>
          </CardHeader>
          <CardContent className="space-y-4">
            <p className="text-muted-foreground">
              Aici va veni dashboard-ul specific rolului tău. Deocamdată e un placeholder.
            </p>
            <div className="flex flex-wrap gap-2">
              <Link to="/content" className={buttonVariants({ variant: 'default' })}>
                Conținut
              </Link>
              {user.role === 'STUDENT' && (
                <>
                  <Link to="/quizzes" className={buttonVariants({ variant: 'default' })}>
                    Testele mele
                  </Link>
                  <Link to="/progress" data-testid="my-progress" className={buttonVariants({ variant: 'default' })}>
                    Progresul meu
                  </Link>
                </>
              )}
              {user.role === 'PARENT' && (
                <Link to="/parent" data-testid="my-children" className={buttonVariants({ variant: 'default' })}>
                  Copiii mei
                </Link>
              )}
              {user.role === 'ADMIN' && (
                <>
                  <Link to="/admin/content" className={buttonVariants({ variant: 'secondary' })}>
                    Gestionează conținut
                  </Link>
                  <Link to="/admin/quizzes" className={buttonVariants({ variant: 'secondary' })}>
                    Quiz-uri
                  </Link>
                  <Link to="/admin/assignments" data-testid="admin-assignments" className={buttonVariants({ variant: 'secondary' })}>
                    Teme
                  </Link>
                  <Link to="/admin/grading" className={buttonVariants({ variant: 'secondary' })}>
                    Corectură<Count n={toGrade} label={`${toGrade} lucrări de corectat`} />
                  </Link>
                  <Link to="/admin/pending" className={buttonVariants({ variant: 'secondary' })}>
                    Conturi în așteptare<Count n={pending} label={`${pending} conturi de aprobat`} />
                  </Link>
                  <Link to="/admin/links" className={buttonVariants({ variant: 'secondary' })}>
                    Invitații și părinți
                  </Link>
                </>
              )}
              <Button variant="outline" onClick={logout} data-testid="logout">
                Logout
              </Button>
            </div>
          </CardContent>
        </Card>
      </div>
    </div>
  )
}
