import { describe, expect, it } from 'vitest'
import {
  ghostDurationMs,
  ghostMultiplierAt,
  shouldKeepFlightVisual,
} from './ghostFlight'

describe('ghostDurationMs', () => {
  it('lands near 2s for cashout 2.50× and crash 7.20×', () => {
    const ms = ghostDurationMs(2.5, 7.2)
    expect(ms).toBeGreaterThan(1800)
    expect(ms).toBeLessThan(2300)
  })

  it('returns 0 when the crash is not above the cashout', () => {
    expect(ghostDurationMs(3, 3)).toBe(0)
    expect(ghostDurationMs(4, 2)).toBe(0)
    expect(ghostDurationMs(0, 5)).toBe(0)
  })

  it('clamps a tiny gap to the minimum and a huge gap to the maximum', () => {
    expect(ghostDurationMs(2, 2.01)).toBe(700)
    expect(ghostDurationMs(1.1, 500)).toBe(4500)
  })
})

describe('ghostMultiplierAt', () => {
  it('starts at cashout K and ends at the crash point', () => {
    expect(ghostMultiplierAt(2.5, 7.2, 0)).toBeCloseTo(2.5, 6)
    expect(ghostMultiplierAt(2.5, 7.2, 1)).toBeCloseTo(7.2, 6)
  })

  it('climbs in log space (geometric midpoint)', () => {
    const mid = ghostMultiplierAt(2.5, 7.2, 0.5)
    expect(mid).toBeCloseTo(Math.sqrt(2.5 * 7.2), 6)
    expect(mid).toBeGreaterThan(2.5)
    expect(mid).toBeLessThan(7.2)
  })

  it('clamps progress outside 0..1', () => {
    expect(ghostMultiplierAt(2, 8, -1)).toBeCloseTo(2, 6)
    expect(ghostMultiplierAt(2, 8, 3)).toBeCloseTo(8, 6)
  })
})

describe('shouldKeepFlightVisual', () => {
  it('keeps the scene alive during flight and spectating', () => {
    expect(shouldKeepFlightVisual('FLYING', 'flight', false)).toBe(true)
    expect(shouldKeepFlightVisual('CASHED_OUT', 'spectating', false)).toBe(true)
  })

  it('stops the climb on a real crash, void, result, or ghost burst', () => {
    expect(shouldKeepFlightVisual('CRASHED', 'result', false)).toBe(false)
    expect(shouldKeepFlightVisual('VOID', 'result', false)).toBe(false)
    expect(shouldKeepFlightVisual('CASHED_OUT', 'result', false)).toBe(false)
    expect(shouldKeepFlightVisual('CASHED_OUT', 'spectating', true)).toBe(false)
  })
})
