import { motion } from 'framer-motion'
import { playClickSfx } from '../audio/clickSfx'
import { useSettingsStore } from '../stores/settingsStore'
import './Header.css'

interface HeaderProps {
  balance: number
  onBack?: () => void
  onHelp?: () => void
  showBack?: boolean
}

export function Header({ balance, onBack, onHelp, showBack = false }: HeaderProps) {
  const sfxMuted = useSettingsStore((s) => s.sfxMuted)
  const toggleSfxMuted = useSettingsStore((s) => s.toggleSfxMuted)

  return (
    <header className="game-header">
      <button
        type="button"
        className="icon-btn"
        aria-label={showBack ? 'Назад' : 'Меню'}
        onClick={() => {
          playClickSfx()
          onBack?.()
        }}
        disabled={!onBack}
      >
        ‹
      </button>
      <motion.div
        className="balance-chip steam-plate"
        key={balance}
        initial={{ scale: 0.96 }}
        animate={{ scale: 1 }}
        transition={{ type: 'spring', stiffness: 380, damping: 22 }}
      >
        <span className="balance-value steam-meter">{Math.floor(balance)}</span>
        <span className="balance-currency" aria-hidden>
          ◎
        </span>
      </motion.div>
      <div className="header-actions">
        <button
          type="button"
          className="icon-btn"
          aria-label={sfxMuted ? 'Включить звук' : 'Выключить звук'}
          aria-pressed={sfxMuted}
          onClick={() => {
            toggleSfxMuted()
            if (sfxMuted) playClickSfx()
          }}
        >
          {sfxMuted ? '∅' : '♪'}
        </button>
        <button
          type="button"
          className="icon-btn"
          aria-label="Правила"
          onClick={() => {
            playClickSfx()
            onHelp?.()
          }}
        >
          ?
        </button>
      </div>
    </header>
  )
}
