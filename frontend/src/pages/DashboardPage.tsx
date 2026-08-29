import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { useAuth } from '@/context/AuthContext'

export function DashboardPage() {
  const { user, logout } = useAuth()

  if (!user) {
    return null
  }

  return (
    <div className="min-h-screen bg-muted p-4">
      <div className="mx-auto max-w-2xl">
        <Card>
          <CardHeader>
            <CardTitle className="text-2xl">Bine ai venit, {user.fullName}</CardTitle>
            <CardDescription>
              Ești autentificat ca <strong>{user.role}</strong> ({user.email})
            </CardDescription>
          </CardHeader>
          <CardContent className="space-y-4">
            <p className="text-muted-foreground">
              Aici va veni dashboard-ul specific rolului tău. Deocamdată e un placeholder.
            </p>
            <Button variant="outline" onClick={logout}>
              Logout
            </Button>
          </CardContent>
        </Card>
      </div>
    </div>
  )
}
