const ACCESS_KEY = 'balloon.admin.accessToken'
const REFRESH_KEY = 'balloon.admin.refreshToken'

export function getAccessToken(): string | null {
  return sessionStorage.getItem(ACCESS_KEY)
}

export function getRefreshToken(): string | null {
  return sessionStorage.getItem(REFRESH_KEY)
}

export function setTokens(accessToken: string, refreshToken: string): void {
  sessionStorage.setItem(ACCESS_KEY, accessToken)
  sessionStorage.setItem(REFRESH_KEY, refreshToken)
}

export function clearTokens(): void {
  sessionStorage.removeItem(ACCESS_KEY)
  sessionStorage.removeItem(REFRESH_KEY)
}

export function redirectToLogin(): void {
  if (window.location.pathname !== '/admin/login') {
    window.location.replace('/admin/login')
  }
}
