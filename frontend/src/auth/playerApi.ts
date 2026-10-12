import { apiRequest } from '../api/client'
import type { AlbumSlotInput } from './albumSlots'
import { clearPlayerSession, writePlayerSession, type PlayerSession } from './playerSession'

interface TokenResponse {
  accessToken: string
  refreshToken: string
  expiresIn: number
  playerId: string
  email: string
}

export interface AlbumSlotResponse {
  slot: number
  gameId: string | null
  earnedAt: string
}

export async function registerPlayer(email: string, password: string): Promise<PlayerSession> {
  return saveSession(await apiRequest<TokenResponse>('/api/auth/register', {
    method: 'POST',
    body: { email, password },
  }))
}

export async function loginPlayer(email: string, password: string): Promise<PlayerSession> {
  return saveSession(await apiRequest<TokenResponse>('/api/auth/login', {
    method: 'POST',
    body: { email, password },
  }))
}

export async function logoutPlayer(refreshToken: string | undefined): Promise<void> {
  try {
    if (refreshToken) {
      await apiRequest<void>('/api/auth/logout', { method: 'POST', body: { refreshToken } })
    }
  } catch {
    /* local session still clears */
  } finally {
    clearPlayerSession()
  }
}

export function syncAlbum(pieces: AlbumSlotInput[]): Promise<AlbumSlotResponse[]> {
  return apiRequest<AlbumSlotResponse[]>('/api/album/sync', {
    method: 'POST',
    body: { pieces },
  })
}

function saveSession(body: TokenResponse): PlayerSession {
  const session: PlayerSession = {
    accessToken: body.accessToken,
    refreshToken: body.refreshToken,
    playerId: body.playerId,
    email: body.email,
  }
  writePlayerSession(session)
  return session
}
