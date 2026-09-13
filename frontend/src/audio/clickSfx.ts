import { useSettingsStore } from '../stores/settingsStore'

/**
 * Victorian steampunk UI / game SFX (Web Audio noise + filters).
 * Mute: settingsStore.sfxMuted
 */

let ctx: AudioContext | null = null
let noiseBuf: AudioBuffer | null = null

function getCtx(): AudioContext | null {
  if (typeof window === 'undefined') return null
  const AC =
    window.AudioContext ||
    (window as unknown as { webkitAudioContext: typeof AudioContext }).webkitAudioContext
  if (!AC) return null
  if (!ctx) ctx = new AC()
  return ctx
}

function noise(audio: AudioContext): AudioBuffer {
  if (noiseBuf && noiseBuf.sampleRate === audio.sampleRate) return noiseBuf
  const len = Math.floor(audio.sampleRate * 1.2)
  const buf = audio.createBuffer(1, len, audio.sampleRate)
  const data = buf.getChannelData(0)
  for (let i = 0; i < len; i++) data[i] = Math.random() * 2 - 1
  noiseBuf = buf
  return buf
}

function withAudio(run: (audio: AudioContext, t0: number) => void): void {
  if (useSettingsStore.getState().sfxMuted) return
  const audio = getCtx()
  if (!audio) return
  void audio
    .resume()
    .then(() => run(audio, audio.currentTime))
    .catch(() => {
      /* autoplay / closed */
    })
}

/** Skip hover SFX on touch / coarse pointers. */
function allowHoverSfx(): boolean {
  if (typeof window === 'undefined') return false
  return window.matchMedia('(hover: hover) and (pointer: fine)').matches
}

function envGain(
  audio: AudioContext,
  t0: number,
  {
    attack = 0.004,
    hold = 0,
    decay,
    peak = 0.2,
    delay = 0,
  }: { attack?: number; hold?: number; decay: number; peak?: number; delay?: number },
): GainNode {
  const g = audio.createGain()
  const start = t0 + delay
  g.gain.setValueAtTime(0.0001, start)
  g.gain.exponentialRampToValueAtTime(Math.max(peak, 0.0002), start + attack)
  if (hold > 0) g.gain.setValueAtTime(Math.max(peak, 0.0002), start + attack + hold)
  g.gain.exponentialRampToValueAtTime(0.0001, start + attack + hold + decay)
  g.connect(audio.destination)
  return g
}

function noiseBurst(
  audio: AudioContext,
  t0: number,
  {
    dur,
    peak = 0.15,
    delay = 0,
    type = 'bandpass',
    freq = 1800,
    q = 1.2,
    attack = 0.008,
  }: {
    dur: number
    peak?: number
    delay?: number
    type?: BiquadFilterType
    freq?: number
    q?: number
    attack?: number
  },
): void {
  const start = t0 + delay
  const src = audio.createBufferSource()
  src.buffer = noise(audio)
  const filter = audio.createBiquadFilter()
  filter.type = type
  filter.frequency.setValueAtTime(freq, start)
  filter.Q.setValueAtTime(q, start)
  const g = envGain(audio, t0, { attack, decay: dur, peak, delay })
  src.connect(filter)
  filter.connect(g)
  src.start(start)
  src.stop(start + attack + dur + 0.05)
}

function partial(
  audio: AudioContext,
  t0: number,
  {
    freq,
    freqEnd,
    dur,
    peak = 0.08,
    delay = 0,
    type = 'sine',
    attack = 0.002,
  }: {
    freq: number
    freqEnd?: number
    dur: number
    peak?: number
    delay?: number
    type?: OscillatorType
    attack?: number
  },
): void {
  const start = t0 + delay
  const osc = audio.createOscillator()
  osc.type = type
  osc.frequency.setValueAtTime(freq, start)
  if (freqEnd != null) {
    osc.frequency.exponentialRampToValueAtTime(Math.max(freqEnd, 20), start + dur)
  }
  const g = envGain(audio, t0, { attack, decay: dur, peak, delay })
  osc.connect(g)
  osc.start(start)
  osc.stop(start + attack + dur + 0.04)
}

/** Very quiet paper/wood rustle — desktop hover only. */
export function playHoverSfx(): void {
  if (!allowHoverSfx()) return
  withAudio((audio, t0) => {
    noiseBurst(audio, t0, {
      dur: 0.035,
      peak: 0.028,
      type: 'bandpass',
      freq: 2100,
      q: 0.8,
      attack: 0.004,
    })
  })
}

/**
 * Default UI tap — dull wood / bakelite (ordinary buttons).
 * Kept as playClickSfx for existing call sites.
 */
export function playClickSfx(): void {
  withAudio((audio, t0) => {
    // Soft wooden body
    partial(audio, t0, { freq: 180, freqEnd: 95, dur: 0.05, peak: 0.07, type: 'sine', attack: 0.001 })
    // Dull mid knock (not metallic ring)
    noiseBurst(audio, t0, {
      dur: 0.028,
      peak: 0.09,
      type: 'lowpass',
      freq: 900,
      q: 0.7,
      attack: 0.001,
    })
    noiseBurst(audio, t0, {
      dur: 0.02,
      peak: 0.05,
      type: 'bandpass',
      freq: 1400,
      q: 2.2,
      attack: 0.001,
    })
  })
}

