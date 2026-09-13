import { motion, useReducedMotion } from 'framer-motion'
import type { BalloonType } from '../api/types'
import { playClickSfx } from '../audio/clickSfx'
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
  swayDelay: number
}> = [
  {
    type: 'LUCKY',
    title: 'Красный шар',
    subtitle: 'от 25 ◎ · быстрее · до 100x · 12 риск-уровней',
    tone: 'red',
    swayDelay: 0,
  },
  {
    type: 'STANDARD',
    title: 'Зеленый шар',
    subtitle: 'от 12 ◎ · спокойнее · до 50x · 6 риск-уровней',
    tone: 'green',
    swayDelay: 0.55,
  },
]

/** Spec 5 V2 — Hub baskets with heavy sway + distinct LUCKY/STANDARD silhouettes. */
export function ThemeCards({ selected, onSelect }: ThemeCardsProps) {
  const reduceMotion = useReducedMotion()

  return (
    <div className="hub-yard">
      <div className="hub-yard-sky" aria-hidden />
      <div className="hub-yard-haze" aria-hidden />
      <div className="hub-yard-map" aria-hidden />
      <div className="hub-yard-platform" aria-hidden />

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
              whileTap={{ scale: 0.985 }}
              onClick={() => {
                playClickSfx()
                onSelect(t.type)
              }}
            >
              <div className="aerostat" aria-hidden>
                <motion.div
                  className="aerostat-sway"
                  animate={
                    reduceMotion ? undefined : { rotate: [-2.2, 2.2], y: [0, -4, 0] }
                  }
                  transition={{
                    duration: 5.2,
                    repeat: Infinity,
                    ease: 'easeInOut',
                    delay: t.swayDelay,
                  }}
                >
                  <span className={`aerostat-envelope tone-${t.tone}`}>
                    <span className="aerostat-patch" />
                    <span className="aerostat-gleam" />
                    {t.tone === 'red' ? (
                      <span className="aerostat-gear" />
                    ) : (
                      <span className="aerostat-vine" />
                    )}
                  </span>
                  <span className="aerostat-ropes">
                    <i />
                    <i />
                    <i />
                  </span>
                  <span className={`aerostat-basket tone-${t.tone}`}>
                    {t.tone === 'red' ? (
                      <span className="basket-gear" title="copper gear" />
                    ) : (
                      <>
                        <span className="basket-pipe" />
                        <span className="basket-leaf" />
                      </>
                    )}
                  </span>
                </motion.div>
              </div>
              <span className="theme-copy">
                <span className="theme-title">{t.title}</span>
                <span className="theme-sub">{t.subtitle}</span>
              </span>
            </motion.button>
          )
        })}
      </div>
    </div>
  )
}
