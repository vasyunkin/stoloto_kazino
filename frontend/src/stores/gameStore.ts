import { create } from 'zustand'
import type { BalloonType, BoosterPreference, BoosterStateDto, RoundStatus } from '../api/types'

export type UiPhase = 'hub' | 'prefight' | 'flight' | 'result'

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
  setPhase: (phase: UiPhase) => void
  setBalloonType: (t: BalloonType) => void
  setBetAmount: (n: number) => void
  setBoosterPreference: (p: BoosterPreference) => void
  setCashoutPending: (pending: boolean) => void
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
  /** Apply cashout API result — never store serverSeed (FI7). */
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
  betAmount: 50,
  balloonType: 'STANDARD',
  boosterPreference: 'AUTO',
  booster: null,
  winAmount: null,
  puzzlePieceIndex: null,
  commitHash: null,
  startedAt: null,
  isCashoutPending: false,

  setPhase: (phase) => set({ phase }),
  setBalloonType: (balloonType) => set({ balloonType }),
  setBetAmount: (betAmount) => set({ betAmount }),
  setBoosterPreference: (boosterPreference) => set({ boosterPreference }),
  setCashoutPending: (isCashoutPending) => set({ isCashoutPending }),

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
    }),

  applyPublicState: (s) =>
    set((state) => ({
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
    })),

  applyCashoutResult: (s) =>
    set({
      status: 'CASHED_OUT',
      phase: 'result',
      multiplier: num(s.multiplierAtCashout, 1),
      winAmount: num(s.winAmount),
      pointsTotal: s.pointsTotal,
      pointsDelta: 0,
      puzzlePieceIndex: s.puzzlePieceIndex,
      isCashoutPending: false,
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
    }),
}))
