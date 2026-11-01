import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { Link, MemoryRouter, useNavigate } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { ScrollToTop } from '@/components/ScrollToTop'

function Links() {
  const navigate = useNavigate()
  return (
    <>
      <Link to="/teste">teste</Link>
      <Link to="/acasa?filtru=1">același drum</Link>
      <button onClick={() => navigate(-1)}>înapoi</button>
    </>
  )
}

function renderAt(path: string) {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <ScrollToTop />
      <Links />
    </MemoryRouter>,
  )
}

describe('ScrollToTop', () => {
  const scrollTo = vi.fn()

  beforeEach(() => {
    scrollTo.mockReset()
    vi.stubGlobal('scrollTo', scrollTo)
  })
  afterEach(() => vi.unstubAllGlobals())

  it('leaves the first page alone', () => {
    renderAt('/acasa')
    expect(scrollTo).not.toHaveBeenCalled()
  })

  it('takes a new page to its top', async () => {
    renderAt('/acasa')
    await userEvent.click(screen.getByRole('link', { name: 'teste' }))
    expect(scrollTo).toHaveBeenCalledWith(0, 0)
  })

  it('leaves the place alone on Back, where the browser restores it', async () => {
    renderAt('/acasa')
    await userEvent.click(screen.getByRole('link', { name: 'teste' }))
    scrollTo.mockClear()

    await userEvent.click(screen.getByRole('button', { name: 'înapoi' }))

    expect(scrollTo).not.toHaveBeenCalled()
  })

  it('does not count a change in the query as a new page', async () => {
    renderAt('/acasa')
    await userEvent.click(screen.getByRole('link', { name: 'același drum' }))
    expect(scrollTo).not.toHaveBeenCalled()
  })
})
