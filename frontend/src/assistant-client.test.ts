import { beforeEach, afterEach, expect, it, vi } from 'vitest'
let values: Map<string, string>
beforeEach(() => {
  vi.resetModules()
  values = new Map()
  vi.stubGlobal('location', { hostname: 'danjiahao88-sys.github.io' })
  vi.stubGlobal('localStorage', {
    getItem: (key: string) => values.get(key) ?? null,
    setItem: (key: string, value: string) => values.set(key, value),
    removeItem: (key: string) => values.delete(key)
  })
})
afterEach(() => vi.unstubAllGlobals())
it('never sends public visitors to an unauthenticated same-origin API', async () => {
  const fetch = vi.fn()
  vi.stubGlobal('fetch', fetch)
  const client = await import('./assistant-client')
  await expect(client.assistantFetch('snapshot')).rejects.toThrow('请先连接')
  expect(fetch).not.toHaveBeenCalled()
})
it('pairs without cookies and carries a saved token in a header, never a URL', async () => {
  const token = 'a'.repeat(43)
  const fetch = vi
    .fn()
    .mockResolvedValueOnce(new Response(JSON.stringify({ token })))
    .mockResolvedValueOnce(new Response('{}'))
  vi.stubGlobal('fetch', fetch)
  const client = await import('./assistant-client')
  await client.pairAssistant('12345678')
  await client.assistantFetch('snapshot')
  expect(client.shouldReconnect()).toBe(true)
  expect(fetch.mock.calls[0]![0]).toBe('http://127.0.0.1:17890/bridge/pair')
  expect(fetch.mock.calls[0]![1].credentials).toBe('omit')
  expect(fetch.mock.calls[1]![0]).not.toContain(token)
  expect(fetch.mock.calls[1]![1].headers.get('Authorization')).toBe(`Bearer ${token}`)
  client.rememberSource(false)
  expect(client.shouldReconnect()).toBe(false)
})
it('clears revoked credentials and does not keep retrying with a dead token', async () => {
  values.set('ai-footprint-assistant-v1', 'b'.repeat(43))
  vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response('', { status: 401 })))
  const client = await import('./assistant-client')
  expect(client.shouldReconnect()).toBe(true)
  await client.assistantFetch('status')
  expect(client.assistantToken.value).toBe('')
  expect(values.has('ai-footprint-assistant-v1')).toBe(false)
})
it('disconnects locally even when the assistant is unavailable', async () => {
  values.set('ai-footprint-assistant-v1', 'c'.repeat(43))
  vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new TypeError('offline')))
  const client = await import('./assistant-client')
  await expect(client.disconnectAssistant()).rejects.toThrow()
  expect(client.assistantToken.value).toBe('')
  expect(client.shouldReconnect()).toBe(false)
})
it('keeps local development available without turning on public CORS', async () => {
  vi.stubGlobal('location', { hostname: '127.0.0.1' })
  const fetch = vi.fn().mockResolvedValue(new Response('{}'))
  vi.stubGlobal('fetch', fetch)
  const client = await import('./assistant-client')
  await client.assistantFetch('status')
  expect(fetch.mock.calls[0]![0]).toBe('/api/assistant/status')
})
