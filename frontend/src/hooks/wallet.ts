import { ApiError } from '../api/client'
import { deposit, getBalance } from '../api/gameApi'
import { usePlayerStore } from '../stores/playerStore'

const DEMO_DEPOSIT = 1000

function toNumber(v: number | string): number {
  const n = typeof v === 'number' ? v : Number(v)
  return Number.isFinite(n) ? n : 0
}

function applyWallet(balance: number | string, charges: number | undefined) {
  const { setWallet } = usePlayerStore.getState()
  setWallet(toNumber(balance), typeof charges === 'number' ? charges : 0)
  return toNumber(balance)
}

/**
 * Load balance; if player missing — demo deposit so Hub is playable (F2).
 */
export async function ensureWallet(autoDepositIfMissing = true): Promise<number> {
  const { playerId } = usePlayerStore.getState()
  try {
    const res = await getBalance(playerId)
    return applyWallet(res.balance, res.boosterCharges)
  } catch (err) {
    if (err instanceof ApiError && err.status === 404 && autoDepositIfMissing) {
      const res = await deposit(playerId, DEMO_DEPOSIT)
      return applyWallet(res.newBalance, res.boosterCharges)
    }
    throw err
  }
}

export async function demoDeposit(amount = DEMO_DEPOSIT): Promise<number> {
  const { playerId } = usePlayerStore.getState()
  const res = await deposit(playerId, amount)
  return applyWallet(res.newBalance, res.boosterCharges)
}
