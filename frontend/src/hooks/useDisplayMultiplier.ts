import { useEffect, useRef, useState } from 'react'
import { useGameStore } from '../stores/gameStore'

/** Smoothed K for React overlay; Pixi has its own lerp (Spec 3 §10.1). */
export function useDisplayMultiplier(): number {
  const serverK = useGameStore((s) => s.multiplier)
  const status = useGameStore((s) => s.status)
  const [display, setDisplay] = useState(serverK)
  const currentRef = useRef(serverK)
  const targetRef = useRef(serverK)

  useEffect(() => {
    targetRef.current = serverK
    if (status !== 'FLYING') {
      currentRef.current = serverK
      setDisplay(serverK)
    }
  }, [serverK, status])

  useEffect(() => {
    if (status !== 'FLYING') return
    let raf = 0
    const tick = () => {
      const target = targetRef.current
      currentRef.current += (target - currentRef.current) * 0.18
      if (Math.abs(target - currentRef.current) < 0.001) {
        currentRef.current = target
      }
      setDisplay(currentRef.current)
      raf = requestAnimationFrame(tick)
    }
    raf = requestAnimationFrame(tick)
    return () => cancelAnimationFrame(raf)
  }, [status])

  return display
}
