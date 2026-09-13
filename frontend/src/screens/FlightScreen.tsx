import { AnimatePresence, motion } from 'framer-motion'
import { lazy, Suspense, useEffect, useRef, useState } from 'react'
import {
  playBoosterSfx,
  playCashoutArmSfx,
  playCashoutSfx,
  playCrashSfx,
  playErrorSfx,
} from '../audio/clickSfx'
import { disabledPressHandlers, hoverHandlers } from '../audio/sfxHandlers'
import { Header } from '../components/Header'
import { HelpModal } from '../components/HelpModal'
import { HistoryStrip } from '../components/HistoryStrip'
import { PuzzleAlbumSheet } from '../components/PuzzleAlbumSheet'
import { ResultModal } from '../components/ResultModal'
import { requestCashout } from '../hooks/cashout'
import { markFlightOnboardingSeen, shouldShowFlightOnboarding } from '../hooks/onboarding'
import { useDisplayMultiplier } from '../hooks/useDisplayMultiplier'
import { useGameHistory } from '../hooks/useGameHistory'
import { useGameTransport } from '../hooks/useGameTransport'
import { ensureWallet } from '../hooks/wallet'
import { useGameStore } from '../stores/gameStore'
import { usePlayerStore } from '../stores/playerStore'
import { usePuzzleAlbumStore } from '../stores/puzzleAlbumStore'
import './FlightScreen.css'

/** Spec 5 V5 — code-split Pixi so Hub/Prefight stay light. */
const BalloonStage = lazy(() =>
  import('../pixi/BalloonStage').then((m) => ({ default: m.BalloonStage })),
)

