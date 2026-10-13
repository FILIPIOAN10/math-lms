import { Link } from 'react-router-dom'
import { cn } from '@/lib/utils'

const TABS = [
  { key: 'quizzes', to: '/admin/quizzes', label: 'Teste' },
  { key: 'assignments', to: '/admin/assignments', label: 'Teme' },
] as const

/** The two halves of the teacher's "Teste" area (T6): the quizzes, and the homework made from them. */
export function TestsTabs({ current }: { current: (typeof TABS)[number]['key'] }) {
  return (
    <nav aria-label="Teste" className="flex gap-1 border-b">
      {TABS.map((tab) => (
        <Link
          key={tab.key}
          to={tab.to}
          aria-current={tab.key === current ? 'page' : undefined}
          data-testid={tab.key === 'assignments' ? 'admin-assignments' : undefined}
          className={cn(
            '-mb-px border-b-2 px-3 py-2 text-sm font-medium outline-none focus-visible:ring-3 focus-visible:ring-ring/50',
            tab.key === current
              ? 'border-foreground text-foreground'
              : 'border-transparent text-muted-foreground hover:text-foreground',
          )}
        >
          {tab.label}
        </Link>
      ))}
    </nav>
  )
}
