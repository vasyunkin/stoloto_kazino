import { useEffect, useMemo, useState } from 'react'
import { ApiError } from '../api/client'
import {
  adminLogout,
  fetchAdminConfig,
  fetchConfigHistory,
  saveAdminConfig,
  type ConfigHistoryItem,
} from './adminApi'

export function AdminConfigScreen() {
  const [text, setText] = useState('')
  const [history, setHistory] = useState<ConfigHistoryItem[]>([])
  const [status, setStatus] = useState<{ kind: 'ok' | 'error'; text: string } | null>(null)
  const [busy, setBusy] = useState(false)

  const parseError = useMemo(() => {
    try {
      JSON.parse(text)
      return null
    } catch (e) {
      return e instanceof Error ? e.message : 'Невалидный JSON'
    }
  }, [text])

  useEffect(() => {
    void load()
  }, [])

  async function load() {
    setBusy(true)
    setStatus(null)
    try {
      const [config, items] = await Promise.all([fetchAdminConfig(), fetchConfigHistory(20)])
      setText(JSON.stringify(config, null, 2))
      setHistory(items)
    } catch (e) {
      setStatus({ kind: 'error', text: errorMessage(e) })
    } finally {
      setBusy(false)
    }
  }

  async function onSave() {
    if (parseError) {
      setStatus({ kind: 'error', text: parseError })
      return
    }
    setBusy(true)
    setStatus(null)
    try {
      const payload = JSON.parse(text) as unknown
      const saved = await saveAdminConfig(payload)
      setText(JSON.stringify(saved, null, 2))
      setHistory(await fetchConfigHistory(20))
      setStatus({ kind: 'ok', text: 'Сохранено. Летящие раунды не затрагиваются (I8).' })
    } catch (e) {
      setStatus({ kind: 'error', text: errorMessage(e) })
    } finally {
      setBusy(false)
    }
  }

  async function onLogout() {
    await adminLogout()
    window.location.replace('/admin/login')
  }

  return (
    <div className="admin-shell">
      <header className="admin-header">
        <div>
          <h1>Конфиг игры</h1>
          <p className="admin-muted">Редактор JSON текущего снимка. Секреты сервер не отдаёт.</p>
        </div>
        <button className="admin-btn danger" type="button" onClick={() => void onLogout()}>
          Выйти
        </button>
      </header>

      <section className="admin-card">
        <p className="admin-muted">
          Изменение α / house edge / тем действует только на <strong>новые</strong> старты.
          Уже летящие раунды сохраняют снимок с момента POST /start.
        </p>
        <label className="admin-label" htmlFor="admin-json">JSON конфига</label>
        <textarea
          id="admin-json"
          className="admin-editor"
          spellCheck={false}
          value={text}
          onChange={(e) => setText(e.target.value)}
        />
        <div className="admin-actions">
          <button className="admin-btn" type="button" disabled={busy || !!parseError} onClick={() => void onSave()}>
            Сохранить
          </button>
          <button className="admin-btn secondary" type="button" disabled={busy} onClick={() => void load()}>
            Обновить
          </button>
        </div>
        {parseError && (
          <p className="admin-alert error">Невалидный JSON — запрос не отправлен. {parseError}</p>
        )}
        {status && <p className={`admin-alert ${status.kind}`}>{status.text}</p>}
      </section>

      <section className="admin-card" style={{ marginTop: 16 }}>
        <h2 style={{ margin: '0 0 8px', fontSize: '1.05rem' }}>История</h2>
        {history.length === 0 ? (
          <p className="admin-muted">Пока нет снимков.</p>
        ) : (
          <table className="admin-table">
            <thead>
              <tr>
                <th>Когда</th>
                <th>Кто</th>
                <th>Фрагмент</th>
              </tr>
            </thead>
            <tbody>
              {history.map((row) => (
                <tr key={row.id}>
                  <td>{formatTime(row.appliedAt)}</td>
                  <td>{row.appliedBy}</td>
                  <td className="admin-preview">{row.payloadPreview}</td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </section>
    </div>
  )
}

function errorMessage(e: unknown): string {
  if (e instanceof ApiError) {
    switch (e.code) {
      case 'CONFIG_VALIDATION_FAILED':
        return `Конфиг не прошёл проверку: ${e.message}`
      case 'AUTH_UNAUTHORIZED':
      case 'AUTH_TOKEN_EXPIRED':
        return 'Сессия истекла, войдите снова'
      case 'FORBIDDEN':
        return 'Недостаточно прав'
      default:
        return e.body?.message ?? e.message
    }
  }
  return e instanceof Error ? e.message : 'Ошибка запроса'
}

function formatTime(iso: string): string {
  const d = new Date(iso)
  if (Number.isNaN(d.getTime())) {
    return iso
  }
  return d.toLocaleString('ru-RU')
}
