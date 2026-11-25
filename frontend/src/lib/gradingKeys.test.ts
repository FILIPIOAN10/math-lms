import { describe, expect, it } from 'vitest'
import { gradingKey, isTypingTarget, neighbourPaper } from '@/lib/gradingKeys'

const press = (key: string, over: Partial<KeyboardEvent> = {}, target: EventTarget | null = document.body) =>
  ({ key, ctrlKey: false, metaKey: false, altKey: false, target, ...over }) as KeyboardEvent

describe('gradingKey', () => {
  it('reads N, P, C and ?, in either case', () => {
    expect(gradingKey(press('n'), false)).toBe('next')
    expect(gradingKey(press('P'), false)).toBe('prev')
    expect(gradingKey(press('c'), false)).toBe('comment')
    expect(gradingKey(press('?'), false)).toBe('help')
    expect(gradingKey(press('x'), false)).toBeNull()
  })

  it('stays out of the way of typing, browser shortcuts and open dialogs', () => {
    const textarea = document.createElement('textarea')
    expect(gradingKey(press('n', {}, textarea), false)).toBeNull()
    expect(gradingKey(press('c', { ctrlKey: true }), false)).toBeNull()
    expect(gradingKey(press('p', { metaKey: true }), false)).toBeNull()
    expect(gradingKey(press('n'), true)).toBeNull()
  })

  it('counts text boxes as typing, buttons and radios not', () => {
    const text = document.createElement('input')
    const radio = document.createElement('input')
    radio.type = 'radio'
    expect(isTypingTarget(text)).toBe(true)
    expect(isTypingTarget(radio)).toBe(false)
    expect(isTypingTarget(document.createElement('button'))).toBe(false)
  })
})

describe('neighbourPaper', () => {
  it('finds the next and the previous paper, none past the ends', () => {
    expect(neighbourPaper([4, 7, 9], 7, 1)).toBe(9)
    expect(neighbourPaper([4, 7, 9], 7, -1)).toBe(4)
    expect(neighbourPaper([4, 7, 9], 9, 1)).toBeNull()
    expect(neighbourPaper([4, 7, 9], 3, 1)).toBeNull()
  })
})
