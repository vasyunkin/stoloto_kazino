import { motion } from 'framer-motion'
import { useEffect, useMemo, useState } from 'react'
import { ApiError } from '../api/client'
import { startGame } from '../api/gameApi'
import { BetChips } from '../components/BetChips'
import { BoosterCards, type BoosterCardId } from '../components/BoosterCards'
import { Header } from '../components/Header'
import { HelpModal } from '../components/HelpModal'
import { playErrorSfx, playStartSfx } from '../audio/clickSfx'
import { disabledPressHandlers, hoverHandlers } from '../audio/sfxHandlers'
import { ensureWallet } from '../hooks/wallet'
import { useGameStore } from '../stores/gameStore'
import { usePlayerStore } from '../stores/playerStore'
import './PrefightScreen.css'

const MIN_BET = { STANDARD: 12, LUCKY: 25 } as const

export function PrefightScreen() {
  const balance = usePlayerStore((s) => s.balance)
  const boosterCharges = usePlayerStore((s) => s.boosterCharges)
  const playerId = usePlayerStore((s) => s.playerId)

  const balloonType = useGameStore((s) => s.balloonType)
  const betAmount = useGameStore((s) => s.betAmount)
  const boosterPreference = useGameStore((s) => s.boosterPreference)
  const setBetAmount = useGameStore((s) => s.setBetAmount)
  const setBoosterPreference = useGameStore((s) => s.setBoosterPreference)
  const setPhase = useGameStore((s) => s.setPhase)
  const beginRound = useGameStore((s) => s.beginRound)

  const [cardId, setCardId] = useState<BoosterCardId>(
    boosterPreference === 'NONE' || boosterCharges <= 0 ? 'none' : 'auto',
  )
  const [starting, setStarting] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [helpOpen, setHelpOpen] = useState(false)

  useEffect(() => {
    const min = MIN_BET[balloonType]
    if (betAmount < min) setBetAmount(min)
  }, [balloonType, betAmount, setBetAmount])

  useEffect(() => {
    if (boosterCharges <= 0 && cardId === 'auto') {
      setCardId('none')
      setBoosterPreference('NONE')
    }
  }, [boosterCharges, cardId, setBoosterPreference])

  const themeLabel = balloonType === 'LUCKY' ? 'Красный шар' : 'Зеленый шар'
  const minBet = MIN_BET[balloonType]
  const canStart = useMemo(
    () =>
      betAmount >= minBet &&
      betAmount <= balance &&
      !starting &&
      (boosterPreference === 'NONE' || boosterCharges > 0),
    [betAmount, balance, starting, minBet, boosterPreference, boosterCharges],
  )

  const onStart = async () => {
    if (!canStart) {
      playErrorSfx()
      return
    }
    playStartSfx()
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
      await ensureWallet(false)
    } catch (err) {
      playErrorSfx()
      if (err instanceof ApiError) {
        if (err.code === 'INSUFFICIENT_BALANCE') {
          setError('Недостаточно средств — пополни баланс на Hub')
        } else if (err.code === 'NO_BOOSTER_CHARGES') {
          setError('Нет зарядов бустера — выбери «Без» или сделай депозит')
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
        <p className="prefight-theme">
          {themeLabel} · мин. ставка {minBet} ◎ · заряды {boosterCharges}
        </p>

        <section className="prefight-section">
          <h3>Ставка</h3>
          <BetChips
            value={betAmount}
            balance={balance}
            balloonType={balloonType}
            onChange={setBetAmount}
          />
        </section>

        <BoosterCards
          selectedId={cardId}
          charges={boosterCharges}
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
          {...hoverHandlers()}
          {...disabledPressHandlers(!canStart)}
          onClick={onStart}
        >
          {starting ? 'Старт…' : 'Начать'}
        </button>
      </motion.div>

      <HelpModal open={helpOpen} onClose={() => setHelpOpen(false)} />
    </div>
  )
}
