/** History pill display helpers (Spec 3 §3.5 / §10.5). */

export function historyPillLabel(status: string, betAmount: number | string, winAmount: number | string | null): string {
  if (status === 'CASHED_OUT' && winAmount != null) {
    const bet = Number(betAmount)
    const win = Number(winAmount)
    if (bet > 0 && Number.isFinite(win)) {
      return `${(win / bet).toFixed(2)}x`
    }
  }
  if (status === 'CRASHED') return 'Crash'
  if (status === 'VOID') return 'Void'
  return status
}
