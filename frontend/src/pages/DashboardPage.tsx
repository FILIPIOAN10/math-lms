import { ParentHome } from '@/components/home/ParentHome'
import { StudentHome } from '@/components/home/StudentHome'
import { TeacherHome } from '@/components/home/TeacherHome'
import { useAuth } from '@/context/AuthContext'
import { InstallBanner } from '@/components/InstallApp'
import { usePageTitle } from '@/lib/usePageTitle'

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
    <div className="p-4">
      <div className="mx-auto max-w-3xl space-y-6">
        <div>
          <h1 data-testid="welcome" className="text-2xl font-semibold">Bine ai venit, {user.fullName}</h1>
          <p className="text-sm text-muted-foreground">{SUBTITLES[user.role]}</p>
        </div>
        <InstallBanner />
        {user.role === 'STUDENT' && <StudentHome />}
        {(user.role === 'ADMIN' || user.role === 'TEACHER') && <TeacherHome canApprove={user.role === 'ADMIN'} />}
        {user.role === 'PARENT' && <ParentHome />}
      </div>
    </div>
  )
}
