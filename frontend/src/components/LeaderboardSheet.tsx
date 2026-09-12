import { AnimatePresence, motion } from 'framer-motion'
import { useEffect, useState } from 'react'
import { ApiError } from '../api/client'
import { getLeaderboard } from '../api/gameApi'
import type { LeaderboardEntryResponse } from '../api/types'
import './LeaderboardSheet.css'

interface LeaderboardSheetProps {
  open: boolean
  onClose: () => void
}

export function LeaderboardSheet({ open, onClose }: LeaderboardSheetProps) {
  const [rows, setRows] = useState<LeaderboardEntryResponse[]>([])
  const [error, setError] = useState<string | null>(null)
  const [loading, setLoading] = useState(false)

  useEffect(() => {
    if (!open) return
    let cancelled = false
    setLoading(true)
    setError(null)
    getLeaderboard(20)
      .then((data) => {
        if (!cancelled) setRows(data)
      })
      .catch((err) => {
        if (cancelled) return
        setError(err instanceof ApiError ? err.message : 'Не удалось загрузить рейтинг')
        setRows([])
      })
      .finally(() => {
        if (!cancelled) setLoading(false)
      })
    return () => {
      cancelled = true
    }
  }, [open])

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
            className="sheet-panel"
            role="dialog"
            aria-modal
            aria-labelledby="lb-title"
            initial={{ y: 40, opacity: 0 }}
            animate={{ y: 0, opacity: 1 }}
            exit={{ y: 24, opacity: 0 }}
            transition={{ type: 'spring', stiffness: 380, damping: 28 }}
            onClick={(e) => e.stopPropagation()}
          >
            <header className="sheet-head">
              <h2 id="lb-title">Рейтинг участников</h2>
              <button type="button" className="sheet-close" onClick={onClose} aria-label="Закрыть">
                ×
              </button>
            </header>
            {loading && <p className="sheet-muted">Загрузка…</p>}
            {error && <p className="sheet-error">{error}</p>}
            {!loading && !error && rows.length === 0 && (
              <p className="sheet-muted">Пока пусто — сыграйте раунд, чтобы попасть в топ.</p>
            )}
            <ol className="lb-list">
              {rows.map((r) => (
                <li key={r.externalId}>
                  <span className="lb-rank">{r.rank}</span>
                  <span className="lb-id">{r.externalId}</span>
                  <span className="lb-pts">{r.totalPoints}</span>
                </li>
              ))}
            </ol>
          </motion.div>
        </motion.div>
      )}
    </AnimatePresence>
  )
}
