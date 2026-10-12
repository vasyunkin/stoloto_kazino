import { useEffect, useMemo, useState } from 'react'
import { ApiError } from '../api/client'
import { AdminConfigForm } from './AdminConfigForm'
import { AdminDesk } from './AdminDesk'
import {
  adminLogout,
  fetchAdminConfig,
  fetchConfigHistory,
  saveAdminConfig,
  type ConfigHistoryItem,
} from './adminApi'
import {
  applyDepositDisabled,
  applyGentleHouseEdge,
  buildPatch,
  cloneConfig,
  diffConfig,
  fieldPathsFromValidationMessage,
  formatChange,
  setPath,
  validateDraft,
  type ConfigChange,
  type ConfigDoc,
} from './configModel'

type Tab = 'form' | 'json'

export function AdminConfigScreen() {
  const [baseline, setBaseline] = useState<ConfigDoc | null>(null)
  const [draft, setDraft] = useState<ConfigDoc | null>(null)
  const [tab, setTab] = useState<Tab>('form')
  const [jsonText, setJsonText] = useState('')
  const [jsonError, setJsonError] = useState<string | null>(null)
  const [serverErrors, setServerErrors] = useState<Record<string, string>>({})
  const [history, setHistory] = useState<ConfigHistoryItem[]>([])
  const [openHistory, setOpenHistory] = useState<string | null>(null)
  const [status, setStatus] = useState<{ kind: 'ok' | 'error'; text: string } | null>(null)
  const [busy, setBusy] = useState(false)

  const localErrors = useMemo(() => (draft ? validateDraft(draft) : {}), [draft])
  const errors = { ...localErrors, ...serverErrors }
  const changes = useMemo(
    () => (baseline && draft ? diffConfig(baseline, draft) : []),
    [baseline, draft],
  )

  useEffect(() => {
    void load()
  }, [])

  async function load() {
    setBusy(true)
    setStatus(null)
    setServerErrors({})
    try {
      const [config, items] = await Promise.all([fetchAdminConfig(), fetchConfigHistory(20)])
      const doc = cloneConfig(config)
      setBaseline(doc)
      setDraft(doc)
      setJsonText(JSON.stringify(doc, null, 2))
      setHistory(items)
      setJsonError(null)
    } catch (e) {
      setStatus({ kind: 'error', text: errorMessage(e) })
    } finally {
      setBusy(false)
    }
  }

  function edit(path: string, value: unknown) {
    setDraft((current) => (current ? setPath(current, path, value) : current))
    setServerErrors((current) => {
      if (!current[path]) return current
      const next = { ...current }
      delete next[path]
      return next
    })
  }

  function showForm(next: ConfigDoc) {
    setDraft(next)
    setTab('form')
    setJsonText(JSON.stringify(next, null, 2))
    setJsonError(null)
  }

  function openTab(next: Tab) {
    if (next === 'json' && draft) {
      setJsonText(JSON.stringify(draft, null, 2))
      setJsonError(null)
    }
    if (next === 'form' && tab === 'json') {
      const parsed = parseJson(jsonText)
      if (!parsed.ok) {
        setJsonError(parsed.error)
        return
      }
      setDraft(parsed.doc)
      setJsonError(null)
    }
    setTab(next)
  }

  async function onSave() {
    if (!baseline || !draft) return
    const source = tab === 'json' ? parseJson(jsonText) : { ok: true as const, doc: draft }
    if (!source.ok) {
      setJsonError(source.error)
      setStatus({ kind: 'error', text: source.error })
      return
    }
    const local = validateDraft(source.doc)
    if (Object.keys(local).length > 0) {
      setDraft(source.doc)
      setTab('form')
      setServerErrors({})
      setStatus({ kind: 'error', text: 'Проверьте подсвеченные поля — запрос не отправлен.' })
      return
    }
    const patch = buildPatch(baseline, source.doc)
    if (!patch) {
      setStatus({ kind: 'ok', text: 'Изменений нет.' })
      return
    }
    setBusy(true)
    setStatus(null)
    setServerErrors({})
    try {
      const saved = cloneConfig(await saveAdminConfig(patch))
      setBaseline(saved)
      setDraft(saved)
      setJsonText(JSON.stringify(saved, null, 2))
      setHistory(await fetchConfigHistory(20))
      setStatus({ kind: 'ok', text: 'Сохранено. Уже летящие раунды не затрагиваются.' })
    } catch (e) {
      const paths = e instanceof ApiError && e.code === 'CONFIG_VALIDATION_FAILED'
        ? fieldPathsFromValidationMessage(e.message)
        : []
      if (paths.length > 0) {
        const message = e instanceof ApiError ? e.message : 'Ошибка'
        const next: Record<string, string> = {}
        for (const path of paths) next[path] = message
        setServerErrors(next)
        setTab('form')
        document.querySelector(`[data-field="${cssEscape(paths[0])}"]`)?.scrollIntoView({ block: 'center' })
      }
      setStatus({ kind: 'error', text: errorMessage(e) })
    } finally {
      setBusy(false)
    }
  }

  async function onLogout() {
    await adminLogout()
    window.location.replace('/admin/login')
  }

  const saveDisabled = busy || (tab === 'form' ? Object.keys(localErrors).length > 0 : !!jsonError)

  return (
    <div className="admin-shell">
      <header className="admin-header">
        <div>
          <h1>Конфиг игры</h1>
          <p className="admin-muted">Поля с подписями и лимитами. Секреты сервер не отдаёт и форма их не показывает.</p>
        </div>
        <button className="admin-btn danger" type="button" onClick={() => void onLogout()}>
          Выйти
        </button>
      </header>

      <AdminDesk />

      <div className="admin-tabs" role="tablist">
        <button className={tab === 'form' ? 'is-active' : ''} type="button" onClick={() => openTab('form')}>
          Форма
        </button>
        <button className={tab === 'json' ? 'is-active' : ''} type="button" onClick={() => openTab('json')}>
          Для эксперта
        </button>
      </div>

      <section className="admin-card">
        <div className="admin-presets">
          <button
            className="admin-btn secondary"
            type="button"
            disabled={!draft}
            onClick={() => draft && showForm(applyGentleHouseEdge(draft))}
          >
            Щадящая доля казино 3%
          </button>
          <button
            className="admin-btn secondary"
            type="button"
            disabled={!draft}
            onClick={() => draft && showForm(applyDepositDisabled(draft))}
          >
            Отключить пополнение
          </button>
        </div>

        {tab === 'form' && draft && (
          <AdminConfigForm draft={draft} errors={errors} onChange={edit} onReplace={showForm} />
        )}

        {tab === 'json' && (
          <>
            <label className="admin-label" htmlFor="admin-json">JSON конфига</label>
            <textarea
              id="admin-json"
              className="admin-editor"
              spellCheck={false}
              value={jsonText}
              onChange={(e) => {
                setJsonText(e.target.value)
                setJsonError(parseJson(e.target.value).ok ? null : 'Невалидный JSON')
              }}
            />
            {jsonError && <p className="admin-alert error">{jsonError}</p>}
          </>
        )}

        <DiffList changes={changes} />

        <div className="admin-actions">
          <button className="admin-btn" type="button" disabled={saveDisabled || changes.length === 0} onClick={() => void onSave()}>
            Сохранить{changes.length > 0 ? ` (${changes.length})` : ''}
          </button>
          <button className="admin-btn secondary" type="button" disabled={busy} onClick={() => void load()}>
            Обновить
          </button>
        </div>
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
                <th>Что изменилось</th>
              </tr>
            </thead>
            <tbody>
              {history.map((row, index) => (
                <tr key={row.id}>
                  <td>{formatTime(row.appliedAt)}</td>
                  <td>{row.appliedBy}</td>
                  <td>
                    <button className="admin-link" type="button" onClick={() => setOpenHistory(openHistory === row.id ? null : row.id)}>
                      {openHistory === row.id ? 'Скрыть' : 'Смотреть diff'}
                    </button>
                    {openHistory === row.id && (
                      <HistoryDiff current={row} older={history[index + 1]} />
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </section>
    </div>
  )
}

function DiffList({ changes }: { changes: ConfigChange[] }) {
  if (changes.length === 0) {
    return <p className="admin-muted admin-diff-empty">Нет несохранённых изменений.</p>
  }
  return (
    <div className="admin-diff">
      <p className="admin-label">Будет отправлено</p>
      <ul>
        {changes.map((change) => (
          <li key={change.path}>{formatChange(change)}</li>
        ))}
      </ul>
    </div>
  )
}

function HistoryDiff({ current, older }: { current: ConfigHistoryItem; older?: ConfigHistoryItem }) {
  const parsed = historyChanges(current, older)
  if (parsed.kind === 'preview') return <p className="admin-preview">{parsed.text}</p>
  if (parsed.kind === 'note') return <p className="admin-muted">{parsed.text}</p>
  return (
    <ul className="admin-diff-list">
      {parsed.changes.map((change) => (
        <li key={change.path}>{formatChange(change)}</li>
      ))}
    </ul>
  )
}

function historyChanges(
  current: ConfigHistoryItem,
  older?: ConfigHistoryItem,
): { kind: 'preview'; text: string } | { kind: 'note'; text: string } | { kind: 'diff'; changes: ConfigChange[] } {
  if (!current.payload) return { kind: 'preview', text: current.payloadPreview }
  if (!older?.payload) return { kind: 'note', text: 'Первый снимок в этой выборке — сравнивать не с чем.' }
  try {
    const changes = diffConfig(JSON.parse(older.payload) as unknown, JSON.parse(current.payload) as unknown)
    if (changes.length === 0) return { kind: 'note', text: 'Совпадает с предыдущим снимком.' }
    return { kind: 'diff', changes }
  } catch {
    return { kind: 'preview', text: current.payloadPreview }
  }
}

function parseJson(text: string): { ok: true; doc: ConfigDoc } | { ok: false; error: string } {
  try {
    return { ok: true, doc: cloneConfig(JSON.parse(text) as unknown) }
  } catch (e) {
    return { ok: false, error: e instanceof Error ? e.message : 'Невалидный JSON' }
  }
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
  if (Number.isNaN(d.getTime())) return iso
  return d.toLocaleString('ru-RU')
}

function cssEscape(value: string): string {
  return window.CSS?.escape ? window.CSS.escape(value) : value.replace(/"/g, '\\"')
}
