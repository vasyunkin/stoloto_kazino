import { useState } from 'react'
import { fetchStats, searchPlayers, type GgrResponse, type PlayerAdminRow } from './adminApi'

/** Operator desk: turnover and player lookup. The bet switch lives in the config form. */
export function AdminDesk() {
  const [stats, setStats] = useState<GgrResponse | null>(null)
  const [query, setQuery] = useState('')
  const [players, setPlayers] = useState<PlayerAdminRow[]>([])
  const [error, setError] = useState<string | null>(null)

  async function loadStats() {
    setError(null)
    try {
      setStats(await fetchStats())
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Не удалось прочитать статистику')
    }
  }

  async function find() {
    setError(null)
    try {
      setPlayers(await searchPlayers(query.trim()))
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Поиск не удался')
    }
  }

  return (
    <section className="admin-card">
      <h2>Стол</h2>
      <p className="admin-muted">
        Новые ставки выключаются флажком «Приём ставок» в форме. Здесь оборот и поиск игрока.
      </p>
      <div className="admin-presets">
        <button className="admin-btn secondary" type="button" onClick={() => void loadStats()}>
          Показать оборот
        </button>
        {stats && (
          <span className="admin-muted">
            ставки {Number(stats.stakes).toFixed(2)} · выплаты {Number(stats.wins).toFixed(2)} · GGR{' '}
            {Number(stats.ggr).toFixed(2)}
          </span>
        )}
      </div>
      <div className="admin-presets">
        <input
          className="admin-input"
          value={query}
          placeholder="external id"
          onChange={(e) => setQuery(e.target.value)}
        />
        <button className="admin-btn secondary" type="button" onClick={() => void find()}>
          Найти
        </button>
      </div>
      {players.length > 0 && (
        <ul>
          {players.map((player) => (
            <li key={player.externalId}>
              {player.externalId} · {Number(player.balance).toFixed(2)} · заряды {player.boosterCharges}
              {player.account ? ' · аккаунт' : ' · гость'}
            </li>
          ))}
        </ul>
      )}
      {error && <p className="admin-alert error">{error}</p>}
    </section>
  )
}
