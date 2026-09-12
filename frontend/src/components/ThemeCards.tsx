import { motion } from 'framer-motion'
import type { BalloonType } from '../api/types'
import './ThemeCards.css'

interface ThemeCardsProps {
  selected: BalloonType
  onSelect: (type: BalloonType) => void
}

const themes: Array<{
  type: BalloonType
  title: string
  subtitle: string
  tone: 'red' | 'green'
}> = [
  {
    type: 'LUCKY',
    title: 'Красный шар',
    subtitle: '12 уровней · от 25 ◎',
    tone: 'red',
  },
  {
    type: 'STANDARD',
    title: 'Зеленый шар',
    subtitle: '9 уровней · от 12 ◎',
    tone: 'green',
  },
]

export function ThemeCards({ selected, onSelect }: ThemeCardsProps) {
  return (
    <div className="theme-grid" role="listbox" aria-label="Выбор темы">
      {themes.map((t) => {
        const active = selected === t.type
        return (
          <motion.button
            key={t.type}
            type="button"
            role="option"
            aria-selected={active}
            className={`theme-card tone-${t.tone}${active ? ' is-selected' : ''}`}
            whileTap={{ scale: 0.97 }}
            onClick={() => onSelect(t.type)}
          >
            <span className={`balloon-orb tone-${t.tone}`} aria-hidden />
            <span className="theme-title">{t.title}</span>
            <span className="theme-sub">{t.subtitle}</span>
          </motion.button>
        )
      })}
    </div>
  )
}
