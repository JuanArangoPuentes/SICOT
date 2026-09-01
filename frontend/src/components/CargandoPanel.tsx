// Pantalla intermedia mientras se descarga el paquete del panel del rol.
//
// Los paneles se cargan bajo demanda (React.lazy en App.tsx), así que entre el
// login y el panel hay una petición de red. En la red del centro puede tardar
// lo suficiente como para que un fondo vacío parezca que algo falló.
//
// Deliberadamente sobrio: no promete nada que no esté ocurriendo ni inventa una
// barra de progreso que no mide nada real.

export default function CargandoPanel() {
  return (
    <div
      className="min-h-screen flex flex-col items-center justify-center gap-4 bg-[#0f1419] text-slate-300"
      role="status"
      aria-live="polite"
    >
      <div
        className="h-8 w-8 rounded-full border-2 border-slate-600 border-t-emerald-400 motion-safe:animate-spin"
        aria-hidden="true"
      />
      <p className="text-sm tracking-wide">Abriendo su panel…</p>
    </div>
  )
}
