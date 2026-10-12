import { apiRequest } from './client'
import type {
  BalanceResponse,
  BetRequest,
  CashoutResponse,
  DepositResponse,
  GameStateResponse,
  HistoryItemResponse,
  OwnRoundResponse,
  LeaderboardEntryResponse,
  StartGameResponse,
  VerifyResponse,
  BetLimitsResponse,
  FairnessResponse,
} from './types'

export function getBetLimits() {
  return apiRequest<BetLimitsResponse>('/api/game/limits')
}

export function getFairness(playerId: string) {
  return apiRequest<FairnessResponse>('/api/game/fairness', { playerId })
}

export function getBalance(playerId: string) {
  return apiRequest<BalanceResponse>(`/api/players/${encodeURIComponent(playerId)}/balance`)
}

export function deposit(playerId: string, amount: number) {
  return apiRequest<DepositResponse>(`/api/players/${encodeURIComponent(playerId)}/deposit`, {
    method: 'POST',
    body: { amount },
  })
}

export function startGame(playerId: string, body: BetRequest) {
  return apiRequest<StartGameResponse>('/api/game/start', {
    method: 'POST',
    playerId,
    body,
  })
}

export function getState(gameId: string, playerId: string) {
  return apiRequest<GameStateResponse>(`/api/game/state/${gameId}`, { playerId })
}

export function getVerify(gameId: string, playerId: string) {
  return apiRequest<VerifyResponse>(`/api/game/verify/${gameId}`, { playerId })
}

export function cashout(gameId: string, playerId: string) {
  return apiRequest<CashoutResponse>(`/api/game/cashout/${gameId}`, {
    method: 'POST',
    playerId,
  })
}

export function getHistory(limit = 20) {
  return apiRequest<HistoryItemResponse[]>(`/api/game/history?limit=${limit}`)
}

export function getMyHistory(playerId: string, limit = 20) {
  return apiRequest<OwnRoundResponse[]>(`/api/game/me/history?limit=${limit}`, { playerId })
}

export function getLeaderboard(limit = 20) {
  return apiRequest<LeaderboardEntryResponse[]>(`/api/players/leaderboard?limit=${limit}`)
}
