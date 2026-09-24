// Seguimiento de supervisores: en qué parte del proceso va cada uno, contrato
// por contrato. Lo pidió la dirección del proyecto el 24-09-2026: el
// Administrador tiene que poder ver «a detalle en qué parte y cómo va cada
// supervisor con la información de su respectivo contrato» sin entrar al panel
// de cada uno.
//
// Todo lo que se pinta viene de GET /api/seguimiento/supervisores. El semáforo
// es el mismo cálculo que ve el supervisor en su panel (Cronograma, en el
// backend): aquí no se recalcula nada, para que el Administrador y el
// supervisor nunca vean dos estados distintos del mismo contrato.

import { useMemo, useState } from 'react'
import { Chip, type ChipType } from '@/components/ui'
import { IconAlertTriangle, IconUsers } from '@/components/icons'
import { FORMAL_DOCS } from '@/data/contractFlow'
import { formatCOP, formatFecha } from '@/services/format'
import type {
  ContratoSeguimiento,
  EstadoContrato,
  SeguimientoResponse,
  SemaforoCronograma,
  SupervisorSeguimiento,
} from '@/services/api/types'

const ESTADO_CONTRATO: Record<EstadoContrato, { label: string; type: ChipType }> = {
  BORRADOR: { label: 'Borrador', type: 'pending' },
  ACTIVO: { label: 'Activo', type: 'running' },
  SUSPENDIDO: { label: 'Suspendido', type: 'unassigned' },
  FINALIZADO: { label: 'Finalizado', type: 'finished' },
  CANCELADO: { label: 'Cancelado', type: 'inactive' },
}

const SEMAFORO: Record<SemaforoCronograma, { label: string; color: string }> = {
  VERDE: { label: 'A tiempo', color: 'var(--accent)' },
  AMARILLO: { label: 'En riesgo', color: 'var(--alert-leve)' },
  ROJO: { label: 'Atrasado', color: 'var(--alert-critica)' },
  SIN_DATOS: { label: 'Sin datos de plazo', color: 'var(--text-muted)' },
}

const HORA = new Intl.DateTimeFormat('es-CO', {
  timeZone: 'America/Bogota',
  day: '2-digit',
  month: '2-digit',
  year: 'numeric',
  hour: '2-digit',
  minute: '2-digit',
  hourCycle: 'h23',
})

function fechaYHora(iso: string | null | undefined) {
  return iso ? HORA.format(new Date(iso)) : '—'
}

/**
 * Estado de cada documento formal del supervisor en un contrato.
 *
 * «Paso cerrado sin documento» es el caso que más le importa al
 * Administrador: la subetapa se marcó completada, pero el documento que la
 * respalda no está en el expediente. Se distingue de «aún no corresponde» para
 * no alarmar por un paso al que el supervisor todavía no ha llegado.
 */
export function estadoDocumentosFormales(c: ContratoSeguimiento) {
  const subetapas = new Map(c.etapas.flatMap((e) => e.subEtapas).map((s) => [s.codigo, s.estado]))
  return FORMAL_DOCS.map((f) => {
    const docs = c.documentos.filter((d) => d.subetapaCodigo === f.subStepId)
    const estadoSub = subetapas.get(f.subStepId)
    let estado: { label: string; type: ChipType }
    if (docs.some((d) => d.firmado)) estado = { label: 'Firmado', type: 'signed' }
    else if (docs.length > 0) estado = { label: 'Pendiente de firma', type: 'unassigned' }
    else if (estadoSub === 'COMPLETADA') estado = { label: 'Paso cerrado sin documento', type: 'conflicto' }
    else if (estadoSub === 'EN_CURSO') estado = { label: 'Por generar', type: 'running' }
    else estado = { label: 'Aún no corresponde', type: 'pending' }
    return { ...f, estado }
  })
}

