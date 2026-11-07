import { describe, expect, it } from 'vitest'
import { leerNumero, leerPegado } from './pegarSolicitudDePedido'

// Las cifras son las del pedido real del Lote 4 («Solicitud_Maybe.xlsx»), que
// es lo que el supervisor va a pegar: ahí un mouse vale «$ 29,462.51» y la hoja
// de otro contrato, configurada en español, escribiría «$ 29.462,51». Leer mal
// el separador convierte 29 mil pesos en 29, y el pedido sale mal por un
// detalle de formato.
describe('leerNumero', () => {
  it('lee el formato del archivo real: coma de miles y punto decimal', () => {
    expect(leerNumero('$ 29,462.51')).toBe(29462.51)
    expect(leerNumero(' $ 2,445,388.33 ')).toBe(2445388.33)
  })

  it('lee el formato en español: punto de miles y coma decimal', () => {
    expect(leerNumero('$ 29.462,51')).toBe(29462.51)
    expect(leerNumero('45.900.300')).toBe(45900300)
  })

  it('un solo separador es de miles cuando deja tres dígitos detrás', () => {
    // «1.500» es mil quinientos: ninguna hoja escribe pesos con tres decimales.
    expect(leerNumero('1.500')).toBe(1500)
    expect(leerNumero('1,500')).toBe(1500)
    expect(leerNumero('1.5')).toBe(1.5)
    expect(leerNumero('1,5')).toBe(1.5)
  })

  it('devuelve null cuando no hay número, para no inventar un cero', () => {
    expect(leerNumero('')).toBeNull()
    expect(leerNumero(' - ')).toBeNull()
    expect(leerNumero('ITEMS')).toBeNull()
    expect(leerNumero('n/a')).toBeNull()
  })

  it('acepta el cero, que en el formato real significa «no se pide»', () => {
    expect(leerNumero('0')).toBe(0)
    expect(leerNumero('$ -')).toBeNull()
  })
})

describe('leerPegado', () => {
  it('lee el bloque pegado de la oferta, con encabezado y subtotal incluidos', () => {
    const pegado = [
      'ITEMS\tDESCRIPCIÓN\tCANTIDAD\tVALOR UNITARIO CON IVA\tVALOR TOTAL CON IVA',
      '1\tMouse Óptico Alámbrico 1000 DPI\t83\t$ 29,462.51\t$ 2,445,388.33',
      '2\tUnidad de Estado Sólido 500 GB\t20\t$ 183,122.52\t$ 3,662,450.40',
      '30\tGENIO MECÁNICO 1004 FICHAS\t0\t$ 161,789.83\t$ -',
      'SUBTOTAL\t\t\t\t$ 45,899,436.42',
    ].join('\n')

    const { items, descartadas } = leerPegado(pegado)

    // El encabezado y el subtotal se van solos: el supervisor pega el bloque
    // entero sin tener que recortarlo.
    expect(descartadas).toEqual([])
    expect(items).toHaveLength(3)
    expect(items[0]).toEqual({
      numero: 1,
      descripcion: 'Mouse Óptico Alámbrico 1000 DPI',
      cantidad: 83,
      valorUnitario: 29462.51,
    })
    // La cantidad 0 se conserva: en el formato real significa «estaba en la
    // oferta y no se pide», y borrar la fila cambiaría la numeración.
    expect(items[2]).toEqual({
      numero: 30,
      descripcion: 'GENIO MECÁNICO 1004 FICHAS',
      cantidad: 0,
      valorUnitario: 161789.83,
    })
  })

  it('ignora la columna del total que trae la oferta', () => {
    // 83 × 29.462,51 = 2.445.388,33; si el total se arrastrara en vez de
    // calcularse, un total mal copiado en la oferta llegaría al contratista.
    const { items } = leerPegado('1\tMouse\t83\t29462.51\t999999999')
    expect(items[0]).not.toHaveProperty('valorTotal')
    expect(items[0].valorUnitario).toBe(29462.51)
  })

  it('ordena por número de ítem, que es como los nombra el contratista', () => {
    const { items } = leerPegado(['12\tMemoria RAM\t30\t263421.62', '3\tMemoria USB\t80\t81303.87'].join('\n'))
    expect(items.map((i) => i.numero)).toEqual([3, 12])
  })

  it('descarta la fila incompleta diciendo cuál, en vez de guardarla a medias', () => {
    const { items, descartadas } = leerPegado(
      ['1\tCable HDMI 3 m\t50\t37667.31', '2\tKit electrónico\t\t94246.02'].join('\n'),
    )
    expect(items).toHaveLength(1)
    expect(descartadas).toHaveLength(1)
    expect(descartadas[0].motivo).toContain('ítem 2')
  })

  it('avisa del ítem repetido y se queda con el primero', () => {
    const { items, descartadas } = leerPegado(
      ['7\tExtensión eléctrica\t20\t49602.46', '7\tMicrómetro digital\t10\t205463.32'].join('\n'),
    )
    expect(items).toHaveLength(1)
    expect(items[0].descripcion).toBe('Extensión eléctrica')
    expect(descartadas[0].motivo).toContain('repetido')
  })

  it('acepta el punto y coma, que es lo que sale al pegar desde un CSV', () => {
    const { items } = leerPegado('5;Kit Arduino;9;162237.39')
    expect(items[0]).toEqual({ numero: 5, descripcion: 'Kit Arduino', cantidad: 9, valorUnitario: 162237.39 })
  })

  it('no devuelve nada con un pegado vacío o que no es una tabla', () => {
    expect(leerPegado('').items).toEqual([])
    expect(leerPegado('   \n  \n').items).toEqual([])
    expect(leerPegado('esto no es una tabla').items).toEqual([])
  })
})
