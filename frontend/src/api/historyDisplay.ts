/** History pills show the bust multiplier once the round is terminal. */

export type PillTone = 'win' | 'hot' | 'crash' | 'void' | 'neutral'

export function historyPillLabel(
  status: string,
  crashPoint: number | string | null | undefined,
): string {
  if (status === 'VOID') return 'Void'
  const k = asNumber(crashPoint)
  if (k != null) return `${k.toFixed(2)}x`
  if (status === 'CRASHED') return 'Crash'
  return status
}

export function historyPillTone(
  status: string,
  crashPoint: number | string | null | undefined,
): PillTone {
  if (status === 'VOID') return 'void'
  const k = asNumber(crashPoint)
  if (k == null) return status === 'CRASHED' ? 'crash' : 'neutral'
  if (k < 1.2) return 'crash'
  if (k >= 10) return 'hot'
  return 'win'
}

function asNumber(value: number | string | null | undefined): number | null {
  if (value == null) return null
  const n = typeof value === 'number' ? value : Number(value)
  return Number.isFinite(n) && n > 0 ? n : null
}
