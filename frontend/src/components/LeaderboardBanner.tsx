import { motion } from 'framer-motion'
import { playClickSfx } from '../audio/clickSfx'
import './LeaderboardBanner.css'

interface LeaderboardBannerProps {
  onOpen: () => void
}

export function LeaderboardBanner({ onOpen }: LeaderboardBannerProps) {
  return (
    <motion.button
      type="button"
      className="lb-banner"
      whileTap={{ scale: 0.99 }}
      onClick={() => {
        playClickSfx()
        onOpen()
      }}
      aria-label="Открыть рейтинг участников"
    >
      <span className="lb-trophy" aria-hidden>
        <span className="lb-badge">топ</span>
      </span>
      <span className="lb-copy">
        <strong>Рейтинг участников</strong>
        <span>Успей заработать больше всех очков и получай награды</span>
      </span>
    </motion.button>
  )
}
