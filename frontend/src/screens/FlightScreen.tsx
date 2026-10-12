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
import { useGhostSpectate } from '../hooks/useGhostSpectate'
import { markFlightOnboardingSeen, shouldShowFlightOnboarding } from '../hooks/onboarding'
import { useDisplayMultiplier } from '../hooks/useDisplayMultiplier'
import { useGameHistory } from '../hooks/useGameHistory'
import { useGameTransport } from '../hooks/useGameTransport'
import { syncAlbum } from '../auth/playerApi'
import { getPlayerAccessToken } from '../auth/playerSession'
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
  const phase = useGameStore((s) => s.phase)
  const status = useGameStore((s) => s.status)
  const cashoutMultiplier = useGameStore((s) => s.cashoutMultiplier)
  const ghostBurst = useGameStore((s) => s.ghostBurst)
  const fairness = useGameStore((s) => s.fairness)
  const betAmount = useGameStore((s) => s.betAmount)
  const balloonType = useGameStore((s) => s.balloonType)
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
  const spectating = phase === 'spectating'
  const showResult = phase === 'result'
  useGameTransport(Boolean(gameId) && flying)
  useGhostSpectate()
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
    if (!showResult) {
      setResultOpen(false)
      return
    }
    const delay = status === 'CRASHED' ? 480 : 180
    const t = window.setTimeout(() => setResultOpen(true), delay)
    void ensureWallet(false)
    void reloadHistory()
    return () => window.clearTimeout(t)
  }, [showResult, status, gameId, reloadHistory])

  // Spec 5 V4 — persist puzzle fragment on CRASHED / CASHED_OUT (not VOID)
  useEffect(() => {
    if (status !== 'CRASHED' && status !== 'CASHED_OUT') return
    if (puzzlePieceIndex == null) return
    collectPiece(playerId, puzzlePieceIndex, gameId)
    if (!getPlayerAccessToken()) return
    const piece = Number.isInteger(puzzlePieceIndex)
      ? [{ slot: puzzlePieceIndex, ...(gameId ? { gameId } : {}) }]
      : []
    if (piece.length === 0) return
    void syncAlbum(piece).catch(() => {
      /* local album already has the piece; next login syncs again */
    })
  }, [status, puzzlePieceIndex, playerId, gameId, collectPiece])

  // Spec 5 V5 — game SFX (respect mute)
  const prevStatus = useRef(status)
  useEffect(() => {
    const prev = prevStatus.current
    prevStatus.current = status
    if (prev === 'FLYING' && status === 'CRASHED') playCrashSfx()
    if (prev === 'FLYING' && status === 'CASHED_OUT') playCashoutSfx()
  }, [status])

  const prevGhostBurst = useRef(false)
  useEffect(() => {
    if (ghostBurst && !prevGhostBurst.current) playCrashSfx()
    prevGhostBurst.current = ghostBurst
  }, [ghostBurst])

  const prevBoosterOn = useRef(false)
  useEffect(() => {
    const on = Boolean(booster?.activated)
    if (on && !prevBoosterOn.current) playBoosterSfx()
    prevBoosterOn.current = on
  }, [booster?.activated])

  const lockedK = cashoutMultiplier ?? multiplier
  const potentialWin = spectating || status === 'CASHED_OUT' ? (winAmount ?? betAmount * lockedK) : betAmount * displayK
  const minCashout = 1.01
  const cashoutDisabled =
    !flying || isCashoutPending || multiplier < minCashout || spectating

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
              {booster.activated
                ? `Бустер активен${booster.multiplier != null ? ` ×${booster.multiplier}` : ''}`
                : 'Бустер в этом полёте'}
            </p>
          )}

          {spectating && (
            <p className="flight-spectate">
              Выигрыш зафиксирован: {Number(winAmount ?? 0).toFixed(0)} ◎ на {lockedK.toFixed(2)}×
              {fairness
                ? `. Шар лопнул бы на ${fairness.crashPoint.toFixed(2)}×`
                : '. Считаем, где он лопнул бы…'}
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
          <span className="flight-payout">
            Выигрыш <strong>{potentialWin.toFixed(0)} ◎</strong>
          </span>
          <span className="flight-pts">
            ★ {pointsTotal}
            {pointsDelta > 0 && <span className="flight-delta">+{pointsDelta}</span>}
          </span>
        </div>
        <button
          type="button"
          className={`cashout-btn${
            flying && !isCashoutPending && multiplier >= minCashout ? ' is-armed' : ''
          }`}
          disabled={cashoutDisabled}
          {...hoverHandlers()}
          {...disabledPressHandlers(cashoutDisabled)}
          onClick={onCashout}
        >
          {spectating
            ? ghostBurst
              ? 'Лопнул'
              : 'Смотрим…'
            : isCashoutPending
              ? 'Забираю…'
              : flying && multiplier < minCashout
                ? `С ${minCashout.toFixed(2)}x`
                : 'Забрать'}
        </button>
      </footer>

      <ResultModal
        open={resultOpen && showResult && !albumOpen}
        status={status}
        betAmount={betAmount}
        multiplier={status === 'CASHED_OUT' ? lockedK : multiplier}
        theoreticalCrash={fairness?.crashPoint ?? null}
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
