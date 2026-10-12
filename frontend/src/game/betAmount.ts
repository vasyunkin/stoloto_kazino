export interface BetLimits {
  minBetAmount: number
  maxBetAmount: number
}

export const DEFAULT_BET_LIMITS: Record<'STANDARD' | 'LUCKY', BetLimits> = {
  STANDARD: { minBetAmount: 12, maxBetAmount: 5000 },
  LUCKY: { minBetAmount: 25, maxBetAmount: 10000 },
}

const MONEY = /^\d+(?:[.,]\d{1,2})?$/

export type BetProblem = 'empty' | 'format' | 'below-min' | 'above-max' | 'above-balance'

export function formatBet(amount: number): string {
  const cents = Math.round(amount * 100)
  if (cents % 100 === 0) return String(cents / 100)
  if (cents % 10 === 0) return (cents / 100).toFixed(1)
  return (cents / 100).toFixed(2)
}

export function parseBetDraft(raw: string): number | null {
  const trimmed = raw.trim().replace(',', '.')
  if (!MONEY.test(trimmed)) return null
  const value = Number(trimmed)
  return Number.isFinite(value) ? value : null
}

function cents(amount: number): number {
  return Math.round(amount * 100)
}

/** Largest stake the wallet can cover inside the theme cap, in 0.01 steps. */
export function maxAffordableBet(limits: BetLimits, balance: number): number | null {
  const capCents = Math.min(cents(limits.maxBetAmount), Math.floor(balance * 100 + 1e-6))
  if (capCents < cents(limits.minBetAmount)) return null
  return capCents / 100
}

export function betProblem(raw: string, limits: BetLimits, balance: number): BetProblem | null {
  const trimmed = raw.trim()
  if (!trimmed) return 'empty'
  const amount = parseBetDraft(trimmed)
  if (amount == null) return 'format'
  const stake = cents(amount)
  if (stake < cents(limits.minBetAmount)) return 'below-min'
  if (stake > cents(limits.maxBetAmount)) return 'above-max'
  if (stake > Math.floor(balance * 100 + 1e-6)) return 'above-balance'
  return null
}

export function betProblemMessage(problem: BetProblem, limits: BetLimits): string {
  switch (problem) {
    case 'empty':
      return 'Введите сумму ставки'
    case 'format':
      return 'Число, не больше двух знаков после запятой'
    case 'below-min':
      return `Минимум ${formatBet(limits.minBetAmount)} ◎`
    case 'above-max':
      return `Максимум ${formatBet(limits.maxBetAmount)} ◎`
    case 'above-balance':
      return 'Недостаточно средств на балансе'
  }
}
