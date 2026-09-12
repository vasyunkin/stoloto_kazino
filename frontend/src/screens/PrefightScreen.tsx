import { motion } from 'framer-motion'
import { useMemo, useState } from 'react'
import { ApiError } from '../api/client'
import { startGame } from '../api/gameApi'
import { BetChips } from '../components/BetChips'
import { BoosterCards, type BoosterCardId } from '../components/BoosterCards'
import { Header } from '../components/Header'
import { HelpModal } from '../components/HelpModal'
import { ensureWallet } from '../hooks/wallet'
import { useGameStore } from '../stores/gameStore'
import { usePlayerStore } from '../stores/playerStore'
import './PrefightScreen.css'

export function PrefightScreen() {
  const balance = usePlayerStore((s) => s.balance)
  const playerId = usePlayerStore((s) => s.playerId)
  const setBalance = usePlayerStore((s) => s.setBalance)

  const balloonType = useGameStore((s) => s.balloonType)
  const betAmount = useGameStore((s) => s.betAmount)
  const boosterPreference = useGameStore((s) => s.boosterPreference)
  const setBetAmount = useGameStore((s) => s.setBetAmount)
  const setBoosterPreference = useGameStore((s) => s.setBoosterPreference)
  const setPhase = useGameStore((s) => s.setPhase)
  const beginRound = useGameStore((s) => s.beginRound)

  const [cardId, setCardId] = useState<BoosterCardId>(
    boosterPreference === 'NONE' ? 'none' : 'x2',
  )
  const [starting, setStarting] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [helpOpen, setHelpOpen] = useState(false)

  const themeLabel = balloonType === 'LUCKY' ? 'Красный шар' : 'Зеленый шар'
  const canStart = useMemo(
    () => betAmount > 0 && betAmount <= balance && !starting,
    [betAmount, balance, starting],
  )

  const onStart = async () => {
    if (!canStart) return
    setStarting(true)
    setError(null)
    try {
      const res = await startGame(playerId, {
        playerId,
        betAmount,
        balloonType,
        boosterPreference,
      })
      beginRound(res.gameId, res.commitHash, betAmount, res.startedAt)
      // Refresh wallet after debit (don't auto-deposit here).
      const bal = await ensureWallet(false)
      setBalance(bal)
    } catch (err) {
      if (err instanceof ApiError) {
        if (err.code === 'INSUFFICIENT_BALANCE') {
          setError('Недостаточно средств — пополни баланс на Hub')
        } else {
          setError(`${err.code}: ${err.message}`)
        }
      } else {
        setError('Не удалось начать раунд')
      }
    } finally {
      setStarting(false)
    }
  }

  return (
    <div className="prefight-screen">
      <Header
        balance={balance}
        showBack
        onBack={() => setPhase('hub')}
        onHelp={() => setHelpOpen(true)}
      />

      <motion.div
        className="prefight-panel"
        initial={{ opacity: 0, y: 14 }}
        animate={{ opacity: 1, y: 0 }}
      >
        <p className="prefight-theme">{themeLabel}</p>

        <section className="prefight-section">
          <h3>Ставка</h3>
          <BetChips value={betAmount} balance={balance} onChange={setBetAmount} />
        </section>

        <BoosterCards
          selectedId={cardId}
          onSelect={(id, preference) => {
            setCardId(id)
            setBoosterPreference(preference)
          }}
        />

        {error && (
          <p className="prefight-error" role="alert">
            {error}
          </p>
        )}

        <button
          type="button"
          className="start-btn"
          disabled={!canStart}
          onClick={onStart}
        >
          {starting ? 'Старт…' : 'Начать'}
        </button>
      </motion.div>

      <HelpModal open={helpOpen} onClose={() => setHelpOpen(false)} />
    </div>
  )
}
