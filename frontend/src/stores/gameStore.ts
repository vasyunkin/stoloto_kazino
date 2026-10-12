import { create } from 'zustand'
import type { BalloonType, BoosterPreference, BoosterStateDto, RoundStatus } from '../api/types'

export type UiPhase = 'hub' | 'prefight' | 'flight' | 'spectating' | 'result'

/** Provably-fair reveal shown after cashout (TD-13). Seed arrives only via GET /verify. */
export interface FairnessProof {
  serverSeed: string
  clientSeed: string
  nonce: number
  commitHash: string
  crashPoint: number
  algorithmVersion: string
  commitOk: boolean
  precommitOk?: boolean | null
  crashOk?: boolean | null
}

export interface GameStore {
  phase: UiPhase
  gameId: string | null
  status: RoundStatus | 'IDLE'
  multiplier: number
  lineIndex: number
  zone: string
  pointsTotal: number
  pointsDelta: number
  betAmount: number
  balloonType: BalloonType
  boosterPreference: BoosterPreference
  booster: BoosterStateDto | null
  winAmount: number | null
  puzzlePieceIndex: number | null
  commitHash: string | null
  startedAt: string | null
  isCashoutPending: boolean
  /** Locked at cashout. Display `multiplier` may keep climbing during spectating. */
  cashoutMultiplier: number | null
  ghostBurst: boolean
  fairness: FairnessProof | null
  setPhase: (phase: UiPhase) => void
  setBalloonType: (t: BalloonType) => void
  setBetAmount: (n: number) => void
  setBoosterPreference: (p: BoosterPreference) => void
  setCashoutPending: (pending: boolean) => void
  setDisplayMultiplier: (k: number) => void
  applyFairness: (proof: FairnessProof) => void
  armGhostBurst: () => void
  finishSpectating: () => void
  applyPublicState: (s: {
    gameId: string
    status: RoundStatus
    multiplier: number | string
    lineIndex: number
    zone: string
    pointsTotal: number
    pointsDelta: number
    booster: BoosterStateDto | null
    winAmount?: number | string | null
    puzzlePieceIndex?: number | null
  }) => void
  /** Apply cashout API result — never store serverSeed (FI7). Enters spectating, not result. */
  applyCashoutResult: (s: {
    multiplierAtCashout: number | string
    winAmount: number | string
    pointsTotal: number
    puzzlePieceIndex: number | null
  }) => void
  beginRound: (gameId: string, commitHash: string, betAmount: number, startedAt: string) => void
  resetToHub: () => void
}

function num(v: number | string | null | undefined, fallback = 0): number {
  if (v == null) return fallback
  const n = typeof v === 'number' ? v : Number(v)
  return Number.isFinite(n) ? n : fallback
}

