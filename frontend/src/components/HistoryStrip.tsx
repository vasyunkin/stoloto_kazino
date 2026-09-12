import { motion } from 'framer-motion'
import {
  historyPillLabel,
  historyPillTone,
} from '../api/historyDisplay'
import type { HistoryItemResponse } from '../api/types'
import './HistoryStrip.css'

interface HistoryStripProps {
  items: HistoryItemResponse[]
  title?: string
}

export function HistoryStrip({ items, title = 'Прошлые игры' }: HistoryStripProps) {
  if (items.length === 0) {
    return (
      <section className="history-strip" aria-label={title}>
        <h3 className="history-title">{title}</h3>
        <p className="history-empty">Пока нет завершённых раундов</p>
      </section>
    )
  }

  return (
    <section className="history-strip" aria-label={title}>
      <h3 className="history-title">{title}</h3>
      <div className="history-row" role="list">
        {items.map((item, index) => {
          const label = historyPillLabel(item.status, item.betAmount, item.winAmount)
          const tone = historyPillTone(item.status, item.betAmount, item.winAmount)
          return (
            <motion.span
              key={item.gameId}
              role="listitem"
              className={`history-pill tone-${tone}`}
              initial={{ opacity: 0, y: -6 }}
              animate={{ opacity: 1, y: 0 }}
              transition={{ delay: Math.min(index, 12) * 0.03 }}
              title={`${item.status} · ${item.balloonType}`}
            >
              {label}
            </motion.span>
          )
        })}
      </div>
    </section>
  )
}
