import { type StudyPlan, type StudyPlanItem, type StudyPlanWeek } from '@/lib/api'
import { formatGrade } from '@/lib/enPrediction'
import { roCount } from '@/lib/format'

/** „Planul până la EN": how the plan is written - the target, the steps, their links, the weeks. */

/** The targets offered as buttons; any other grade from 1 to 10 can be typed. */
export const TARGET_CHOICES = [8, 8.5, 9, 9.5, 10]

/** "9,25" or "9.25" → 9.25; null for anything off the 1 - 10 scale or with more than two decimals. */
export function parseTarget(text: string): number | null {
  const clean = text.trim().replace(',', '.')
  if (!/^\d{1,2}(\.\d{1,2})?$/.test(clean)) return null
  const value = Number(clean)
  return value >= 1 && value <= 10 ? value : null
}

/** "Acum ~7,40 → ținta 9,00 · mai ai 94 de zile"; null without a target. */
export function goalLine(grade: number | null, target: number | null, daysLeft: number | null): string | null {
  if (target === null) return null
  const where = grade === null ? 'Ținta ' + formatGrade(target) : `Acum ~${formatGrade(grade)} → ținta ${formatGrade(target)}`
  if (daysLeft === null || daysLeft < 0) return where
  return where + ' · ' + (daysLeft === 0 ? 'EN e azi' : 'mai ai ' + roCount(daysLeft, 'zi', 'zile'))
}

/** What a step asks, in a few words. */
export function itemText(item: StudyPlanItem, practiceQuestions: number): string {
  switch (item.type) {
    case 'LESSON':
      return 'Citește lecția'
    case 'PRACTICE':
      return `Exersează ${practiceQuestions} întrebări`
    case 'MINI_TEST':
      return 'Mini-test: o rundă, cel puțin 4 din 5 corecte'
    case 'MOCK':
      return 'Rezolvă un test complet de EN'
    case 'REVIEW':
      return 'Recapitularea zilnică'
  }
}

/** The page that does the step: the chapter's lesson or practice, the tests, the daily review. */
export function itemLink(item: StudyPlanItem): string {
  switch (item.type) {
    case 'LESSON':
      return '/capitole/' + item.chapterId
    case 'PRACTICE':
    case 'MINI_TEST':
      return '/capitole/' + item.chapterId + '/exersare'
    case 'MOCK':
      return '/quizzes'
    case 'REVIEW':
      return '/review'
  }
}

/** Week 1 starts today, so it is "the next seven days" rather than a calendar week. */
export function weekTitle(week: StudyPlanWeek): string {
  return week.number === 1 ? 'Următoarele 7 zile' : 'Săptămâna ' + week.number
}

const DAY_MONTH = new Intl.DateTimeFormat('ro-RO', { day: 'numeric', month: 'short', timeZone: 'UTC' })

/** "28 nov. – 4 dec." (the dates are days, read as UTC so no zone moves them). */
export function weekDates(week: StudyPlanWeek): string {
  const day = (iso: string) => DAY_MONTH.format(new Date(iso + 'T00:00:00Z'))
  return week.start === week.end ? day(week.start) : day(week.start) + ' – ' + day(week.end)
}

/** A week's steps by chapter, in order; the steps without a chapter (mock paper, review) last. */
export interface ChapterGroup {
  chapterId: number | null
  code: string | null
  title: string | null
  items: StudyPlanItem[]
}

export function chapterGroups(week: StudyPlanWeek): ChapterGroup[] {
  const groups: ChapterGroup[] = []
  const loose: StudyPlanItem[] = []
  for (const item of week.items) {
    if (item.chapterId === null) {
      loose.push(item)
      continue
    }
    const group = groups.find((g) => g.chapterId === item.chapterId)
    if (group) group.items.push(item)
    else groups.push({ chapterId: item.chapterId, code: item.code, title: item.title, items: [item] })
  }
  if (loose.length > 0) groups.push({ chapterId: null, code: null, title: null, items: loose })
  return groups
}

/** „Azi": the daily review, then this week's first chapter (or a mock paper in the last weeks). */
export function todayActions(plan: StudyPlan): { label: string; to: string }[] {
  const actions = [{ label: 'Recapitularea zilnică', to: '/review' }]
  const first = plan.weeks[0]
  if (!first) return actions
  const chapter = first.items.find((i) => i.chapterId !== null)
  if (chapter) {
    actions.push({ label: `Exersează ${chapter.code} · ${chapter.title}`, to: '/capitole/' + chapter.chapterId + '/exersare' })
  } else if (first.items.some((i) => i.type === 'MOCK')) {
    actions.push({ label: 'Rezolvă un test complet de EN', to: '/quizzes' })
  }
  return actions
}

/** What kind of plan this is, said to the student ({@code own}) or about them (parent, teacher). */
export function modeText(plan: StudyPlan, own: boolean): string {
  switch (plan.mode) {
    case 'NO_EXAM_DATE':
      return own
        ? 'Profesorul nu a stabilit încă data EN. Planul apare după ce o stabilește.'
        : 'Data EN nu e stabilită încă, deci nu există plan.'
    case 'EXAM_OVER':
      return 'Data EN a trecut: nu mai e nimic de planificat.'
    case 'NO_TARGET':
      return own ? 'Alege nota pe care o vrei la EN și îți fac planul pe săptămâni.' : 'Elevul nu și-a ales încă o țintă.'
    case 'NEEDS_ANSWERS': {
      const left = roCount(Math.max(0, plan.answersNeeded - plan.answers), 'răspuns', 'răspunsuri')
      return own
        ? `Mai rezolvă ${left} și apar nota estimată și planul pe săptămâni. Până atunci, începe cu acestea:`
        : `Mai are ${left} până la nota estimată; până atunci, planul are doar săptămâna aceasta.`
    }
    case 'FINAL':
      return 'Ultimele trei săptămâni: simulări complete și recapitularea zilnică.'
    case 'MAINTENANCE':
      return own
        ? 'Ești la țintă. Ca să rămâi acolo: câte un capitol pe săptămână, cel mai slab.'
        : 'Elevul e la țintă: câte un capitol pe săptămână, cel mai slab, ca să rămână acolo.'
    case 'CATCH_UP':
      return own
        ? 'Capitolele care te duc cel mai repede la țintă, în ordine. Ultimele trei săptămâni sunt simulări.'
        : 'Capitolele care îl duc cel mai repede la țintă, în ordine. Ultimele trei săptămâni sunt simulări.'
  }
}
