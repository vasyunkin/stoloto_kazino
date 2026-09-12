import { AnimatePresence, motion } from 'framer-motion'
import './HelpModal.css'

interface HelpModalProps {
  open: boolean
  onClose: () => void
}

export function HelpModal({ open, onClose }: HelpModalProps) {
  return (
    <AnimatePresence>
      {open && (
        <motion.div
          className="sheet-backdrop"
          initial={{ opacity: 0 }}
          animate={{ opacity: 1 }}
          exit={{ opacity: 0 }}
          onClick={onClose}
        >
          <motion.div
            className="sheet-panel help-panel"
            role="dialog"
            aria-modal
            aria-labelledby="help-title"
            initial={{ y: 40, opacity: 0 }}
            animate={{ y: 0, opacity: 1 }}
            exit={{ y: 24, opacity: 0 }}
            onClick={(e) => e.stopPropagation()}
          >
            <header className="sheet-head">
              <h2 id="help-title">Как играть</h2>
              <button type="button" className="sheet-close" onClick={onClose} aria-label="Закрыть">
                ×
              </button>
            </header>
            <ol className="help-list">
              <li>
                <strong>Коэффициент</strong> растёт экспоненциально: K(t)=e^(α·t) — сначала медленно,
                потом быстрее. «Забрать» с <strong>1.20x</strong>.
              </li>
              <li>
                <strong>Крах</strong> загадан до старта (чаще низкие множители). Минимум краха —
                1.20x, без мгновенного лосса на 1.00.
              </li>
              <li>
                <strong>Темы:</strong> зелёный — ниже ставка и спокойнее рост; красный — дороже,
                быстрее, выше потолок.
              </li>
              <li>
                <strong>Бустер</strong> — событие на уровне: умножает K и даёт очки. Тратит 1 заряд
                (пополнение с депозитом).
              </li>
              <li>
                <strong>Очки</strong> за линии и активацию бустера — даже при крахе; после «Забрать»
                больше не копятся.
              </li>
              <li>
                <strong>Provably Fair:</strong> до полёта — commitHash; после раунда — verify
                (seed, crashPoint, бустер).
              </li>
            </ol>
          </motion.div>
        </motion.div>
      )}
    </AnimatePresence>
  )
}
