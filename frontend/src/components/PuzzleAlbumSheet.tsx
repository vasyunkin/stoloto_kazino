import { AnimatePresence, motion } from 'framer-motion'
import { useEffect, useMemo, useState } from 'react'
import { playClickSfx } from '../audio/clickSfx'
import {
  PUZZLE_SLOT_COUNT,
  usePuzzleAlbumStore,
  type AlbumPiece,
} from '../stores/puzzleAlbumStore'
import { usePlayerStore } from '../stores/playerStore'
import './PuzzleAlbumSheet.css'

interface PuzzleAlbumSheetProps {
  open: boolean
  onClose: () => void
  /** Highlight this slot when opening from Result. */
  focusSlot?: number | null
}

function formatCollectedAt(iso: string): string {
  try {
    return new Date(iso).toLocaleString('ru-RU', {
      day: '2-digit',
      month: 'short',
      year: 'numeric',
      hour: '2-digit',
      minute: '2-digit',
    })
  } catch {
    return iso
  }
}

export function PuzzleAlbumSheet({ open, onClose, focusSlot = null }: PuzzleAlbumSheetProps) {
  const playerId = usePlayerStore((s) => s.playerId)
  const pieces = usePuzzleAlbumStore((s) => s.pieces)
  const loadForPlayer = usePuzzleAlbumStore((s) => s.loadForPlayer)
  const [preview, setPreview] = useState<{ index: number; piece: AlbumPiece } | null>(null)

  useEffect(() => {
    if (!open) return
    loadForPlayer(playerId)
  }, [open, playerId, loadForPlayer])

  useEffect(() => {
    if (!open) {
      setPreview(null)
      return
    }
    if (focusSlot != null && pieces[focusSlot]) {
      setPreview({ index: focusSlot, piece: pieces[focusSlot]! })
    }
  }, [open, focusSlot, pieces])

  const collected = useMemo(() => Object.keys(pieces).length, [pieces])
  const slots = useMemo(() => Array.from({ length: PUZZLE_SLOT_COUNT }, (_, i) => i), [])

  return (
    <AnimatePresence>
      {open && (
        <motion.div
          className="sheet-backdrop album-backdrop"
          initial={{ opacity: 0 }}
          animate={{ opacity: 1 }}
          exit={{ opacity: 0 }}
          onClick={onClose}
        >
          <motion.div
            className="sheet-panel album-panel"
            role="dialog"
            aria-modal
            aria-labelledby="album-title"
            initial={{ y: 40, opacity: 0 }}
            animate={{ y: 0, opacity: 1 }}
            exit={{ y: 24, opacity: 0 }}
            transition={{ type: 'spring', stiffness: 380, damping: 28 }}
            onClick={(e) => e.stopPropagation()}
          >
            <header className="sheet-head">
              <h2 id="album-title">Альбом карты</h2>
              <button
                type="button"
                className="sheet-close"
                onClick={() => {
                  playClickSfx()
                  onClose()
                }}
                aria-label="Закрыть"
              >
                ×
              </button>
            </header>

            <p className="album-progress">
              Собрано <strong className="steam-meter">{collected}</strong>
              <span className="steam-meter">/{PUZZLE_SLOT_COUNT}</span>
            </p>

            {collected === 0 && (
              <p className="album-empty">Летай — собирай фрагменты карты</p>
            )}

            <div className="album-grid" role="list" aria-label="Фрагменты пазла">
              {slots.map((i) => {
                const piece = pieces[i]
                const filled = Boolean(piece)
                return (
                  <button
                    key={i}
                    type="button"
                    role="listitem"
                    className={`album-slot${filled ? ' is-filled' : ''}${
                      focusSlot === i ? ' is-focus' : ''
                    }`}
                    disabled={!filled}
                    aria-label={
                      filled ? `Фрагмент ${i}, собран` : `Фрагмент ${i}, не найден`
                    }
                    onClick={() => {
                      if (!piece) return
                      playClickSfx()
                      setPreview({ index: i, piece })
                    }}
                  >
                    {filled ? (
                      <>
                        <span className="album-slot-piece" aria-hidden />
                        <span className="album-slot-num steam-meter">{i}</span>
                      </>
                    ) : (
                      <span className="album-slot-empty">·</span>
                    )}
                  </button>
                )
              })}
            </div>

            <AnimatePresence>
              {preview && (
                <motion.div
                  className="album-preview"
                  initial={{ opacity: 0, y: 8 }}
                  animate={{ opacity: 1, y: 0 }}
                  exit={{ opacity: 0, y: 6 }}
                >
                  <span className="album-preview-piece" aria-hidden />
                  <div>
                    <strong>Фрагмент #{preview.index}</strong>
                    <p>{formatCollectedAt(preview.piece.collectedAt)}</p>
                  </div>
                  <button
                    type="button"
                    className="album-preview-close"
                    onClick={() => {
                      playClickSfx()
                      setPreview(null)
                    }}
                  >
                    Скрыть
                  </button>
                </motion.div>
              )}
            </AnimatePresence>
          </motion.div>
        </motion.div>
      )}
    </AnimatePresence>
  )
}
