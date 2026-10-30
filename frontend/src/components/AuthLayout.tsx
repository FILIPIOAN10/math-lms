import { type ReactNode } from 'react'

const TAGLINE = 'Teste, teme și pregătire pentru Evaluarea Națională'

/** Plain text, not KaTeX: this page stays light, and the formulas are only a picture of what the app is about. */
const FORMULAS = [
  { text: 'a² + b² = c²', place: 'left-[8%] top-[14%] text-3xl -rotate-6' },
  { text: 'Δ = b² − 4ac', place: 'right-[10%] top-[24%] text-2xl rotate-3' },
  { text: 'x = (−b ± √Δ) / 2a', place: 'left-[12%] bottom-[26%] text-2xl rotate-2' },
  { text: 'π ≈ 3,14', place: 'right-[14%] bottom-[34%] text-xl -rotate-3' },
  { text: 'A = πr²', place: 'left-[40%] top-[8%] text-xl rotate-6' },
  { text: '√2 ∉ ℚ', place: 'right-[8%] bottom-[12%] text-3xl -rotate-2' },
]

/**
 * The frame of the pages before signing in: on a wide screen a panel in the brand colour says what the app is, next to
 * the card; on a phone the card comes first and only the sentence stays above it.
 */
export function AuthLayout({ children }: { children: ReactNode }) {
  return (
    <div className="flex min-h-screen bg-muted">
      <aside className="relative hidden flex-1 flex-col justify-center overflow-hidden bg-primary p-12 text-primary-foreground lg:flex">
        <div aria-hidden="true">
          {FORMULAS.map((f) => (
            <span key={f.text} className={`absolute font-medium whitespace-nowrap opacity-20 ${f.place}`}>{f.text}</span>
          ))}
        </div>
        <div className="relative max-w-md space-y-3">
          <p className="text-4xl font-semibold">Math LMS</p>
          <p className="text-lg">{TAGLINE}</p>
        </div>
      </aside>
      <main className="flex flex-1 flex-col items-center justify-center gap-4 p-4">
        <p className="text-center text-sm text-muted-foreground lg:hidden">{TAGLINE}</p>
        {children}
      </main>
    </div>
  )
}
