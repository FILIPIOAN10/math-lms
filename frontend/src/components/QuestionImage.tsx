import { useState } from 'react'
import { PhotoViewer } from '@/components/PhotoViewer'
import { questionImageUrl } from '@/lib/api'
import { cn } from '@/lib/utils'

/**
 * A picture shown inside a page (a question's figure, a worked example's drawing), centred under its text whatever its
 * width, so the teacher need not crop it to the pixel. A click opens it large on the same page - the question stays
 * where it was - and the viewer still has "Tab nou" for whoever wants it apart.
 */
export function ZoomableImage({
  src,
  alt,
  testId,
  imgClassName = 'max-h-80',
  className,
}: {
  src: string
  alt: string
  testId?: string
  imgClassName?: string
  className?: string
}) {
  const [open, setOpen] = useState(false)
  return (
    <>
      <button
        type="button"
        onClick={() => setOpen(true)}
        title="Apasă ca s-o vezi mare"
        className={cn(
          'mx-auto block w-fit max-w-full cursor-zoom-in rounded-lg outline-none focus-visible:ring-3 focus-visible:ring-ring/50',
          className,
        )}
      >
        <img src={src} alt={alt} data-testid={testId} className={cn('max-w-full rounded-lg border bg-white object-contain', imgClassName)} />
      </button>
      <PhotoViewer
        urls={[src]}
        alt={() => alt}
        title={() => alt}
        index={open ? 0 : null}
        onIndexChange={() => undefined}
        onClose={() => setOpen(false)}
      />
    </>
  )
}

/** The picture under a question's statement (EN Subiectul II figures), wherever the question is shown. Nothing when the question has none. */
export function QuestionImage({ itemId, image, className }: { itemId: number; image?: string | null; className?: string }) {
  if (!image) return null
  return (
    <ZoomableImage src={questionImageUrl(itemId, image)} alt="Figura întrebării" testId="question-image" className={className} />
  )
}
