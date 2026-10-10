import type { ItemInput } from '@/lib/api'

/**
 * Turns pasted text into quiz items, so a whole test is added in one go instead of one dialog per item:
 *
 *   1. Soluția ecuației $2x+5=13$ este: (2p)
 *   a) $x=3$
 *   *b) $x=4$          ← the star marks the right option
 *   R: $2x = 8 \Rightarrow x = 4$
 *   I: Mută termenul liber în dreapta.
 *
 *   2. Rezolvă ecuația $x^2-7x+12=0$. (5p)   ← no options = an open item
 *   R: Δ = 1 (2p); x = 4, x = 3 (3p)
 *
 * "(Np)" at the end of the statement gives the points (1 when missing). Lines without a prefix continue the statement,
 * or the solution once "R:" has started. Positions are 0-based here; the caller shifts them after existing items.
 */
export interface ParsedItems {
  items: ItemInput[]
  errors: string[]
}

const ITEM_START = /^\s*(\d+)[.)]\s+(.+)$/
const OPTION = /^\s*(\*)?\s*([a-h])[).]\s+(.+)$/i
const POINTS = /\(\s*(\d+)\s*(?:p|pct|puncte?)\s*\)\s*$/i
const SOLUTION = /^\s*R:\s*(.*)$/i
const HINT = /^\s*I:\s*(.+)$/i
const MAX_HINTS = 5 // matches QuizItem.MAX_HINTS on the server

interface Draft {
  number: string
  statement: string[]
  options: { text: string; correct: boolean }[]
  solution: string[]
  hints: string[]
  section: 'statement' | 'options' | 'solution' | 'hints'
}

export function parseItems(text: string): ParsedItems {
  const drafts: Draft[] = []
  const errors: string[] = []

  text.split(/\r?\n/).forEach((raw, index) => {
    const line = raw.trim()
    if (line === '') return
    const start = ITEM_START.exec(line)
    if (start) {
      drafts.push({ number: start[1], statement: [start[2].trim()], options: [], solution: [], hints: [], section: 'statement' })
      return
    }
    const current = drafts[drafts.length - 1]
    if (!current) {
      errors.push(`Linia ${index + 1}: textul trebuie să înceapă cu un subiect numerotat („1. …”).`)
      return
    }
    const option = OPTION.exec(line)
    if (option) {
      current.options.push({ text: option[3].trim(), correct: option[1] === '*' })
      current.section = 'options'
      return
    }
    const solution = SOLUTION.exec(line)
    if (solution) {
      current.solution.push(solution[1].trim())
      current.section = 'solution'
      return
    }
    const hint = HINT.exec(line)
    if (hint) {
      current.hints.push(hint[1].trim())
      current.section = 'hints'
      return
    }
    if (current.section === 'statement') {
      current.statement.push(line)
    } else if (current.section === 'solution') {
      current.solution.push(line)
    } else {
      errors.push(`Subiectul ${current.number}, linia ${index + 1}: nu știu ce e „${line}” `
        + '(variantele încep cu a), b)…, rezolvarea cu R:, indiciile cu I:).')
    }
  })

  const items: ItemInput[] = []
  drafts.forEach((draft, i) => {
    const label = `Subiectul ${draft.number}`
    let statement = draft.statement.join('\n').trim()
    const points = POINTS.exec(statement)
    if (points) statement = statement.slice(0, points.index).trim()
    if (statement === '') {
      errors.push(`${label}: lipsește enunțul.`)
      return
    }
    const choice = draft.options.length > 0
    if (choice) {
      if (draft.options.length < 2) errors.push(`${label}: o grilă are nevoie de cel puțin 2 variante.`)
      const correct = draft.options.filter((o) => o.correct).length
      if (correct !== 1) errors.push(`${label}: marchează cu * exact o variantă corectă (acum: ${correct}).`)
    }
    if (draft.hints.length > MAX_HINTS) errors.push(`${label}: cel mult ${MAX_HINTS} indicii.`)
    items.push({
      type: choice ? 'SINGLE_CHOICE' : 'OPEN',
      position: i,
      statement,
      points: points ? Number(points[1]) : 1,
      solution: draft.solution.join('\n').trim() || null,
      options: choice ? draft.options.map((o, p) => ({ position: p, text: o.text, correct: o.correct })) : null,
      hints: draft.hints,
    })
  })
  return { items, errors }
}
