import { useEffect } from 'react'
import { getVerify } from '../api/gameApi'
import type { VerifyResponse } from '../api/types'
import { GHOST_BURST_HOLD_MS, ghostDurationMs, ghostMultiplierAt } from '../game/ghostFlight'
import { recomputeCrashPoint, sha256Hex, verifyCommitHash } from '../game/provablyFair'
import { useGameStore, type FairnessProof } from '../stores/gameStore'
import { usePlayerStore } from '../stores/playerStore'

function num(v: number | string | null | undefined, fallback = 0): number {
  if (v == null) return fallback
  const n = typeof v === 'number' ? v : Number(v)
  return Number.isFinite(n) ? n : fallback
}

async function proofFromVerify(verify: VerifyResponse, crashK: number): Promise<FairnessProof> {
  const commitOk = await verifyCommitHash(
    verify.serverSeed,
    verify.clientSeed ?? '',
    verify.nonce,
    verify.commitHash,
  )
  const precommitOk = verify.seedPrecommit
    ? (await sha256Hex(verify.serverSeed)).toLowerCase() === verify.seedPrecommit.toLowerCase()
    : null
  let crashOk: boolean | null = null
  if (verify.algorithmVersion === 'crash-v2' && verify.houseEdge != null && verify.maxWinMultiplier != null) {
    const recomputed = await recomputeCrashPoint({
      serverSeed: verify.serverSeed,
      clientSeed: verify.clientSeed ?? '',
      nonce: verify.nonce,
      houseEdge: num(verify.houseEdge),
      maxWin: num(verify.maxWinMultiplier),
      minCrash: num(verify.minCrashPoint, 1),
    })
    crashOk = Math.abs(recomputed - crashK) < 0.00015
  }
  return {
    serverSeed: verify.serverSeed,
    clientSeed: verify.clientSeed ?? '',
    nonce: verify.nonce,
    commitHash: verify.commitHash,
    crashPoint: crashK,
    algorithmVersion: verify.algorithmVersion,
    commitOk,
    precommitOk,
    crashOk,
  }
}

/**
 * After cashout, load the theoretical crash via GET /verify and climb K on the client
 * until that point, then open the result.
 */
export function useGhostSpectate() {
  const phase = useGameStore((s) => s.phase)

  useEffect(() => {
    if (phase !== 'spectating') return

    let cancelled = false
    let raf = 0
    let burstTimer = 0

    const run = async () => {
      const snap = useGameStore.getState()
      const playerId = usePlayerStore.getState().playerId
      const cashK = snap.cashoutMultiplier ?? snap.multiplier
      if (!snap.gameId || !playerId) {
        snap.finishSpectating()
        return
      }

      let crashK = cashK
      try {
        const verify = await getVerify(snap.gameId, playerId)
        if (cancelled) return
        crashK = num(verify.crashPoint, cashK)
        const proof = await proofFromVerify(verify, crashK)
        if (cancelled) return
        useGameStore.getState().applyFairness(proof)
      } catch {
        if (!cancelled) useGameStore.getState().finishSpectating()
        return
      }

      if (cancelled) return
      if (!(crashK > cashK)) {
        useGameStore.getState().finishSpectating()
        return
      }

      const duration = ghostDurationMs(cashK, crashK)
      const t0 = performance.now()
      const frame = (now: number) => {
        if (cancelled) return
        const p = duration <= 0 ? 1 : Math.min(1, (now - t0) / duration)
        useGameStore.getState().setDisplayMultiplier(ghostMultiplierAt(cashK, crashK, p))
        if (p < 1) {
          raf = requestAnimationFrame(frame)
          return
        }
        useGameStore.getState().armGhostBurst()
        burstTimer = window.setTimeout(() => {
          if (!cancelled) useGameStore.getState().finishSpectating()
        }, GHOST_BURST_HOLD_MS)
      }
      raf = requestAnimationFrame(frame)
    }

    void run()
    return () => {
      cancelled = true
      cancelAnimationFrame(raf)
      window.clearTimeout(burstTimer)
    }
  }, [phase])
}
