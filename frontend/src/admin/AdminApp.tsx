import { useEffect } from 'react'
import { AdminConfigScreen } from './AdminConfigScreen'
import { AdminLoginScreen } from './AdminLoginScreen'
import { getAccessToken, redirectToLogin } from './adminSession'
import './admin.css'

export function AdminApp() {
  const path = window.location.pathname.replace(/\/+$/, '') || '/'
  const isLogin = path === '/admin/login'
  const loggedIn = !!getAccessToken()

  useEffect(() => {
    document.body.classList.add('admin-body')
    document.title = 'Админ-панель — Воздушный Шар'
    return () => document.body.classList.remove('admin-body')
  }, [])

  useEffect(() => {
    if (!isLogin && !loggedIn) {
      redirectToLogin()
    }
    if (isLogin && loggedIn) {
      window.location.replace('/admin')
    }
  }, [isLogin, loggedIn])

  return (
    <div className="admin-root">
      {isLogin || !loggedIn ? <AdminLoginScreen /> : <AdminConfigScreen />}
    </div>
  )
}
