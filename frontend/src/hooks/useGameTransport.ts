import { Client, type IMessage, type StompSubscription } from '@stomp/stompjs'
import { useEffect, useRef } from 'react'
import { getState } from '../api/gameApi'
import type { PublicGameState, WsGameEvent } from '../api/types'
import { useGameStore } from '../stores/gameStore'
import { usePlayerStore } from '../stores/playerStore'

const CONNECT_TIMEOUT_MS = 1500
const STALE_TICK_MS = 1000
const POLL_INTERVAL_MS = 200
const STALE_CHECK_MS = 250

function brokerUrl(): string {
  const base = import.meta.env.VITE_API_BASE as string | undefined
  if (base && base.length > 0) {
    const u = new URL(base)
    u.protocol = u.protocol === 'https:' ? 'wss:' : 'ws:'
    u.pathname = '/ws'
    u.search = ''
    u.hash = ''
    return u.toString()
  }
  const proto = window.location.protocol === 'https:' ? 'wss' : 'ws'
  return `${proto}://${window.location.host}/ws`
}

function applyState(state: PublicGameState) {
  useGameStore.getState().applyPublicState(state)
}

/**
 * F4 transport: STOMP primary, REST poll on connect fail / stale ticks (>1s).
 * Spec 3 §5.3 / §10.2.
 */
export function useGameTransport(active: boolean) {
  const modeRef = useRef<'ws' | 'poll' | 'idle'>('idle')

  useEffect(() => {
    if (!active) return

    const gameId = useGameStore.getState().gameId
    const playerId = usePlayerStore.getState().playerId
    if (!gameId || !playerId) return

    let cancelled = false
    let lastTickAt = 0
    let pollTimer: ReturnType<typeof setInterval> | null = null
    let staleTimer: ReturnType<typeof setInterval> | null = null
    let connectTimer: ReturnType<typeof setTimeout> | null = null
    let subscription: StompSubscription | null = null
    let client: Client | null = null
    let polling = false

    const ingest = (state: PublicGameState) => {
      if (cancelled) return
      lastTickAt = Date.now()
      applyState(state)
      if (state.status !== 'FLYING') {
        stopAll()
      }
    }

    const pollOnce = async () => {
      if (cancelled) return
      try {
        const state = await getState(gameId, playerId)
        ingest(state)
      } catch {
        /* keep trying while active */
      }
    }

    const startPolling = (reason: string) => {
      if (cancelled || polling) return
      polling = true
      modeRef.current = 'poll'
      void reason
      void pollOnce()
      pollTimer = setInterval(() => {
        void pollOnce()
      }, POLL_INTERVAL_MS)
    }

    const stopPolling = () => {
      polling = false
      if (pollTimer) {
        clearInterval(pollTimer)
        pollTimer = null
      }
    }

    const stopAll = () => {
      stopPolling()
      if (staleTimer) {
        clearInterval(staleTimer)
        staleTimer = null
      }
      if (connectTimer) {
        clearTimeout(connectTimer)
        connectTimer = null
      }
      try {
        subscription?.unsubscribe()
      } catch {
        /* ignore */
      }
      subscription = null
      if (client) {
        const c = client
        client = null
        try {
          void c.deactivate()
        } catch {
          /* ignore */
        }
      }
      modeRef.current = 'idle'
    }

    const onMessage = (msg: IMessage) => {
      try {
        const event = JSON.parse(msg.body) as WsGameEvent
        if (event?.state) {
          modeRef.current = 'ws'
          stopPolling()
          ingest(event.state)
        }
      } catch {
        /* ignore malformed */
      }
    }

    // Always get an immediate snapshot (covers WS delay / first paint).
    void pollOnce()

    client = new Client({
      brokerURL: brokerUrl(),
      connectHeaders: { 'X-Player-Id': playerId },
      reconnectDelay: 0,
      heartbeatIncoming: 10000,
      heartbeatOutgoing: 10000,
      onConnect: () => {
        if (cancelled || !client) return
        if (connectTimer) {
          clearTimeout(connectTimer)
          connectTimer = null
        }
        subscription = client.subscribe(`/topic/game/${gameId}`, onMessage)
        modeRef.current = 'ws'
        lastTickAt = Date.now()
      },
      onStompError: () => {
        startPolling('stomp-error')
      },
      onWebSocketError: () => {
        startPolling('ws-error')
      },
      onWebSocketClose: () => {
        if (!cancelled && useGameStore.getState().status === 'FLYING') {
          startPolling('ws-close')
        }
      },
    })

    connectTimer = setTimeout(() => {
      if (cancelled) return
      if (modeRef.current !== 'ws') {
        startPolling('connect-timeout')
      }
    }, CONNECT_TIMEOUT_MS)

    staleTimer = setInterval(() => {
      if (cancelled) return
      if (useGameStore.getState().status !== 'FLYING') return
      if (lastTickAt === 0) return
      if (Date.now() - lastTickAt > STALE_TICK_MS) {
        startPolling('stale')
        void pollOnce()
      }
    }, STALE_CHECK_MS)

    client.activate()

    return () => {
      cancelled = true
      stopAll()
    }
  }, [active])
}
