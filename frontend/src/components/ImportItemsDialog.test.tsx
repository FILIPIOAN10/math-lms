import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { ImportItemsDialog } from '@/components/ImportItemsDialog'

const TEXT = '1. Cât face 2+2? (2p)\na) 3\n*b) 4\n\n2. Explică de ce. (3p)\nR: pentru că da'

function renderDialog(onImport = vi.fn().mockResolvedValue(null), onClose = vi.fn()) {
  render(<ImportItemsDialog open onClose={onClose} onImport={onImport} />)
  return { onImport, onClose }
}

async function paste(text: string) {
  await userEvent.click(screen.getByLabelText('Textul testului'))
  await userEvent.paste(text)
}

describe('ImportItemsDialog', () => {
  it('previews what will be added and adds it all in one click', async () => {
    const { onImport, onClose } = renderDialog()
    await paste(TEXT)

    expect(screen.getByTestId('import-preview')).toHaveTextContent('2 subiecte: 1 grilă, 1 deschis · Total: 5 p')
    await userEvent.click(screen.getByRole('button', { name: 'Adaugă 2 subiecte' }))

    await waitFor(() => expect(onClose).toHaveBeenCalled())
    expect(onImport.mock.calls[0][0].map((i: { type: string }) => i.type)).toEqual(['SINGLE_CHOICE', 'OPEN'])
  })

  it('will not import while the text has mistakes', async () => {
    const { onImport } = renderDialog()
    await paste('1. Fără variantă corectă\na) unu\nb) doi')

    expect(screen.getByRole('alert')).toHaveTextContent('marchează cu * exact o variantă corectă')
    expect(screen.getByRole('button', { name: 'Adaugă 1 subiect' })).toBeDisabled()
    expect(onImport).not.toHaveBeenCalled()
  })

  it('stays open and says how far it got when the server refuses an item', async () => {
    const { onClose } = renderDialog(vi.fn().mockResolvedValue('S-au adăugat 1 din 2. Quiz-ul a fost deja dat de elevi'))
    await paste(TEXT)

    await userEvent.click(screen.getByRole('button', { name: 'Adaugă 2 subiecte' }))

    expect(await screen.findByText(/S-au adăugat 1 din 2/)).toBeInTheDocument()
    expect(onClose).not.toHaveBeenCalled()
  })
})