/** Low “thud / bulp” — insufficient balance, disabled, API error. */
export function playErrorSfx(): void {
  withAudio((audio, t0) => {
    partial(audio, t0, { freq: 95, freqEnd: 48, dur: 0.14, peak: 0.12, type: 'sine', attack: 0.008 })
    noiseBurst(audio, t0, {
      dur: 0.1,
      peak: 0.08,
      type: 'lowpass',
      freq: 220,
      q: 1.2,
      attack: 0.01,
    })
  })
}

/** Theme switch — heavy folio page + soft copper dial. */
export function playThemeSelectSfx(): void {
  withAudio((audio, t0) => {
    // Paper / parchment flip
    noiseBurst(audio, t0, {
      dur: 0.12,
      peak: 0.1,
      type: 'bandpass',
      freq: 1600,
      q: 0.65,
      attack: 0.012,
    })
    noiseBurst(audio, t0, {
      dur: 0.09,
      peak: 0.06,
      delay: 0.04,
      type: 'highpass',
      freq: 3200,
      q: 0.7,
      attack: 0.02,
    })
    // Copper knob tick
    partial(audio, t0, {
      freq: 620,
      freqEnd: 410,
      dur: 0.06,
      peak: 0.04,
      delay: 0.07,
      type: 'triangle',
      attack: 0.002,
    })
  })
}

/** «Начать» — spring wind + steam valve crack. */
export function playStartSfx(): void {
  withAudio((audio, t0) => {
    // Coil spring tension (rising scrape)
    noiseBurst(audio, t0, {
      dur: 0.16,
      peak: 0.09,
      type: 'bandpass',
      freq: 2400,
      q: 3.5,
      attack: 0.02,
    })
    partial(audio, t0, {
      freq: 220,
      freqEnd: 480,
      dur: 0.18,
      peak: 0.05,
      type: 'triangle',
      attack: 0.03,
    })
    // Valve opens — steam puff
    noiseBurst(audio, t0, {
      dur: 0.28,
      peak: 0.11,
      delay: 0.12,
      type: 'highpass',
      freq: 3800,
      q: 0.55,
      attack: 0.025,
    })
    partial(audio, t0, {
      freq: 160,
      freqEnd: 90,
      dur: 0.12,
      peak: 0.06,
      delay: 0.12,
      type: 'sine',
      attack: 0.01,
    })
  })
}

/** «Забрать» press — telegraph key / latch (before server confirms). */
export function playCashoutArmSfx(): void {
  withAudio((audio, t0) => {
    // Sharp mechanical latch
    noiseBurst(audio, t0, {
      dur: 0.035,
      peak: 0.18,
      type: 'highpass',
      freq: 1800,
      q: 0.9,
      attack: 0.0008,
    })
    // Telegraph key clack
    partial(audio, t0, { freq: 980, freqEnd: 520, dur: 0.07, peak: 0.08, type: 'triangle', attack: 0.001 })
    partial(audio, t0, { freq: 210, freqEnd: 120, dur: 0.06, peak: 0.07, type: 'sine', attack: 0.001 })
    noiseBurst(audio, t0, {
      dur: 0.04,
      peak: 0.07,
      delay: 0.015,
      type: 'bandpass',
      freq: 1200,
      q: 4,
      attack: 0.001,
    })
  })
}

/** Success after cashout — coin / satisfying mechanical “чиньк”. */
export function playCashoutSfx(): void {
  withAudio((audio, t0) => {
    partial(audio, t0, { freq: 1240, dur: 0.12, peak: 0.09, type: 'sine', attack: 0.002 })
    partial(audio, t0, { freq: 1860, dur: 0.16, peak: 0.06, delay: 0.04, type: 'sine', attack: 0.002 })
    partial(audio, t0, { freq: 2480, dur: 0.1, peak: 0.035, delay: 0.08, type: 'triangle', attack: 0.002 })
    noiseBurst(audio, t0, {
      dur: 0.05,
      peak: 0.05,
      type: 'bandpass',
      freq: 4500,
      q: 2,
      attack: 0.001,
    })
  })
}

/** Burner valve + steam (booster activate). */
export function playBoosterSfx(): void {
  withAudio((audio, t0) => {
    noiseBurst(audio, t0, {
      dur: 0.04,
      peak: 0.14,
      type: 'highpass',
      freq: 2200,
      q: 0.8,
      attack: 0.001,
    })
    noiseBurst(audio, t0, {
      dur: 0.5,
      peak: 0.12,
      delay: 0.03,
      type: 'bandpass',
      freq: 4000,
      q: 0.55,
      attack: 0.04,
    })
    partial(audio, t0, {
      freq: 70,
      freqEnd: 48,
      dur: 0.4,
      peak: 0.06,
      delay: 0.02,
      type: 'sine',
      attack: 0.05,
    })
  })
}

/** Balloon pop + fabric + vapor. */
export function playCrashSfx(): void {
  withAudio((audio, t0) => {
    noiseBurst(audio, t0, {
      dur: 0.05,
      peak: 0.32,
      type: 'highpass',
      freq: 700,
      q: 0.5,
      attack: 0.0008,
    })
    partial(audio, t0, {
      freq: 220,
      freqEnd: 45,
      dur: 0.18,
      peak: 0.15,
      type: 'triangle',
      attack: 0.001,
    })
    noiseBurst(audio, t0, {
      dur: 0.22,
      peak: 0.11,
      delay: 0.02,
      type: 'bandpass',
      freq: 2800,
      q: 1.8,
      attack: 0.01,
    })
    noiseBurst(audio, t0, {
      dur: 0.4,
      peak: 0.09,
      delay: 0.04,
      type: 'bandpass',
      freq: 900,
      q: 0.7,
      attack: 0.03,
    })
  })
}
