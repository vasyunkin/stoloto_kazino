import { create } from 'zustand'

const STORAGE_KEY = 'balloon.playerId'

function loadPlayerId(): string {
  const existing = localStorage.getItem(STORAGE_KEY)
  if (existing) return existing
  const id = `demo-${crypto.randomUUID().slice(0, 8)}`
  localStorage.setItem(STORAGE_KEY, id)
  return id
}

export interface PlayerStore {
  playerId: string
  balance: number
  boosterCharges: number
  setBalance: (n: number) => void
  setBoosterCharges: (n: number) => void
  setWallet: (balance: number, boosterCharges: number) => void
  setPlayerId: (id: string) => void
}

export const usePlayerStore = create<PlayerStore>((set) => ({
  playerId: loadPlayerId(),
  balance: 0,
  boosterCharges: 0,
  setBalance: (balance) => set({ balance }),
  setBoosterCharges: (boosterCharges) => set({ boosterCharges }),
  setWallet: (balance, boosterCharges) => set({ balance, boosterCharges }),
  setPlayerId: (playerId) => {
    localStorage.setItem(STORAGE_KEY, playerId)
    set({ playerId })
  },
}))
