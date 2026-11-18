import { fireEvent, render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { QuestionImage } from '@/components/QuestionImage'

describe('QuestionImage', () => {
  it('is centred under the question and opens large on the same page, not in a new tab', async () => {
    render(<QuestionImage itemId={9} image="fig.png" />)

    const picture = screen.getByTestId('question-image')
    const opener = picture.closest('button')!
    expect(opener).toHaveClass('mx-auto')
    expect(screen.queryByRole('link')).not.toBeInTheDocument()

    fireEvent.click(opener)
    const dialog = await screen.findByRole('dialog')
    expect(dialog).toHaveTextContent('Figura întrebării')
    expect(screen.getByTestId('photo-viewer-image')).toHaveAttribute('src', '/api/quiz/items/9/image?v=fig.png')
    // one picture: no page arrows; a new tab stays possible for whoever wants it
    expect(screen.queryByRole('button', { name: 'Pagina următoare' })).not.toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Tab nou' })).toHaveAttribute('target', '_blank')
  })

  it('shows nothing for a question without a picture', () => {
    const { container } = render(<QuestionImage itemId={9} image={null} />)
    expect(container).toBeEmptyDOMElement()
  })
})
