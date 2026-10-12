import { beforeEach, describe, expect, it } from 'vitest'
import { useGameStore } from './gameStore'

describe('cashout spectating', () => {
  beforeEach(() => {
    useGameStore.getState().resetToHub()
    useGameStore.getState().beginRound('game-1', 'abc123', 12, '2026-10-01T00:00:00Z')
  })

  it('does not jump to the result when the player cashes out', () => {
    useGameStore.getState().applyCashoutResult({
      multiplierAtCashout: 2.5,
      winAmount: 30,
      pointsTotal: 40,
      puzzlePieceIndex: 3,
    })
    const s = useGameStore.getState()
    expect(s.phase).toBe('spectating')
    expect(s.status).toBe('CASHED_OUT')
    expect(s.cashoutMultiplier).toBe(2.5)
    expect(s.winAmount).toBe(30)
    expect(s.ghostBurst).toBe(false)
  })

  it('ignores a late public cashout tick while spectating', () => {
    useGameStore.getState().applyCashoutResult({
      multiplierAtCashout: 2.5,
      winAmount: 30,
      pointsTotal: 40,
      puzzlePieceIndex: 3,
    })
    useGameStore.getState().setDisplayMultiplier(4.2)
    useGameStore.getState().applyPublicState({
      gameId: 'game-1',
      status: 'CASHED_OUT',
      multiplier: 2.5,
      lineIndex: 9,
      zone: 'RED',
      pointsTotal: 40,
      pointsDelta: 0,
      booster: null,
      winAmount: 30,
      puzzlePieceIndex: 3,
    })
    const s = useGameStore.getState()
    expect(s.phase).toBe('spectating')
    expect(s.multiplier).toBe(4.2)
  })

  it('opens the result only after the ghost climb finishes', () => {
    useGameStore.getState().applyCashoutResult({
      multiplierAtCashout: 2.5,
      winAmount: 30,
      pointsTotal: 40,
      puzzlePieceIndex: null,
    })
    useGameStore.getState().applyFairness({
      serverSeed: 'aa',
      clientSeed: '',
      nonce: 1,
      commitHash: 'bb',
      crashPoint: 7.2,
      algorithmVersion: 'crash-v1',
      commitOk: true,
    })
    useGameStore.getState().armGhostBurst()
    expect(useGameStore.getState().ghostBurst).toBe(true)
    useGameStore.getState().finishSpectating()
    const s = useGameStore.getState()
    expect(s.phase).toBe('result')
    expect(s.fairness?.crashPoint).toBe(7.2)
    expect(s.cashoutMultiplier).toBe(2.5)
  })

  it('starts the ghost climb when the server cashes out at the target', () => {
    useGameStore.getState().applyPublicState({
      gameId: 'game-1',
      status: 'CASHED_OUT',
      multiplier: 1.2,
      lineIndex: 3,
      zone: 'GREEN',
      pointsTotal: 12,
      pointsDelta: 4,
      booster: null,
      winAmount: 14.4,
    })
    const s = useGameStore.getState()
    expect(s.phase).toBe('spectating')
    expect(s.cashoutMultiplier).toBe(1.2)
    expect(s.winAmount).toBe(14.4)
  })

  it('keeps the flight open when a cashout tick arrives before the cashout response', () => {
    useGameStore.getState().setCashoutPending(true)
    useGameStore.getState().applyPublicState({
      gameId: 'game-1',
      status: 'CASHED_OUT',
      multiplier: 2.5,
      lineIndex: 8,
      zone: 'RED',
      pointsTotal: 20,
      pointsDelta: 0,
      booster: null,
      winAmount: 30,
    })
    const s = useGameStore.getState()
    expect(s.phase).toBe('flight')
    expect(s.status).toBe('CASHED_OUT')
    expect(s.multiplier).toBe(1)
  })

  it('still sends a real crash straight to the result', () => {
    useGameStore.getState().applyPublicState({
      gameId: 'game-1',
      status: 'CRASHED',
      multiplier: 1.4,
      lineIndex: 4,
      zone: 'CRASHED',
      pointsTotal: 10,
      pointsDelta: 0,
      booster: null,
    })
    expect(useGameStore.getState().phase).toBe('result')
    expect(useGameStore.getState().status).toBe('CRASHED')
  })
})
