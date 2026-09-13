import { useState, type FormEvent } from 'react'
import { ApiError } from '../api/client'
import { adminLogin } from './adminApi'

export function AdminLoginScreen() {
  const [username, setUsername] = useState('admin')
  const [password, setPassword] = useState('admin')
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  async function onSubmit(e: FormEvent) {
    e.preventDefault()
    setError(null)
    setBusy(true)
    try {
      await adminLogin(username.trim(), password)
      window.location.replace('/admin')
    } catch (err) {
      if (err instanceof ApiError) {
        setError(localizeAuthError(err))
      } else {
        setError('Не удалось войти')
      }
      setBusy(false)
    }
  }

  return (
    <div className="admin-login">
      <form className="admin-card" onSubmit={onSubmit}>
        <h1>Админ-панель</h1>
        <p className="admin-muted">Вход оператора для runtime-конфига игры.</p>
        <div className="admin-field">
          <label className="admin-label" htmlFor="admin-user">Логин</label>
          <input
            id="admin-user"
            className="admin-input"
            autoComplete="username"
            value={username}
            onChange={(e) => setUsername(e.target.value)}
          />
        </div>
        <div className="admin-field">
          <label className="admin-label" htmlFor="admin-pass">Пароль</label>
          <input
            id="admin-pass"
            className="admin-input"
            type="password"
            autoComplete="current-password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
          />
        </div>
        <button className="admin-btn" type="submit" disabled={busy}>
          {busy ? 'Вход…' : 'Войти'}
        </button>
        {error && <p className="admin-alert error">{error}</p>}
      </form>
    </div>
  )
}

function localizeAuthError(err: ApiError): string {
  switch (err.code) {
    case 'AUTH_INVALID_CREDENTIALS':
      return 'Неверный логин или пароль'
    case 'AUTH_DISABLED':
      return 'Учётная запись отключена'
    case 'AUTH_UNAUTHORIZED':
    case 'AUTH_TOKEN_EXPIRED':
      return 'Сессия истекла, войдите снова'
    default:
      return err.body?.message ?? err.message
  }
}
