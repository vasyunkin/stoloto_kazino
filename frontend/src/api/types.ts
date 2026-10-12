/** Shared API types — mirror backend DTOs (Spec 3). No crashPoint/serverSeed on flying payloads. */

export type RoundStatus = 'FLYING' | 'CRASHED' | 'CASHED_OUT' | 'VOID'

export type BalloonType = 'STANDARD' | 'LUCKY'

export type BoosterPreference = 'AUTO' | 'NONE'

export interface ErrorResponse {
  code: string
  message: string
  timestamp?: string
  details?: unknown
}

export interface BalanceResponse {
  externalId: string
  balance: number | string
  boosterCharges: number
}

export interface DepositResponse {
  externalId: string
  depositedAmount: number | string
  newBalance: number | string
  boosterCharges: number
}

export interface StartGameResponse {
  gameId: string
  commitHash: string
  serverSeedHash: string
  startedAt: string
  nextServerSeedHash: string
  nonce: number
}

export interface FairnessResponse {
  serverSeedHash: string
  nextNonce: number
  clientSeed: string
}

export interface BoosterStateDto {
  spawned: boolean
  tier?: number | null
  name?: string | null
  multiplier?: number | string | null
  triggerLine?: number | null
  activated: boolean
}

export interface PublicGameState {
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
}

export interface GameStateResponse extends PublicGameState {
  crashPoint?: number | string | null
  serverSeed?: string | null
}

export interface VerifyResponse {
  gameId: string
  serverSeed: string
  clientSeed: string
  nonce: number
  commitHash: string
  crashPoint: number | string
  algorithmVersion: string
  boostTier: number | null
  boostTriggerLine: number | null
  boostMultiplier: number | string | null
  seedPrecommit?: string | null
  houseEdge?: number | string | null
  maxWinMultiplier?: number | string | null
  minCrashPoint?: number | string | null
  instantCrashRate?: number | string | null
}

export interface CashoutResponse {
  gameId: string
  multiplierAtCashout: number | string
  winAmount: number | string
  pointsTotal: number
  serverSeed: string
  puzzlePieceIndex: number | null
}

export interface OwnRoundResponse {
  gameId: string
  status: RoundStatus
  betAmount: number | string
  winAmount: number | string | null
  crashPoint: number | string | null
  cashoutMultiplier: number | string | null
  commitHash: string
  algorithmVersion: string
  balloonType: string
  startedAt: string
  endedAt: string | null
}

export interface HistoryItemResponse {
  gameId: string
  status: RoundStatus
  betAmount: number | string
  winAmount: number | string | null
  pointsEarned: number
  balloonType: string
  startedAt: string
  endedAt: string | null
  crashPoint?: number | string | null
}

export interface LeaderboardEntryResponse {
  rank: number
  externalId: string
  totalPoints: number
}

export interface WsGameEvent {
  type: 'tick' | 'crash' | 'cashout' | 'void' | string
  state: PublicGameState
}

export interface ThemeBetLimit {
  minBetAmount: number | string
  maxBetAmount: number | string
}

export interface BetLimitsResponse {
  standard: ThemeBetLimit
  lucky: ThemeBetLimit
}

export interface BetRequest {
  playerId: string
  betAmount: number
  balloonType: BalloonType
  boosterPreference?: BoosterPreference
  clientSeed?: string
  autoCashoutMultiplier?: number
}
