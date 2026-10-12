import { describe, expect, it } from 'vitest'
import { slotsFromPieces } from './albumSlots'

describe('slotsFromPieces', () => {
  it('keeps in-range slots and drops a game id when it is missing', () => {
    expect(
      slotsFromPieces({
        2: { collectedAt: 't', fromGameId: '3fa85f64-5717-4562-b3fc-2c963f66afa6' },
        5: { collectedAt: 't' },
        30: { collectedAt: 't' },
      }),
    ).toEqual([{ slot: 2, gameId: '3fa85f64-5717-4562-b3fc-2c963f66afa6' }, { slot: 5 }])
  })
})