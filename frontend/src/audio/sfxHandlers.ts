import type { PointerEvent } from 'react'
import { playErrorSfx, playHoverSfx } from './clickSfx'

/** Desktop-only hover handler for primary controls. */
export function hoverHandlers(): {
  onMouseEnter: () => void
} {
  return {
    onMouseEnter: () => playHoverSfx(),
  }
}

/** Play error thud when pressing a disabled control (HTML disabled blocks click). */
export function disabledPressHandlers(disabled: boolean): {
  onPointerDown: (e: PointerEvent) => void
} {
  return {
    onPointerDown: (e) => {
      if (!disabled) return
      e.preventDefault()
      playErrorSfx()
    },
  }
}
