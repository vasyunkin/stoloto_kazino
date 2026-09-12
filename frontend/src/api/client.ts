import type { ErrorResponse } from './types'

const API_BASE = import.meta.env.VITE_API_BASE ?? ''

export class ApiError extends Error {
  readonly code: string
  readonly status: number
  readonly body: ErrorResponse | null

  constructor(status: number, body: ErrorResponse | null, fallback: string) {
    super(body?.message ?? fallback)
    this.name = 'ApiError'
    this.status = status
    this.code = body?.code ?? 'UNKNOWN'
    this.body = body
  }
}

export interface RequestOptions {
  method?: string
  playerId?: string
  body?: unknown
  signal?: AbortSignal
}

export async function apiRequest<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const headers: Record<string, string> = {
    Accept: 'application/json',
  }
  if (options.body !== undefined) {
    headers['Content-Type'] = 'application/json'
  }
  if (options.playerId) {
    headers['X-Player-Id'] = options.playerId
  }

  const res = await fetch(`${API_BASE}${path}`, {
    method: options.method ?? (options.body !== undefined ? 'POST' : 'GET'),
    headers,
    body: options.body !== undefined ? JSON.stringify(options.body) : undefined,
    signal: options.signal,
  })

  if (!res.ok) {
    let errBody: ErrorResponse | null = null
    try {
      errBody = (await res.json()) as ErrorResponse
    } catch {
      /* ignore */
    }
    throw new ApiError(res.status, errBody, res.statusText)
  }

  if (res.status === 204) {
    return undefined as T
  }
  return (await res.json()) as T
}
