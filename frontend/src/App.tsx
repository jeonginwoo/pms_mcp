import { StoreProvider, useStore } from './store'
import { useTheme } from './theme'
import Sidebar from './components/Sidebar'
import Header from './components/Header'
import { Toast } from './components/ui'
import Login from './views/Login'
import Home from './views/Home'
import People from './views/People'
import Projects from './views/Projects'
import ProjectDetail from './views/ProjectDetail'
import Utilization from './views/Utilization'
import Maintenance from './views/Maintenance'
import MaintenanceContract from './views/MaintenanceContract'
import MaintenanceIssues from './views/MaintenanceIssues'
import Audit from './views/Audit'

function Shell() {
  const { phase, bootError, route, detail, contract, toast, reload } = useStore()
  const { theme, toggle } = useTheme()

  if (phase === 'anon') {
    return <Login theme={theme} onToggleTheme={toggle} />
  }

  if (phase === 'loading') {
    return (
      <div style={{ display: 'grid', placeItems: 'center', height: '100vh' }}>
        <div style={{ textAlign: 'center' }}>
          <div style={{ fontSize: 22, fontWeight: 800, marginBottom: 8 }}>프로텐 PMS</div>
          <div className="muted">
            백엔드에 연결하는 중…{' '}
            <span style={{ display: 'inline-block', animation: 'pmsblink 1s infinite' }}>●</span>
          </div>
        </div>
      </div>
    )
  }

  if (phase === 'error') {
    return (
      <div style={{ display: 'grid', placeItems: 'center', height: '100vh', padding: 24 }}>
        <div className="card" style={{ maxWidth: 460, textAlign: 'center' }}>
          <div style={{ fontSize: 17, fontWeight: 800, marginBottom: 8 }}>백엔드에 연결할 수 없습니다</div>
          <div className="muted" style={{ fontSize: 13, marginBottom: 6 }}>{bootError}</div>
          <div className="muted2" style={{ fontSize: 12, marginBottom: 16 }}>
            pms 앱(8080)이 떠 있는지 확인하세요 — <span className="code">cd pms &amp;&amp; ./gradlew bootRun</span>
          </div>
          <button className="btn btn-primary" onClick={() => void reload()}>다시 연결</button>
        </div>
      </div>
    )
  }

  return (
    <div className="layout">
      <Sidebar />
      <div className="main">
        <Header theme={theme} onToggleTheme={toggle} />
        <main className="content">
          {route === 'home' && <Home />}
          {route === 'projects' && (detail ? <ProjectDetail /> : <Projects />)}
          {route === 'people' && <People />}
          {route === 'utilization' && <Utilization />}
          {route === 'maintenance' && (contract ? <MaintenanceContract /> : <Maintenance />)}
          {route === 'issues' && <MaintenanceIssues />}
          {route === 'audit' && <Audit />}
        </main>
      </div>
      {toast && <Toast message={toast} />}
    </div>
  )
}

export default function App() {
  return (
    <StoreProvider>
      <Shell />
    </StoreProvider>
  )
}
