import { Application, Container, Graphics } from 'pixi.js'
import { useEffect, useRef } from 'react'
import type { BalloonType } from '../api/types'
import { useGameStore } from '../stores/gameStore'
import './BalloonStage.css'

interface BalloonStageProps {
  balloonType: BalloonType
}

/**
 * Pixi flight canvas. Lerps visual K toward last server multiplier (Spec 3 §10.1).
 * Ticker stops on hidden tab / terminal (§10.3).
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

    const onVisibility = () => {
      if (!app) return
      if (document.visibilityState === 'hidden') {
        app.ticker.stop()
      } else if (useGameStore.getState().status === 'FLYING') {
        app.ticker.start()
      }
    }

    ;(async () => {
      const application = new Application()
      await application.init({
        resizeTo: host,
        backgroundAlpha: 0,
        antialias: true,
        resolution: Math.min(window.devicePixelRatio || 1, 2),
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
      const scale = new Graphics()
      const boosterMark = new Graphics()
      const balloon = new Graphics()
      const burst = new Graphics()
      let burstLife = 0
      let lastBalloonX = 0
      let lastBalloonY = 0
      let crashed = status === 'CRASHED'

      world.addChild(sky, scale, boosterMark, balloon, burst)

      const paintSky = (w: number, h: number) => {
        sky.clear()
        sky.rect(0, 0, w, h)
        sky.fill({ color: 0x4fa3c8, alpha: 0.001 })
        for (let i = 0; i < 6; i++) {
          const y = (h / 6) * i
          sky.rect(0, y, w, h / 6 + 2)
          sky.fill({ color: i % 2 === 0 ? 0x6bb7d8 : 0x3d8fb5, alpha: 0.12 })
        }
      }

      const balloonColor = balloonType === 'LUCKY' ? 0xd62828 : 0x2e9b4f

      const drawBalloon = (x: number, y: number) => {
        lastBalloonX = x
        lastBalloonY = y
        balloon.clear()
        if (crashed) return
        balloon.ellipse(x, y, 34, 42)
        balloon.fill({ color: balloonColor })
        balloon.ellipse(x - 10, y - 12, 10, 14)
        balloon.fill({ color: 0xffffff, alpha: 0.22 })
        balloon.moveTo(x, y + 40)
        balloon.lineTo(x, y + 58)
        balloon.stroke({ width: 2, color: 0x5a4632 })
        balloon.moveTo(x - 10, y + 58)
        balloon.lineTo(x + 10, y + 58)
        balloon.lineTo(x, y + 70)
        balloon.lineTo(x - 10, y + 58)
        balloon.fill({ color: 0xc4a574 })
      }

      const drawBurst = (dt: number) => {
        burst.clear()
        if (burstLife <= 0) return
        burstLife = Math.max(0, burstLife - dt)
        const t = burstLife / 450
        const shards = 12
        for (let i = 0; i < shards; i++) {
          const ang = (Math.PI * 2 * i) / shards
          const dist = (1 - t) * 70
          const x = lastBalloonX + Math.cos(ang) * dist
          const y = lastBalloonY + Math.sin(ang) * dist
          burst.circle(x, y, 4 + t * 5)
          burst.fill({ color: i % 2 === 0 ? balloonColor : 0xffe566, alpha: t })
        }
      }

      const drawScale = (w: number, h: number, currentLine: number) => {
        scale.clear()
        const maxLines = 21
        const topPad = 48
        const bottomPad = 90
        const usable = h - topPad - bottomPad
        for (let i = 0; i <= maxLines; i++) {
          const t = i / maxLines
          const y = h - bottomPad - t * usable
          const reached = i <= currentLine
          scale.circle(18, y, reached ? 5 : 3.5)
          scale.fill({ color: reached ? 0xff6b5a : 0xffffff, alpha: reached ? 1 : 0.45 })
          if (i % 3 === 0) {
            scale.moveTo(28, y)
            scale.lineTo(w - 16, y)
            scale.stroke({ width: 1, color: 0xffffff, alpha: 0.12 })
          }
        }
        if (boosterLine != null && boosterLine >= 0 && boosterLine <= maxLines) {
          const t = boosterLine / maxLines
          const y = h - bottomPad - t * usable
          boosterMark.clear()
          boosterMark.roundRect(w - 52, y - 14, 40, 28, 8)
          boosterMark.fill({ color: 0xffc107 })
          boosterMark.circle(w - 32, y, 6)
          boosterMark.fill({ color: 0xffffff })
        } else {
          boosterMark.clear()
        }
      }

      const layout = (dt = 16) => {
        const w = app!.screen.width
        const h = app!.screen.height
        paintSky(w, h)
        drawScale(w, h, lineIndex)
        const progress = Math.min(Math.max((displayK - 1) / 12, 0), 1)
        const y = h - 100 - progress * (h - 180)
        const x = w * 0.55
        drawBalloon(x, y)
        drawBurst(dt)
      }

      unsub = useGameStore.subscribe((s) => {
        targetK = s.multiplier
        lineIndex = s.lineIndex
        const prev = status
        status = s.status
        boosterLine = s.booster?.triggerLine ?? null
        if (status === 'CRASHED' && prev !== 'CRASHED') {
          crashed = true
          burstLife = 450
          // brief ticker for burst even after terminal
          app?.ticker.start()
        }
        if (status === 'CASHED_OUT') {
          crashed = false
          burstLife = 0
        }
        if (status !== 'FLYING' && burstLife <= 0) {
          displayK = targetK
          layout()
          app?.ticker.stop()
        } else if (document.visibilityState === 'visible' && status === 'FLYING') {
          app?.ticker.start()
        }
      })

      document.addEventListener('visibilitychange', onVisibility)

      app.ticker.add((ticker) => {
        if (status === 'FLYING') {
          const alpha = 1 - Math.exp(-ticker.deltaMS / 110)
          displayK += (targetK - displayK) * alpha
        } else {
          displayK = targetK
        }
        layout(ticker.deltaMS)
        if (status !== 'FLYING' && burstLife <= 0) {
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

  return <div ref={hostRef} className="pixi-host" aria-hidden />
}
