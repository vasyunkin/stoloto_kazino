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
 * Load wallet. Backend GET /balance creates the player + welcome credit if missing.
 * Optional deposit only when still empty and autoDepositIfMissing (legacy / top-up).
 */
export async function ensureWallet(autoDepositIfMissing = true): Promise<number> {
  const { playerId } = usePlayerStore.getState()
  const res = await getBalance(playerId)
  const bal = applyWallet(res.balance, res.boosterCharges)
  if (bal > 0 || !autoDepositIfMissing) {
    return bal
  }
  // Empty wallet (welcome=0 or spent): try demo deposit; ignore 403 (deposit disabled).
  try {
    const dep = await deposit(playerId, DEMO_DEPOSIT)
    return applyWallet(dep.newBalance, dep.boosterCharges)
  } catch (err) {
    if (err instanceof ApiError && (err.status === 403 || err.code === 'DEPOSIT_NOT_ALLOWED')) {
      return bal
    }
    throw err
  }
}

export async function demoDeposit(amount = DEMO_DEPOSIT): Promise<number> {
  const { playerId } = usePlayerStore.getState()
  const res = await deposit(playerId, amount)
  return applyWallet(res.newBalance, res.boosterCharges)
}
