import { ParentHome } from '@/components/home/ParentHome'
import { StudentHome } from '@/components/home/StudentHome'
import { TeacherHome } from '@/components/home/TeacherHome'
import { useAuth } from '@/context/AuthContext'

const SUBTITLES = {
  STUDENT: 'Iată ce ai de făcut.',
  ADMIN: 'Iată ce te așteaptă.',
  PARENT: 'Iată cum le merge copiilor tăi.',
} as const

/** The start page: a greeting, then the home of the signed-in role. The navigation lives in the app shell. */
export function DashboardPage() {
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
        {user.role === 'STUDENT' && <StudentHome />}
        {user.role === 'ADMIN' && <TeacherHome />}
        {user.role === 'PARENT' && <ParentHome />}
      </div>
    </div>
  )
}
