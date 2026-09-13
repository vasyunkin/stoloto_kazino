import { motion } from 'framer-motion'
import { playClickSfx } from '../audio/clickSfx'
import { hoverHandlers } from '../audio/sfxHandlers'
import { PUZZLE_SLOT_COUNT, usePuzzleAlbumStore } from '../stores/puzzleAlbumStore'
import './AlbumBanner.css'

interface AlbumBannerProps {
  onOpen: () => void
}

export function AlbumBanner({ onOpen }: AlbumBannerProps) {
  const collected = usePuzzleAlbumStore((s) => Object.keys(s.pieces).length)

  return (
    <motion.button
      type="button"
      className="album-banner"
      whileTap={{ scale: 0.99 }}
      {...hoverHandlers()}
      onClick={() => {
        playClickSfx()
        onOpen()
      }}
      aria-label="Открыть альбом пазла"
    >
      <span className="album-banner-icon" aria-hidden>
        <span className="album-banner-piece" />
      </span>
      <span className="album-banner-copy">
        <strong>Альбом карты</strong>
        <span>
          Фрагменты пазла · {collected}/{PUZZLE_SLOT_COUNT}
        </span>
      </span>
    </motion.button>
  )
}
