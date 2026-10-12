import { describe, expect, it, vi } from 'vitest'
import source from '../../public/sw.js?raw'

/**
 * public/sw.js is plain JavaScript run by the browser, outside the bundle. Here it runs with fake
 * `caches` and `fetch`, and the test fires the events a browser would - to pin the rules that matter most: nothing
 * under /api is ever answered by the worker, pages come from the network, hashed assets from the cache.
 */
const ORIGIN = 'https://mate.exemplu.ro'

interface FakeResponse {
  ok: boolean
  body: string
  clone: () => FakeResponse
}
const response = (body: string, ok = true): FakeResponse => ({ ok, body, clone: () => response(body, ok) })

function worker(network: (url: string) => Promise<FakeResponse>) {
  const listeners: Record<string, (event: unknown) => void> = {}
  const stores = new Map<string, Map<string, FakeResponse>>()
  const keyOf = (r: string | { url: string }) => (typeof r === 'string' ? new URL(r, ORIGIN).href : r.url)
  const open = async (name: string) => {
    if (!stores.has(name)) stores.set(name, new Map())
    const store = stores.get(name)!
    return {
      match: async (r: string | { url: string }) => store.get(keyOf(r)),
      put: async (r: { url: string }, res: FakeResponse) => void store.set(keyOf(r), res),
      addAll: async (urls: string[]) => urls.forEach((u) => store.set(keyOf(u), response(`precached ${u}`))),
      keys: async () => [...store.keys()].map((url) => ({ url })),
      delete: async (r: { url: string }) => store.delete(keyOf(r)),
    }
  }
  const caches = {
    open,
    keys: async () => [...stores.keys()],
    delete: async (name: string) => stores.delete(name),
    match: async (r: string) => {
      for (const store of stores.values()) {
        const hit = store.get(keyOf(r))
        if (hit) return hit
      }
      return undefined
    },
  }
  const fetchSpy = vi.fn((request: { url: string } | string) => network(keyOf(request)))
  const self = {
    addEventListener: (type: string, listener: (event: unknown) => void) => (listeners[type] = listener),
    skipWaiting: vi.fn(),
    clients: { claim: vi.fn(async () => undefined) },
    location: { origin: ORIGIN },
  }
  // The worker's globals are this function's parameters: it sees the fakes, never the test's own window.
  new Function('self', 'caches', 'fetch', source)(self, caches, fetchSpy)

  /** Fires a fetch event; returns what the worker answered with, or null when it let the browser handle it. */
  async function request(url: string, init: { method?: string; mode?: string } = {}) {
    let answered = null as Promise<FakeResponse> | null
    listeners.fetch({
      request: { url: new URL(url, ORIGIN).href, method: init.method ?? 'GET', mode: init.mode ?? 'cors' },
      respondWith: (p: Promise<FakeResponse>) => (answered = p),
    })
    return answered ? await answered : null
  }
  async function lifecycle(type: 'install' | 'activate') {
    let done: Promise<unknown> = Promise.resolve()
    listeners[type]({ waitUntil: (p: Promise<unknown>) => (done = p) })
    await done
  }
  return { request, lifecycle, fetchSpy, stores, self }
}

describe('service worker (public/sw.js)', () => {
  it('never answers for the API, sign-in, the actuator, other sites or anything but GET', async () => {
    const sw = worker(async () => response('net'))
    for (const url of ['/api/quiz/attempts/5/result', '/api/quiz/attempts/5/responses/6/photos/7', '/oauth2/authorization/google',
      '/login/oauth2/code/google', '/actuator/health', 'https://altundeva.ro/assets/x.js']) {
      expect(await sw.request(url), url).toBeNull()
    }
    expect(await sw.request('/assets/app-123.js', { method: 'POST' })).toBeNull()
    expect(sw.fetchSpy).not.toHaveBeenCalled()
  })

  it('takes pages from the network and shows the offline page only when it is down', async () => {
    let online = true
    const sw = worker(async () => (online ? response('<html>app</html>') : Promise.reject(new TypeError('offline'))))
    await sw.lifecycle('install')

    expect((await sw.request('/quizzes', { mode: 'navigate' }))!.body).toBe('<html>app</html>')
    online = false
    expect((await sw.request('/quizzes', { mode: 'navigate' }))!.body).toBe('precached /offline.html')
  })

  it('keeps hashed assets once fetched and serves them from the cache after that', async () => {
    const sw = worker(async (url) => response(`file ${url}`))

    await sw.request('/assets/index-abc123.js')
    const again = await sw.request('/assets/index-abc123.js')

    expect(again!.body).toBe(`file ${ORIGIN}/assets/index-abc123.js`)
    expect(sw.fetchSpy).toHaveBeenCalledTimes(1)
  })

  it('does not keep a failed asset response', async () => {
    const sw = worker(async () => response('not found', false))

    await sw.request('/assets/gone.js')
    await sw.request('/assets/gone.js')

    expect(sw.fetchSpy).toHaveBeenCalledTimes(2)
  })

  it('on activation drops the caches of earlier versions and takes over open pages', async () => {
    const sw = worker(async () => response('x'))
    sw.stores.set('math-lms-assets-v0', new Map())
    await sw.lifecycle('install')
    await sw.request('/assets/a.js')

    await sw.lifecycle('activate')

    expect([...sw.stores.keys()].sort()).toEqual(['math-lms-assets-v1', 'math-lms-shell-v1'])
    expect(sw.self.clients.claim).toHaveBeenCalled()
  })
})
