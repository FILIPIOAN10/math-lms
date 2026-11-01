import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { ErrorState } from '@/components/ErrorState'
import { PageRetryContext } from '@/lib/pageRetry'

describe('ErrorState', () => {
  it('announces the reason and tries again from its button', async () => {
    const retry = vi.fn()
    render(
      <PageRetryContext.Provider value={retry}>
        <ErrorState message="Nu am putut încărca elevii." />
      </PageRetryContext.Provider>,
    )

    expect(screen.getByRole('alert')).toHaveTextContent('Nu am putut încărca elevii.')
    await userEvent.click(screen.getByRole('button', { name: 'Reîncearcă' }))

    expect(retry).toHaveBeenCalledOnce()
  })
})
