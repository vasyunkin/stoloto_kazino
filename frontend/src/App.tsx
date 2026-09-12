import './styles/tokens.css'
import './App.css'
import { HubScreen } from './screens/HubScreen'
import { PrefightScreen } from './screens/PrefightScreen'
import { FlightScreen } from './screens/FlightScreen'
import { useGameStore } from './stores/gameStore'

function App() {
  const phase = useGameStore((s) => s.phase)

  return (
    <div className={`app-shell phase-${phase}`}>
      {phase === 'hub' && <HubScreen />}
      {phase === 'prefight' && <PrefightScreen />}
      {(phase === 'flight' || phase === 'result') && <FlightScreen />}
    </div>
  )
}

export default App
