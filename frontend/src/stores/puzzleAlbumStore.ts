import { create } from 'zustand'

/** Spec 5 §2.1 — client puzzle album (24 slots). */
export const PUZZLE_SLOT_COUNT = 24

export interface AlbumPiece {
  collectedAt: string
  fromGameId?: string
}

type AlbumRecord = Record<string, AlbumPiece>

function storageKey(playerId: string): string {
  return `balloon.puzzleAlbum.${playerId}`
}

function readAlbum(playerId: string): AlbumRecord {
  try {
    const raw = localStorage.getItem(storageKey(playerId))
    if (!raw) return {}
    const parsed = JSON.parse(raw) as unknown
    if (!parsed || typeof parsed !== 'object') return {}
    return parsed as AlbumRecord
  } catch {
    return {}
  }
}

function writeAlbum(playerId: string, album: AlbumRecord): void {
  try {
    localStorage.setItem(storageKey(playerId), JSON.stringify(album))
  } catch {
    /* quota / private mode */
  }
}

function toPiecesMap(record: AlbumRecord): Record<number, AlbumPiece> {
  const out: Record<number, AlbumPiece> = {}
  for (const [k, v] of Object.entries(record)) {
    const idx = Number(k)
    if (!Number.isInteger(idx) || idx < 0 || idx >= PUZZLE_SLOT_COUNT) continue
    if (!v || typeof v.collectedAt !== 'string') continue
    out[idx] = { collectedAt: v.collectedAt, fromGameId: v.fromGameId }
  }
  return out
}

interface PuzzleAlbumStore {
  playerId: string | null
  pieces: Record<number, AlbumPiece>
  loadForPlayer: (playerId: string) => void
  /** Idempotent: first collect wins for a slot. Returns true if newly added. */
  collectPiece: (
    playerId: string,
    slotIndex: number,
    fromGameId?: string | null,
  ) => boolean
  collectedCount: () => number
}

export const usePuzzleAlbumStore = create<PuzzleAlbumStore>((set, get) => ({
  playerId: null,
  pieces: {},
  loadForPlayer: (playerId) => {
    set({ playerId, pieces: toPiecesMap(readAlbum(playerId)) })
  },
  collectPiece: (playerId, slotIndex, fromGameId) => {
    if (!Number.isInteger(slotIndex) || slotIndex < 0 || slotIndex >= PUZZLE_SLOT_COUNT) {
      return false
    }
    const current =
      get().playerId === playerId ? get().pieces : toPiecesMap(readAlbum(playerId))
    if (current[slotIndex]) {
      if (get().playerId !== playerId) set({ playerId, pieces: current })
      return false
    }
    const next: Record<number, AlbumPiece> = {
      ...current,
      [slotIndex]: {
        collectedAt: new Date().toISOString(),
        ...(fromGameId ? { fromGameId } : {}),
      },
    }
    const record: AlbumRecord = {}
    for (const [idx, piece] of Object.entries(next)) {
      record[idx] = piece
    }
    writeAlbum(playerId, record)
    set({ playerId, pieces: next })
    return true
  },
  collectedCount: () => Object.keys(get().pieces).length,
}))
