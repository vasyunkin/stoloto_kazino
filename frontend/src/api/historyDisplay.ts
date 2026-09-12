/** History pill display helpers (Spec 3 §3.5 / §10.5). */

export type PillTone = 'win' | 'hot' | 'crash' | 'void' | 'neutral'

export function historyPillLabel(
  status: string,
  betAmount: number | string,
  winAmount: number | string | null,
): string {
  if (status === 'CASHED_OUT' && winAmount != null) {
    const k = cashoutMultiplier(betAmount, winAmount)
    if (k != null) return `${k.toFixed(2)}x`
  }
  if (status === 'CRASHED') return 'Crash'
  if (status === 'VOID') return 'Void'
  return status
}

export function cashoutMultiplier(
  betAmount: number | string,
  winAmount: number | string | null,
): number | null {
  if (winAmount == null) return null
  const bet = Number(betAmount)
  const win = Number(winAmount)
  if (!(bet > 0) || !Number.isFinite(win)) return null
  return win / bet
}

export function historyPillTone(
  status: string,
  betAmount: number | string,
  winAmount: number | string | null,
): PillTone {
  if (status === 'CRASHED') return 'crash'
  if (status === 'VOID') return 'void'
  if (status === 'CASHED_OUT') {
    const k = cashoutMultiplier(betAmount, winAmount)
    if (k != null && k >= 4) return 'hot'
    return 'win'
  }
  return 'neutral'
}
