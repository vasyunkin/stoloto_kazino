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
}

export interface BoosterStateDto {
  spawned: boolean
  tier: number
  name: string
  multiplier: number | string
  triggerLine: number
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

export interface CashoutResponse {
  gameId: string
  multiplierAtCashout: number | string
  winAmount: number | string
  pointsTotal: number
  serverSeed: string
  puzzlePieceIndex: number | null
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

export interface BetRequest {
  playerId: string
  betAmount: number
  balloonType: BalloonType
  boosterPreference?: BoosterPreference
  clientSeed?: string
}