function BarraDeAvance({ hechas, total }: { hechas: number; total: number }) {
  const pct = total ? Math.round((hechas / total) * 100) : 0
  return (
    <div>
      <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: 12, marginBottom: 4 }}>
        <span style={{ color: 'var(--text-secondary)' }}>
          {hechas} de {total} subetapas completadas
        </span>
        <span style={{ fontWeight: 600 }}>{pct}%</span>
      </div>
      <div
        role="progressbar"
        aria-valuenow={pct}
        aria-valuemin={0}
        aria-valuemax={100}
        aria-label="Avance del contrato"
        style={{ height: 8, borderRadius: 4, background: 'var(--border)', overflow: 'hidden' }}
      >
        <div style={{ width: `${pct}%`, height: '100%', background: 'var(--accent)' }} />
      </div>
    </div>
  )
}

function Etapas({ c }: { c: ContratoSeguimiento }) {
  return (
    <div style={{ display: 'flex', gap: 4 }} aria-label="Avance por paso">
      {c.etapas.map((e) => (
        <div key={e.id} title={`Paso ${e.numero} — ${e.nombre}: ${e.porcentaje}%`} style={{ flex: 1, minWidth: 0 }}>
          <div
            style={{
              height: 6,
              borderRadius: 3,
              background:
                e.estado === 'COMPLETADA'
                  ? 'var(--accent)'
                  : e.estado === 'EN_CURSO'
                    ? 'var(--chip-blue)'
                    : 'var(--border)',
            }}
          />
          <div
            style={{
              fontSize: 10,
              color: e.estado === 'EN_CURSO' ? 'var(--text-primary)' : 'var(--text-muted)',
              marginTop: 3,
              textAlign: 'center',
              fontWeight: e.estado === 'EN_CURSO' ? 600 : 400,
            }}
          >
            {e.numero}
          </div>
        </div>
      ))}
    </div>
  )
}

function DetalleContrato({ c }: { c: ContratoSeguimiento }) {
  return (
    <div style={{ marginTop: 12, borderTop: '1px solid var(--border)', paddingTop: 12, display: 'grid', gap: 14 }}>
      <div>
        <div style={{ fontSize: 12, fontWeight: 600, marginBottom: 6 }}>Pasos y subetapas</div>
        {c.etapas.length === 0 && (
          <div style={{ fontSize: 12, color: 'var(--text-muted)' }}>
            El contrato todavía no tiene etapas: se crean cuando el supervisor abre su panel.
          </div>
        )}
        {c.etapas.map((e) => (
          <div key={e.id} style={{ marginBottom: 8 }}>
            <div style={{ fontSize: 12, fontWeight: 600, color: 'var(--text-secondary)' }}>
              Paso {e.numero} — {e.nombre} · {e.porcentaje}%
            </div>
            <ul style={{ listStyle: 'none', margin: '4px 0 0', padding: 0, display: 'grid', gap: 2 }}>
              {e.subEtapas.map((s) => (
                <li key={s.id} style={{ fontSize: 12, display: 'flex', gap: 6, alignItems: 'baseline' }}>
                  <span
                    aria-label={
                      s.estado === 'COMPLETADA' ? 'Completada' : s.estado === 'EN_CURSO' ? 'En curso' : 'Pendiente'
                    }
                    style={{
                      width: 8,
                      height: 8,
                      borderRadius: '50%',
                      flexShrink: 0,
                      background:
                        s.estado === 'COMPLETADA'
                          ? 'var(--accent)'
                          : s.estado === 'EN_CURSO'
                            ? 'var(--chip-blue)'
                            : 'var(--border)',
                    }}
                  />
                  <span style={{ fontFamily: 'var(--font-mono)', color: 'var(--text-muted)', flexShrink: 0 }}>
                    {s.codigo}
                  </span>
                  <span style={{ minWidth: 0 }}>
                    {s.nombre}
                    <span style={{ color: 'var(--text-muted)' }}> · {s.responsable}</span>
                  </span>
                </li>
              ))}
            </ul>
          </div>
        ))}
      </div>
      <div>
        <div style={{ fontSize: 12, fontWeight: 600, marginBottom: 6 }}>Expediente ({c.documentos.length})</div>
        {c.documentos.length === 0 && (
          <div style={{ fontSize: 12, color: 'var(--text-muted)' }}>Todavía no hay documentos en el expediente.</div>
        )}
        <ul style={{ listStyle: 'none', margin: 0, padding: 0, display: 'grid', gap: 4 }}>
          {c.documentos.map((d) => (
            <li key={d.id} style={{ fontSize: 12, display: 'flex', flexWrap: 'wrap', gap: 6, alignItems: 'center' }}>
              <span style={{ minWidth: 0, overflowWrap: 'anywhere' }}>{d.nombre}</span>
              {d.subetapaCodigo && <span style={{ color: 'var(--text-muted)' }}>· subetapa {d.subetapaCodigo}</span>}
              <span style={{ color: 'var(--text-muted)' }}>· {fechaYHora(d.fechaSubida)}</span>
              {d.generadoPorIa && <Chip text="Copiloto IA" type="document" />}
              {d.firmado ? (
                <Chip text={`Firmado ${fechaYHora(d.fechaFirma)}`} type="signed" />
              ) : (
                <Chip text="Sin firmar" type="pending" />
              )}
            </li>
          ))}
        </ul>
      </div>
    </div>
  )
}

