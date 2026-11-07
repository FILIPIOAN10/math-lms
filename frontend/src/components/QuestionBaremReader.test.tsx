import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { useState } from 'react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { QuestionBaremReader } from '@/components/QuestionBaremReader'
import type { QuestionBarem, QuestionBaremRequest } from '@/lib/api'

const api = vi.hoisted(() => ({
  createQuestionBaremRead: vi.fn(),
  getQuestionBaremRead: vi.fn(),
}))
vi.mock('@/lib/api', async (original) => ({ ...(await original<typeof import('@/lib/api')>()), ...api }))
const image = vi.hoisted(() => ({ cropImage: vi.fn() }))
vi.mock('@/lib/image', () => image)
const pdfPages = vi.hoisted(() => ({ images: [] as File[] }))
vi.mock('@/lib/pdfPages', () => ({
  pdfPageImages: async function* () {
    for (const [i, file] of pdfPages.images.entries()) yield { file, page: i + 1, total: pdfPages.images.length }
  },
}))

const QUESTION: QuestionBaremRequest = {
  statement: 'Într-o pungă sunt bomboane.',
  points: 5,
  parts: [{ statement: 'Verificați 55.', points: 2 }, { statement: 'Cel mai mic număr.', points: 3 }],
}

const READ: QuestionBarem = {
  barem: null,
  parts: [{ label: 'a', barem: '$55 : 4 = 13$ rest 3 ... 1p' }, { label: 'b', barem: '$x = 27$ ... 3p' }],
  warnings: ['Baremul dă 3p, testul are 2p.'],
}

const photo = (name = 'barem.jpg') => new File(['x'], name, { type: 'image/jpeg' })

/** The reader as the review uses it: the barem pages live above it, so the next question finds them. */
function Harness({ initial = [] as File[], onRead }: { initial?: File[]; onRead: (b: QuestionBarem) => void }) {
  const [photos, setPhotos] = useState<File[]>(initial)
  return (
    <>
      <QuestionBaremReader question={QUESTION} photos={photos} onPhotos={setPhotos} onRead={onRead} pollMs={10} />
      <p data-testid="kept">{photos.map((p) => p.name).join(',')}</p>
    </>
  )
}

describe('QuestionBaremReader', () => {
  beforeEach(() => {
    Object.values(api).forEach((fn) => fn.mockReset())
    image.cropImage.mockReset()
    image.cropImage.mockImplementation(async (file: File) => new File(['crop'], `crop-${file.name}`, { type: 'image/jpeg' }))
    URL.createObjectURL = vi.fn(() => 'blob:barem')
    URL.revokeObjectURL = vi.fn()
  })

  it('reads the barem of the framed problem and hands back each requirement', async () => {
    const user = userEvent.setup()
    const onRead = vi.fn()
    api.createQuestionBaremRead.mockResolvedValue({ id: 7, status: 'QUEUED', error: null, barem: null })
    api.getQuestionBaremRead
      .mockResolvedValueOnce({ id: 7, status: 'RUNNING', error: null, barem: null })
      .mockResolvedValueOnce({ id: 7, status: 'DONE', error: null, barem: READ })
    render(<Harness onRead={onRead} />)

    await user.click(screen.getByRole('button', { name: 'Barem din poză (AI)' }))
    await user.upload(screen.getByLabelText('Poza cu baremul'), photo())
    expect(screen.getByTestId('kept')).toHaveTextContent('barem.jpg')
    await user.click(await screen.findByRole('button', { name: 'Citește baremul (AI)' }))

    await waitFor(() => expect(onRead).toHaveBeenCalledWith(READ))
    expect(image.cropImage).toHaveBeenCalledWith(expect.objectContaining({ name: 'barem.jpg' }), { x: 0, y: 0, w: 1, h: 1 })
    const [sent, question] = api.createQuestionBaremRead.mock.calls[0]
    expect((sent as File).name).toBe('crop-barem.jpg')
    expect(question).toEqual(QUESTION)
    expect(await screen.findByText(/Baremul dă 3p, testul are 2p\./)).toBeInTheDocument()
    expect(screen.getByText(/Am pus baremul/)).toBeInTheDocument()
  })

  it('goes straight to framing when the barem photo was already chosen for another question', async () => {
    const user = userEvent.setup()
    render(<Harness initial={[photo('p1.jpg'), photo('p2.jpg')]} onRead={vi.fn()} />)

    await user.click(screen.getByRole('button', { name: 'Barem din poză (AI)' }))

    expect(screen.queryByLabelText('Poza cu baremul')).not.toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Citește baremul (AI)' })).toBeInTheDocument()
    // Two pages: the teacher picks the one with this problem.
    await user.click(screen.getByRole('button', { name: 'Pagina 2' }))
    expect(screen.getByRole('button', { name: 'Pagina 2' })).toHaveAttribute('aria-pressed', 'true')
  })

  it('takes a PDF barem as its pages', async () => {
    const user = userEvent.setup()
    pdfPages.images = [photo('pagina-1.jpg'), photo('pagina-2.jpg')]
    render(<Harness onRead={vi.fn()} />)

    await user.click(screen.getByRole('button', { name: 'Barem din poză (AI)' }))
    await user.upload(screen.getByLabelText('Poza cu baremul'), new File(['%PDF'], 'barem.pdf', { type: 'application/pdf' }))

    await waitFor(() => expect(screen.getByTestId('kept')).toHaveTextContent('pagina-1.jpg,pagina-2.jpg'))
  })

  it('says why a reading failed and lets the teacher frame again', async () => {
    const user = userEvent.setup()
    const onRead = vi.fn()
    api.createQuestionBaremRead.mockResolvedValue({ id: 7, status: 'QUEUED', error: null, barem: null })
    api.getQuestionBaremRead.mockResolvedValue({ id: 7, status: 'FAILED', error: 'Nu am găsit în poze baremul.', barem: null })
    render(<Harness initial={[photo()]} onRead={onRead} />)

    await user.click(screen.getByRole('button', { name: 'Barem din poză (AI)' }))
    await user.click(screen.getByRole('button', { name: 'Citește baremul (AI)' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('Nu am găsit în poze baremul.')
    expect(onRead).not.toHaveBeenCalled()
    expect(screen.getByRole('button', { name: 'Citește baremul (AI)' })).toBeEnabled()
  })
})
