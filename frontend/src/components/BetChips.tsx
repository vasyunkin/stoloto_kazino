import { motion } from 'framer-motion'
import type { BalloonType } from '../api/types'
import { playClickSfx } from '../audio/clickSfx'
import { disabledPressHandlers } from '../audio/sfxHandlers'
import { betProblemMessage, formatBet, type BetProblem } from '../game/betAmount'
import './BetChips.css'

const PRESETS_BY_THEME: Record<BalloonType, readonly number[]> = {
  STANDARD: [12, 50, 150, 300],
  LUCKY: [25, 50, 150, 300],
}

interface BetChipsProps {
  draft: string
  balance: number
  balloonType: BalloonType
  minBet: number
  maxBet: number
  problem: BetProblem | null
  maxStake: number | null
  onDraft: (raw: string) => void
  onPick: (n: number) => void
}

export function BetChips({
  draft,
  balance,
  balloonType,
  minBet,
  maxBet,
  problem,
  maxStake,
  onDraft,
  onPick,
}: BetChipsProps) {
  const presets = PRESETS_BY_THEME[balloonType]
  const parsed = Number(draft.replace(',', '.'))
  return (
    <div className="bet-picker">
      <div className="bet-chips" role="group" aria-label="Готовые ставки">
        {presets.map((n) => {
          const disabled = n > balance || n < minBet || n > maxBet
          const selected = Number.isFinite(parsed) && parsed === n
          return (
            <motion.button
              key={n}
              type="button"
              className={`bet-chip${selected ? ' is-selected' : ''}`}
              disabled={disabled}
              whileTap={disabled ? undefined : { scale: 0.96 }}
              {...disabledPressHandlers(disabled)}
              onClick={() => {
                if (disabled) return
                playClickSfx()
                onPick(n)
              }}
            >
              {n}
            </motion.button>
          )
        })}
      </div>
      <div className="bet-custom">
        <label className="bet-custom-label" htmlFor="bet-amount">
          Своя сумма
          <input
            id="bet-amount"
            inputMode="decimal"
            autoComplete="off"
            value={draft}
            aria-invalid={problem != null}
            onChange={(e) => onDraft(e.target.value)}
          />
        </label>
        <button
          type="button"
          className="bet-max"
          disabled={maxStake == null}
          onClick={() => {
            if (maxStake == null) return
            playClickSfx()
            onPick(maxStake)
          }}
        >
          Макс
        </button>
      </div>
      <p className={`bet-range${problem ? ' is-problem' : ''}`} role={problem ? 'alert' : undefined}>
        {problem
          ? betProblemMessage(problem, { minBetAmount: minBet, maxBetAmount: maxBet })
          : `от ${formatBet(minBet)} до ${formatBet(maxBet)} ◎`}
      </p>
    </div>
  )
}