function TarjetaContrato({ c }: { c: ContratoSeguimiento }) {
  const [abierto, setAbierto] = useState(false)
  const semaforo = c.cronograma ? SEMAFORO[c.cronograma.semaforo] : SEMAFORO.SIN_DATOS
  const formales = estadoDocumentosFormales(c)
  return (
    <div className="card" style={{ padding: '14px 16px', minWidth: 0 }}>
      <div style={{ display: 'flex', flexWrap: 'wrap', gap: 8, alignItems: 'center', marginBottom: 6 }}>
        <span
          style={{ fontFamily: 'var(--font-mono)', fontSize: 13, color: 'var(--accent)', overflowWrap: 'anywhere' }}
        >
          {c.numeroContrato}
        </span>
        <Chip text={ESTADO_CONTRATO[c.estado].label} type={ESTADO_CONTRATO[c.estado].type} />
        <span style={{ display: 'inline-flex', alignItems: 'center', gap: 5, fontSize: 12 }}>
          <span
            aria-hidden
            style={{ width: 9, height: 9, borderRadius: '50%', background: semaforo.color, display: 'inline-block' }}
          />
          {semaforo.label}
        </span>
        {c.alertasSinLeer > 0 && (
          <span
            style={{ display: 'inline-flex', alignItems: 'center', gap: 4, fontSize: 12, color: 'var(--alert-leve)' }}
          >
            <IconAlertTriangle size={13} /> {c.alertasSinLeer} alerta{c.alertasSinLeer === 1 ? '' : 's'} sin leer
          </span>
        )}
      </div>
      <div style={{ fontSize: 13, marginBottom: 2, overflowWrap: 'anywhere' }}>{c.objeto}</div>
      <div style={{ fontSize: 12, color: 'var(--text-muted)', marginBottom: 10 }}>
        {c.contratista ?? 'Contratista sin registrar'} · {formatCOP(c.valor)} · {formatFecha(c.fechaInicio)} →{' '}
        {formatFecha(c.fechaFin)}
      </div>

      <BarraDeAvance hechas={c.subetapasCompletadas} total={c.subetapasTotales} />
      <div style={{ marginTop: 8 }}>
        <Etapas c={c} />
      </div>

      <div style={{ fontSize: 12, marginTop: 10, display: 'grid', gap: 3 }}>
        <div>
          <span style={{ color: 'var(--text-muted)' }}>Va en: </span>
          {c.etapaActual != null ? (
            <strong>
              Paso {c.etapaActual} de {c.etapas.length || 6} — {c.etapaActualNombre}
            </strong>
          ) : c.subetapasTotales > 0 ? (
            <strong>Todos los pasos completados</strong>
          ) : (
            <span>Sin etapas todavía</span>
          )}
        </div>
        {c.subetapaEnCurso && (
          <div>
            <span style={{ color: 'var(--text-muted)' }}>Trabajando en: </span>
            {c.subetapaEnCurso.codigo} {c.subetapaEnCurso.nombre}{' '}
            <span style={{ color: 'var(--text-muted)' }}>({c.subetapaEnCurso.responsable})</span>
          </div>
        )}
        {c.cronograma && <div style={{ color: 'var(--text-secondary)' }}>{c.cronograma.mensaje}</div>}
        <div>
          <span style={{ color: 'var(--text-muted)' }}>Última actividad: </span>
          {c.ultimaActividad ? (
            <>
              {fechaYHora(c.ultimaActividad.fecha)} — {c.ultimaActividad.descripcion ?? c.ultimaActividad.accion}
            </>
          ) : (
            'ninguna registrada'
          )}
        </div>
      </div>

      <div style={{ marginTop: 10 }}>
        <div style={{ fontSize: 12, color: 'var(--text-muted)', marginBottom: 4 }}>Documentos del supervisor</div>
        <div style={{ display: 'flex', flexWrap: 'wrap', gap: 6 }}>
          {formales.map((f) => (
            <span
              key={f.subStepId}
              title={`${f.code} · subetapa ${f.subStepId}`}
              style={{ display: 'inline-flex', alignItems: 'center', gap: 5, fontSize: 12 }}
            >
              {f.name}: <Chip text={f.estado.label} type={f.estado.type} />
            </span>
          ))}
        </div>
      </div>

      <button
        className="btn-ghost"
        onClick={() => setAbierto((a) => !a)}
        aria-expanded={abierto}
        style={{ marginTop: 10, padding: '8px 12px', fontSize: 12, minHeight: 44 }}
      >
        {abierto ? 'Ocultar detalle' : 'Ver detalle del contrato'}
      </button>
      {abierto && <DetalleContrato c={c} />}
    </div>
  )
}

