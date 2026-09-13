import { useSettingsStore } from '../stores/settingsStore'

/** Tiny mechanical click via Web Audio (no asset file). Spec 5 §5.1 / V1. */
let ctx: AudioContext | null = null

function getCtx(): AudioContext | null {
  if (typeof window === 'undefined') return null
  const AC = window.AudioContext || (window as unknown as { webkitAudioContext: typeof AudioContext }).webkitAudioContext
  if (!AC) return null
  if (!ctx) ctx = new AC()
  return ctx
}

export function playClickSfx(): void {
  if (useSettingsStore.getState().sfxMuted) return
  const audio = getCtx()
  if (!audio) return

  void audio.resume().then(() => {
    const t0 = audio.currentTime
    const osc = audio.createOscillator()
    const gain = audio.createGain()
    osc.type = 'square'
    osc.frequency.setValueAtTime(620, t0)
    osc.frequency.exponentialRampToValueAtTime(180, t0 + 0.06)
    gain.gain.setValueAtTime(0.0001, t0)
    gain.gain.exponentialRampToValueAtTime(0.12, t0 + 0.008)
    gain.gain.exponentialRampToValueAtTime(0.0001, t0 + 0.07)
    osc.connect(gain)
    gain.connect(audio.destination)
    osc.start(t0)
    osc.stop(t0 + 0.08)
  }).catch(() => {
    /* autoplay / closed context */
  })
}
