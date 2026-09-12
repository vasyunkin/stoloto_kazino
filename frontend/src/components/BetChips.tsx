import { motion } from 'framer-motion'
import type { BalloonType } from '../api/types'
import './BetChips.css'

const PRESETS_BY_THEME: Record<BalloonType, readonly number[]> = {
  STANDARD: [12, 50, 150, 300],
  LUCKY: [25, 50, 150, 300],
}

interface BetChipsProps {
  value: number
  balance: number
  balloonType: BalloonType
  onChange: (n: number) => void
}

export function BetChips({ value, balance, balloonType, onChange }: BetChipsProps) {
  const presets = PRESETS_BY_THEME[balloonType]
  return (
    <div className="bet-chips" role="group" aria-label="Ставка">
      {presets.map((n) => {
        const disabled = n > balance
        return (
          <motion.button
            key={n}
            type="button"
            className={`bet-chip${value === n ? ' is-selected' : ''}`}
            disabled={disabled}
            whileTap={disabled ? undefined : { scale: 0.96 }}
            onClick={() => onChange(n)}
          >
            {n}
          </motion.button>
        )
      })}
    </div>
  )
}
