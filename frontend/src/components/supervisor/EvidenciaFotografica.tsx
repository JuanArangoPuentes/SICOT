// Evidencia fotográfica de la entrega en bodega (subetapas 3.1 y 3.2).
//
// El supervisor recibe los bienes en el Centro y tiene que dejar constancia de
// lo que llegó. Hasta hoy el sistema no tenía dónde poner esa foto: el panel no
// ofrecía ninguna carga de archivos y el backend rechazaba las imágenes, así
// que la evidencia vivía en el teléfono de cada quien, fuera del expediente.
//
// La foto se envía tal como sale de la cámara. Reducirla aquí borraría la fecha
// y la ubicación que el teléfono escribe dentro del archivo, y son justo los
// datos que convierten una foto en evidencia de cuándo y dónde se recibió. El
// backend los lee al cargarla (MDL-205) y aquí se muestra lo que encontró.

import { useEffect, useRef, useState } from 'react'
import { subirDocumento } from '@/services/documentoService'
import { ApiError } from '@/services/api/client'
import { describirCaptura } from '@/services/format'
import type { DocumentoResponse } from '@/services/api/types'

interface Props {
  contratoId: number
  /** Identificador de la subetapa en el servidor. Sin él la foto no se puede asociar. */
  subetapaApiId?: number | null
  /** Código de la subetapa, para nombrar la evidencia (p. ej. «3.2»). */
  codigoSubetapa: string
  /** Se llama tras una carga correcta, para que la lista de documentos se refresque. */
  onCargada: () => void
}

/** De qué botón salió la foto: importa para explicar una ubicación ausente. */
type Origen = 'camara' | 'galeria'

type Estado =
  | { fase: 'vacio' }
  | { fase: 'elegida'; archivo: File; vistaPrevia: string; origen: Origen }
  | { fase: 'cargando'; archivo: File; vistaPrevia: string; origen: Origen }
  | { fase: 'cargada'; documento: DocumentoResponse; origen: Origen }
  | { fase: 'error'; mensaje: string; archivo: File; vistaPrevia: string; origen: Origen }

const TAMANIO_MAXIMO_BYTES = 20 * 1024 * 1024

