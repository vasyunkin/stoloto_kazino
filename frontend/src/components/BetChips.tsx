import { motion } from 'framer-motion'
import './BetChips.css'

const PRESETS = [50, 150, 300, 600] as const

interface BetChipsProps {
  value: number
  balance: number
  onChange: (n: number) => void
}

export function BetChips({ value, balance, onChange }: BetChipsProps) {
  return (
    <div className="bet-chips" role="group" aria-label="Ставка">
      {PRESETS.map((n) => {
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
