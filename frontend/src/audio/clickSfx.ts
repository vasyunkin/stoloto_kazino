import { useSettingsStore } from '../stores/settingsStore'

/**
 * Victorian steampunk SFX via Web Audio (noise + filters, not beepy squares).
 * Spec 5 V1/V5 — mute via settingsStore.
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
  g.gain.exponentialRampToValueAtTime(peak, start + attack)
  if (hold > 0) g.gain.setValueAtTime(peak, start + attack + hold)
  g.gain.exponentialRampToValueAtTime(0.0001, start + attack + hold + decay)
  g.connect(audio.destination)
  return g
}

/** Band-limited noise burst (steam, fabric, spray). */
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

/** Quiet sine/triangle partial (metal ring, brass). */
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

/**
 * Brass / iron latch — short metallic tick for UI buttons.
 * (Not a square-wave beep.)
 */
export function playClickSfx(): void {
  withAudio((audio, t0) => {
    // Transient “strike”
    noiseBurst(audio, t0, {
      dur: 0.045,
      peak: 0.22,
      type: 'bandpass',
      freq: 3200,
      q: 4.5,
      attack: 0.001,
    })
    // Resonant metal ring
    partial(audio, t0, { freq: 1850, freqEnd: 920, dur: 0.09, peak: 0.07, type: 'triangle' })
    partial(audio, t0, { freq: 2780, freqEnd: 1400, dur: 0.07, peak: 0.045, type: 'sine', delay: 0.004 })
    // Soft body thump of a lever
    partial(audio, t0, { freq: 140, freqEnd: 70, dur: 0.06, peak: 0.05, type: 'sine' })
  })
}

/**
 * Burner valve + escaping steam (booster).
 */
export function playBoosterSfx(): void {
  withAudio((audio, t0) => {
    // Valve clank
    noiseBurst(audio, t0, {
      dur: 0.04,
      peak: 0.18,
      type: 'highpass',
      freq: 2400,
      q: 0.8,
      attack: 0.001,
    })
    partial(audio, t0, { freq: 420, freqEnd: 180, dur: 0.08, peak: 0.06, type: 'triangle' })
    // Steam hiss (long, airy)
    noiseBurst(audio, t0, {
      dur: 0.55,
      peak: 0.14,
      delay: 0.03,
      type: 'bandpass',
      freq: 4200,
      q: 0.55,
      attack: 0.04,
    })
    noiseBurst(audio, t0, {
      dur: 0.4,
      peak: 0.08,
      delay: 0.08,
      type: 'highpass',
      freq: 6000,
      q: 0.7,
      attack: 0.06,
    })
    // Low furnace rumble
    partial(audio, t0, {
      freq: 70,
      freqEnd: 48,
      dur: 0.45,
      peak: 0.07,
      delay: 0.02,
      type: 'sine',
      attack: 0.05,
    })
  })
}

/**
 * Atomizer / cologne “пшик” on successful cashout — genteel Victorian.
 */
export function playCashoutSfx(): void {
  withAudio((audio, t0) => {
    // Pump click
    noiseBurst(audio, t0, {
      dur: 0.03,
      peak: 0.12,
      type: 'bandpass',
      freq: 1600,
      q: 3,
      attack: 0.001,
    })
    // Fine mist spray
    noiseBurst(audio, t0, {
      dur: 0.28,
      peak: 0.11,
      delay: 0.02,
      type: 'highpass',
      freq: 5500,
      q: 0.6,
      attack: 0.015,
    })
    noiseBurst(audio, t0, {
      dur: 0.22,
      peak: 0.07,
      delay: 0.05,
      type: 'bandpass',
      freq: 9000,
      q: 0.9,
      attack: 0.02,
    })
    // Tiny brass chime under the spray
    partial(audio, t0, {
      freq: 880,
      freqEnd: 1320,
      dur: 0.22,
      peak: 0.04,
      delay: 0.04,
      type: 'sine',
      attack: 0.01,
    })
  })
}

/**
 * Balloon burst: rubber pop + fabric tear + brief foul vapor release.
 */
export function playCrashSfx(): void {
  withAudio((audio, t0) => {
    // Sharp pop transient
    noiseBurst(audio, t0, {
      dur: 0.05,
      peak: 0.35,
      type: 'highpass',
      freq: 800,
      q: 0.5,
      attack: 0.0008,
    })
    // Rubber membrane snap (pitch dive)
    partial(audio, t0, {
      freq: 220,
      freqEnd: 45,
      dur: 0.18,
      peak: 0.16,
      type: 'triangle',
      attack: 0.001,
    })
    partial(audio, t0, {
      freq: 380,
      freqEnd: 60,
      dur: 0.14,
      peak: 0.08,
      type: 'sine',
      attack: 0.001,
    })
    // Fabric / silk tear
    noiseBurst(audio, t0, {
      dur: 0.22,
      peak: 0.12,
      delay: 0.02,
      type: 'bandpass',
      freq: 2800,
      q: 1.8,
      attack: 0.01,
    })
    // Escaping gas whoosh
    noiseBurst(audio, t0, {
      dur: 0.45,
      peak: 0.1,
      delay: 0.04,
      type: 'bandpass',
      freq: 900,
      q: 0.7,
      attack: 0.03,
    })
    // Short “reek / belch” of foul air — low, muffled (Victorian comedy beat)
    noiseBurst(audio, t0, {
      dur: 0.2,
      peak: 0.09,
      delay: 0.12,
      type: 'lowpass',
      freq: 280,
      q: 1.1,
      attack: 0.04,
    })
    partial(audio, t0, {
      freq: 95,
      freqEnd: 55,
      dur: 0.18,
      peak: 0.06,
      delay: 0.14,
      type: 'sine',
      attack: 0.03,
    })
  })
}
