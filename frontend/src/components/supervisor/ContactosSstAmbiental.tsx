// Paso 2.2 del proceso real: los profesionales de SST y ambiental.
//
// La hoja de Alex Zapata (paso 3) pide identificarlos —«la información de
// contacto se debe encontrar en el estudio previo el cual reposa en el SECOP»—
// y escribirles que el contrato inició, invitándolos a las entregas. Hasta el
// 22-10-2026 el borrador salía sin destinatarios y en cada correo había que
// volver a buscarlos en un PDF de más de 5 MB.
//
// Ahora se registran una vez con el contrato. Se escriben a mano o se carga el
// estudio previo y SICOT propone los correos que encuentra en él, con el rol
// que sugiere el texto de alrededor; el supervisor elige. El archivo no se
// guarda: el estudio previo ya está publicado en el SECOP.
//
// Sigue a la vista con el 2.2 cerrado: el correo de las fechas del 2.4 sale
// dirigido a estos contactos, y si faltan, aquí es donde se registran.

import { useCallback, useEffect, useRef, useState } from 'react'
import {
  guardarContactosSstAmbiental,
  leerContactosDelEstudioPrevio,
  obtenerContactosSstAmbiental,
  obtenerEntregasAcordadas,
} from '@/services/contratoService'
import { ApiError } from '@/services/api/client'
import type {
  ContactoDelContrato,
  ContratoResponse,
  EntregaAcordada,
  LecturaDeContactosResponse,
  RolDeContacto,
} from '@/services/api/types'
import BorradorDeCorreo from './BorradorDeCorreo'
import { BOTON, BOTON_SECUNDARIO } from './enlacesExternos'

const CAJA: React.CSSProperties = {
  paddingLeft: 26,
  display: 'flex',
  flexDirection: 'column',
  gap: 8,
  marginTop: 6,
}
const NOTA: React.CSSProperties = { fontSize: 11.5, color: 'var(--text-muted)' }
const CAMPO: React.CSSProperties = {
  fontFamily: 'var(--font-ui)',
  fontSize: 11.5,
  padding: '4px 6px',
  borderRadius: 4,
  border: '1px solid var(--border)',
  background: 'var(--bg-card)',
  color: 'var(--text-primary)',
}

export const NOMBRE_DEL_ROL: Record<RolDeContacto, string> = { SST: 'SST', AMBIENTAL: 'Ambiental' }

/** El mismo criterio que el backend: el correo se compara sin mayúsculas. */
function yaEsta(contactos: ContactoDelContrato[], rol: RolDeContacto, correo: string): boolean {
  return contactos.some((c) => c.rol === rol && c.correo.toLowerCase() === correo.toLowerCase())
}

