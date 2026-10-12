const SESSION_KEY = 'balloon.player.session'

export interface PlayerSession {
  accessToken: string
  refreshToken: string
  playerId: string
  email: string
}

export function readPlayerSession(): PlayerSession | null {
  try {
    const raw = localStorage.getItem(SESSION_KEY)
    if (!raw) return null
    const parsed = JSON.parse(raw) as PlayerSession
    if (!parsed?.accessToken || !parsed.playerId || !parsed.email) return null
    return parsed
  } catch {
    return null
  }
}

export function writePlayerSession(session: PlayerSession): void {
  localStorage.setItem(SESSION_KEY, JSON.stringify(session))
}

export function clearPlayerSession(): void {
  localStorage.removeItem(SESSION_KEY)
}

export function getPlayerAccessToken(): string | null {
  return readPlayerSession()?.accessToken ?? null
}
