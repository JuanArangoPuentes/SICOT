// Pantalla intermedia mientras se descarga el paquete del panel del rol.
//
// Los paneles se cargan bajo demanda (React.lazy en App.tsx), así que entre el
// login y el panel hay una petición de red. En la red del centro puede tardar
// lo suficiente como para que un fondo vacío parezca que algo falló.
//
// Deliberadamente sobrio: no promete nada que no esté ocurriendo ni inventa una
// barra de progreso que no mide nada real.
//
// Estilado con los tokens del proyecto, no con utilidades de un framework
// (ver docs/decisiones/ADR-004): este componente era el único de todo el
// frontend que usaba clases de Tailwind, y mantenerlo así habría obligado a
// conservar la dependencia entera por un archivo.

export default function CargandoPanel() {
  return (
    <div
      role="status"
      aria-live="polite"
      style={{
        minHeight: '100vh',
        display: 'flex',
        flexDirection: 'column',
        alignItems: 'center',
        justifyContent: 'center',
        gap: 16,
        background: 'var(--bg-base)',
        color: 'var(--text-secondary)',
      }}
    >
      <div className="cargando-giro" aria-hidden="true" />
      <p style={{ fontSize: 13, letterSpacing: '0.03em', margin: 0 }}>Abriendo su panel…</p>
    </div>
  )
}
