import { describe, expect, it } from 'vitest'
import { splitTables } from '@/lib/textTables'

describe('splitTables', () => {
  it('leaves text without a table as one text block', () => {
    expect(splitTables('Calculează $\\frac{1}{2} + 3$.')).toEqual([{ kind: 'text', text: 'Calculează $\\frac{1}{2} + 3$.' }])
  })

  it('turns a LaTeX tabular from the AI import into rows and cells, the text around it kept', () => {
    const statement = [
      'În tabelul de mai jos este prezentată repartiția elevilor.',
      '\\begin{tabular}{|l|c|c|c|c|}',
      '\\hline',
      'Tip de activitate & volei & baschet & tenis & handbal \\\\',
      '\\hline',
      'Număr de elevi & 10 & 7 & 4 & 5 \\\\',
      '\\hline',
      '\\end{tabular}',
      'Numărul elevilor care sunt înscriși la baschet este egal cu:',
    ].join('\n')

    expect(splitTables(statement)).toEqual([
      { kind: 'text', text: 'În tabelul de mai jos este prezentată repartiția elevilor.' },
      {
        kind: 'table',
        header: false,
        rows: [
          ['Tip de activitate', 'volei', 'baschet', 'tenis', 'handbal'],
          ['Număr de elevi', '10', '7', '4', '5'],
        ],
      },
      { kind: 'text', text: 'Numărul elevilor care sunt înscriși la baschet este egal cu:' },
    ])
  })

  it('reads a tabular written on one line, with math in the cells and an escaped ampersand', () => {
    expect(splitTables('\\begin{tabular}{cc} $x$ & $x^2$ \\\\ \\hline 2 & 4 \\\\ A \\& B & 1 \\end{tabular}')).toEqual([
      { kind: 'table', header: false, rows: [['$x$', '$x^2$'], ['2', '4'], ['A & B', '1']] },
    ])
  })

  it('reads a Markdown table, its first row the header', () => {
    const statement = 'Tabelul:\n| $x$ | 1 | 2 |\n|---|---|---|\n| $f(x)$ | 3 | 5 |\nCompletează.'
    expect(splitTables(statement)).toEqual([
      { kind: 'text', text: 'Tabelul:' },
      { kind: 'table', header: true, rows: [['$x$', '1', '2'], ['$f(x)$', '3', '5']] },
      { kind: 'text', text: 'Completează.' },
    ])
  })

  it('keeps a lone line with bars (an absolute value) as text', () => {
    expect(splitTables('|x - 2| = 3')).toEqual([{ kind: 'text', text: '|x - 2| = 3' }])
  })

  it('keeps an unfinished tabular as text rather than losing it', () => {
    const text = '\\begin{tabular}{cc} 1 & 2'
    expect(splitTables(text)).toEqual([{ kind: 'text', text }])
  })
})
