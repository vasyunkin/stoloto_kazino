import { motion } from 'framer-motion'
import type { BoosterPreference } from '../api/types'
import './BoosterCards.css'

export type BoosterCardId = 'none' | 'x2' | 'x3' | 'x4'

interface CardDef {
  id: BoosterCardId
  preference: BoosterPreference
  label: string
  hint: string
  tone: string
}

const CARDS: CardDef[] = [
  { id: 'none', preference: 'NONE', label: 'Без бустера', hint: '5', tone: 'amber' },
  { id: 'x2', preference: 'AUTO', label: 'x2', hint: '15', tone: 'blue' },
  { id: 'x3', preference: 'AUTO', label: 'x3', hint: '300', tone: 'pink' },
  { id: 'x4', preference: 'AUTO', label: 'x4', hint: '150', tone: 'brown' },
]

interface BoosterCardsProps {
  selectedId: BoosterCardId
  onSelect: (id: BoosterCardId, preference: BoosterPreference) => void
}

/**
 * Visual “puzzle” cards from mockups. Only NONE vs AUTO hit the API (Spec 3 §4.4).
 * Hint costs are cosmetic — no extra ledger debit.
 */
export function BoosterCards({ selectedId, onSelect }: BoosterCardsProps) {
  return (
    <div className="booster-block">
      <div className="booster-head">
        <h3>Выбери фрагмент</h3>
        <p className="booster-note">Цена на карточке — UI; сервер роллит бустер при AUTO</p>
      </div>
      <div className="booster-grid" role="listbox" aria-label="Бустер">
        {CARDS.map((c) => {
          const active = selectedId === c.id
          return (
            <motion.button
              key={c.id}
              type="button"
              role="option"
              aria-selected={active}
              className={`booster-card tone-${c.tone}${active ? ' is-selected' : ''}`}
              whileTap={{ scale: 0.97 }}
              onClick={() => onSelect(c.id, c.preference)}
            >
              <span className="booster-piece" aria-hidden />
              <span className="booster-label">{c.label}</span>
              <span className="booster-hint">{c.hint} ◎</span>
            </motion.button>
          )
        })}
      </div>
    </div>
  )
}
