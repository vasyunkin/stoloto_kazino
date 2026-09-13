import { ApiError, apiRequest } from '../api/client'
import {
  clearTokens,
  getAccessToken,
  getRefreshToken,
  redirectToLogin,
  setTokens,
} from './adminSession'

export interface AdminProfile {
  id: string
  username: string
  displayName: string | null
}

export interface AdminTokenResponse {
  accessToken: string
  refreshToken: string
  expiresIn: number
  admin: AdminProfile
}

export interface ConfigHistoryItem {
  id: string
  appliedAt: string
  appliedBy: string
  payloadPreview: string
}

let refreshInFlight: Promise<boolean> | null = null

export async function adminLogin(username: string, password: string): Promise<AdminTokenResponse> {
  const body = await apiRequest<AdminTokenResponse>('/api/admin/auth/login', {
    method: 'POST',
    body: { username, password },
  })
  setTokens(body.accessToken, body.refreshToken)
  return body
}

export async function adminLogout(): Promise<void> {
  const refreshToken = getRefreshToken()
  try {
    if (refreshToken) {
      await apiRequest<void>('/api/admin/auth/logout', {
        method: 'POST',
        body: { refreshToken },
      })
    }
  } catch {
    /* still clear local session */
  } finally {
    clearTokens()
  }
}

export async function fetchAdminConfig(): Promise<unknown> {
  return withAuthRetry((accessToken) =>
    apiRequest<unknown>('/api/admin/config', { accessToken }),
  )
}

export async function saveAdminConfig(payload: unknown): Promise<unknown> {
  return withAuthRetry((accessToken) =>
    apiRequest<unknown>('/api/admin/config', {
      method: 'PUT',
      accessToken,
      body: payload,
    }),
  )
}

export async function fetchConfigHistory(limit = 20): Promise<ConfigHistoryItem[]> {
  return withAuthRetry((accessToken) =>
    apiRequest<ConfigHistoryItem[]>(`/api/admin/config/history?limit=${limit}`, { accessToken }),
  )
}

async function withAuthRetry<T>(fn: (accessToken: string) => Promise<T>): Promise<T> {
  const token = getAccessToken()
  if (!token) {
    redirectToLogin()
    throw new ApiError(401, null, 'Unauthorized')
  }
  try {
    return await fn(token)
  } catch (e) {
    if (!(e instanceof ApiError) || e.status !== 401) {
      throw e
    }
    const refreshed = await refreshOnce()
    if (!refreshed) {
      clearTokens()
      redirectToLogin()
      throw e
    }
    const next = getAccessToken()
    if (!next) {
      redirectToLogin()
      throw e
    }
    return fn(next)
  }
}

async function refreshOnce(): Promise<boolean> {
  if (!refreshInFlight) {
    refreshInFlight = doRefresh().finally(() => {
      refreshInFlight = null
    })
  }
  return refreshInFlight
}

async function doRefresh(): Promise<boolean> {
  const refreshToken = getRefreshToken()
  if (!refreshToken) {
    return false
  }
  try {
    const body = await apiRequest<AdminTokenResponse>('/api/admin/auth/refresh', {
      method: 'POST',
      body: { refreshToken },
    })
    setTokens(body.accessToken, body.refreshToken)
    return true
  } catch {
    return false
  }
}
