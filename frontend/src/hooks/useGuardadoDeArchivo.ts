import { useState } from 'react'
import { ApiError } from '@/services/api/client'
import type { ResultadoGuardado } from '@/services/guardarArchivo'

/**
 * Lo que se le dice a la persona después de guardar o descargar un archivo.
 *
 * <p>En el APK de Android el «Guardar como» del sistema se cierra y vuelve a
 * SICOT sin ninguna otra señal: si no se dice que el archivo quedó escrito,
 * guardar y no hacer nada se ven igual, que es el síntoma mudo que MDL-184 vino
 * a quitar. Y si falla —el proveedor elegido no admite escritura, no queda
 * espacio, el formato ya no existe— el motivo tiene que verse. En el navegador
 * el gestor de descargas ya avisa, y quien cierra el diálogo sin elegir sitio
 * sabe que no guardó, así que «navegador» y «cancelado» no dicen nada.
 *
 * <p>Exportar la bitácora en CSV y descargar un formato tragaban el resultado
 * y el error; la descarga de documentos del supervisor ya lo hacía así.
 */
export function useGuardadoDeArchivo() {
  const [aviso, setAviso] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)

  const guardar = async (nombre: string, accion: () => Promise<ResultadoGuardado>) => {
    setAviso(null)
    setError(null)
    try {
      if ((await accion()) === 'guardado') setAviso(`«${nombre}» quedó guardado en el teléfono.`)
    } catch (err) {
      // El motivo del servidor cuando lo hay; si no, el fallo fue al escribir
      // en el teléfono, y elegir otra carpeta es lo único que la persona puede
      // cambiar.
      setError(
        err instanceof ApiError
          ? err.message
          : `No se pudo guardar «${nombre}». Intente de nuevo y, si vuelve a fallar, elija otra carpeta.`,
      )
    }
  }

  return { aviso, error, guardar }
}
