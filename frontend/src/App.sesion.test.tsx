import { render, screen, waitFor } from '@testing-library/react'
import { BrowserRouter } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import userEvent from '@testing-library/user-event'
import App from './App'
import { profundidadDeNavegacion } from '@/services/pilaDeNavegacion'
import { getContratos } from '@/services/contratoService'
import { getRegistrosContrato } from '@/services/registroService'
import { sesionSupervisor } from '@/test/dobles'

/**
 * El historial del navegador a lo largo de un ciclo de sesión.
 *
 * **Qué protege.** El botón «atrás» del teléfono se quedaba muerto después de
 * cerrar sesión: cada pulsación iba a una vista del panel, el guardia de sesión
 * la devolvía a `/login` y la pantalla no cambiaba, tantas veces como vistas se
 * hubieran visitado. En un arranque limpio, sin nada apilado, sí salía de la
 * aplicación. Es el punto 13 de `docs/operacion/PRUEBA_MANUAL_APK.md`, observado
 * el 08-10-2026 en el emulador.
 *
 * **Por qué se mira `history.state.idx`.** Lo que decide si «atrás» cierra la
 * aplicación es cuántas entradas hay DETRÁS de la actual, y eso no se puede leer
 * del DOM: `history.length` cuenta también las de delante y `location` solo dice
 * dónde se está. El índice de la entrada actual es el dato exacto —es el que
 * lleva react-router en el estado del historial— y la afirmación que importa es
 * que al cerrar sesión vuelve a ser el que era al entrar.
 */

vi.mock('@/screens/LoginScreen', () => ({
  default: ({ onLogin }: { onLogin: (s: ReturnType<typeof sesionSupervisor>) => void }) => (
    <button onClick={() => onLogin(sesionSupervisor())}>Ingresar</button>
  ),
}))

// El panel real no aporta nada a esta prueba y arrastra el contrato, las
// etapas, las alertas y el Copiloto. Lo que hace falta de él son las dos cosas
// que mueven el historial: cambiar de vista y cerrar sesión.
vi.mock('@/screens/SupervisorPanel', () => ({
  default: ({
    vista,
    onCambiarVista,
    onLogout,
  }: {
    vista: string
    onCambiarVista: (v: string) => void
    onLogout: () => void
  }) => (
    <div>
      <p>Vista: {vista}</p>
      <button onClick={() => onCambiarVista('alertas')}>Ir a alertas</button>
      <button onClick={() => onCambiarVista('documentos')}>Ir a documentos</button>
      <button onClick={() => onCambiarVista('registros')}>Ir a registros</button>
      <button onClick={onLogout}>Cerrar sesión</button>
    </div>
  ),
}))
vi.mock('@/screens/GestionPanel', () => ({ default: () => <div>Gestión</div> }))
vi.mock('@/screens/AdminPanel', () => ({ default: () => <div>Administración</div> }))
vi.mock('@/components/AvatarLayer', () => ({ default: () => null }))
vi.mock('@/components/Settings', () => ({ default: () => null }))
vi.mock('@/services/contratoService', () => ({ getContratos: vi.fn() }))
vi.mock('@/services/registroService', () => ({ getRegistrosContrato: vi.fn() }))

/** Índice de la entrada de historial actual, tal como lo lleva react-router. */
const indiceDeHistorial = () => (window.history.state as { idx?: number } | null)?.idx ?? 0

describe('historial a lo largo de la sesión', () => {
  beforeEach(() => {
    sessionStorage.clear()
    window.history.replaceState(null, '', '/')
    vi.mocked(getContratos).mockResolvedValue([])
    vi.mocked(getRegistrosContrato).mockResolvedValue([])
  })

  it('cerrar sesión deja el historial como estaba al entrar', async () => {
    const usuario = userEvent.setup()
    render(
      <BrowserRouter>
        <App />
      </BrowserRouter>,
    )

    await usuario.click(await screen.findByRole('button', { name: 'Ingresar' }))
    await screen.findByText('Vista: bandeja')

    // La entrada a la sesión se hace con `replace`, así que esta es la base.
    const base = indiceDeHistorial()
    expect(profundidadDeNavegacion()).toBe(0)

    await usuario.click(screen.getByRole('button', { name: 'Ir a alertas' }))
    await screen.findByText('Vista: alertas')
    await usuario.click(screen.getByRole('button', { name: 'Ir a documentos' }))
    await screen.findByText('Vista: documentos')
    await usuario.click(screen.getByRole('button', { name: 'Ir a registros' }))
    await screen.findByText('Vista: registros')

    // Tres vistas, tres entradas apiladas: es lo que antes se quedaba debajo de
    // `/login` y se comía tres pulsaciones del botón «atrás».
    await waitFor(() => expect(profundidadDeNavegacion()).toBe(3))
    expect(indiceDeHistorial()).toBe(base + 3)

    await usuario.click(screen.getByRole('button', { name: 'Cerrar sesión' }))

    await waitFor(() => expect(window.location.pathname).toBe('/login'))
    // Lo que de verdad se afirma: no queda ninguna entrada del panel detrás, así
    // que la siguiente pulsación de «atrás» cierra la aplicación en vez de
    // rebotar contra el guardia de sesión.
    await waitFor(() => expect(indiceDeHistorial()).toBe(base))
    expect(profundidadDeNavegacion()).toBe(0)
    expect(await screen.findByRole('button', { name: 'Ingresar' })).toBeInTheDocument()
  })

  // El caso que sí funcionaba antes del arreglo, para que siga funcionando:
  // entrar y cerrar sesión sin moverse de la primera vista.
  it('cerrar sesión sin haber cambiado de vista no mueve el historial', async () => {
    const usuario = userEvent.setup()
    render(
      <BrowserRouter>
        <App />
      </BrowserRouter>,
    )

    await usuario.click(await screen.findByRole('button', { name: 'Ingresar' }))
    await screen.findByText('Vista: bandeja')
    const base = indiceDeHistorial()

    await usuario.click(screen.getByRole('button', { name: 'Cerrar sesión' }))

    await waitFor(() => expect(window.location.pathname).toBe('/login'))
    expect(indiceDeHistorial()).toBe(base)
  })
})
