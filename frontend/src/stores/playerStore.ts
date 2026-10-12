import { create } from 'zustand'
import { readPlayerSession } from '../auth/playerSession'

const STORAGE_KEY = 'balloon.playerId'

export function loadGuestId(): string {
  const existing = localStorage.getItem(STORAGE_KEY)
  if (existing) return existing
  const id = `demo-${crypto.randomUUID().slice(0, 8)}`
  localStorage.setItem(STORAGE_KEY, id)
  return id
}

export interface PlayerStore {
  playerId: string
  email: string | null
  balance: number
  boosterCharges: number
  setBalance: (n: number) => void
  setBoosterCharges: (n: number) => void
  setWallet: (balance: number, boosterCharges: number) => void
  setPlayerId: (id: string) => void
  adoptAccount: (playerId: string, email: string) => void
  restoreGuest: () => void
}

const openingSession = readPlayerSession()

export const usePlayerStore = create<PlayerStore>((set) => ({
  playerId: openingSession?.playerId ?? loadGuestId(),
  email: openingSession?.email ?? null,
  balance: 0,
  boosterCharges: 0,
  setBalance: (balance) => set({ balance }),
  setBoosterCharges: (boosterCharges) => set({ boosterCharges }),
  setWallet: (balance, boosterCharges) => set({ balance, boosterCharges }),
  setPlayerId: (playerId) => {
    localStorage.setItem(STORAGE_KEY, playerId)
    set({ playerId })
  },
  adoptAccount: (playerId, email) => set({ playerId, email }),
  restoreGuest: () => set({ playerId: loadGuestId(), email: null }),
}))
