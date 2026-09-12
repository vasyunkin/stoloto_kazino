import { ApiError } from '../api/client'
import { deposit, getBalance } from '../api/gameApi'
import { usePlayerStore } from '../stores/playerStore'

const DEMO_DEPOSIT = 1000

function toNumber(v: number | string): number {
  const n = typeof v === 'number' ? v : Number(v)
  return Number.isFinite(n) ? n : 0
}

/**
 * Load balance; if player missing — demo deposit so Hub is playable (F2).
 */
export async function ensureWallet(autoDepositIfMissing = true): Promise<number> {
  const { playerId, setBalance } = usePlayerStore.getState()
  try {
    const res = await getBalance(playerId)
    const bal = toNumber(res.balance)
    setBalance(bal)
    return bal
  } catch (err) {
    if (err instanceof ApiError && err.status === 404 && autoDepositIfMissing) {
      const res = await deposit(playerId, DEMO_DEPOSIT)
      const bal = toNumber(res.newBalance)
      setBalance(bal)
      return bal
    }
    throw err
  }
}

export async function demoDeposit(amount = DEMO_DEPOSIT): Promise<number> {
  const { playerId, setBalance } = usePlayerStore.getState()
  const res = await deposit(playerId, amount)
  const bal = toNumber(res.newBalance)
  setBalance(bal)
  return bal
}
