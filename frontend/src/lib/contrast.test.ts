// @vitest-environment node
import { readFileSync } from 'node:fs'
import { describe, expect, it } from 'vitest'

/*
 * The secondary text colour (subtitles, breadcrumbs, dates, legends) must stay readable on every surface it is put on:
 * WCAG AA asks 4.5:1 for normal-size text. Read straight from index.css (not imported: the test config drops CSS), so
 * a token change that drops below it fails here.
 */
const css = readFileSync(new URL('../index.css', import.meta.url), 'utf-8')

function tokens(selector: ':root' | '.dark'): Record<string, string> {
  const start = css.indexOf(`${selector} {`)
  const block = css.slice(start, css.indexOf('}', start))
  return Object.fromEntries([...block.matchAll(/--([\w-]+):\s*([^;]+);/g)].map((m) => [m[1], m[2].trim()]))
}

/** Relative luminance of an `oklch(L C h)` colour: OKLab -> linear sRGB (Björn Ottosson's matrices). */
function luminance(value: string): number {
  const m = value.match(/^oklch\(([\d.]+) ([\d.]+) ([\d.]+)\)$/)
  if (!m) throw new Error(`not a plain oklch colour: ${value}`)
  const [L, C, h] = m.slice(1).map(Number)
  const a = C * Math.cos((h * Math.PI) / 180)
  const b = C * Math.sin((h * Math.PI) / 180)
  const l = (L + 0.3963377774 * a + 0.2158037573 * b) ** 3
  const mm = (L - 0.1055613458 * a - 0.0638541728 * b) ** 3
  const s = (L - 0.0894841775 * a - 1.291485548 * b) ** 3
  const clamp = (x: number) => Math.min(1, Math.max(0, x))
  const r = clamp(4.0767416621 * l - 3.3077115913 * mm + 0.2309699292 * s)
  const g = clamp(-1.2684380046 * l + 2.6097574011 * mm - 0.3413193965 * s)
  const bl = clamp(-0.0041960863 * l - 0.7034186147 * mm + 1.707614701 * s)
  return 0.2126 * r + 0.7152 * g + 0.0722 * bl
}

function contrast(fg: string, bg: string): number {
  const [hi, lo] = [luminance(fg), luminance(bg)].sort((x, y) => y - x)
  return (hi + 0.05) / (lo + 0.05)
}

describe.each([':root', '.dark'] as const)('%s secondary text', (selector) => {
  const t = tokens(selector)

  // The page background of every signed-in page is `bg-muted`; cards and dialogs are `card` / `background`.
  it.each(['muted', 'background', 'card'])('reads at 4.5:1 or more on %s', (surface) => {
    expect(contrast(t['muted-foreground'], t[surface])).toBeGreaterThanOrEqual(4.5)
  })
})

it('measures a known pair right (black on white is 21:1)', () => {
  expect(contrast('oklch(0 0 0)', 'oklch(1 0 0)')).toBeCloseTo(21, 1)
})
