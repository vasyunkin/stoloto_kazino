import { motion } from 'framer-motion'
import { useEffect, useMemo, useState } from 'react'
import { ApiError } from '../api/client'
import { getBetLimits, startGame } from '../api/gameApi'
import { BetChips } from '../components/BetChips'
import { BoosterCards, type BoosterCardId } from '../components/BoosterCards'
import { Header } from '../components/Header'
import { HelpModal } from '../components/HelpModal'
import { playErrorSfx, playStartSfx } from '../audio/clickSfx'
import { disabledPressHandlers, hoverHandlers } from '../audio/sfxHandlers'
import {
  DEFAULT_BET_LIMITS,
  betProblem,
  formatBet,
  maxAffordableBet,
  parseBetDraft,
  type BetLimits,
} from '../game/betAmount'
import { ensureWallet } from '../hooks/wallet'
import { useGameStore } from '../stores/gameStore'
import { usePlayerStore } from '../stores/playerStore'
import './PrefightScreen.css'

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
  const [autoCashout, setAutoCashout] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [helpOpen, setHelpOpen] = useState(false)
  const [limits, setLimits] = useState(DEFAULT_BET_LIMITS)
  const [draft, setDraft] = useState(() => formatBet(betAmount))

  useEffect(() => {
    let cancelled = false
    getBetLimits()
      .then((res) => {
        if (cancelled) return
        setLimits({
          STANDARD: toLimits(res.standard),
          LUCKY: toLimits(res.lucky),
        })
      })
      .catch(() => {
        /* keep the built-in theme bounds */
      })
    return () => {
      cancelled = true
    }
  }, [])

  const themeLimits = limits[balloonType]
  const problem = betProblem(draft, themeLimits, balance)
  const maxStake = maxAffordableBet(themeLimits, balance)

  useEffect(() => {
    if (boosterCharges <= 0 && cardId === 'auto') {
      setCardId('none')
      setBoosterPreference('NONE')
    }
  }, [boosterCharges, cardId, setBoosterPreference])

  const themeLabel = balloonType === 'LUCKY' ? 'Красный шар' : 'Зеленый шар'
  const canStart = useMemo(
    () => problem == null && !starting && (boosterPreference === 'NONE' || boosterCharges > 0),
    [problem, starting, boosterPreference, boosterCharges],
  )

  const applyStake = (amount: number) => {
    setBetAmount(amount)
    setDraft(formatBet(amount))
    setError(null)
  }

  const onStart = async () => {
    if (!canStart) {
      playErrorSfx()
      return
    }
    playStartSfx()
    setStarting(true)
    setError(null)
    try {
      const stake = parseBetDraft(draft)
      if (stake == null) return
      const auto = Number(autoCashout.replace(',', '.'))
      const res = await startGame(playerId, {
        playerId,
        betAmount: stake,
        balloonType,
        boosterPreference,
        autoCashoutMultiplier: Number.isFinite(auto) && auto >= 1.01 ? auto : undefined,
      })
      beginRound(res.gameId, res.commitHash, stake, res.startedAt)
      await ensureWallet(false)
    } catch (err) {
      playErrorSfx()
      if (err instanceof ApiError) {
        if (err.code === 'INSUFFICIENT_BALANCE') {
          setError('Недостаточно средств — пополни баланс на Hub')
        } else if (err.code === 'INVALID_BET') {
          setError('Ставка вне диапазона этой темы')
        } else if (err.code === 'NO_BOOSTER_CHARGES') {
          setError('Нет зарядов бустера — выбери «Без» или сделай депозит')
        } else if (err.code === 'ROUND_IN_PROGRESS') {
          setError('Сначала дождись конца текущего полёта')
        } else if (err.code === 'BETS_CLOSED') {
          setError('Приём ставок закрыт')
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
          {themeLabel} · {formatBet(themeLimits.minBetAmount)}–{formatBet(themeLimits.maxBetAmount)} ◎ · заряды {boosterCharges}
        </p>

        <section className="prefight-section">
          <h3>Ставка</h3>
          <BetChips
            draft={draft}
            balance={balance}
            balloonType={balloonType}
            minBet={themeLimits.minBetAmount}
            maxBet={themeLimits.maxBetAmount}
            problem={problem}
            maxStake={maxStake}
            onDraft={(raw) => {
              setDraft(raw)
              setError(null)
              const parsed = parseBetDraft(raw)
              if (parsed != null) setBetAmount(parsed)
            }}
            onPick={applyStake}
          />
        </section>

        <section className="prefight-section">
          <h3>Забрать автоматически</h3>
          <input
            className="prefight-input"
            inputMode="decimal"
            placeholder="например 2.00, пусто — только кнопка"
            value={autoCashout}
            onChange={(e) => setAutoCashout(e.target.value)}
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

function toLimits(raw: { minBetAmount: number | string; maxBetAmount: number | string }): BetLimits {
  return {
    minBetAmount: Number(raw.minBetAmount),
    maxBetAmount: Number(raw.maxBetAmount),
  }
}
