import { StoreProvider, useStore } from './store.jsx'
import { useTheme } from './theme.js'
import Sidebar from './components/Sidebar.jsx'
import Header from './components/Header.jsx'
import ChatPanel from './components/ChatPanel.jsx'
import Modals from './components/Modals.jsx'
import AccountModal from './components/AccountModal.jsx'
import Login from './views/Login.jsx'
import Home from './views/Home.jsx'
import Projects from './views/Projects.jsx'
import Util from './views/Util.jsx'
import Maint from './views/Maint.jsx'
import People from './views/People.jsx'
import Settings from './views/Settings.jsx'

function Shell() {
  const { S, bootstrap } = useStore()
  const { theme, toggle } = useTheme()

  if (S.boot === 'anon') {
    return <Login theme={theme} onToggleTheme={toggle} />
  }
  if (S.boot === 'loading') {
    return (
      <div style={{ display: 'grid', placeItems: 'center', height: '100vh' }}>
        <div style={{ textAlign: 'center' }}>
          <div style={{ fontSize: 22, fontWeight: 800, marginBottom: 8 }}>프로텐 PMS</div>
          <div className="muted">백엔드에 연결하는 중… <span style={{ display: 'inline-block', animation: 'pmsblink 1s infinite' }}>●</span></div>
        </div>
      </div>
    )
  }
  if (S.boot === 'error') {
    return (
      <div style={{ display: 'grid', placeItems: 'center', height: '100vh', padding: 24 }}>
        <div className="card" style={{ maxWidth: 460, textAlign: 'center' }}>
          <div style={{ fontSize: 17, fontWeight: 800, marginBottom: 8 }}>백엔드에 연결할 수 없습니다</div>
          <div className="muted" style={{ fontSize: 13, marginBottom: 6 }}>{S.bootError}</div>
          <div className="muted2" style={{ fontSize: 12, marginBottom: 16 }}>pms_back(8080)이 떠 있는지 확인하세요 — `cd pms_back && ./gradlew bootRun` 또는 `docker compose up`</div>
          <button className="btn btn-primary" onClick={() => bootstrap()}>다시 연결</button>
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
          {S.route === 'home' && <Home />}
          {S.route === 'projects' && <Projects />}
          {S.route === 'util' && <Util />}
          {S.route === 'maint' && <Maint />}
          {S.route === 'people' && <People />}
          {S.route === 'settings' && <Settings />}
        </main>
      </div>
      <ChatPanel />
      <Modals />
      <AccountModal />
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
