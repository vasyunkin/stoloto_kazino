import { PUZZLE_SLOT_COUNT, type AlbumPiece } from '../stores/puzzleAlbumStore'

const GAME_ID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i

export interface AlbumSlotInput {
  slot: number
  gameId?: string
}

export function slotsFromPieces(pieces: Record<number, AlbumPiece>): AlbumSlotInput[] {
  return Object.entries(pieces)
    .map(([key, piece]) => ({
      slot: Number(key),
      gameId: piece?.fromGameId,
    }))
    .filter((slot) => Number.isInteger(slot.slot) && slot.slot >= 0 && slot.slot < PUZZLE_SLOT_COUNT)
    .map((slot) => (slot.gameId && GAME_ID.test(slot.gameId) ? slot : { slot: slot.slot }))
}
