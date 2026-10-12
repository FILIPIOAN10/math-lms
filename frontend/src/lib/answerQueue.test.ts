import { beforeEach, describe, expect, it } from 'vitest'
import { AnswerQueue, clearAllAnswerQueues, storedPending } from '@/lib/answerQueue'
import { ApiError } from '@/lib/api'
import { isRetryable } from '@/lib/useAnswerSync'

describe('AnswerQueue', () => {
  beforeEach(() => localStorage.clear())

  it('keeps one waiting answer per question, the newest, with a rising counter', () => {
    const q = new AnswerQueue(50)
    q.enqueue(100, 1000)
    q.enqueue(101, 2000)
    q.enqueue(100, 1001)

    expect(q.pending()).toEqual([
      { itemId: 101, optionId: 2000, seq: 2 },
      { itemId: 100, optionId: 1001, seq: 3 },
    ])
  })

  it('survives a reload: same browser id, the counter goes on', () => {
    const first = new AnswerQueue(50)
    first.enqueue(100, 1000)
    const id = first.clientId

    const again = new AnswerQueue(50)
    expect(again.clientId).toBe(id)
    expect(again.pending()).toEqual([{ itemId: 100, optionId: 1000, seq: 1 }])
    expect(again.enqueue(101, 2000).seq).toBe(2)
    expect(storedPending(50)).toHaveLength(2)
  })

  it('forgets an answer the server dealt with, but not a newer one for the same question', () => {
    const q = new AnswerQueue(50)
    const old = q.enqueue(100, 1000)
    q.enqueue(100, 1001) // made while the first was on its way

    q.settle(old)
    expect(q.pending()).toEqual([{ itemId: 100, optionId: 1001, seq: 2 }])
    // the counter is kept even once nothing waits, so later answers are still "newer" after a reload
    q.settle({ itemId: 100, optionId: 1001, seq: 2 })
    expect(new AnswerQueue(50).enqueue(100, 1002).seq).toBe(3)
  })

  it('is emptied on hand-in, and every attempt on sign-out', () => {
    new AnswerQueue(50).enqueue(100, 1000)
    new AnswerQueue(51).enqueue(200, 3000)
    localStorage.setItem('theme', 'dark')

    new AnswerQueue(50).clear()
    expect(storedPending(50)).toEqual([])
    clearAllAnswerQueues()
    expect(storedPending(51)).toEqual([])
    expect(localStorage.getItem('theme')).toBe('dark')
  })

  it('starts empty over garbage in storage', () => {
    localStorage.setItem('mathlms:pending:50', '{not json')
    expect(new AnswerQueue(50).pending()).toEqual([])
  })
})

describe('isRetryable', () => {
  it('retries what may pass: no connection, a server or proxy down, the rate limiter', () => {
    expect(isRetryable(new TypeError('Failed to fetch'))).toBe(true)
    expect(isRetryable(new ApiError(502, ''))).toBe(true)
    expect(isRetryable(new ApiError(429, ''))).toBe(true)
    expect(isRetryable(new ApiError(400, ''))).toBe(false)
    expect(isRetryable(new ApiError(409, ''))).toBe(false)
    expect(isRetryable(new Error('other'))).toBe(false)
  })
})