export default function ContactosSstAmbiental({
  contrato,
  version = 0,
  onGuardado,
}: {
  contrato: ContratoResponse
  /** Sube cuando el 2.4 guarda fechas: el correo de este paso las lleva. Ver AcuerdoDeEntregas. */
  version?: number
  onGuardado?: () => void
}) {
  // null mientras no se haya podido consultar: con la consulta fallida,
  // «Agregar» haría un reemplazo completo y borraría los que sí existen.
  const [contactos, setContactos] = useState<ContactoDelContrato[] | null>(null)
  const [entregas, setEntregas] = useState<EntregaAcordada[]>([])
  const [error, setError] = useState<string | null>(null)
  const [guardando, setGuardando] = useState(false)
  const [rol, setRol] = useState<RolDeContacto>('SST')
  const [nombre, setNombre] = useState('')
  const [correo, setCorreo] = useState('')
  const [leyendo, setLeyendo] = useState(false)
  const [lectura, setLectura] = useState<LecturaDeContactosResponse | null>(null)
  const archivoRef = useRef<HTMLInputElement>(null)

  const cargar = useCallback(() => {
    obtenerContactosSstAmbiental(contrato.id)
      .then((r) => {
        setContactos(r.contactos)
        setError(null)
      })
      .catch((e) => setError(e instanceof ApiError ? e.message : 'No se pudieron consultar los contactos.'))
    // Las fechas acordadas solo enriquecen el correo: sin ellas, el borrador
    // promete compartirlas cuando estén.
    obtenerEntregasAcordadas(contrato.id)
      .then((a) => setEntregas(a.entregas))
      .catch(() => setEntregas([]))
  }, [contrato.id])

  useEffect(() => {
    cargar()
  }, [cargar, version])

  const guardar = (lista: ContactoDelContrato[], alTerminar?: () => void) => {
    setGuardando(true)
    setError(null)
    guardarContactosSstAmbiental(contrato.id, lista)
      .then((r) => {
        setContactos(r.contactos)
        alTerminar?.()
        onGuardado?.()
      })
      .catch((e) => setError(e instanceof ApiError ? e.message : 'No se pudieron guardar los contactos.'))
      .finally(() => setGuardando(false))
  }

  const agregar = (nuevo: ContactoDelContrato, alTerminar?: () => void) => {
    if (!contactos) return
    if (yaEsta(contactos, nuevo.rol, nuevo.correo)) {
      setError(`${nuevo.correo} ya está registrado como ${NOMBRE_DEL_ROL[nuevo.rol]}.`)
      return
    }
    guardar([...contactos, nuevo], alTerminar)
  }

  const leerArchivo = (archivo: File) => {
    setLeyendo(true)
    setError(null)
    setLectura(null)
    leerContactosDelEstudioPrevio(contrato.id, archivo)
      .then(setLectura)
      .catch((e) => setError(e instanceof ApiError ? e.message : 'No se pudo leer el documento.'))
      .finally(() => {
        setLeyendo(false)
        if (archivoRef.current) archivoRef.current.value = ''
      })
  }

  if (!contactos) {
    return (
      <div style={CAJA}>
        {error ? (
          <>
            <span style={{ fontSize: 11.5, color: 'var(--alert-critica)' }}>{error}</span>
            <button type="button" style={{ ...BOTON, ...BOTON_SECUNDARIO, alignSelf: 'flex-start' }} onClick={cargar}>
              Volver a consultar
            </button>
          </>
        ) : (
          <span style={NOTA}>Consultando los contactos de SST y ambiental…</span>
        )}
      </div>
    )
  }

  const correoValido = /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(correo.trim())

  return (
    <>
      <div style={CAJA}>
        <span style={{ ...NOTA, color: 'var(--text-primary)', fontWeight: 600 }}>Profesionales de SST y ambiental</span>
        <span style={NOTA}>
          Sus correos están en el estudio previo, dentro del expediente del proceso en el SECOP II (el enlace, en el
          paso 2.3). Regístrelos una vez: los correos de este paso y el de las fechas de entrega saldrán dirigidos a
          ellos.
        </span>

        {error && <span style={{ fontSize: 11.5, color: 'var(--alert-critica)' }}>{error}</span>}

        {contactos.length === 0 ? (
          <span style={NOTA}>Todavía no hay contactos registrados.</span>
        ) : (
          <ul style={{ margin: 0, paddingLeft: 16, display: 'flex', flexDirection: 'column', gap: 4 }}>
            {contactos.map((c) => (
              <li key={`${c.rol}|${c.correo}`} style={{ fontSize: 11.5, color: 'var(--text-primary)' }}>
                <strong>{NOMBRE_DEL_ROL[c.rol]}</strong>
                {c.nombre ? ` · ${c.nombre}` : ''} · {c.correo}{' '}
                <button
                  type="button"
                  style={{ ...BOTON, ...BOTON_SECUNDARIO, padding: '2px 8px', marginLeft: 6 }}
                  disabled={guardando}
                  aria-label={`Quitar a ${c.correo}`}
                  onClick={() => guardar(contactos.filter((x) => x !== c))}
                >
                  Quitar
                </button>
              </li>
            ))}
          </ul>
        )}

        <div style={{ display: 'flex', gap: 6, flexWrap: 'wrap', alignItems: 'center' }}>
          <select
            aria-label="Rol del contacto"
            value={rol}
            onChange={(e) => setRol(e.target.value as RolDeContacto)}
            style={CAMPO}
          >
            <option value="SST">SST</option>
            <option value="AMBIENTAL">Ambiental</option>
          </select>
          <input
            type="text"
            aria-label="Nombre del contacto"
            placeholder="Nombre (opcional)"
            value={nombre}
            maxLength={255}
            onChange={(e) => setNombre(e.target.value)}
            style={{ ...CAMPO, flex: '1 1 140px' }}
          />
          <input
            type="email"
            aria-label="Correo del contacto"
            placeholder="correo@sena.edu.co"
            value={correo}
            maxLength={255}
            onChange={(e) => setCorreo(e.target.value)}
            style={{ ...CAMPO, flex: '1 1 180px' }}
          />
          <button
            type="button"
            className="btn-green"
            style={BOTON}
            disabled={guardando || !correoValido}
            onClick={() =>
              agregar({ rol, nombre: nombre.trim() || null, correo: correo.trim() }, () => {
                setNombre('')
                setCorreo('')
              })
            }
          >
            Agregar
          </button>
        </div>

        <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap', alignItems: 'center' }}>
          <input
            ref={archivoRef}
            type="file"
            accept=".pdf,.docx,application/pdf,application/vnd.openxmlformats-officedocument.wordprocessingml.document"
            aria-label="Estudio previo"
            style={{ display: 'none' }}
            onChange={(e) => {
              const archivo = e.target.files?.[0]
              if (archivo) leerArchivo(archivo)
            }}
          />
          <button
            type="button"
            style={{ ...BOTON, ...BOTON_SECUNDARIO }}
            disabled={leyendo}
            onClick={() => archivoRef.current?.click()}
          >
            {leyendo ? 'Leyendo el estudio previo…' : 'Buscar los correos en el estudio previo'}
          </button>
          <span style={NOTA}>PDF o Word. SICOT lo lee y no lo guarda.</span>
        </div>

        {lectura?.aviso && <span style={{ fontSize: 11.5, color: 'var(--alert-leve)' }}>{lectura.aviso}</span>}
        {lectura && lectura.encontrados.length > 0 && (
          <div style={{ display: 'flex', flexDirection: 'column', gap: 6 }}>
            <span style={NOTA}>
              Correos del estudio previo. El rol es una sugerencia por el texto que los rodea: revíselo antes de
              agregarlos.
            </span>
            {lectura.encontrados.map((e) => (
              <div
                key={e.correo}
                style={{
                  border: '1px solid var(--border)',
                  borderRadius: 4,
                  padding: 6,
                  display: 'flex',
                  flexDirection: 'column',
                  gap: 4,
                }}
              >
                <span style={{ fontSize: 11.5, color: 'var(--text-primary)' }}>
                  <strong>{e.correo}</strong>
                  {e.rolSugerido ? ` — parece de ${NOMBRE_DEL_ROL[e.rolSugerido]}` : ''}
                </span>
                <span style={{ ...NOTA, fontStyle: 'italic' }}>«…{e.contexto}…»</span>
                <div style={{ display: 'flex', gap: 6, flexWrap: 'wrap' }}>
                  {(['SST', 'AMBIENTAL'] as RolDeContacto[]).map((r) => (
                    <button
                      key={r}
                      type="button"
                      className={e.rolSugerido === r ? 'btn-green' : undefined}
                      style={e.rolSugerido === r ? BOTON : { ...BOTON, ...BOTON_SECUNDARIO }}
                      disabled={guardando || yaEsta(contactos, r, e.correo)}
                      onClick={() => agregar({ rol: r, nombre: null, correo: e.correo })}
                    >
                      {yaEsta(contactos, r, e.correo)
                        ? `Ya es ${NOMBRE_DEL_ROL[r]}`
                        : `Agregar como ${NOMBRE_DEL_ROL[r]}`}
                    </button>
                  ))}
                </div>
              </div>
            ))}
          </div>
        )}
      </div>

      <BorradorDeCorreo tipo="CORREO_SST" contrato={contrato} datos={{ contactos, entregas }} />
    </>
  )
}
