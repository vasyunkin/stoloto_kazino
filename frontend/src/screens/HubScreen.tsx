import { motion } from 'framer-motion'
import { useEffect, useState } from 'react'
import { ApiError } from '../api/client'
import type { BalloonType } from '../api/types'
import { Header } from '../components/Header'
import { HelpModal } from '../components/HelpModal'
import { LeaderboardBanner } from '../components/LeaderboardBanner'
import { LeaderboardSheet } from '../components/LeaderboardSheet'
import { ThemeCards } from '../components/ThemeCards'
import { demoDeposit, ensureWallet } from '../hooks/wallet'
import { useGameStore } from '../stores/gameStore'
import { usePlayerStore } from '../stores/playerStore'
import './HubScreen.css'

export function HubScreen() {
  const balance = usePlayerStore((s) => s.balance)
  const playerId = usePlayerStore((s) => s.playerId)
  const balloonType = useGameStore((s) => s.balloonType)
  const setBalloonType = useGameStore((s) => s.setBalloonType)
  const setPhase = useGameStore((s) => s.setPhase)

  const [busy, setBusy] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [helpOpen, setHelpOpen] = useState(false)
  const [lbOpen, setLbOpen] = useState(false)
  const [depositBusy, setDepositBusy] = useState(false)

  useEffect(() => {
    let cancelled = false
    setBusy(true)
    ensureWallet(true)
      .then(() => {
        if (!cancelled) setError(null)
      })
      .catch((err) => {
        if (cancelled) return
        const msg =
          err instanceof ApiError
            ? `${err.code}: ${err.message}`
            : 'Нет связи с API. Запусти backend на :8080.'
        setError(msg)
      })
      .finally(() => {
        if (!cancelled) setBusy(false)
      })
    return () => {
      cancelled = true
    }
  }, [playerId])

  const onSelectTheme = (type: BalloonType) => {
    setBalloonType(type)
    setPhase('prefight')
  }

  const onDeposit = async () => {
    setDepositBusy(true)
    setError(null)
    try {
      await demoDeposit(500)
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Deposit failed')
    } finally {
      setDepositBusy(false)
    }
  }

  return (
    <div className="hub-screen">
      <Header balance={balance} onHelp={() => setHelpOpen(true)} />

      <motion.section
        className="hub-hero"
        initial={{ opacity: 0, y: 10 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ duration: 0.35 }}
      >
        <div className="hub-logo" aria-hidden>
          <span className="hub-logo-balloon" />
        </div>
        <h1 className="hub-brand">
          ВОЗДУШНЫЙ
          <br />
          ШАР
        </h1>
      </motion.section>

      <section className="hub-main">
        <ThemeCards selected={balloonType} onSelect={onSelectTheme} />
        <LeaderboardBanner onOpen={() => setLbOpen(true)} />

        <div className="hub-wallet">
          <button
            type="button"
            className="deposit-btn"
            onClick={onDeposit}
            disabled={depositBusy || busy}
          >
            {depositBusy ? 'Пополняю…' : 'Пополнить +500'}
          </button>
          <p className="hub-player">id: {playerId}</p>
        </div>

        {busy && <p className="hub-status">Синхронизация баланса…</p>}
        {error && <p className="hub-error" role="alert">{error}</p>}
      </section>

      <HelpModal open={helpOpen} onClose={() => setHelpOpen(false)} />
      <LeaderboardSheet open={lbOpen} onClose={() => setLbOpen(false)} />
    </div>
  )
}
