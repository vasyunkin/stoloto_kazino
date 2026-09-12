import { useCallback, useEffect, useState } from 'react'
import { getHistory } from '../api/gameApi'
import type { HistoryItemResponse } from '../api/types'

export function useGameHistory(limit = 24) {
  const [items, setItems] = useState<HistoryItemResponse[]>([])
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const reload = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      const data = await getHistory(limit)
      setItems(data)
    } catch {
      setError('history')
      setItems([])
    } finally {
      setLoading(false)
    }
  }, [limit])

  useEffect(() => {
    void reload()
  }, [reload])

  return { items, loading, error, reload }
}
