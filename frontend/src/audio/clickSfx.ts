import { useSettingsStore } from '../stores/settingsStore'

/** Shared Web Audio helpers — Spec 5 V1 click + V5 game SFX. */

let ctx: AudioContext | null = null

function getCtx(): AudioContext | null {
  if (typeof window === 'undefined') return null
  const AC =
    window.AudioContext ||
    (window as unknown as { webkitAudioContext: typeof AudioContext }).webkitAudioContext
  if (!AC) return null
  if (!ctx) ctx = new AC()
  return ctx
}

function withAudio(run: (audio: AudioContext, t0: number) => void): void {
  if (useSettingsStore.getState().sfxMuted) return
  const audio = getCtx()
  if (!audio) return
  void audio
    .resume()
    .then(() => {
      run(audio, audio.currentTime)
    })
    .catch(() => {
      /* autoplay / closed */
    })
}

function tone(
  audio: AudioContext,
  t0: number,
  {
    type = 'square',
    freq,
    freqEnd,
    dur,
    gain = 0.1,
    delay = 0,
  }: {
    type?: OscillatorType
    freq: number
    freqEnd?: number
    dur: number
    gain?: number
    delay?: number
  },
): void {
  const start = t0 + delay
  const osc = audio.createOscillator()
  const g = audio.createGain()
  osc.type = type
  osc.frequency.setValueAtTime(freq, start)
  if (freqEnd != null) {
    osc.frequency.exponentialRampToValueAtTime(Math.max(freqEnd, 1), start + dur)
  }
  g.gain.setValueAtTime(0.0001, start)
  g.gain.exponentialRampToValueAtTime(gain, start + 0.01)
  g.gain.exponentialRampToValueAtTime(0.0001, start + dur)
  osc.connect(g)
  g.connect(audio.destination)
  osc.start(start)
  osc.stop(start + dur + 0.02)
}

/** UI mechanical click (V1). */
export function playClickSfx(): void {
  withAudio((audio, t0) => {
    tone(audio, t0, { type: 'square', freq: 620, freqEnd: 180, dur: 0.07, gain: 0.12 })
  })
}

/** Burner / steam burst on booster activation (V5). */
export function playBoosterSfx(): void {
  withAudio((audio, t0) => {
    tone(audio, t0, { type: 'sawtooth', freq: 140, freqEnd: 90, dur: 0.22, gain: 0.08 })
    tone(audio, t0, { type: 'triangle', freq: 320, freqEnd: 180, dur: 0.18, gain: 0.06, delay: 0.04 })
    tone(audio, t0, { type: 'square', freq: 90, freqEnd: 50, dur: 0.28, gain: 0.05, delay: 0.02 })
  })
}

/** Soft brass chime on successful cashout (V5). */
export function playCashoutSfx(): void {
  withAudio((audio, t0) => {
    tone(audio, t0, { type: 'triangle', freq: 520, freqEnd: 780, dur: 0.16, gain: 0.09 })
    tone(audio, t0, { type: 'triangle', freq: 780, freqEnd: 1040, dur: 0.2, gain: 0.07, delay: 0.1 })
  })
}

/** Fabric tear / pop on crash (V5). */
export function playCrashSfx(): void {
  withAudio((audio, t0) => {
    tone(audio, t0, { type: 'sawtooth', freq: 280, freqEnd: 40, dur: 0.28, gain: 0.11 })
    tone(audio, t0, { type: 'square', freq: 90, freqEnd: 35, dur: 0.35, gain: 0.07, delay: 0.05 })
  })
}
