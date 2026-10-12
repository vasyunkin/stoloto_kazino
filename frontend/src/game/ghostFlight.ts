/**
 * Client-side continuation after cashout (Spec 7 TD-10).
 *
 * Money is already locked on the server. The balloon keeps climbing in log-space
 * from the cashout multiplier to the revealed crash point, compressed so a
 * typical gap (2.50× → 7.20×) lasts about two seconds.
 */

/** ln(crash/cash) × this ≈ duration. Tuned so ln(7.2/2.5) ≈ 2.0s. */
export const GHOST_MS_PER_NAT = 1900
export const GHOST_MIN_MS = 700
export const GHOST_MAX_MS = 4500
/** Burst VFX hold before the result card (matches BalloonStage burst life). */
export const GHOST_BURST_HOLD_MS = 480

export function ghostDurationMs(cashK: number, crashK: number): number {
  if (!(crashK > cashK) || !(cashK > 0)) return 0
  const span = Math.log(crashK / cashK)
  const raw = span * GHOST_MS_PER_NAT
  return Math.min(GHOST_MAX_MS, Math.max(GHOST_MIN_MS, raw))
}

/** Log-space lerp: same shape as K(t) = e^(αt), compressed into [0, 1]. */
export function ghostMultiplierAt(cashK: number, crashK: number, progress: number): number {
  const p = Math.min(1, Math.max(0, progress))
  if (!(crashK > cashK) || !(cashK > 0)) return cashK
  const logK = Math.log(cashK) + (Math.log(crashK) - Math.log(cashK)) * p
  return Math.exp(logK)
}

/**
 * Keep the Pixi ticker alive while the round is actually flying, or while
 * we are replaying the theoretical climb after cashout (until the burst).
 */
export function shouldKeepFlightVisual(
  status: string,
  phase: string,
  ghostBurst: boolean,
): boolean {
  if (ghostBurst) return false
  return status === 'FLYING' || phase === 'spectating'
}
