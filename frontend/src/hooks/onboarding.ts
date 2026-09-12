const KEY = 'balloon.onboarding.flightHint.v1'

export function shouldShowFlightOnboarding(): boolean {
  try {
    return localStorage.getItem(KEY) !== '1'
  } catch {
    return true
  }
}

export function markFlightOnboardingSeen(): void {
  try {
    localStorage.setItem(KEY, '1')
  } catch {
    /* ignore */
  }
}
