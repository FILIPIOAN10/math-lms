// @vitest-environment node
import { readFileSync } from 'node:fs'
import { describe, expect, it } from 'vitest'
import { THEME_KEY } from '@/lib/theme'

/* public/theme-boot.js puts a dark page in dark mode before the app loads (no white flash). It is a plain file, not
 * inline (the CSP allows no inline script), so it cannot import THEME_KEY: this keeps the two in step. */
describe('theme boot script', () => {
  it('reads the same key and is loaded in <head>, before the app', () => {
    const boot = readFileSync(new URL('../../public/theme-boot.js', import.meta.url), 'utf-8')
    const html = readFileSync(new URL('../../index.html', import.meta.url), 'utf-8')
    expect(boot).toContain(`'${THEME_KEY}'`)
    expect(html).toMatch(/<head>[\s\S]*<script src="\/theme-boot.js"><\/script>[\s\S]*<\/head>/)
  })
})