function TarjetaSupervisor({ s }: { s: SupervisorSeguimiento }) {
  return (
    <section className="card" style={{ padding: 16, minWidth: 0 }} aria-label={`Supervisor ${s.nombre}`}>
      <div style={{ display: 'flex', flexWrap: 'wrap', gap: 10, alignItems: 'center', marginBottom: 12 }}>
        <div
          aria-hidden
          style={{
            width: 36,
            height: 36,
            borderRadius: '50%',
            background: 'var(--accent-soft)',
            color: 'var(--accent)',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            fontWeight: 700,
            flexShrink: 0,
          }}
        >
          {s.nombre.charAt(0).toUpperCase()}
        </div>
        <div style={{ minWidth: 0, flex: '1 1 200px' }}>
          <div style={{ fontSize: 14, fontWeight: 600, overflowWrap: 'anywhere' }}>{s.nombre}</div>
          <div style={{ fontSize: 12, color: 'var(--text-muted)', overflowWrap: 'anywhere' }}>{s.email}</div>
        </div>
        <div style={{ display: 'flex', flexWrap: 'wrap', gap: 6 }}>
          {s.firmaVigente ? (
            <Chip text="Firma electrónica vigente" type="vigente" />
          ) : (
            <Chip text="Sin firma electrónica" type="conflicto" />
          )}
          {!s.activo && <Chip text="Cuenta inactiva" type="inactive" />}
          <Chip
            text={`${s.contratos.length} abierto${s.contratos.length === 1 ? '' : 's'} · ${s.contratosFinalizados} finalizado${s.contratosFinalizados === 1 ? '' : 's'}`}
            type="document"
          />
        </div>
      </div>
      {!s.firmaVigente && s.contratos.length > 0 && (
        <div style={{ fontSize: 12, color: 'var(--alert-critica)', marginBottom: 10 }}>
          No podrá firmar los documentos de sus contratos hasta que se le asigne una firma en «Firmas electrónicas».
        </div>
      )}
      {s.contratos.length === 0 ? (
        <div style={{ fontSize: 12, color: 'var(--text-muted)' }}>No tiene contratos abiertos asignados.</div>
      ) : (
        <div style={{ display: 'grid', gap: 10 }}>
          {s.contratos.map((c) => (
            <TarjetaContrato key={c.id} c={c} />
          ))}
        </div>
      )}
    </section>
  )
}