export const useGameStore = create<GameStore>((set) => ({
  phase: 'hub',
  gameId: null,
  status: 'IDLE',
  multiplier: 1,
  lineIndex: 0,
  zone: 'GREEN',
  pointsTotal: 0,
  pointsDelta: 0,
  betAmount: 12,
  balloonType: 'STANDARD',
  boosterPreference: 'AUTO',
  booster: null,
  winAmount: null,
  puzzlePieceIndex: null,
  commitHash: null,
  startedAt: null,
  isCashoutPending: false,
  cashoutMultiplier: null,
  ghostBurst: false,
  fairness: null,

  setPhase: (phase) => set({ phase }),
  setBalloonType: (balloonType) => set({ balloonType }),
  setBetAmount: (betAmount) => set({ betAmount }),
  setBoosterPreference: (boosterPreference) => set({ boosterPreference }),
  setCashoutPending: (isCashoutPending) => set({ isCashoutPending }),
  setDisplayMultiplier: (multiplier) => set({ multiplier }),
  applyFairness: (fairness) => set({ fairness }),
  armGhostBurst: () => set({ ghostBurst: true }),
  finishSpectating: () => set({ phase: 'result' }),

  beginRound: (gameId, commitHash, betAmount, startedAt) =>
    set({
      phase: 'flight',
      gameId,
      commitHash,
      betAmount,
      startedAt,
      status: 'FLYING',
      multiplier: 1,
      lineIndex: 0,
      pointsTotal: 0,
      pointsDelta: 0,
      winAmount: null,
      puzzlePieceIndex: null,
      booster: null,
      isCashoutPending: false,
      cashoutMultiplier: null,
      ghostBurst: false,
      fairness: null,
    }),

  applyPublicState: (s) =>
    set((state) => {
      if (state.phase === 'spectating') {
        return {
          pointsTotal: s.pointsTotal,
          puzzlePieceIndex: s.puzzlePieceIndex ?? state.puzzlePieceIndex,
          winAmount: s.winAmount == null ? state.winAmount : num(s.winAmount),
        }
      }
      // A cashout tick can arrive before POST /cashout resolves. Do not open Result yet.
      if (s.status === 'CASHED_OUT' && state.phase !== 'result') {
        const paid = num(s.multiplier, state.multiplier)
        if (!state.isCashoutPending) {
          return {
            gameId: s.gameId,
            status: s.status,
            multiplier: paid,
            cashoutMultiplier: paid,
            lineIndex: s.lineIndex,
            zone: s.zone,
            pointsTotal: s.pointsTotal,
            pointsDelta: s.pointsDelta,
            booster: s.booster,
            winAmount: s.winAmount == null ? state.winAmount : num(s.winAmount),
            puzzlePieceIndex: s.puzzlePieceIndex ?? state.puzzlePieceIndex,
            phase: 'spectating',
            isCashoutPending: false,
            ghostBurst: false,
            fairness: null,
          }
        }
        return {
          gameId: s.gameId,
          status: s.status,
          lineIndex: s.lineIndex,
          zone: s.zone,
          pointsTotal: s.pointsTotal,
          pointsDelta: s.pointsDelta,
          booster: s.booster,
          winAmount: s.winAmount == null ? state.winAmount : num(s.winAmount),
          puzzlePieceIndex: s.puzzlePieceIndex ?? state.puzzlePieceIndex,
        }
      }
      return {
        gameId: s.gameId,
        status: s.status,
        multiplier: num(s.multiplier, 1),
        lineIndex: s.lineIndex,
        zone: s.zone,
        pointsTotal: s.pointsTotal,
        pointsDelta: s.pointsDelta,
        booster: s.booster,
        winAmount: s.winAmount == null ? null : num(s.winAmount),
        puzzlePieceIndex: s.puzzlePieceIndex ?? null,
        phase: s.status === 'FLYING' ? 'flight' : 'result',
        isCashoutPending: s.status === 'FLYING' ? state.isCashoutPending : false,
      }
    }),

  applyCashoutResult: (s) =>
    set({
      status: 'CASHED_OUT',
      phase: 'spectating',
      multiplier: num(s.multiplierAtCashout, 1),
      cashoutMultiplier: num(s.multiplierAtCashout, 1),
      winAmount: num(s.winAmount),
      pointsTotal: s.pointsTotal,
      pointsDelta: 0,
      puzzlePieceIndex: s.puzzlePieceIndex,
      isCashoutPending: false,
      ghostBurst: false,
      fairness: null,
    }),

  resetToHub: () =>
    set({
      phase: 'hub',
      gameId: null,
      status: 'IDLE',
      multiplier: 1,
      lineIndex: 0,
      zone: 'GREEN',
      pointsTotal: 0,
      pointsDelta: 0,
      winAmount: null,
      puzzlePieceIndex: null,
      commitHash: null,
      startedAt: null,
      booster: null,
      isCashoutPending: false,
      cashoutMultiplier: null,
      ghostBurst: false,
      fairness: null,
    }),
}))
