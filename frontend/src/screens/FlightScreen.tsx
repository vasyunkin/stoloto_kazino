import { AnimatePresence, motion } from 'framer-motion'
import { useEffect, useState } from 'react'
import { Header } from '../components/Header'
import { HelpModal } from '../components/HelpModal'
import { useDisplayMultiplier } from '../hooks/useDisplayMultiplier'
import { useGameTransport } from '../hooks/useGameTransport'
import { BalloonStage } from '../pixi/BalloonStage'
import { useGameStore } from '../stores/gameStore'
import { usePlayerStore } from '../stores/playerStore'
import './FlightScreen.css'

/**
 * F4 — Pixi stage + STOMP/poll transport. Cashout action → F5.
 */
export function FlightScreen() {
  const balance = usePlayerStore((s) => s.balance)
  const gameId = useGameStore((s) => s.gameId)
  const status = useGameStore((s) => s.status)
  const betAmount = useGameStore((s) => s.betAmount)
  const balloonType = useGameStore((s) => s.balloonType)
  const commitHash = useGameStore((s) => s.commitHash)
  const pointsTotal = useGameStore((s) => s.pointsTotal)
  const pointsDelta = useGameStore((s) => s.pointsDelta)
  const isCashoutPending = useGameStore((s) => s.isCashoutPending)
  const booster = useGameStore((s) => s.booster)
  const winAmount = useGameStore((s) => s.winAmount)
  const puzzlePieceIndex = useGameStore((s) => s.puzzlePieceIndex)
  const resetToHub = useGameStore((s) => s.resetToHub)

  const flying = status === 'FLYING'
  useGameTransport(Boolean(gameId) && flying)

  const displayK = useDisplayMultiplier()
  const [helpOpen, setHelpOpen] = useState(false)
  const [hintGone, setHintGone] = useState(false)

  useEffect(() => {
    if (!flying) return
    const t = window.setTimeout(() => setHintGone(true), 4000)
    return () => window.clearTimeout(t)
  }, [flying, gameId])

  const potentialWin = betAmount * displayK
  const cashoutDisabled = !flying || isCashoutPending
  const terminal = status === 'CRASHED' || status === 'CASHED_OUT' || status === 'VOID'

  return (
    <div className="flight-screen">
      <Header
        balance={balance}
        showBack
        onBack={resetToHub}
        onHelp={() => setHelpOpen(true)}
      />

      <div className="flight-stage">
        <BalloonStage balloonType={balloonType} />

        <div className="flight-overlay">
          <AnimatePresence>
            {!hintGone && flying && (
              <motion.p
                className="flight-hint"
                initial={{ opacity: 0, y: -8 }}
                animate={{ opacity: 1, y: 0 }}
                exit={{ opacity: 0 }}
              >
                Нажимай «Забрать», пока шарик не лопнул!
              </motion.p>
            )}
          </AnimatePresence>

          <div className="flight-readout">
            <div className="flight-win">{potentialWin.toFixed(0)} ◎</div>
            <div className="flight-k">{displayK.toFixed(2)}x</div>
          </div>

          {booster?.spawned && (
            <p className="flight-booster">
              Бустер {booster.name}
              {booster.activated ? ' · активен' : ` · линия ${booster.triggerLine}`}
            </p>
          )}

          {terminal && (
            <motion.div
              className={`flight-terminal status-${status}`}
              initial={{ opacity: 0, scale: 0.96 }}
              animate={{ opacity: 1, scale: 1 }}
            >
              {status === 'CRASHED' && <strong>Шар лопнул!</strong>}
              {status === 'CASHED_OUT' && <strong>Забрал!</strong>}
              {status === 'VOID' && <strong>Раунд VOID</strong>}
              {winAmount != null && <span>+{Number(winAmount).toFixed(0)} ◎</span>}
              {puzzlePieceIndex != null && <span>пазл #{puzzlePieceIndex}</span>}
              <button type="button" className="flight-again" onClick={resetToHub}>
                В лобби
              </button>
            </motion.div>
          )}
        </div>
      </div>

      <footer className="flight-footer">
        <div className="flight-stats">
          <span>Выигрыш:</span>
          <strong>{potentialWin.toFixed(0)} ◎</strong>
          <span className="flight-pts">★ {pointsTotal}</span>
          {pointsDelta > 0 && <span className="flight-delta">+{pointsDelta}</span>}
        </div>
        <button type="button" className="cashout-btn" disabled={cashoutDisabled}>
          Забрать
        </button>
      </footer>

      {commitHash && (
        <p className="flight-commit" title={commitHash}>
          commit {commitHash.slice(0, 12)}…
        </p>
      )}

      <HelpModal open={helpOpen} onClose={() => setHelpOpen(false)} />
    </div>
  )
}
