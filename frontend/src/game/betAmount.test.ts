import { describe, expect, it } from 'vitest'
import {
  betProblem,
  formatBet,
  maxAffordableBet,
  parseBetDraft,
  type BetLimits,
} from './betAmount'

const standard: BetLimits = { minBetAmount: 12, maxBetAmount: 5000 }

describe('parseBetDraft', () => {
  it('accepts whole amounts and up to two decimals, including a comma', () => {
    expect(parseBetDraft('40')).toBe(40)
    expect(parseBetDraft('12,5')).toBe(12.5)
    expect(parseBetDraft('12.50')).toBe(12.5)
  })

  it('rejects extra precision and junk', () => {
    expect(parseBetDraft('12.555')).toBeNull()
    expect(parseBetDraft('abc')).toBeNull()
    expect(parseBetDraft('')).toBeNull()
  })
})

describe('betProblem', () => {
  it('allows a custom stake inside the theme range and the balance', () => {
    expect(betProblem('77.5', standard, 1000)).toBeNull()
  })

  it('rejects a stake below the theme minimum, above the cap, or above the balance', () => {
    expect(betProblem('11', standard, 1000)).toBe('below-min')
    expect(betProblem('5000.01', standard, 9000)).toBe('above-max')
    expect(betProblem('1000.01', standard, 1000)).toBe('above-balance')
    expect(betProblem('', standard, 1000)).toBe('empty')
    expect(betProblem('12.555', standard, 1000)).toBe('format')
  })
})

describe('maxAffordableBet', () => {
  it('caps at the theme maximum and floors the balance to cents', () => {
    expect(maxAffordableBet(standard, 8000)).toBe(5000)
    expect(maxAffordableBet(standard, 40.559)).toBe(40.55)
    expect(maxAffordableBet(standard, 10)).toBeNull()
  })
})

describe('formatBet', () => {
  it('drops trailing zeros', () => {
    expect(formatBet(12)).toBe('12')
    expect(formatBet(12.5)).toBe('12.5')
    expect(formatBet(12.1)).toBe('12.1')
  })
})