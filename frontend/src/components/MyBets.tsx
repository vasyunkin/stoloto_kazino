import { useEffect, useState } from 'react'
import { getMyHistory } from '../api/gameApi'
import type { OwnRoundResponse } from '../api/types'
import { usePlayerStore } from '../stores/playerStore'

function money(value: number | string | null | undefined): string {
  if (value == null) return '—'
  const n = typeof value === 'number' ? value : Number(value)
  return Number.isFinite(n) ? n.toFixed(2) : '—'
}

function outcome(row: OwnRoundResponse): string {
  if (row.status === 'CASHED_OUT') return `забрал ${money(row.cashoutMultiplier)}×`
  if (row.status === 'CRASHED') return `взрыв ${money(row.crashPoint)}×`
  if (row.status === 'VOID') return 'void'
  return row.status
}

/** Personal stake journal. Public pills stay on the shared strip. */
export function MyBets() {
  const playerId = usePlayerStore((s) => s.playerId)
  const [rows, setRows] = useState<OwnRoundResponse[]>([])

  useEffect(() => {
    if (!playerId) return
    let cancelled = false
    getMyHistory(playerId, 8)
      .then((data) => {
        if (!cancelled) setRows(data)
      })
      .catch(() => {
        if (!cancelled) setRows([])
      })
    return () => {
      cancelled = true
    }
  }, [playerId])

  return (
    <section className="my-bets" aria-label="Мои ставки">
      <h3 className="history-title">Мои ставки</h3>
      {rows.length === 0 ? (
        <p className="history-empty">Пока нет своих раундов</p>
      ) : (
        <ul className="my-bets-list">
          {rows.map((row) => (
            <li key={row.gameId}>
              <span>{money(row.betAmount)}</span>
              <span>{outcome(row)}</span>
              <span>{row.status === 'CASHED_OUT' ? `+${money(row.winAmount)}` : '0'}</span>
            </li>
          ))}
        </ul>
      )}
    </section>
  )
}
