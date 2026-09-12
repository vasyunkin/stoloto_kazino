import { motion } from 'framer-motion'
import type { BoosterPreference } from '../api/types'
import './BoosterCards.css'

export type BoosterCardId = 'none' | 'auto'

interface CardDef {
  id: BoosterCardId
  preference: BoosterPreference
  label: string
  hint: string
  tone: string
}

interface BoosterCardsProps {
  selectedId: BoosterCardId
  charges: number
  onSelect: (id: BoosterCardId, preference: BoosterPreference) => void
}

/** NONE vs AUTO — one charge consumed on AUTO at start. */
export function BoosterCards({ selectedId, charges, onSelect }: BoosterCardsProps) {
  const cards: CardDef[] = [
    { id: 'none', preference: 'NONE', label: 'Без бустера', hint: '0 зарядов', tone: 'amber' },
    {
      id: 'auto',
      preference: 'AUTO',
      label: 'С бустером',
      hint: charges > 0 ? `−1 · осталось ${charges}` : 'нет зарядов',
      tone: 'blue',
    },
  ]

  return (
    <div className="booster-block">
      <div className="booster-head">
        <h3>Бустер</h3>
        <p className="booster-note">
          Событие на уровне: умножает коэффициент и даёт очки. Заряды: {charges}
        </p>
      </div>
      <div className="booster-grid booster-grid-2" role="listbox" aria-label="Бустер">
        {cards.map((c) => {
          const active = selectedId === c.id
          const disabled = c.preference === 'AUTO' && charges <= 0
          return (
            <motion.button
              key={c.id}
              type="button"
              role="option"
              aria-selected={active}
              disabled={disabled}
              className={`booster-card tone-${c.tone}${active ? ' is-selected' : ''}`}
              whileTap={disabled ? undefined : { scale: 0.97 }}
              onClick={() => {
                if (!disabled) onSelect(c.id, c.preference)
              }}
            >
              <span className="booster-piece" aria-hidden />
              <span className="booster-label">{c.label}</span>
              <span className="booster-hint">{c.hint}</span>
            </motion.button>
          )
        })}
      </div>
    </div>
  )
}
