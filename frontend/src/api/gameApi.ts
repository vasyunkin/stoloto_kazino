import { apiRequest } from './client'
import type {
  BalanceResponse,
  BetRequest,
  CashoutResponse,
  DepositResponse,
  GameStateResponse,
  HistoryItemResponse,
  LeaderboardEntryResponse,
  StartGameResponse,
} from './types'

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

export function cashout(gameId: string, playerId: string) {
  return apiRequest<CashoutResponse>(`/api/game/cashout/${gameId}`, {
    method: 'POST',
    playerId,
  })
}

export function getHistory(limit = 20) {
  return apiRequest<HistoryItemResponse[]>(`/api/game/history?limit=${limit}`)
}

export function getLeaderboard(limit = 20) {
  return apiRequest<LeaderboardEntryResponse[]>(`/api/players/leaderboard?limit=${limit}`)
}
