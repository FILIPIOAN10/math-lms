import { ParentHome } from '@/components/home/ParentHome'
import { StudentHome } from '@/components/home/StudentHome'
import { TeacherHome } from '@/components/home/TeacherHome'
import { TwoFactorRequired } from '@/components/TwoFactorRequired'
import { useAuth } from '@/context/AuthContext'
import { InstallBanner } from '@/components/InstallApp'
import { usePageTitle } from '@/lib/usePageTitle'
import { cn } from '@/lib/utils'
import { greeting } from '@/lib/home'

const SUBTITLES = {
  STUDENT: 'Iată ce ai de făcut.',
  ADMIN: 'Iată ce te așteaptă.',
  TEACHER: 'Iată ce te așteaptă.',
  PARENT: 'Iată cum le merge copiilor tăi.',
} as const

/** The start page: a greeting, then the home of the signed-in role. The navigation lives in the app shell. */
export function DashboardPage() {
  usePageTitle('Acasă')
  const { user } = useAuth()

  if (!user?.role) {
    return null
  }

  return (
    <div className="py-4">
      {/* A parent has one card per child: a narrow column reads better. A student's pages all share one width (4xl), so
          the content does not jump sideways between tabs; staff get a wide page with a side column (see TeacherHome). */}
      <div
        className={cn(
          'mx-auto w-full space-y-6 px-4',
          user.role === 'STUDENT' ? 'max-w-4xl' : user.role === 'PARENT' ? 'max-w-3xl' : 'max-w-6xl lg:px-8',
        )}
      >
        <div>
          <p className="text-sm text-muted-foreground first-letter:uppercase">
            {new Date().toLocaleDateString('ro-RO', { weekday: 'long', day: 'numeric', month: 'long' })}
          </p>
          {/* The whole name: Romanian names are often written surname first, so a "first name" would be a guess. */}
          <h1 data-testid="welcome" className="text-2xl font-semibold tracking-tight lg:text-3xl">
            {greeting()}, {user.fullName}
          </h1>
          <p className="text-sm text-muted-foreground">{SUBTITLES[user.role]}</p>
        </div>
        <InstallBanner />
        {user.role === 'STUDENT' && <StudentHome />}
        {/* F8: Acasă is not behind the staff gate, so it shows the same card instead of the server's 403s. */}
        {(user.role === 'ADMIN' || user.role === 'TEACHER') &&
          (user.twoFactorSetupRequired ? <TwoFactorRequired /> : <TeacherHome canApprove={user.role === 'ADMIN'} />)}
        {user.role === 'PARENT' && <ParentHome />}
      </div>
    </div>
  )
}
