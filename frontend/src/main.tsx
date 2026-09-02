import React from 'react'
import ReactDOM from 'react-dom/client'
import { BrowserRouter } from 'react-router-dom'
import App from './App'
import './index.css'

// BrowserRouter y no HashRouter: las URLs quedan limpias (/supervisor/alertas
// en vez de /#/supervisor/alertas) y nginx ya sirve index.html para cualquier
// ruta profunda (`try_files` en frontend/nginx.conf), así que entrar directo a
// una URL funciona. Ver docs/decisiones/ADR-007.
ReactDOM.createRoot(document.getElementById('root')!).render(
  <React.StrictMode>
    <BrowserRouter>
      <App />
    </BrowserRouter>
  </React.StrictMode>,
)
