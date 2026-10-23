// Leer la tabla de bienes que el supervisor pega desde la oferta económica.
//
// POR QUÉ EXISTE
// El paso 5 de la lista de chequeo de Alex pide adjuntarle al contratista un
// Excel con los bienes y cantidades. Esos datos están en la oferta económica
// adjudicada, que el supervisor ya tiene abierta: capturar 35 ítems a mano
// —con fichas técnicas de 900 caracteres— no lo haría nadie dos veces. Pegar
// las columnas es la vía que sí existe, y es lo que convierte el requerimiento
// en algo usable.
//
// LO QUE TOLERA, Y POR QUÉ
// Lo que llega del portapapeles no es un CSV limpio: Excel pega columnas
// separadas por tabulación, los valores vienen con «$», con separador de miles
// y a veces con la columna del total ya calculada. El archivo real del Lote 4
// escribe «$ 29,462.51» (coma de miles, punto decimal) y una hoja configurada
// en español escribe «$ 29.462,51» exactamente al contrario. Si el lector se
// equivoca de separador, un mouse de 29 mil pesos se convierte en uno de 29, y
// el pedido que recibe el contratista queda mal por un detalle de formato.

/** Una fila leída del pegado, lista para mandar al backend. */
export interface ItemPegado {
  numero: number
  descripcion: string
  cantidad: number
  valorUnitario: number
}

export interface ResultadoDelPegado {
  items: ItemPegado[]
  /** Las líneas que no se pudieron leer, con el motivo, para mostrarlas tal cual. */
  descartadas: Array<{ linea: string; motivo: string }>
}

/**
 * Convierte un número escrito a mano o pegado de una hoja de cálculo.
 *
 * Decide el separador decimal por el último que aparezca, que es lo que
 * distingue «29,462.51» de «29.462,51»; con un solo separador, es decimal solo
 * si deja uno o dos dígitos detrás («1.5» es 1,5 pero «1.500» es mil quinientos,
 * porque ninguna hoja escribe tres decimales en pesos).
 */
export function leerNumero(texto: string): number | null {
  const limpio = texto.replace(/\$/g, '').replace(/\s| /g, '').trim()
  if (limpio === '' || limpio === '-') return null
  const negativo = /^\(.*\)$/.test(limpio) || limpio.startsWith('-')
  const soloCifras = limpio.replace(/[()\-+]/g, '')
  if (!/^[\d.,]+$/.test(soloCifras)) return null

  const ultimaComa = soloCifras.lastIndexOf(',')
  const ultimoPunto = soloCifras.lastIndexOf('.')
  let decimal = ''
  if (ultimaComa >= 0 && ultimoPunto >= 0) {
    decimal = ultimaComa > ultimoPunto ? ',' : '.'
  } else if (ultimaComa >= 0 || ultimoPunto >= 0) {
    const sep = ultimaComa >= 0 ? ',' : '.'
    const partes = soloCifras.split(sep)
    // Un solo separador con 1 o 2 dígitos detrás y sin repetirse: es decimal.
    if (partes.length === 2 && partes[1].length > 0 && partes[1].length <= 2) decimal = sep
  }

  const normalizado =
    decimal === ''
      ? soloCifras.replace(/[.,]/g, '')
      : soloCifras
          .split(decimal)
          .map((parte, i) => (i === 0 ? parte.replace(/[.,]/g, '') : parte))
          .join('.')
  const valor = Number(normalizado)
  if (!Number.isFinite(valor)) return null
  return negativo ? -valor : valor
}

/**
 * Lee la tabla pegada. Cada línea debe traer, separados por tabulación (o por
 * punto y coma, que es lo que sale al pegar desde un CSV): número de ítem,
 * descripción, cantidad y valor unitario. Una quinta columna con el total se
 * ignora: el total lo calcula SICOT, y arrastrar el de la oferta sería guardar
 * una cifra que puede no coincidir con cantidad × valor.
 *
 * Las líneas de encabezado y las de subtotal se descartan solas, porque su
 * primera celda no es un número: así se puede pegar el bloque entero de la
 * hoja sin recortarlo.
 */
export function leerPegado(texto: string): ResultadoDelPegado {
  const items: ItemPegado[] = []
  const descartadas: Array<{ linea: string; motivo: string }> = []
  const numerosVistos = new Set<number>()

  for (const cruda of texto.split(/\r?\n/)) {
    const linea = cruda.trim()
    if (linea === '') continue
    const celdas = linea.split(/\t|;/).map((c) => c.trim())
    if (celdas.length < 4) {
      descartadas.push({ linea, motivo: 'La fila no trae las cuatro columnas (ítem, descripción, cantidad, valor).' })
      continue
    }

    const numero = leerNumero(celdas[0])
    if (numero === null || !Number.isInteger(numero) || numero < 1) {
      // Encabezados y subtotales caen aquí: se ignoran en silencio, porque no
      // son un error del supervisor sino parte de lo que pegó.
      continue
    }
    const descripcion = celdas[1].replace(/^"|"$/g, '').trim()
    const cantidad = leerNumero(celdas[2])
    const valorUnitario = leerNumero(celdas[3])

    if (descripcion === '') {
      descartadas.push({ linea, motivo: `El ítem ${numero} no trae descripción.` })
      continue
    }
    if (cantidad === null || !Number.isInteger(cantidad) || cantidad < 0) {
      descartadas.push({ linea, motivo: `El ítem ${numero} no trae una cantidad entera.` })
      continue
    }
    if (valorUnitario === null || valorUnitario < 0) {
      descartadas.push({ linea, motivo: `El ítem ${numero} no trae un valor unitario.` })
      continue
    }
    if (numerosVistos.has(numero)) {
      descartadas.push({ linea, motivo: `El ítem ${numero} está repetido; se queda el primero.` })
      continue
    }

    numerosVistos.add(numero)
    items.push({ numero, descripcion, cantidad, valorUnitario })
  }

  items.sort((a, b) => a.numero - b.numero)
  return { items, descartadas }
}
