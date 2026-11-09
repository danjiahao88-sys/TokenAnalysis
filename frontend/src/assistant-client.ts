import { ref } from 'vue'
import { z } from 'zod'

const storageKey = 'ai-footprint-assistant-v1'
const tokenSchema = z.string().regex(/^[A-Za-z0-9_-]{43}$/)
export const assistantToken = ref('')
export const assistantBase = 'http://127.0.0.1:17890'
export const localSite = ['localhost', '127.0.0.1', '[::1]'].includes(location.hostname)
try {
  const stored = tokenSchema.safeParse(localStorage.getItem(storageKey))
  if (stored.success) assistantToken.value = stored.data
} catch {
  /* Private browsing can disable storage; the in-memory connection still works. */
}

export const liveStatusSchema = z.object({
  enabled: z.boolean(),
  collecting: z.boolean(),
  state: z.enum(['waiting', 'collecting', 'ready', 'error']),
  lastAttemptAt: z.iso.datetime().nullable(),
  lastSuccessAt: z.iso.datetime().nullable(),
  pollSeconds: z.number().int().min(1).max(60)
})
export type LiveStatus = z.infer<typeof liveStatusSchema>
export function rememberSource(local: boolean) {
  try {
    localStorage.setItem('ai-footprint-auto-connect', local ? 'yes' : 'no')
  } catch {
    /* Memory-only mode. */
  }
}
export function shouldReconnect() {
  try {
    return !!assistantToken.value && localStorage.getItem('ai-footprint-auto-connect') !== 'no'
  } catch {
    return false
  }
}
export function forgetAssistant() {
  assistantToken.value = ''
  try {
    localStorage.removeItem(storageKey)
  } catch {
    /* Memory-only mode. */
  }
}
async function bridge(path: string, init: RequestInit = {}, token = assistantToken.value) {
  const headers = new Headers(init.headers)
  headers.delete('X-AI-Footprint')
  if (token) headers.set('Authorization', `Bearer ${token}`)
  const response = await fetch(`${assistantBase}/bridge/${path}`, {
    ...init,
    headers,
    credentials: 'omit',
    cache: 'no-store',
    redirect: 'error',
    referrerPolicy: 'no-referrer',
    signal: init.signal ?? AbortSignal.timeout(15000)
  })
  if (response.status === 401 && token === assistantToken.value) forgetAssistant()
  return response
}
export async function pairAssistant(code: string) {
  if (!/^\d{8}$/.test(code)) throw Error('请输入助手显示的 8 位配对码。')
  const response = await bridge(
    'pair',
    {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ code })
    },
    ''
  )
  if (response.status === 429) throw Error('尝试过于频繁，请等一分钟后生成新配对码。')
  if (!response.ok) throw Error('配对未通过，请在本机助手生成新码，并确认当前网站受信任。')
  const grant = z.object({ token: tokenSchema }).parse(await response.json())
  assistantToken.value = grant.token
  try {
    localStorage.setItem(storageKey, grant.token)
  } catch {
    /* Keep this tab connected. */
  }
  rememberSource(true)
}
export async function disconnectAssistant() {
  const token = assistantToken.value
  forgetAssistant()
  rememberSource(false)
  if (token) {
    const r = await bridge('disconnect', { method: 'POST' }, token)
    if (!r.ok && r.status !== 401) throw Error('浏览器已断开。可在本机助手撤销全部授权。')
  }
}
export async function assistantFetch(path: string, init: RequestInit = {}) {
  if (assistantToken.value) return bridge(path, init)
  if (!localSite) throw Error('请先连接本机采集助手。')
  return fetch(path === 'status' ? '/api/assistant/status' : `/api/${path}`, {
    ...init,
    credentials: 'omit',
    cache: 'no-store',
    redirect: 'error',
    signal: init.signal ?? AbortSignal.timeout(15000)
  })
}
