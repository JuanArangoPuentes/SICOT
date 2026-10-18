// Qué palabras de la redacción del Copiloto no están en las notas del
// supervisor, y qué palabras de las notas no repite la redacción.
//
// Existe por la revisión ciega del 01-10-2026: de 81 redacciones del modelo
// (qwen2.5:7b) que pasaron todas las comprobaciones automáticas, 23 cambiaban
// algo grave que esas comprobaciones no pueden ver. «La otra semana» salió
// «la semana anterior», «se instaló en coordinación académica» (un lugar)
// salió «en coordinación con el área académica», «cambiar las sillas» salió
// «la reparación», y «todo completo» salió «en buen estado». Casi siempre el
// error está en una palabra que no estaba en las notas, o en una de las notas
// que desapareció; resaltarlas lleva la vista a lo que hay que leer dos veces
// antes de firmar. Simulado sobre esas 81 redacciones, en las fieles se
// marcan en promedio menos de dos palabras de cada lado, y en las 23 graves
// hay al menos una marcada que apunta al error («anterior», «área»,
// «reparación», «buen estado», o «completo» y «vigentes» que desaparecen de
// las notas). No decide nada: el supervisor lee y elige.

export type Tramo = { texto: string; marcada: boolean }

/** Sin tildes y en minúsculas: «Recibió» y «recibio» son la misma palabra. */
function normalizar(texto: string): string {
  return texto.normalize('NFD').replace(/\p{M}/gu, '').toLowerCase()
}

/**
 * La raíz con que se comparan las palabras: sus cinco primeras letras.
 * «entregó», «entrega» y «entregaron» comparten «entre»; así una conjugación
 * distinta no se resalta como si fuera otra cosa.
 */
const raiz = (palabra: string) => normalizar(palabra).slice(0, 5)

/**
 * Lo que la redacción formal agrega sin agregar hechos: giros como «se
 * realizó», «se procedió», «correspondiente», «durante». Resaltarlos solo
 * haría ruido. Lo que sí puede cambiar el sentido («verificó», «observaciones»,
 * «según») no está aquí a propósito.
 *
 * Por raíz van solo los giros cuya raíz no comparte ninguna palabra que diga
 * otra cosa, para cubrir todas sus conjugaciones. El resto va como palabra
 * exacta: hasta el 02-10-2026 todo iba por raíz, y «entre» tapaba «entregó»,
 * «corre» tapaba «correctamente», «nuevo» tapaba «nuevos», «prese» tapaba
 * «presentó» y «const» tapaba «constató»; el cambio de verbo, la valoración y
 * el dato añadido que este resaltado existe para señalar quedaban sin marca.
 * «todos» y «nuevo» salieron de la lista: afirman una cantidad o un hecho.
 */
const RAICES_DE_RELLENO = new Set(['reali', 'regis', 'indic', 'menci', 'refer', 'relac'])

const PALABRAS_DE_RELLENO = new Set([
  'efectuo',
  'efectuar',
  'efectuado',
  'efectuada',
  'efectuaron',
  'procedio',
  'proceder',
  'procedieron',
  'encuentra',
  'encuentran',
  'correspondiente',
  'correspondientes',
  'corresponde',
  'corresponden',
  'respecto',
  'respectivo',
  'respectiva',
  'respectivos',
  'respectivas',
  'respectivamente',
  'mediante',
  'durante',
  'debido',
  'dicho',
  'dicha',
  'dichos',
  'dichas',
  'mismo',
  'misma',
  'mismos',
  'mismas',
  'cuales',
  'siendo',
  'estan',
  'estaba',
  'estaban',
  'dentro',
  'parte',
  'asimismo',
  'ademas',
  'embargo',
  'mientras',
  'aunque',
  'tanto',
  'consta',
  'constar',
  'constancia',
  'actual',
  'actualmente',
  'presente',
  'continuacion',
  'siguiente',
  'siguientes',
  'cabe',
  'cual',
  'quien',
  'quienes',
  'tambien',
  'luego',
  'hecho',
  'haber',
  'habia',
  'habian',
  'hayan',
  'puede',
  'pueden',
  'pudo',
  'sobre',
  'entre',
  'hasta',
  'desde',
  'cada',
  'otro',
  'otra',
  'estos',
  'estas',
  'aquel',
  'aquella',
  // palabras de función de cuatro letras
  'para',
  'esta',
  'este',
  'esto',
  'hubo',
  'todo',
  'toda',
  'dijo',
  'sido',
  'como',
  'pero',
  'algo',
  'bajo',
])

/**
 * Números, ordinales y meses en letras: los comprueba el backend por su valor,
 * no por la palabra. «segundo» va entero porque su raíz es la de «según», que
 * cambia de quién es lo que se afirma.
 */
const RAICES_NUMERICAS = new Set([
  'uno',
  'una',
  'dos',
  'tres',
  'cuatr',
  'cinco',
  'seis',
  'siete',
  'ocho',
  'nueve',
  'diez',
  'once',
  'doce',
  'veint',
  'trein',
  'cuare',
  'cincu',
  'sesen',
  'seten',
  'ochen',
  'noven',
  'cient',
  'mil',
  'millo',
  'prime',
  'terce',
  'cuart',
  'quint',
  'cero',
  'enero',
  'febre',
  'marzo',
  'abril',
  'mayo',
  'junio',
  'julio',
  'agost',
  'septi',
  'octub',
  'novie',
  'dicie',
])

const PALABRAS_NUMERICAS = new Set(['segundo', 'segunda', 'segundos', 'segundas'])

/** Si la palabra (ya normalizada) no aporta hechos o es una cifra en letras. */
function noCuenta(palabra: string): boolean {
  const r = palabra.slice(0, 5)
  return (
    PALABRAS_DE_RELLENO.has(palabra) ||
    RAICES_DE_RELLENO.has(r) ||
    PALABRAS_NUMERICAS.has(palabra) ||
    RAICES_NUMERICAS.has(r)
  )
}

/**
 * Las raíces de las palabras con contenido del texto. Las de relleno no
 * cuentan: «entre semana» no da por dicho que alguien «entregó» algo, aunque
 * las dos palabras empiecen igual.
 */
function raicesDe(texto: string): Set<string> {
  const r = new Set<string>()
  for (const p of normalizar(texto).match(/\p{L}{3,}/gu) ?? []) if (!PALABRAS_DE_RELLENO.has(p)) r.add(p.slice(0, 5))
  return r
}

/**
 * El texto partido en tramos, con marcadas las palabras de cuatro letras o
 * más cuya raíz no aparece en el otro texto. Los tramos, unidos, dan el texto
 * original exacto.
 */
export function marcarLoQueNoEstaEn(texto: string, otro: string): Tramo[] {
  const delOtro = raicesDe(otro)
  const tramos: Tramo[] = []
  for (const parte of texto.split(/(\p{L}+)/u)) {
    if (parte === '') continue
    const marcada = /^\p{L}{4,}$/u.test(parte) && !delOtro.has(raiz(parte)) && !noCuenta(normalizar(parte))
    const ultimo = tramos[tramos.length - 1]
    if (ultimo && ultimo.marcada === marcada) ultimo.texto += parte
    else tramos.push({ texto: parte, marcada })
  }
  return tramos
}
