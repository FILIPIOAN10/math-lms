import { render, screen, within } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { MarkdownText, inlineRuns, parseBlocks } from '@/components/MarkdownText'

describe('inlineRuns', () => {
  it('splits bold from plain text', () => {
    expect(inlineRuns('Un **termen** al sumei')).toEqual([
      { text: 'Un ', bold: false },
      { text: 'termen', bold: true },
      { text: ' al sumei', bold: false },
    ])
  })

  it('never reads the asterisks or underscores of a formula as emphasis', () => {
    const runs = inlineRuns('Mulțimea $\\mathbb{N}^*$ și $a_1 \\cdot a_2$ sunt **nenule**.')

    expect(runs.map((r) => r.text).join('')).toBe('Mulțimea $\\mathbb{N}^*$ și $a_1 \\cdot a_2$ sunt nenule.')
    expect(runs.filter((r) => r.bold).map((r) => r.text)).toEqual(['nenule'])
  })

  it('drops italic markers and keeps the words', () => {
    expect(inlineRuns('*Probabil* că *da*').map((r) => r.text).join('')).toBe('Probabil că da')
  })
})

describe('parseBlocks', () => {
  it('reads paragraphs, a heading and a table', () => {
    const blocks = parseBlocks('## Titlu\n\nPrima linie\nA doua linie\n\n| a | b |\n|---|---:|\n| 1 | 2 |\n| 3 | 4 |\n\nFinal')

    expect(blocks).toEqual([
      { kind: 'heading', text: 'Titlu' },
      { kind: 'paragraph', text: 'Prima linie\nA doua linie' },
      { kind: 'table', header: ['a', 'b'], rows: [['1', '2'], ['3', '4']] },
      { kind: 'paragraph', text: 'Final' },
    ])
  })
})

describe('figures', () => {
  it('reads a short line over bullets as a heading over a list, the way the chapter texts are written', () => {
    expect(parseBlocks('Radicali\n• $\\sqrt{4}=2$\n• Doar radicali asemenea se adună.')).toEqual([
      { kind: 'heading', text: 'Radicali' },
      { kind: 'list', items: ['$\\sqrt{4}=2$', 'Doar radicali asemenea se adună.'] },
    ])
  })

  it('keeps a sentence before bullets a sentence, and reads "- " as a bullet too', () => {
    expect(parseBlocks('Reținem:\n- unu\n- doi\n\nAltceva.')).toEqual([
      { kind: 'paragraph', text: 'Reținem:' },
      { kind: 'list', items: ['unu', 'doi'] },
      { kind: 'paragraph', text: 'Altceva.' },
    ])
  })

  it('reads a figure line as the part of a photo, apart from the text around it', () => {
    expect(parseBlocks('Text.\n\n![Axa](poza:42?x=0.078&y=0.396&w=0.844&h=0.061)\n\nDupă.')).toEqual([
      { kind: 'paragraph', text: 'Text.' },
      { kind: 'figure', alt: 'Axa', photoId: 42, x: 0.078, y: 0.396, w: 0.844, h: 0.061 },
      { kind: 'paragraph', text: 'După.' },
    ])
  })

  it('draws it from the photo address it is given, and says what it is for a screen reader', () => {
    render(<MarkdownText photoUrl={(id) => `/api/en-chapters/9/photos/${id}`}>{'![Axa numerelor](poza:42?x=0.1&y=0.2&w=0.5&h=0.1)'}</MarkdownText>)

    expect(screen.getByRole('img', { name: 'Axa numerelor' }).tagName).toBe('CANVAS')
  })

  it('shows only the description when it is not told where photos are', () => {
    render(<MarkdownText>{'![Axa numerelor](poza:42?x=0.1&y=0.2&w=0.5&h=0.1)'}</MarkdownText>)

    expect(screen.getByText(/figură: Axa numerelor/)).toBeInTheDocument()
    expect(screen.queryByRole('img')).not.toBeInTheDocument()
  })
})

describe('MarkdownText', () => {
  it('shows bold as bold, a table as a table and a formula typeset', () => {
    const { container } = render(
      <MarkdownText>{'**Reținem!** suma e $a+b=s$.\n\n| Zeci | Sute |\n|---|---|\n| 38 780 | 38 700 |'}</MarkdownText>,
    )

    expect(screen.getByText('Reținem!').tagName).toBe('SPAN')
    expect(screen.getByText('Reținem!').closest('strong')).not.toBeNull()
    const table = screen.getByRole('table')
    expect(within(table).getAllByRole('columnheader').map((c) => c.textContent)).toEqual(['Zeci', 'Sute'])
    expect(within(table).getAllByRole('cell').map((c) => c.textContent)).toEqual(['38 780', '38 700'])
    expect(container.querySelector('.katex')).not.toBeNull()
  })

  it('sets a heading as a real heading at the level it is given, and bullets as a list', () => {
    const { container } = render(<MarkdownText headingLevel={4}>{'Radicali\n• unu\n• doi $x^2$'}</MarkdownText>)

    expect(screen.getByRole('heading', { level: 4, name: 'Radicali' })).toBeInTheDocument()
    expect(within(screen.getByRole('list')).getAllByRole('listitem')).toHaveLength(2)
    expect(container).not.toHaveTextContent('•') // the list draws its own bullets, with the wrapped lines indented
    expect(container.querySelector('li .katex')).not.toBeNull()
  })

  it('never turns the text into HTML', () => {
    const { container } = render(<MarkdownText>{'<img src=x onerror=alert(1)> **ok**'}</MarkdownText>)

    expect(container.querySelector('img')).toBeNull()
    expect(container.textContent).toContain('<img src=x onerror=alert(1)>')
  })
})
