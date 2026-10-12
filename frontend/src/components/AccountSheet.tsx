import { AnimatePresence, motion } from 'framer-motion'
import { useState } from 'react'
import { ApiError } from '../api/client'
import { slotsFromPieces } from '../auth/albumSlots'
import { loginPlayer, registerPlayer, syncAlbum } from '../auth/playerApi'
import { playClickSfx, playErrorSfx } from '../audio/clickSfx'
import { loadGuestId, usePlayerStore } from '../stores/playerStore'
import { usePuzzleAlbumStore } from '../stores/puzzleAlbumStore'
import './LeaderboardSheet.css'
import './AccountSheet.css'

interface AccountSheetProps {
  open: boolean
  onClose: () => void
  onSignedIn: () => void
}

export function AccountSheet({ open, onClose, onSignedIn }: AccountSheetProps) {
  const [mode, setMode] = useState<'login' | 'register'>('login')
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const submit = async () => {
    playClickSfx()
    setBusy(true)
    setError(null)
    try {
      const guestId = loadGuestId()
      usePuzzleAlbumStore.getState().loadForPlayer(guestId)
      const guestSlots = slotsFromPieces(usePuzzleAlbumStore.getState().pieces)
      const session =
        mode === 'register'
          ? await registerPlayer(email.trim(), password)
          : await loginPlayer(email.trim(), password)
      usePlayerStore.getState().adoptAccount(session.playerId, session.email)
      const remote = await syncAlbum(guestSlots)
      usePuzzleAlbumStore.getState().replaceFromServer(session.playerId, remote)
      onSignedIn()
    } catch (err) {
      playErrorSfx()
      setError(err instanceof ApiError ? err.message : 'Не удалось войти')
    } finally {
      setBusy(false)
    }
  }

  return (
    <AnimatePresence>
      {open && (
        <motion.div
          className="sheet-backdrop"
          initial={{ opacity: 0 }}
          animate={{ opacity: 1 }}
          exit={{ opacity: 0 }}
          onClick={onClose}
        >
          <motion.div
            className="sheet-panel"
            role="dialog"
            aria-modal
            aria-labelledby="account-title"
            initial={{ y: 40, opacity: 0 }}
            animate={{ y: 0, opacity: 1 }}
            exit={{ y: 24, opacity: 0 }}
            onClick={(e) => e.stopPropagation()}
          >
            <header className="sheet-head">
              <h2 id="account-title">{mode === 'login' ? 'Вход' : 'Регистрация'}</h2>
              <button type="button" className="sheet-close" aria-label="Закрыть" onClick={onClose}>
                ×
              </button>
            </header>
            <p className="sheet-muted">
              Гостевой кошелёк останется отдельно. Собранные фрагменты альбома перенесутся на аккаунт.
            </p>
            <form
              className="account-form"
              onSubmit={(e) => {
                e.preventDefault()
                void submit()
              }}
            >
              <label className="account-label" htmlFor="account-email">
                Email
                <input
                  id="account-email"
                  type="email"
                  autoComplete="email"
                  value={email}
                  onChange={(e) => setEmail(e.target.value)}
                  required
                />
              </label>
              <label className="account-label" htmlFor="account-password">
                Пароль
                <input
                  id="account-password"
                  type="password"
                  autoComplete={mode === 'login' ? 'current-password' : 'new-password'}
                  minLength={8}
                  value={password}
                  onChange={(e) => setPassword(e.target.value)}
                  required
                />
              </label>
              {error && (
                <p className="sheet-error" role="alert">
                  {error}
                </p>
              )}
              <button type="submit" className="account-submit" disabled={busy}>
                {busy ? 'Секунду…' : mode === 'login' ? 'Войти' : 'Создать аккаунт'}
              </button>
            </form>
            <button
              type="button"
              className="account-switch"
              onClick={() => {
                playClickSfx()
                setMode(mode === 'login' ? 'register' : 'login')
                setError(null)
              }}
            >
              {mode === 'login' ? 'Нет аккаунта — зарегистрироваться' : 'Уже есть аккаунт — войти'}
            </button>
          </motion.div>
        </motion.div>
      )}
    </AnimatePresence>
  )
}
