import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { Tabs, TabsList, TabsTrigger } from '@/components/ui/tabs'

describe('TabsTrigger', () => {
  it('is styled by the attribute Base UI really sets on the selected tab', () => {
    render(
      <Tabs defaultValue="google">
        <TabsList>
          <TabsTrigger value="google">Google</TabsTrigger>
          <TabsTrigger value="password">Email și parolă</TabsTrigger>
        </TabsList>
      </Tabs>,
    )

    const selected = screen.getByRole('tab', { selected: true })
    const other = screen.getByRole('tab', { selected: false })
    // Every `data-[x]:` variant on the trigger must name an attribute the selected tab carries and the other does not;
    // a renamed attribute (data-selected -> data-active) left both tabs looking the same.
    const variants = [...selected.className.matchAll(/data-\[([a-z-]+)\]:bg-/g)].map((m) => m[1])
    expect(variants.length).toBeGreaterThan(0)
    for (const name of variants) {
      expect(selected).toHaveAttribute(`data-${name}`)
      expect(other).not.toHaveAttribute(`data-${name}`)
    }
  })
})
