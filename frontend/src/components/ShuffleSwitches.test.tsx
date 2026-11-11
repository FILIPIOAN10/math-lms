import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { ShuffleSwitches } from '@/components/ShuffleSwitches'

describe('ShuffleSwitches', () => {
  it('shows the test\'s switches and saves one change at a time', async () => {
    const getShuffle = vi.fn().mockResolvedValue({ items: false, options: true })
    const setShuffle = vi.fn().mockResolvedValue({ items: true, options: true })
    render(<ShuffleSwitches quizId={4} getShuffle={getShuffle} setShuffle={setShuffle} />)

    const items = await screen.findByRole('checkbox', { name: /Amestecă întrebările/ })
    expect(items).not.toBeChecked()
    expect(screen.getByRole('checkbox', { name: 'Amestecă variantele de la grile' })).toBeChecked()

    await userEvent.click(items)

    expect(setShuffle).toHaveBeenCalledWith(4, { items: true, options: true })
    await waitFor(() => expect(items).toBeChecked())
  })

  it('shows nothing when the switches cannot be read', async () => {
    const getShuffle = vi.fn().mockRejectedValue(new Error('down'))
    render(<ShuffleSwitches quizId={4} getShuffle={getShuffle} />)

    await waitFor(() => expect(getShuffle).toHaveBeenCalled())
    expect(screen.queryByTestId('shuffle-switches')).not.toBeInTheDocument()
  })
})
