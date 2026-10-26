// @vitest-environment node
import { readFileSync } from 'node:fs'
import { describe, expect, it } from 'vitest'

/*
 * Native radios and checkboxes (the grile in a test, the quiz builder) take the theme's colour, not the browser's
 * default blue in an otherwise black-and-white app. Read straight from index.css: the test config drops CSS.
 */
const css = readFileSync(new URL('../index.css', import.meta.url), 'utf-8')

describe('form controls', () => {
  it('gives radios and checkboxes the primary colour of the theme', () => {
    expect(css).toMatch(/input\[type=['"]?radio['"]?\][^{]*input\[type=['"]?checkbox['"]?\][^{]*\{[^}]*accent-color:\s*var\(--primary\)/)
  })
})
