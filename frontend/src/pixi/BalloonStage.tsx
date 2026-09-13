import { Application, Container, Graphics } from 'pixi.js'
import { useEffect, useRef } from 'react'
import type { BalloonType } from '../api/types'
import { useGameStore } from '../stores/gameStore'
import './BalloonStage.css'

interface BalloonStageProps {
  balloonType: BalloonType
}

/** Spec 5 §3.4.1 — explicit low-end gate. */
export function computeLowDetail(): boolean {
  if (typeof window === 'undefined') return false
  return Math.min(window.innerWidth, window.innerHeight) < 400
}

const THEME = {
  LUCKY: {
    fill: 0x8b2e2e,
    accent: 0xc4783a,
    deep: 0x5a1c1c,
  },
  STANDARD: {
    fill: 0x2f5d4a,
    accent: 0x7a9b6d,
    deep: 0x1a382c,
  },
} as const

/**
 * Pixi flight canvas — Spec 5 V3.
 * Lerps visual K (Spec 3 §10.1); ticker pause on hidden/terminal (§10.3).
 */
export function BalloonStage({ balloonType }: BalloonStageProps) {
  const hostRef = useRef<HTMLDivElement>(null)

  useEffect(() => {
    const host = hostRef.current
    if (!host) return

    let destroyed = false
    let app: Application | null = null
    let unsub: (() => void) | null = null
    let displayK = useGameStore.getState().multiplier
    let targetK = displayK
    let lineIndex = useGameStore.getState().lineIndex
    let status = useGameStore.getState().status
    let boosterLine = useGameStore.getState().booster?.triggerLine ?? null
    let boosterActivated = useGameStore.getState().booster?.activated ?? false
    let lowDetail = computeLowDetail()
    let elapsed = 0
    let burstLife = 0
    let boosterFlash = 0
    let lastBalloonX = 0
    let lastBalloonY = 0
    let crashed = status === 'CRASHED'
    const steam: Array<{ x: number; y: number; life: number; vx: number; vy: number }> = []

    const syncLowDetailAttr = () => {
      host.dataset.lowDetail = lowDetail ? '1' : '0'
      if (import.meta.env.DEV) {
        console.debug('[BalloonStage] lowDetail=', lowDetail)
      }
    }
    syncLowDetailAttr()

    const onVisibility = () => {
      if (!app) return
      if (document.visibilityState === 'hidden') {
        app.ticker.stop()
      } else if (useGameStore.getState().status === 'FLYING') {
        app.ticker.start()
      }
    }

    const onViewportChange = () => {
      const next = computeLowDetail()
      if (next !== lowDetail) {
        lowDetail = next
        syncLowDetailAttr()
        if (lowDetail) steam.length = 0
      }
    }

    ;(async () => {
      const application = new Application()
      await application.init({
        resizeTo: host,
        backgroundAlpha: 0,
        antialias: !lowDetail,
        resolution: Math.min(window.devicePixelRatio || 1, lowDetail ? 1.5 : 2),
        autoDensity: true,
      })
      if (destroyed) {
        application.destroy(true)
        return
      }
      app = application
      host.appendChild(app.canvas)

      const world = new Container()
      app.stage.addChild(world)

      const sky = new Graphics()
      const cloudsFar = new Graphics()
      const cloudsNear = new Graphics()
      const scale = new Graphics()
      const boosterMark = new Graphics()
      const balloon = new Graphics()
      const vfx = new Graphics()
      const burst = new Graphics()

      world.addChild(sky, cloudsFar, cloudsNear, scale, boosterMark, balloon, vfx, burst)

      const theme = THEME[balloonType]
      const copper = 0xb87333
      const brass = 0xc4a35a
      const parchment = 0xe8dcc4
      const soot = 0x2a2118

      const paintSky = (w: number, h: number) => {
        sky.clear()
        // Map-toned atmosphere bands (fog → mid → high)
        const bands = [
          { y0: 0, y1: h * 0.28, c: 0x5a6a72 },
          { y0: h * 0.28, y1: h * 0.55, c: 0x4a5a58 },
          { y0: h * 0.55, y1: h * 0.78, c: 0x3d4538 },
          { y0: h * 0.78, y1: h, c: 0x2a2118 },
        ]
        for (const b of bands) {
          sky.rect(0, b.y0, w, b.y1 - b.y0 + 1)
          sky.fill({ color: b.c, alpha: 0.92 })
        }
        // Engraved map grid
        sky.setStrokeStyle({ width: 1, color: soot, alpha: 0.12 })
        const step = lowDetail ? 28 : 18
        for (let x = 0; x < w; x += step) {
          sky.moveTo(x, 0)
          sky.lineTo(x, h)
        }
        for (let y = 0; y < h; y += step) {
          sky.moveTo(0, y)
          sky.lineTo(w, y)
        }
        sky.stroke()
        // Soft vignette corners
        sky.ellipse(w * 0.5, h * 0.15, w * 0.55, h * 0.2)
        sky.fill({ color: brass, alpha: 0.06 })
      }

      const paintClouds = (w: number, h: number, t: number) => {
        cloudsFar.clear()
        cloudsNear.clear()
        const drift = lowDetail ? 0 : t * 0.012
        const puff = (g: Graphics, cx: number, cy: number, s: number, a: number) => {
          g.ellipse(cx, cy, s * 1.4, s * 0.7)
          g.fill({ color: parchment, alpha: a })
          g.ellipse(cx - s * 0.7, cy + 2, s * 0.9, s * 0.55)
          g.fill({ color: parchment, alpha: a * 0.9 })
          g.ellipse(cx + s * 0.75, cy + 1, s, s * 0.5)
          g.fill({ color: parchment, alpha: a * 0.85 })
        }
        // Far layer (or single static in lowDetail)
        const farOff = (drift * 0.35) % (w + 80)
        puff(cloudsFar, ((80 + farOff) % (w + 100)) - 40, h * 0.22, 22, 0.14)
        puff(cloudsFar, ((w * 0.55 + farOff * 0.7) % (w + 100)) - 30, h * 0.32, 18, 0.12)
        if (!lowDetail) {
          const nearOff = (drift * 0.7) % (w + 120)
          puff(cloudsNear, ((40 + nearOff) % (w + 120)) - 50, h * 0.4, 26, 0.16)
          puff(cloudsNear, ((w * 0.7 + nearOff) % (w + 120)) - 40, h * 0.18, 20, 0.13)
        }
      }

      const drawBalloon = (x: number, y: number, t: number) => {
        lastBalloonX = x
        lastBalloonY = y
        balloon.clear()
        if (crashed) return

        const sway = lowDetail ? 0 : Math.sin(t * 0.0018) * 2.2
        const bx = x + sway

        // Envelope
        balloon.ellipse(bx, y, 36, 44)
        balloon.fill({ color: theme.fill })
        balloon.ellipse(bx, y + 6, 34, 38)
        balloon.fill({ color: theme.deep, alpha: 0.35 })
        balloon.ellipse(bx - 11, y - 14, 11, 15)
        balloon.fill({ color: 0xffffff, alpha: 0.2 })
        // Patch
        balloon.roundRect(bx + 8, y - 2, 14, 11, 2)
        balloon.fill({ color: soot, alpha: 0.22 })
        balloon.stroke({ width: 1, color: soot, alpha: 0.35 })

        if (balloonType === 'LUCKY') {
          // Copper gear silhouette
          const gx = bx + 22
          const gy = y + 8
          balloon.circle(gx, gy, 9)
          balloon.fill({ color: copper })
          balloon.circle(gx, gy, 3)
          balloon.fill({ color: soot })
          if (!lowDetail) {
            const rot = t * 0.002
            for (let i = 0; i < 6; i++) {
              const a = rot + (i * Math.PI) / 3
              balloon.circle(gx + Math.cos(a) * 10, gy + Math.sin(a) * 10, 2.5)
              balloon.fill({ color: brass })
            }
          }
        } else {
          // Vine + pipe silhouette
          balloon.moveTo(bx - 22, y + 10)
          balloon.quadraticCurveTo(bx - 28, y - 10, bx - 18, y - 28)
          balloon.stroke({ width: 3, color: theme.accent, alpha: 0.9 })
          balloon.ellipse(bx - 24, y - 6, 5, 3)
          balloon.fill({ color: theme.accent })
          balloon.ellipse(bx - 20, y + 4, 4, 2.5)
          balloon.fill({ color: 0x3d5c45 })
          // Pipe on basket later
        }

        // Ropes
        balloon.moveTo(bx - 14, y + 40)
        balloon.lineTo(bx - 12, y + 58)
        balloon.moveTo(bx, y + 42)
        balloon.lineTo(bx, y + 58)
        balloon.moveTo(bx + 14, y + 40)
        balloon.lineTo(bx + 12, y + 58)
        balloon.stroke({ width: 1.5, color: 0x5a4632 })

        // Basket
        balloon.roundRect(bx - 15, y + 56, 30, 18, 2)
        balloon.fill({ color: 0x6b5340 })
        balloon.stroke({ width: 1.5, color: copper })
        balloon.moveTo(bx - 12, y + 62)
        balloon.lineTo(bx + 12, y + 62)
        balloon.moveTo(bx - 12, y + 67)
        balloon.lineTo(bx + 12, y + 67)
        balloon.stroke({ width: 1, color: brass, alpha: 0.55 })

        if (balloonType === 'STANDARD') {
          balloon.roundRect(bx - 10, y + 48, 5, 10, 1)
          balloon.fill({ color: 0x6a7a6a })
          balloon.stroke({ width: 1, color: soot })
        } else {
          balloon.circle(bx + 14, y + 62, 5)
          balloon.fill({ color: copper })
          balloon.circle(bx + 14, y + 62, 1.5)
          balloon.fill({ color: soot })
        }

        // Idle steam under basket (full only)
        if (!lowDetail && status === 'FLYING' && Math.random() < 0.04) {
          steam.push({
            x: bx + (Math.random() - 0.5) * 10,
            y: y + 74,
            life: 500 + Math.random() * 300,
            vx: (Math.random() - 0.5) * 0.04,
            vy: -0.06 - Math.random() * 0.04,
          })
        }
      }

      const drawVfx = (dt: number) => {
        vfx.clear()
        if (boosterFlash > 0) {
          boosterFlash = Math.max(0, boosterFlash - dt)
          const t = boosterFlash / (lowDetail ? 280 : 520)
          const glow = lowDetail ? 0xffa040 : 0xffc107
          vfx.circle(lastBalloonX, lastBalloonY + 50, 18 + (1 - t) * (lowDetail ? 20 : 40))
          vfx.fill({ color: glow, alpha: t * 0.55 })
          if (!lowDetail) {
            // Burner plume
            for (let i = 0; i < 5; i++) {
              const oy = lastBalloonY + 48 - i * 10 * (1 - t)
              vfx.ellipse(lastBalloonX + Math.sin(i + elapsed * 0.01) * 4, oy, 6 - i * 0.6, 8)
              vfx.fill({ color: i % 2 ? 0xffe08a : 0xff8a3a, alpha: t * 0.5 })
            }
          }
        }

        if (!lowDetail) {
          for (let i = steam.length - 1; i >= 0; i--) {
            const p = steam[i]!
            p.life -= dt
            p.x += p.vx * dt
            p.y += p.vy * dt
            if (p.life <= 0) {
              steam.splice(i, 1)
              continue
            }
            const a = Math.min(1, p.life / 400) * 0.28
            vfx.ellipse(p.x, p.y, 8, 5)
            vfx.fill({ color: parchment, alpha: a })
          }
          if (steam.length > 24) steam.splice(0, steam.length - 24)
        }
      }

      const drawBurst = (dt: number) => {
        burst.clear()
        if (burstLife <= 0) return
        burstLife = Math.max(0, burstLife - dt)
        const t = burstLife / 450
        const shards = lowDetail ? 6 : 12
        for (let i = 0; i < shards; i++) {
          const ang = (Math.PI * 2 * i) / shards + 0.2
          const dist = (1 - t) * (lowDetail ? 55 : 78)
          const x = lastBalloonX + Math.cos(ang) * dist
          const y = lastBalloonY + Math.sin(ang) * dist
          // Fabric shreds
          burst.roundRect(x - 4, y - 2, 9, 5, 1)
          burst.fill({ color: i % 2 === 0 ? theme.fill : brass, alpha: t })
        }
        // Smoke
        const smokeN = lowDetail ? 3 : 6
        for (let i = 0; i < smokeN; i++) {
          const ang = (Math.PI * 2 * i) / smokeN
          const dist = (1 - t) * 40 + 10
          burst.circle(
            lastBalloonX + Math.cos(ang) * dist * 0.6,
            lastBalloonY + Math.sin(ang) * dist * 0.4 - (1 - t) * 20,
            6 + (1 - t) * 8,
          )
          burst.fill({ color: 0x3d342c, alpha: t * 0.45 })
        }
      }

      const drawScale = (w: number, h: number, currentLine: number) => {
        scale.clear()
        const maxLines = 21
        const topPad = 48
        const bottomPad = 90
        const usable = h - topPad - bottomPad
        // Altimeter rail
        scale.roundRect(8, topPad - 8, 22, usable + 16, 4)
        scale.fill({ color: soot, alpha: 0.55 })
        scale.stroke({ width: 1.5, color: copper, alpha: 0.8 })

        for (let i = 0; i <= maxLines; i++) {
          const tt = i / maxLines
          const y = h - bottomPad - tt * usable
          const reached = i <= currentLine
          const major = i % 3 === 0
          scale.circle(19, y, reached ? (major ? 5 : 4) : major ? 3.5 : 2.5)
          scale.fill({
            color: reached ? theme.accent : parchment,
            alpha: reached ? 1 : 0.35,
          })
          if (major) {
            scale.moveTo(32, y)
            scale.lineTo(w - 14, y)
            scale.stroke({ width: 1, color: parchment, alpha: 0.1 })
          }
        }

        if (boosterLine != null && boosterLine >= 0 && boosterLine <= maxLines) {
          const tt = boosterLine / maxLines
          const y = h - bottomPad - tt * usable
          boosterMark.clear()
          boosterMark.roundRect(w - 54, y - 13, 42, 26, 4)
          boosterMark.fill({ color: brass })
          boosterMark.stroke({ width: 1.5, color: copper })
          boosterMark.circle(w - 33, y, 5)
          boosterMark.fill({ color: 0xfff3a8 })
        } else {
          boosterMark.clear()
        }
      }

      const layout = (dt = 16) => {
        const w = app!.screen.width
        const h = app!.screen.height
        elapsed += dt
        paintSky(w, h)
        paintClouds(w, h, elapsed)
        drawScale(w, h, lineIndex)
        const progress = Math.min(Math.max((displayK - 1) / 12, 0), 1)
        const y = h - 100 - progress * (h - 180)
        const x = w * 0.55
        drawBalloon(x, y, elapsed)
        drawVfx(dt)
        drawBurst(dt)
      }

      unsub = useGameStore.subscribe((s) => {
        targetK = s.multiplier
        lineIndex = s.lineIndex
        const prev = status
        status = s.status
        boosterLine = s.booster?.triggerLine ?? null
        const nextActivated = s.booster?.activated ?? false
        if (nextActivated && !boosterActivated) {
          boosterFlash = lowDetail ? 280 : 520
          if (!lowDetail) {
            for (let i = 0; i < 8; i++) {
              steam.push({
                x: lastBalloonX + (Math.random() - 0.5) * 16,
                y: lastBalloonY + 60,
                life: 400 + Math.random() * 400,
                vx: (Math.random() - 0.5) * 0.08,
                vy: -0.1 - Math.random() * 0.08,
              })
            }
          }
          app?.ticker.start()
        }
        boosterActivated = nextActivated

        if (status === 'CRASHED' && prev !== 'CRASHED') {
          crashed = true
          burstLife = 450
          steam.length = 0
          app?.ticker.start()
        }
        if (status === 'CASHED_OUT') {
          crashed = false
          burstLife = 0
          boosterFlash = 0
        }
        if (status !== 'FLYING' && burstLife <= 0 && boosterFlash <= 0) {
          displayK = targetK
          layout()
          app?.ticker.stop()
        } else if (document.visibilityState === 'visible' && status === 'FLYING') {
          app?.ticker.start()
        }
      })

      document.addEventListener('visibilitychange', onVisibility)
      window.addEventListener('resize', onViewportChange)
      window.addEventListener('orientationchange', onViewportChange)

      app.ticker.add((ticker) => {
        if (status === 'FLYING') {
          const alpha = 1 - Math.exp(-ticker.deltaMS / 110)
          displayK += (targetK - displayK) * alpha
        } else {
          displayK = targetK
        }
        layout(ticker.deltaMS)
        if (status !== 'FLYING' && burstLife <= 0 && boosterFlash <= 0) {
          app?.ticker.stop()
        }
      })

      layout()
      if (document.visibilityState === 'hidden' || (status !== 'FLYING' && burstLife <= 0)) {
        app.ticker.stop()
      }
    })().catch(() => {
      /* init failure — React overlay still works */
    })

    return () => {
      destroyed = true
      document.removeEventListener('visibilitychange', onVisibility)
      window.removeEventListener('resize', onViewportChange)
      window.removeEventListener('orientationchange', onViewportChange)
      unsub?.()
      if (app) {
        app.ticker.stop()
        const canvas = app.canvas
        app.destroy(true)
        canvas?.remove()
        app = null
      }
    }
  }, [balloonType])

  return <div ref={hostRef} className="pixi-host" data-low-detail="0" aria-hidden />
}
