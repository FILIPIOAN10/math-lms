import { useEffect, useState } from 'react'
import { Card, CardContent } from '@/components/ui/card'
import { HomeSection } from '@/components/home/HomeBits'
import { getClassTutorQuestions, type TutorUnansweredQuestion } from '@/lib/api'
import { formatDate } from '@/lib/format'

/**
 * What the students of a class asked the AI box and the EN lessons did not answer (last 30 days): what the class does
 * not know yet, to take up in class. No names - the point is the question, not who asked it. Nothing at all when the
 * list is empty or cannot be read (the box may be switched off).
 */
export function TutorQuestionsSection({ classId }: { classId: number }) {
  const [questions, setQuestions] = useState<TutorUnansweredQuestion[]>([])

  useEffect(() => {
    let cancelled = false
    getClassTutorQuestions(classId)
      .then((list) => !cancelled && setQuestions(list))
      .catch(() => !cancelled && setQuestions([]))
    return () => {
      cancelled = true
    }
  }, [classId])

  if (questions.length === 0) return null
  return (
    <HomeSection title="Întrebări fără răspuns în lecții">
      <Card>
        <CardContent className="p-0">
          <ul aria-label="Întrebări puse asistentului AI" className="divide-y text-sm">
            {questions.map((q) => (
              <li key={q.id} className="flex flex-wrap items-baseline justify-between gap-x-4 gap-y-0.5 px-3 py-2">
                <span className="break-words">{q.question}</span>
                <span className="text-xs whitespace-nowrap text-muted-foreground">{formatDate(q.askedAt)}</span>
              </li>
            ))}
          </ul>
        </CardContent>
      </Card>
      <p className="text-xs text-muted-foreground">
        Întrebările puse de elevi asistentului AI („Nu înțeleg…”) la care lecțiile EN nu au avut răspuns, din ultimele 30
        de zile, fără numele elevilor. Merită lămurite la clasă sau adăugate în lecții.
      </p>
    </HomeSection>
  )
}
