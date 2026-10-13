import { optionLetter } from '@/lib/format'

/**
 * "b)" in front of an option, as on the paper test and in the barem, so "răspunsul b" means the same on screen and in
 * class. The space after it keeps "b) 75" one phrase for a screen reader (a flex row ignores it on screen).
 */
export function OptionLetter({ index }: { index: number }) {
  return (
    <>
      <span className="shrink-0 font-semibold">{optionLetter(index)})</span>{' '}
    </>
  )
}
