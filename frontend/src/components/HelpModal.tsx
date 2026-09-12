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
              <li>Выбери тему шара и сделай ставку.</li>
              <li>Шар поднимается — коэффициент растёт.</li>
              <li>Нажми «Забрать», пока шар не лопнул.</li>
              <li>Очки копятся за линии высоты и бустер.</li>
              <li>Исход считает сервер (Provably Fair после раунда).</li>
            </ol>
          </motion.div>
        </motion.div>
      )}
    </AnimatePresence>
  )
}