/** F5/F6 — cashout, result, history strip, one-shot onboarding. */
export function FlightScreen() {
  const balance = usePlayerStore((s) => s.balance)
  const playerId = usePlayerStore((s) => s.playerId)
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
  const multiplier = useGameStore((s) => s.multiplier)
  const resetToHub = useGameStore((s) => s.resetToHub)
  const collectPiece = usePuzzleAlbumStore((s) => s.collectPiece)

  const flying = status === 'FLYING'
  const terminal = status === 'CRASHED' || status === 'CASHED_OUT' || status === 'VOID'
  useGameTransport(Boolean(gameId) && flying)
  const { items: history, reload: reloadHistory } = useGameHistory(20)

  const displayK = useDisplayMultiplier()
  const [helpOpen, setHelpOpen] = useState(false)
  const [showHint, setShowHint] = useState(() => shouldShowFlightOnboarding())
  const [cashoutError, setCashoutError] = useState<string | null>(null)
  const [resultOpen, setResultOpen] = useState(false)
  const [albumOpen, setAlbumOpen] = useState(false)
  const [albumFocus, setAlbumFocus] = useState<number | null>(null)

  useEffect(() => {
    if (!flying || !showHint) return
    const t = window.setTimeout(() => {
      setShowHint(false)
      markFlightOnboardingSeen()
    }, 4200)
    return () => window.clearTimeout(t)
  }, [flying, gameId, showHint])

  useEffect(() => {
    if (!terminal) {
      setResultOpen(false)
      return
    }
    const delay = status === 'CRASHED' ? 480 : 180
    const t = window.setTimeout(() => setResultOpen(true), delay)
    void ensureWallet(false)
    void reloadHistory()
    return () => window.clearTimeout(t)
  }, [terminal, status, gameId, reloadHistory])

  // Spec 5 V4 — persist puzzle fragment on CRASHED / CASHED_OUT (not VOID)
  useEffect(() => {
    if (status !== 'CRASHED' && status !== 'CASHED_OUT') return
    if (puzzlePieceIndex == null) return
    collectPiece(playerId, puzzlePieceIndex, gameId)
  }, [status, puzzlePieceIndex, playerId, gameId, collectPiece])

  // Spec 5 V5 — game SFX (respect mute)
  const prevStatus = useRef(status)
  useEffect(() => {
    const prev = prevStatus.current
    prevStatus.current = status
    if (prev === 'FLYING' && status === 'CRASHED') playCrashSfx()
    if (prev === 'FLYING' && status === 'CASHED_OUT') playCashoutSfx()
  }, [status])

  const prevBoosterOn = useRef(false)
  useEffect(() => {
    const on = Boolean(booster?.activated)
    if (on && !prevBoosterOn.current) playBoosterSfx()
    prevBoosterOn.current = on
  }, [booster?.activated])

  const potentialWin = betAmount * displayK
  const minCashout = 1.01
  const cashoutDisabled =
    !flying || isCashoutPending || multiplier < minCashout

  const onCashout = async () => {
    if (cashoutDisabled) {
      playErrorSfx()
      return
    }
    playCashoutArmSfx()
    setCashoutError(null)
    const res = await requestCashout()
    if (!res.ok) {
      playErrorSfx()
      setCashoutError(res.message)
    }
  }

  return (
    <div className="flight-screen">
      <Header
        balance={balance}
        showBack
        onBack={resetToHub}
        onHelp={() => setHelpOpen(true)}
      />
      <HistoryStrip items={history} />

      <div className="flight-stage">
        <Suspense fallback={<div className="pixi-fallback" aria-hidden />}>
          <BalloonStage balloonType={balloonType} />
        </Suspense>

        <div className="flight-overlay">
          <AnimatePresence>
            {showHint && flying && (
              <motion.p
                className="flight-hint"
                initial={{ opacity: 0, y: -8, scale: 0.98 }}
                animate={{ opacity: 1, y: 0, scale: 1 }}
                exit={{ opacity: 0, y: -6 }}
              >
                Нажимай «Забрать», пока шарик не лопнул!
              </motion.p>
            )}
          </AnimatePresence>

          <div className="flight-readout" aria-live="polite">
            <div className="altimeter">
              <span className="altimeter-label">Альтиметр</span>
              <div className="flight-win">{potentialWin.toFixed(0)} ◎</div>
              <div className="flight-k">{displayK.toFixed(2)}x</div>
            </div>
          </div>

          {booster?.spawned && (
            <p className="flight-booster">
              Бустер {booster.name}
              {booster.activated ? ' · активен' : ` · линия ${booster.triggerLine}`}
            </p>
          )}

          {cashoutError && (
            <p className="flight-cashout-error" role="alert">
              {cashoutError}
            </p>
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
        <motion.button
          type="button"
          className="cashout-btn"
          disabled={cashoutDisabled}
          {...hoverHandlers()}
          {...disabledPressHandlers(cashoutDisabled)}
          onClick={onCashout}
          animate={
            flying && !isCashoutPending && multiplier >= minCashout
              ? {
                  scale: [1, 1.03, 1],
                  boxShadow: [
                    '0 0 0 rgba(46,204,113,0)',
                    '0 0 18px rgba(46,204,113,0.55)',
                    '0 0 0 rgba(46,204,113,0)',
                  ],
                }
              : { scale: 1 }
          }
          transition={flying && multiplier >= minCashout ? { repeat: Infinity, duration: 1.4 } : undefined}
        >
          {isCashoutPending
            ? 'Забираю…'
            : flying && multiplier < minCashout
              ? `С ${minCashout.toFixed(2)}x`
              : 'Забрать'}
        </motion.button>
      </footer>

      {commitHash && (
        <p className="flight-commit" title={commitHash}>
          commit {commitHash.slice(0, 12)}…
        </p>
      )}

      <ResultModal
        open={resultOpen && terminal && !albumOpen}
        status={status}
        betAmount={betAmount}
        multiplier={multiplier}
        winAmount={winAmount}
        pointsTotal={pointsTotal}
        puzzlePieceIndex={puzzlePieceIndex}
        onAgain={resetToHub}
        onOpenAlbum={() => {
          setAlbumFocus(puzzlePieceIndex)
          setAlbumOpen(true)
        }}
      />

      <PuzzleAlbumSheet
        open={albumOpen}
        focusSlot={albumFocus}
        onClose={() => {
          setAlbumOpen(false)
          setAlbumFocus(null)
        }}
      />

      <HelpModal open={helpOpen} onClose={() => setHelpOpen(false)} />
    </div>
  )
}