/** Cifras del seguimiento para el panel de control. */
export function resumenSeguimiento(datos: SeguimientoResponse) {
  const contratos = [...datos.supervisores.flatMap((s) => s.contratos), ...datos.contratosSinSupervisor]
  const activos = contratos.filter((c) => c.estado === 'ACTIVO')
  const por = (sem: SemaforoCronograma) => activos.filter((c) => c.cronograma?.semaforo === sem).length
  return {
    verde: por('VERDE'),
    amarillo: por('AMARILLO'),
    rojo: por('ROJO'),
    sinDatos: activos.length - por('VERDE') - por('AMARILLO') - por('ROJO'),
    sinSupervisor: datos.contratosSinSupervisor.length,
    supervisoresSinFirma: datos.supervisores.filter((s) => !s.firmaVigente && s.contratos.length > 0).length,
    pasosSinDocumento: contratos.reduce(
      (n, c) => n + estadoDocumentosFormales(c).filter((f) => f.estado.type === 'conflicto').length,
      0,
    ),
  }
}

/** Resumen para el panel de control: cuántos contratos van bien y qué hay que atender. */
export function ResumenSeguimiento({ datos }: { datos: SeguimientoResponse }) {
  const r = resumenSeguimiento(datos)
  const cifras = [
    { label: 'A tiempo', valor: r.verde, color: 'var(--accent)' },
    { label: 'En riesgo', valor: r.amarillo, color: 'var(--alert-leve)' },
    { label: 'Atrasados', valor: r.rojo, color: 'var(--alert-critica)' },
    { label: 'Sin datos de plazo', valor: r.sinDatos, color: 'var(--text-muted)' },
  ]
  const plural = (n: number, uno: string, varios: string) => `${n} ${n === 1 ? uno : varios}`
  const avisos = [
    r.sinSupervisor > 0 && `${plural(r.sinSupervisor, 'contrato abierto', 'contratos abiertos')} sin supervisor`,
    r.supervisoresSinFirma > 0 &&
      `${plural(r.supervisoresSinFirma, 'supervisor', 'supervisores')} con contratos y sin firma electrónica`,
    r.pasosSinDocumento > 0 && `${plural(r.pasosSinDocumento, 'paso cerrado', 'pasos cerrados')} sin su documento`,
  ].filter((a): a is string => Boolean(a))
  return (
    <>
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(130px, 1fr))', gap: 10 }}>
        {cifras.map((c) => (
          <div key={c.label} style={{ borderLeft: `3px solid ${c.color}`, paddingLeft: 10 }}>
            <div style={{ fontSize: 24, fontWeight: 700, color: c.color }}>{c.valor}</div>
            <div style={{ fontSize: 12, color: 'var(--text-secondary)' }}>{c.label}</div>
          </div>
        ))}
      </div>
      {avisos.length > 0 ? (
        <ul style={{ margin: '14px 0 0', paddingLeft: 18, fontSize: 12, color: 'var(--alert-leve)' }}>
          {avisos.map((a) => (
            <li key={a}>{a}</li>
          ))}
        </ul>
      ) : (
        <div style={{ marginTop: 14, fontSize: 12, color: 'var(--text-muted)' }}>
          Todos los contratos abiertos tienen supervisor, y todos los supervisores con contratos tienen firma.
        </div>
      )}
    </>
  )
}

