/** El resultado de `useGuardadoDeArchivo` (hooks/), con el mismo aspecto en cada pantalla. */
export default function AvisoDeGuardado({ aviso, error }: { aviso: string | null; error: string | null }) {
  if (!aviso && !error) return null
  return (
    <div
      role={error ? 'alert' : 'status'}
      className="card"
      style={{
        padding: '12px 15px',
        marginBottom: 14,
        borderColor: error ? 'var(--alert-critica)' : 'var(--accent)',
        fontSize: 12.5,
        color: 'var(--text-primary)',
      }}
    >
      {error ?? aviso}
    </div>
  )
}
