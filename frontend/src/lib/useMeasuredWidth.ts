import { useCallback, useState } from 'react'

/** Before the first measure, and where nothing can be measured (tests). */
const DEFAULT_WIDTH = 640
/** Narrower than this the two dates under a chart's line would collide: below it the drawing shrinks instead. */
const MIN_WIDTH = 280

/**
 * A chart's own width in CSS pixels, kept up to date: drawn at that width, 12 px text stays 12 px. A fixed
 * 640-unit drawing scaled into a 310 px phone card shrank every label to about 5 px. Pass the ref callback to the
 * element that holds the drawing.
 */
export function useMeasuredWidth(): [(el: HTMLDivElement | null) => (() => void) | undefined, number] {
  const [width, setWidth] = useState(DEFAULT_WIDTH)
  const measure = useCallback((el: HTMLDivElement | null) => {
    if (!el || typeof ResizeObserver === 'undefined') return undefined
    const observer = new ResizeObserver(([entry]) => {
      const measured = Math.round(entry.contentRect.width)
      if (measured > 0) setWidth(Math.max(MIN_WIDTH, measured))
    })
    observer.observe(el)
    return () => observer.disconnect()
  }, [])
  return [measure, width]
}
