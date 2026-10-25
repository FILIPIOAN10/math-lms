import { Link } from 'react-router-dom'
import { buttonVariants } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'

/**
 * F8: an admin without two-step sign-in yet. The server answers every staff request with 403 until it is set up, so
 * instead of a page full of errors: why, and the one place to fix it. Shown by the staff route gate and on Acasă.
 */
export function TwoFactorRequired() {
  return (
    <div className="flex justify-center p-4">
      <Card className="w-full max-w-md" data-testid="two-factor-required">
        <CardHeader>
          <CardTitle className="text-xl">Activează autentificarea în doi pași</CardTitle>
          <CardDescription>
            Contul de administrator aprobă conturi și vede toate clasele, așa că la conectare cere și un cod din
            aplicația de pe telefon. Paginile de profesor se deschid după ce o activezi (durează un minut).
          </CardDescription>
        </CardHeader>
        <CardContent>
          <Link to="/account" className={buttonVariants()}>Mergi la Contul meu</Link>
        </CardContent>
      </Card>
    </div>
  )
}
