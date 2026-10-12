/**
 * Feature 2: the answers of one attempt that the server has not confirmed yet, kept in localStorage so a reload, a dead
 * battery or a lost connection loses nothing. One entry per question (a newer choice replaces a waiting one); each
 * carries this browser's id and a counter, so the server can ignore an older retry that arrives late (V31).
 *
 * Holds only ids the student could send anyway by tapping: the server re-checks everything. Cleared on hand-in and on
 * sign-out (a shared family computer).
 */

export interface PendingAnswer {
  itemId: number
  optionId: number
  /** This browser's counter when the choice was made: higher = newer. */
  seq: number
}

interface Stored {
  clientId: string
  nextSeq: number
  pending: PendingAnswer[]
}

const PREFIX = 'mathlms:pending:'

/** A random v4 UUID - also on plain http on the home network, where crypto.randomUUID is missing. */
function uuid(): string {
  if (typeof crypto.randomUUID === 'function') return crypto.randomUUID()
  const b = crypto.getRandomValues(new Uint8Array(16))
  b[6] = (b[6] & 0x0f) | 0x40
  b[8] = (b[8] & 0x3f) | 0x80
  const hex = [...b].map((x) => x.toString(16).padStart(2, '0')).join('')
  return `${hex.slice(0, 8)}-${hex.slice(8, 12)}-${hex.slice(12, 16)}-${hex.slice(16, 20)}-${hex.slice(20)}`
}

function read(key: string): Stored | null {
  try {
    const raw = localStorage.getItem(key)
    if (!raw) return null
    const parsed = JSON.parse(raw) as Stored
    return typeof parsed.clientId === 'string' && Array.isArray(parsed.pending) ? parsed : null
  } catch {
    return null // storage blocked (private window) or garbage: start empty
  }
}

export class AnswerQueue {
  private readonly key: string
  private state: Stored

  constructor(attemptId: number) {
    this.key = PREFIX + attemptId
    this.state = read(this.key) ?? { clientId: uuid(), nextSeq: 1, pending: [] }
  }

  get clientId(): string {
    return this.state.clientId
  }

  /** Oldest first. */
  pending(): PendingAnswer[] {
    return [...this.state.pending]
  }

  pendingFor(itemId: number): PendingAnswer | undefined {
    return this.state.pending.find((p) => p.itemId === itemId)
  }

  /** A new choice for a question: replaces any older one still waiting for it. */
  enqueue(itemId: number, optionId: number): PendingAnswer {
    const entry = { itemId, optionId, seq: this.state.nextSeq }
    this.state = {
      ...this.state,
      nextSeq: this.state.nextSeq + 1,
      pending: [...this.state.pending.filter((p) => p.itemId !== itemId), entry],
    }
    this.save()
    return entry
  }

  /**
   * The server has dealt with `entry` (saved it, or refused it for good): forget it - unless a newer choice for the
   * same question arrived meanwhile, which must still be sent.
   */
  settle(entry: PendingAnswer): void {
    const before = this.state.pending.length
    this.state = { ...this.state, pending: this.state.pending.filter((p) => !(p.itemId === entry.itemId && p.seq === entry.seq)) }
    if (this.state.pending.length !== before) this.save()
  }

  /** After hand-in: nothing of this attempt is kept. */
  clear(): void {
    this.state = { ...this.state, pending: [] }
    try {
      localStorage.removeItem(this.key)
    } catch {
      // nothing stored
    }
  }

  private save(): void {
    try {
      if (this.state.pending.length === 0 && this.state.nextSeq === 1) localStorage.removeItem(this.key)
      else localStorage.setItem(this.key, JSON.stringify(this.state))
    } catch {
      // storage full or blocked: the queue still works for this page load
    }
  }
}

/** The waiting answers of an attempt, read without keeping a queue (to show them when the attempt is resumed). */
export function storedPending(attemptId: number): PendingAnswer[] {
  return read(PREFIX + attemptId)?.pending ?? []
}

/** On sign-out: every attempt's waiting answers go (the next person on this computer must not send them). */
export function clearAllAnswerQueues(): void {
  try {
    for (let i = localStorage.length - 1; i >= 0; i--) {
      const key = localStorage.key(i)
      if (key?.startsWith(PREFIX)) localStorage.removeItem(key)
    }
  } catch {
    // nothing stored
  }
}