export default function EvidenciaFotografica({ contratoId, subetapaApiId, codigoSubetapa, onCargada }: Props) {
  const [estado, setEstado] = useState<Estado>({ fase: 'vacio' })
  // Dos campos distintos: el de la cámara lleva `capture`, que en Android abre
  // directamente la cámara trasera; el otro deja elegir una foto ya tomada.
  const campoCamara = useRef<HTMLInputElement>(null)
  const campoGaleria = useRef<HTMLInputElement>(null)
  // La cámara se cerró sin devolver foto. En la app de Android eso incluye que
  // se haya negado el permiso de cámara: wry responde «ninguna foto» y nada más,
  // así que sin este aviso el botón parecería no hacer nada.
  const [camaraSinFoto, setCamaraSinFoto] = useState(false)
  // Tras una carga, la vista 'cargada' no pinta los <input>: React los
  // desmonta y, con «Agregar otra», crea otros nuevos. El efecto tiene que
  // volver a engancharse al campo nuevo; con dependencias vacías se quedaba en
  // el primero y el aviso de cámara sin foto dejaba de salir desde la segunda.
  const camposMontados = estado.fase !== 'cargada'

  useEffect(() => {
    const campo = campoCamara.current
    if (!campo) return
    // El evento `cancel` de un <input type="file"> no pasa por el sistema de
    // eventos de React, así que se escucha directamente.
    const alCancelar = () => setCamaraSinFoto(true)
    campo.addEventListener('cancel', alCancelar)
    return () => campo.removeEventListener('cancel', alCancelar)
  }, [camposMontados])

  const elegir = (archivo: File | undefined, origen: Origen) => {
    if (!archivo) return
    setCamaraSinFoto(false)
    if (archivo.size > TAMANIO_MAXIMO_BYTES) {
      setEstado({
        fase: 'error',
        mensaje: `La foto pesa ${(archivo.size / 1024 / 1024).toFixed(1)} MB y el máximo son 20 MB.`,
        archivo,
        vistaPrevia: URL.createObjectURL(archivo),
        origen,
      })
      return
    }
    setEstado({ fase: 'elegida', archivo, vistaPrevia: URL.createObjectURL(archivo), origen })
  }

  const cargar = async () => {
    if (estado.fase !== 'elegida' && estado.fase !== 'error') return
    // Una foto que ya se sabe demasiado grande no se sube: el servidor la
    // rechaza igual, después de gastar los datos del teléfono, y con un mensaje
    // genérico porque corta la conexión a mitad de la subida.
    if (estado.archivo.size > TAMANIO_MAXIMO_BYTES) return
    const { archivo, vistaPrevia, origen } = estado
    setEstado({ fase: 'cargando', archivo, vistaPrevia, origen })
    try {
      const doc = await subirDocumento(contratoId, archivo, {
        nombre: `Evidencia fotográfica ${codigoSubetapa} — ${archivo.name}`,
        subetapaId: subetapaApiId ?? undefined,
      })
      URL.revokeObjectURL(vistaPrevia)
      setEstado({ fase: 'cargada', documento: doc, origen })
      onCargada()
    } catch (e) {
      setEstado({
        fase: 'error',
        mensaje: e instanceof ApiError ? e.message : 'No se pudo cargar la foto.',
        archivo,
        vistaPrevia,
        origen,
      })
    }
  }

  const descartar = () => {
    if (estado.fase === 'elegida' || estado.fase === 'error') URL.revokeObjectURL(estado.vistaPrevia)
    setEstado({ fase: 'vacio' })
    if (campoCamara.current) campoCamara.current.value = ''
    if (campoGaleria.current) campoGaleria.current.value = ''
  }

  const demasiadoGrande =
    estado.fase !== 'vacio' && estado.fase !== 'cargada' && estado.archivo.size > TAMANIO_MAXIMO_BYTES

  if (estado.fase === 'cargada') {
    const { documento, origen } = estado
    const captura = describirCaptura(documento)
    const sinUbicacion = documento.capturaLatitud == null || documento.capturaLongitud == null
    return (
      <div style={{ fontSize: 11.5, color: 'var(--accent)', paddingLeft: 26 }}>
        Evidencia cargada: {documento.nombre}.{' '}
        {captura && <span style={{ color: 'var(--text-secondary)' }}>{captura}. </span>}
        {sinUbicacion && origen === 'galeria' && (
          // Android le quita la ubicación a una foto elegida de la galería
          // cuando la app no tiene permiso de ubicación de medios, y el selector
          // de fotos de Android 13 en adelante la quita siempre. La cámara, en
          // cambio, entrega el archivo que acaba de escribir.
          <span style={{ color: 'var(--text-muted)' }}>
            Al elegirla de la galería, Android puede haberle quitado la ubicación: para conservarla, use «Tomar foto de
            la entrega».{' '}
          </span>
        )}
        <button
          onClick={descartar}
          style={{
            background: 'none',
            border: 'none',
            padding: 0,
            color: 'var(--text-secondary)',
            textDecoration: 'underline',
            cursor: 'pointer',
            font: 'inherit',
          }}
        >
          Agregar otra
        </button>
      </div>
    )
  }

  return (
    <div style={{ paddingLeft: 26, display: 'flex', flexDirection: 'column', gap: 6 }}>
      {/*
        `image/*` y no `image/jpeg,image/png`, a propósito. En la app de Android,
        wry solo abre la cámara si la lista de tipos contiene literalmente
        `image/*` (RustWebChromeClient.onShowFileChooser). Con los dos tipos
        concretos abría el selector de fotos de la galería, y el botón «Tomar
        foto» no tomaba ninguna: se descubrió el 23-09-2026 al probarlo en un
        emulador de Android 14. La cámara entrega JPEG, y el backend valida el
        tipo real por sus bytes, así que no se admite nada que antes se rechazara.
      */}
      <input
        ref={campoCamara}
        type="file"
        accept="image/*"
        capture="environment"
        onChange={(e) => elegir(e.target.files?.[0], 'camara')}
        style={{ display: 'none' }}
      />
      <input
        ref={campoGaleria}
        type="file"
        accept="image/jpeg,image/png"
        onChange={(e) => elegir(e.target.files?.[0], 'galeria')}
        style={{ display: 'none' }}
      />

      {estado.fase === 'vacio' ? (
        <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap' }}>
          <button className="btn-green" style={BOTON} onClick={() => campoCamara.current?.click()}>
            Tomar foto de la entrega
          </button>
          <button style={{ ...BOTON, ...BOTON_SECUNDARIO }} onClick={() => campoGaleria.current?.click()}>
            Elegir una foto
          </button>
        </div>
      ) : (
        <>
          {/* Una URL blob:. Las dos CSP (nginx.conf.template y tauri.conf.json)
              tienen que admitir blob: en img-src; sin eso la vista previa sale rota. */}
          <img
            src={estado.vistaPrevia}
            alt={`Vista previa de la evidencia de la subetapa ${codigoSubetapa}`}
            style={{ maxWidth: 220, maxHeight: 160, borderRadius: 4, border: '1px solid var(--border)' }}
          />
          <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap', alignItems: 'center' }}>
            <button
              className="btn-green"
              style={{ ...BOTON, opacity: estado.fase === 'cargando' || demasiadoGrande ? 0.6 : 1 }}
              onClick={cargar}
              disabled={estado.fase === 'cargando' || demasiadoGrande}
            >
              {estado.fase === 'cargando' ? 'Cargando…' : 'Cargar evidencia'}
            </button>
            <button style={{ ...BOTON, ...BOTON_SECUNDARIO }} onClick={descartar} disabled={estado.fase === 'cargando'}>
              Descartar
            </button>
            <span style={{ fontSize: 11, color: 'var(--text-muted)' }}>
              {(estado.archivo.size / 1024 / 1024).toFixed(1)} MB
            </span>
          </div>
        </>
      )}

      {estado.fase === 'vacio' && camaraSinFoto && (
        <span style={{ fontSize: 11.5, color: 'var(--text-muted)' }}>
          No se tomó ninguna foto. Si la aplicación no tiene permiso para usar la cámara, actívelo en los ajustes del
          teléfono (Aplicaciones › SICOT › Permisos), o use «Elegir una foto».
        </span>
      )}

      {estado.fase === 'error' && (
        <span style={{ fontSize: 11.5, color: 'var(--danger, #c0392b)' }}>{estado.mensaje}</span>
      )}

      {!subetapaApiId && estado.fase !== 'vacio' && (
        // Se dice antes de cargar, no después: la foto se guardaría igual, pero
        // colgando del contrato y no de la subetapa, y quien la busque en el
        // paso no la encontraría.
        <span style={{ fontSize: 11.5, color: 'var(--text-muted)' }}>
          Esta subetapa todavía no existe en el servidor, así que la foto quedará en el contrato sin asociarse al paso{' '}
          {codigoSubetapa}.
        </span>
      )}
    </div>
  )
}

const BOTON: React.CSSProperties = { padding: '4px 12px', fontSize: 11, cursor: 'pointer' }

const BOTON_SECUNDARIO: React.CSSProperties = {
  background: 'transparent',
  border: '1px solid var(--border)',
  borderRadius: 4,
  color: 'var(--text-secondary)',
}
