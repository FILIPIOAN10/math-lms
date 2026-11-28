import { describe, expect, it } from 'vitest'
import { type StudyPlan, type StudyPlanItem, type StudyPlanWeek } from '@/lib/api'
import {
  chapterGroups,
  goalLine,
  itemLink,
  itemText,
  modeText,
  parseTarget,
  TARGET_CHOICES,
  todayActions,
  weekTitle,
} from '@/lib/studyPlan'

const item = (type: StudyPlanItem['type'], chapterId: number | null = null): StudyPlanItem => ({
  type,
  chapterId,
  code: chapterId === null ? null : 'A' + chapterId,
  title: chapterId === null ? null : 'Capitol ' + chapterId,
})

const week = (number: number, items: StudyPlanItem[], finalWeek = false): StudyPlanWeek => ({
  number,
  start: '2026-11-28',
  end: '2026-12-04',
  finalWeek,
  items,
})

const plan = (over: Partial<StudyPlan> = {}): StudyPlan => ({
  examDate: '2027-03-02',
  daysLeft: 94,
  target: 9,
  grade: 7.4,
  low: 7.1,
  high: 7.7,
  answers: 60,
  answersNeeded: 20,
  mode: 'CATCH_UP',
  behind: false,
  expectedToday: null,
  weeks: [],
  history: [],
  pins: [],
  practiceQuestions: 10,
  ...over,
})

describe('studyPlan', () => {
  it('offers the usual targets from 8 to 10', () => {
    expect(TARGET_CHOICES).toEqual([8, 8.5, 9, 9.5, 10])
  })

  it('reads a target typed the Romanian way, from 1 to 10 with at most two decimals', () => {
    expect(parseTarget('9,25')).toBe(9.25)
    expect(parseTarget(' 8.5 ')).toBe(8.5)
    expect(parseTarget('10')).toBe(10)
    expect(parseTarget('10,5')).toBeNull()
    expect(parseTarget('0,9')).toBeNull()
    expect(parseTarget('9,255')).toBeNull()
    expect(parseTarget('nouă')).toBeNull()
    expect(parseTarget('')).toBeNull()
  })

  it('says where the student is, where they aim and how long is left in one line', () => {
    expect(goalLine(7.4, 9, 94)).toBe('Acum ~7,40 → ținta 9,00 · mai ai 94 de zile')
    expect(goalLine(7.4, 9, 1)).toBe('Acum ~7,40 → ținta 9,00 · mai ai 1 zi')
    expect(goalLine(7.4, 9, 0)).toBe('Acum ~7,40 → ținta 9,00 · EN e azi')
    expect(goalLine(null, 9.5, 12)).toBe('Ținta 9,50 · mai ai 12 zile')
    expect(goalLine(7.4, 9, null)).toBe('Acum ~7,40 → ținta 9,00')
    expect(goalLine(7.4, null, 94)).toBeNull()
  })

  it('names each step and opens the page that does it', () => {
    expect(itemText(item('LESSON', 3), 10)).toBe('Citește lecția')
    expect(itemLink(item('LESSON', 3))).toBe('/capitole/3')
    expect(itemText(item('PRACTICE', 3), 10)).toBe('Exersează 10 întrebări')
    expect(itemLink(item('PRACTICE', 3))).toBe('/capitole/3/exersare')
    expect(itemText(item('MINI_TEST', 3), 10)).toBe('Mini-test: o rundă, cel puțin 4 din 5 corecte')
    expect(itemLink(item('MINI_TEST', 3))).toBe('/capitole/3/exersare')
    expect(itemText(item('MOCK'), 10)).toBe('Rezolvă un test complet de EN')
    expect(itemLink(item('MOCK'))).toBe('/quizzes')
    expect(itemText(item('REVIEW'), 10)).toBe('Recapitularea zilnică')
    expect(itemLink(item('REVIEW'))).toBe('/review')
  })

  it('calls the first week the next seven days', () => {
    expect(weekTitle(week(1, []))).toBe('Următoarele 7 zile')
    expect(weekTitle(week(3, []))).toBe('Săptămâna 3')
  })

  it('groups a week by chapter, the steps without a chapter last', () => {
    const groups = chapterGroups(week(1, [item('LESSON', 3), item('PRACTICE', 3), item('PRACTICE', 5), item('REVIEW')]))

    expect(groups.map((g) => g.chapterId)).toEqual([3, 5, null])
    expect(groups[0].items.map((i) => i.type)).toEqual(['LESSON', 'PRACTICE'])
    expect(groups[0].code).toBe('A3')
  })

  it("puts the daily review and this week's first chapter in today's block", () => {
    const p = plan({ weeks: [week(1, [item('LESSON', 3), item('PRACTICE', 3), item('MINI_TEST', 3), item('PRACTICE', 5)])] })

    expect(todayActions(p)).toEqual([
      { label: 'Recapitularea zilnică', to: '/review' },
      { label: 'Exersează A3 · Capitol 3', to: '/capitole/3/exersare' },
    ])
  })

  it("puts a mock paper in today's block in the last weeks", () => {
    const p = plan({ mode: 'FINAL', weeks: [week(1, [item('MOCK'), item('REVIEW')], true)] })

    expect(todayActions(p)).toEqual([
      { label: 'Recapitularea zilnică', to: '/review' },
      { label: 'Rezolvă un test complet de EN', to: '/quizzes' },
    ])
  })

  it('explains each kind of plan, to the student and about the student', () => {
    expect(modeText(plan({ mode: 'NO_TARGET' }), true)).toContain('Alege nota')
    expect(modeText(plan({ mode: 'NO_TARGET' }), false)).toContain('nu și-a ales')
    expect(modeText(plan({ mode: 'NO_EXAM_DATE' }), true)).toContain('data EN')
    expect(modeText(plan({ mode: 'NEEDS_ANSWERS', answers: 12 }), true)).toContain('8 răspunsuri')
    expect(modeText(plan({ mode: 'FINAL' }), true)).toContain('simulări')
    expect(modeText(plan({ mode: 'MAINTENANCE' }), true)).toContain('Ești la țintă')
    expect(modeText(plan({ mode: 'CATCH_UP' }), true)).toContain('cel mai repede')
    expect(modeText(plan({ mode: 'EXAM_OVER' }), true)).toContain('a trecut')
  })
})
