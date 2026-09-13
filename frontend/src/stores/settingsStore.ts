import { create } from 'zustand'

const MUTE_KEY = 'balloon.sfxMuted'

function loadMuted(): boolean {
  try {
    return localStorage.getItem(MUTE_KEY) === '1'
  } catch {
    return false
  }
}

interface SettingsStore {
  sfxMuted: boolean
  setSfxMuted: (muted: boolean) => void
  toggleSfxMuted: () => void
}

export const useSettingsStore = create<SettingsStore>((set, get) => ({
  sfxMuted: loadMuted(),
  setSfxMuted: (sfxMuted) => {
    try {
      localStorage.setItem(MUTE_KEY, sfxMuted ? '1' : '0')
    } catch {
      /* ignore */
    }
    set({ sfxMuted })
  },
  toggleSfxMuted: () => {
    get().setSfxMuted(!get().sfxMuted)
  },
}))
