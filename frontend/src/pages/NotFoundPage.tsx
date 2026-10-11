import { Link } from 'react-router-dom'
import { buttonVariants } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { usePageTitle } from '@/lib/usePageTitle'

/** Any address the app does not know: a plain explanation and a way home, instead of an empty screen. */
export function NotFoundPage() {
  usePageTitle('Pagina nu există')
  return (
    <div className="flex min-h-[60vh] items-center justify-center p-4">
      <Card className="w-full max-w-sm" data-testid="not-found">
        <CardHeader className="text-center">
          <CardTitle className="text-2xl">Pagina nu există</CardTitle>
          <CardDescription>Adresa e greșită sau pagina a fost mutată.</CardDescription>
        </CardHeader>
        <CardContent className="text-center">
          <Link to="/" className={buttonVariants()}>Înapoi acasă</Link>
        </CardContent>
      </Card>
    </div>
  )
}
