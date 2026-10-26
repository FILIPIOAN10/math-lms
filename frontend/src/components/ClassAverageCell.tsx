import { roCount } from '@/lib/format'
import { MASTERY_TONES, MIN_STUDENTS_FOR_AVERAGE, masteryLevel, type classAverage } from '@/lib/mastery'

/**
 * One cell of the "Media clasei" row. An average of fewer than three students says how many it is from and keeps the
 * neutral colour, so one child's 100% does not paint a chapter green for the whole class.
 */
export function ClassAverageCell({ average, testId }: { average: ReturnType<typeof classAverage>; testId?: string }) {
  const few = average !== null && average.students < MIN_STUDENTS_FOR_AVERAGE
  const tone = MASTERY_TONES[few ? 'none' : masteryLevel(average?.percent ?? null)]
  return (
    <td data-testid={testId} className={`border-l px-3 py-2 text-center font-semibold tabular-nums ${tone}`}>
      {average === null ? '—' : `${average.percent}%`}
      {few && <span className="block text-xs font-normal">din {roCount(average.students, 'elev', 'elevi')}</span>}
    </td>
  )
}
