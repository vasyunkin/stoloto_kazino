import { AnimatePresence, motion } from 'framer-motion'
import type { RoundStatus } from '../api/types'
import './ResultModal.css'

interface ResultModalProps {
  open: boolean
  status: RoundStatus | 'IDLE'
  betAmount: number
  multiplier: number
  winAmount: number | null
  pointsTotal: number
  puzzlePieceIndex: number | null
  onAgain: () => void
}

export function ResultModal({
  open,
  status,
  betAmount,
  multiplier,
  winAmount,
  pointsTotal,
  puzzlePieceIndex,
  onAgain,
}: ResultModalProps) {
  const win = status === 'CASHED_OUT'
  const crash = status === 'CRASHED'
  const voided = status === 'VOID'
  const showPuzzle = (win || crash) && puzzlePieceIndex != null

  return (
    <AnimatePresence>
      {open && (
        <motion.div
          className="result-backdrop"
          initial={{ opacity: 0 }}
          animate={{ opacity: 1 }}
          exit={{ opacity: 0 }}
        >
          <motion.div
            className={`result-card ${win ? 'is-win' : crash ? 'is-crash' : 'is-void'}`}
            role="dialog"
            aria-modal
            aria-labelledby="result-title"
            initial={{ y: 28, opacity: 0, scale: 0.96 }}
            animate={{ y: 0, opacity: 1, scale: 1 }}
            exit={{ y: 16, opacity: 0 }}
            transition={{ type: 'spring', stiffness: 380, damping: 26 }}
          >
            <p className="result-emoji" aria-hidden>
              {win ? '🏆' : crash ? '💥' : '↩️'}
            </p>
            <h2 id="result-title">
              {win && 'Победа!'}
              {crash && 'Шар лопнул!'}
              {voided && 'Раунд отменён'}
            </h2>
            <p className="result-sub">
              {win && 'Ставка забрана вовремя'}
              {crash && 'Ставка потеряна'}
              {voided && 'Возврат после рестарта сервера'}
            </p>

            <dl className="result-stats">
              {win && winAmount != null && (
                <div>
                  <dt>Выигрыш</dt>
                  <dd>{Number(winAmount).toFixed(0)} ◎</dd>
                </div>
              )}
              {win && (
                <div>
                  <dt>Коэффициент</dt>
                  <dd>{multiplier.toFixed(2)}x</dd>
                </div>
              )}
              <div>
                <dt>Ставка</dt>
                <dd>{betAmount} ◎</dd>
              </div>
              <div>
                <dt>Очки</dt>
                <dd>+{pointsTotal}</dd>
              </div>
            </dl>

            {showPuzzle && (
              <div className="result-puzzle">
                <span className="result-puzzle-piece" aria-hidden />
                <div>
                  <strong>Награда</strong>
                  <p>Фрагмент пазла #{puzzlePieceIndex}</p>
                </div>
              </div>
            )}

            <button type="button" className="result-again" onClick={onAgain}>
              Играть снова
            </button>
          </motion.div>
        </motion.div>
      )}
    </AnimatePresence>
  )
}
