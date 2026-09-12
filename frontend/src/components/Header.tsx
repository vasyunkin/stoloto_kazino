import { motion } from 'framer-motion'
import './Header.css'

interface HeaderProps {
  balance: number
  onBack?: () => void
  onHelp?: () => void
  showBack?: boolean
}

export function Header({ balance, onBack, onHelp, showBack = false }: HeaderProps) {
  return (
    <header className="game-header">
      <button
        type="button"
        className="icon-btn"
        aria-label={showBack ? 'Назад' : 'Меню'}
        onClick={onBack}
        disabled={!onBack}
      >
        ‹
      </button>
      <motion.div
        className="balance-chip"
        key={balance}
        initial={{ scale: 0.96 }}
        animate={{ scale: 1 }}
        transition={{ type: 'spring', stiffness: 380, damping: 22 }}
      >
        <span className="balance-value">{Math.floor(balance)}</span>
        <span className="balance-currency" aria-hidden>
          ◎
        </span>
      </motion.div>
      <button type="button" className="icon-btn" aria-label="Правила" onClick={onHelp}>
        ?
      </button>
    </header>
  )
}