export default function SeguimientoSupervisores({
  datos,
  error,
  cargando,
  onActualizar,
}: {
  datos: SeguimientoResponse | null
  error: string
  cargando: boolean
  onActualizar: () => void
}) {
  const [filtro, setFiltro] = useState('')
  const supervisores = useMemo(() => {
    if (!datos) return []
    const f = filtro.trim().toLowerCase()
    if (!f) return datos.supervisores
    return datos.supervisores.filter(
      (s) =>
        s.nombre.toLowerCase().includes(f) ||
        s.email.toLowerCase().includes(f) ||
        s.contratos.some((c) => c.numeroContrato.toLowerCase().includes(f) || c.objeto.toLowerCase().includes(f)),
    )
  }, [datos, filtro])

  return (
    <div style={{ display: 'grid', gap: 14 }}>
      <div style={{ display: 'flex', flexWrap: 'wrap', gap: 10, alignItems: 'center' }}>
        <input
          type="search"
          value={filtro}
          onChange={(e) => setFiltro(e.target.value)}
          placeholder="Buscar supervisor, correo o contrato"
          aria-label="Buscar supervisor, correo o contrato"
          style={{ flex: '1 1 240px', minWidth: 0, fontSize: 16, padding: '9px 12px' }}
        />
        <button
          className="btn-ghost"
          onClick={onActualizar}
          disabled={cargando}
          style={{ padding: '9px 14px', fontSize: 12, minHeight: 44 }}
        >
          {cargando ? 'Actualizando…' : 'Actualizar'}
        </button>
        {datos && (
          <span style={{ fontSize: 12, color: 'var(--text-muted)' }}>Datos de las {fechaYHora(datos.generadoEn)}</span>
        )}
      </div>

      {error && (
        <div
          role="alert"
          style={{
            padding: '10px 14px',
            border: '1px solid var(--chip-red)',
            background: 'var(--chip-red-bg)',
            borderRadius: 8,
            fontSize: 13,
          }}
        >
          {error}
        </div>
      )}

      {!datos && !error && (
        <div style={{ fontSize: 13, color: 'var(--text-muted)' }}>Consultando el avance de los supervisores…</div>
      )}

      {datos && datos.contratosSinSupervisor.length > 0 && (
        <section className="card" style={{ padding: 16, borderColor: 'var(--alert-leve)' }}>
          <div
            style={{ display: 'flex', gap: 8, alignItems: 'center', fontSize: 14, fontWeight: 600, marginBottom: 10 }}
          >
            <IconAlertTriangle size={16} /> Contratos abiertos sin supervisor ({datos.contratosSinSupervisor.length})
          </div>
          <div style={{ display: 'grid', gap: 10 }}>
            {datos.contratosSinSupervisor.map((c) => (
              <TarjetaContrato key={c.id} c={c} />
            ))}
          </div>
        </section>
      )}

      {datos && datos.supervisores.length === 0 && (
        <div className="card" style={{ padding: '32px 16px', textAlign: 'center' }}>
          <IconUsers size={26} style={{ opacity: 0.4, margin: '0 auto 10px' }} />
          <div style={{ fontSize: 13, fontWeight: 600 }}>Todavía no hay supervisores registrados</div>
          <div style={{ fontSize: 12, color: 'var(--text-muted)' }}>Créelos en «Usuarios» con el rol Supervisor.</div>
        </div>
      )}

      {datos && datos.supervisores.length > 0 && supervisores.length === 0 && (
        <div style={{ fontSize: 13, color: 'var(--text-muted)' }}>Ningún supervisor coincide con «{filtro}».</div>
      )}

      {supervisores.map((s) => (
        <TarjetaSupervisor key={s.id} s={s} />
      ))}
    </div>
  )
}
