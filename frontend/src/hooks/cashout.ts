import { ApiError } from '../api/client'
import { cashout, getState } from '../api/gameApi'
import { ensureWallet } from './wallet'
import { useGameStore } from '../stores/gameStore'
import { usePlayerStore } from '../stores/playerStore'

/**
 * Cashout with FI4 pending guard. Does not persist serverSeed from response (FI7).
 */
export async function requestCashout(): Promise<{ ok: true } | { ok: false; message: string }> {
  const game = useGameStore.getState()
  const playerId = usePlayerStore.getState().playerId

  if (!game.gameId || game.status !== 'FLYING' || game.isCashoutPending) {
    return { ok: false, message: 'Cashout недоступен' }
  }

  game.setCashoutPending(true)
  try {
    const res = await cashout(game.gameId, playerId)
    useGameStore.getState().applyCashoutResult({
      multiplierAtCashout: res.multiplierAtCashout,
      winAmount: res.winAmount,
      pointsTotal: res.pointsTotal,
      puzzlePieceIndex: res.puzzlePieceIndex,
    })
    await ensureWallet(false)
    return { ok: true }
  } catch (err) {
    if (err instanceof ApiError) {
      // Race: already crashed — refresh public state (no secrets while we only get terminal via state).
      if (
        err.code === 'ALREADY_CRASHED' ||
        err.code === 'ALREADY_CASHED_OUT' ||
        err.status === 409
      ) {
        try {
          const state = await getState(game.gameId, playerId)
          useGameStore.getState().applyPublicState(state)
          await ensureWallet(false)
        } catch {
          /* ignore secondary */
        }
      }
      return { ok: false, message: `${err.code}: ${err.message}` }
    }
    return { ok: false, message: 'Ошибка cashout' }
  } finally {
    useGameStore.getState().setCashoutPending(false)
  }
}
