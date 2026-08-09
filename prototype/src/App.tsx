import { HashRouter, Navigate, Route, Routes } from 'react-router-dom'
import { useApp } from './core/store'
import Layout from './components/Layout'
import Login from './views/Login'
import Home from './views/Home'
import Projects from './views/Projects'
import ProjectNew from './views/ProjectNew'
import ProjectDetail from './views/ProjectDetail'
import Utilization from './views/Utilization'
import People from './views/People'
import Maintenance from './views/Maintenance'
import ContractDetail from './views/ContractDetail'
import Issues from './views/Issues'
import Settings from './views/Settings'

export default function App() {
  const s = useApp()
  const authed = s.currentUserId !== null
  return (
    <HashRouter>
      <Routes>
        <Route path="/login" element={<Login />} />
        {authed ? (
          <Route element={<Layout />}>
            <Route path="/" element={<Home />} />
            <Route path="/projects" element={<Projects />} />
            <Route path="/projects/new" element={<ProjectNew />} />
            <Route path="/projects/:id" element={<ProjectDetail />} />
            <Route path="/utilization" element={<Utilization />} />
            <Route path="/people" element={<People />} />
            <Route path="/maintenance" element={<Maintenance />} />
            <Route path="/maintenance/contracts/:id" element={<ContractDetail />} />
            <Route path="/maintenance/issues" element={<Issues />} />
            <Route path="/settings" element={<Settings />} />
          </Route>
        ) : (
          <Route path="*" element={<Navigate to="/login" replace />} />
        )}
      </Routes>
    </HashRouter>
  )
}
