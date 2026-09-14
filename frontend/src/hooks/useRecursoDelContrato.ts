import { useEffect, useState } from 'react'

/**
 * Carga un recurso que cuelga de un contrato, con cancelación y estado de error
 * separado del estado vacío.
 *
 * ## Por qué existe
 *
 * `SupervisorPanel.tsx` tenía 948 líneas y sostenía a mano el estado de todo el
 * panel: cada recurso con su `useState`, su `useEffect`, su bandera de carga,
 * su bandera de error y su bandera de cancelación, repetidos con pequeñas
 * variaciones. Ese patrón copiado es de donde salen los dos fallos típicos de
 * esta pantalla:
 *
 * - **Condición de carrera al cambiar de contrato.** Sin la bandera de
 *   cancelación, la respuesta lenta del contrato anterior pisa a la del actual
 *   y el panel muestra datos del contrato equivocado. Aquí se cancela siempre,
 *   no cuando alguien se acuerda.
 * - **Confundir "falló" con "no hay".** Pintar "no hay alertas" cuando en
 *   realidad no se pudo consultar el servicio le asegura al supervisor que todo
 *   está en orden justo cuando el sistema no lo sabe. `error` es un estado
 *   propio, distinto de `datos` vacío.
 *
 * ## Lo que este hook NO es
 *
 * No es una caché ni un gestor de estado de servidor. No deduplica peticiones ni
 * revalida. Es la extracción del patrón que ya existía repetido, para que la
 * lógica de datos salga del componente y se pueda probar por separado. Si más
 * adelante el equipo quiere una librería de estado de servidor, la migración
 * parte de hooks aislados en vez de un archivo de mil líneas.
 *
 * @param contratoId contrato del que colgar la petición, o `null` si no hay uno
 *                   seleccionado — en cuyo caso no se pide nada
 * @param cargar     función que hace la petición
 * @param vacio      qué devolver mientras no hay datos o cuando falla
 */
export function useRecursoDelContrato<T>(
  contratoId: number | null | undefined,
  cargar: (contratoId: number) => Promise<T>,
  vacio: T,
): { datos: T; cargando: boolean; error: boolean; recargar: () => void } {
  const [datos, setDatos] = useState<T>(vacio)
  const [cargando, setCargando] = useState(false)
  const [error, setError] = useState(false)
  const [intento, setIntento] = useState(0)

  useEffect(() => {
    if (contratoId == null) {
      setDatos(vacio)
      setError(false)
      setCargando(false)
      return
    }

    let cancelado = false
    setCargando(true)
    setError(false)

    cargar(contratoId)
      .then((resultado) => {
        if (!cancelado) {
          setDatos(resultado)
          setCargando(false)
        }
      })
      .catch((err) => {
        // El detalle al log del navegador; a la interfaz, el hecho de que falló.
        console.error('No se pudo cargar un recurso del contrato ' + contratoId + ':', err)
        if (!cancelado) {
          setDatos(vacio)
          setError(true)
          setCargando(false)
        }
      })

    return () => {
      cancelado = true
    }
    // `cargar` y `vacio` se omiten a propósito: quien llama los define en línea,
    // así que cambian de identidad en cada render y reintroducirían el bucle de
    // peticiones que este hook existe para evitar.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [contratoId, intento])

  return { datos, cargando, error, recargar: () => setIntento((n) => n + 1) }
}
