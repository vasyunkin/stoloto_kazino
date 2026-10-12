import { describe, expect, it } from 'vitest'
import {
  applyDepositDisabled,
  applyGentleHouseEdge,
  buildPatch,
  cloneConfig,
  diffConfig,
  fieldPathsFromValidationMessage,
  formatChange,
  validateDraft,
  type ConfigDoc,
} from './configModel'

const sample = (): ConfigDoc =>
  cloneConfig({
    greenLevels: 9,
    math: { houseEdge: 0.08, growthRate: 0.0433, minCrashPoint: 1, minCashoutMultiplier: 1.01, instantCrashRate: 0.08 },
    admin: {
      apiKey: 'super-secret',
      allowDeposit: true,
      demoMode: true,
      acceptingBets: true,
      minBetAmount: 10,
      maxBetAmount: 1000,
      maxWinMultiplier: 100,
      welcomeBalance: 1000,
      minWinAmount: 50,
    },
    scoring: { redZoneMultiplier: 1 },
    boosters: {
      spawnProbability: 0.45,
      lineBiasPower: 2,
      tiers: [{ tier: 1, name: 'Flame', multiplier: 1.5, probabilityWeight: 50 }],
    },
    themes: {
      standard: { minBetAmount: 12, maxBetAmount: 500, growthRate: 0.03, maxWinMultiplier: 50, greenLevels: 9, redLevels: 6 },
      lucky: { minBetAmount: 25, maxBetAmount: 800, growthRate: 0.05, maxWinMultiplier: 100, greenLevels: 9, redLevels: 12 },
    },
    pointsPerLine: 10,
    ascentSpeedLinesPerSec: 1.5,
    redLevels: 12,
    provablyFair: { devFixedServerSeed: 'abcd', hashAlgorithm: 'SHA-256' },
  })

describe('cloneConfig', () => {
  it('drops secrets so the form never shows them', () => {
    const doc = sample()
    expect(JSON.stringify(doc)).not.toContain('super-secret')
    expect(JSON.stringify(doc)).not.toContain('devFixedServerSeed')
    expect(doc.admin).toMatchObject({ allowDeposit: true })
  })
})

describe('buildPatch', () => {
  it('sends only house edge and deposit when those two change', () => {
    const before = sample()
    let after = applyGentleHouseEdge(before)
    after = applyDepositDisabled(after)
    expect(buildPatch(before, after)).toEqual({
      math: { houseEdge: 0.03 },
      admin: { allowDeposit: false },
    })
  })

  it('replaces the whole tier list when one tier name changes', () => {
    const before = sample()
    const after = structuredClone(before)
    ;(after.boosters as ConfigDoc).tiers = [{ tier: 1, name: 'Steam', multiplier: 1.5, probabilityWeight: 50 }]
    expect(buildPatch(before, after)).toEqual({
      boosters: {
        tiers: [{ tier: 1, name: 'Steam', multiplier: 1.5, probabilityWeight: 50 }],
      },
    })
  })

  it('returns null when nothing changed', () => {
    const doc = sample()
    expect(buildPatch(doc, structuredClone(doc))).toBeNull()
  })
})

describe('validateDraft', () => {
  it('rejects a house edge above 0.5 and a reversed bet range', () => {
    const doc = sample()
    ;(doc.math as ConfigDoc).houseEdge = 0.9
    ;(doc.admin as ConfigDoc).minBetAmount = 500
    ;(doc.admin as ConfigDoc).maxBetAmount = 10
    const errors = validateDraft(doc)
    expect(errors['math.houseEdge']).toMatch(/0\.5/)
    expect(errors['admin.minBetAmount']).toBeTruthy()
  })

  it('accepts the sample document', () => {
    expect(validateDraft(sample())).toEqual({})
  })
})

describe('fieldPathsFromValidationMessage', () => {
  it('maps server bean paths onto form fields', () => {
    const paths = fieldPathsFromValidationMessage(
      'math.houseEdge must be less than or equal to 0.5; boosters.tiers[0].multiplier must be greater than 0',
    )
    expect(paths).toContain('math.houseEdge')
    expect(paths).toContain('boosters.tiers.0.multiplier')
  })

  it('maps bare bet-range failures onto both bet fields', () => {
    expect(fieldPathsFromValidationMessage('minBetAmount must be <= maxBetAmount')).toEqual([
      'admin.minBetAmount',
      'admin.maxBetAmount',
    ])
  })
})

describe('diffConfig', () => {
  it('formats a single leaf change', () => {
    const before = sample()
    const after = applyDepositDisabled(before)
    const changes = diffConfig(before, after)
    expect(changes).toEqual([{ path: 'admin.allowDeposit', before: true, after: false }])
    expect(formatChange(changes[0])).toBe('admin.allowDeposit: true → false')
  })
})
