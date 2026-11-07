// Enlaces que salen de SICOT (el proceso en el SECOP II, un documento del
// expediente, un correo) y el botón de copiar que los acompaña.
//
// En el navegador un <a target="_blank"> abre la página y un mailto: abre el
// correo. En la aplicación empaquetada (escritorio y Android, ADR-012) no:
// Tauri no abre nada fuera de su ventana sin el plugin opener, que SICOT no
// trae. Ahí el enlace sería un control que no hace nada, así que solo se
// ofrece copiar, que funciona en los tres sitios.

import { useState } from 'react'
import { enAplicacionEmpaquetada } from '@/services/entorno'

/** ¿Se pueden abrir enlaces y mailto: desde aquí? */
export function sePuedenAbrirEnlaces(): boolean {
  return !enAplicacionEmpaquetada()
}

/**
 * Copia un texto. navigator.clipboard exige un contexto seguro, y la app de
 * Android se sirve desde http://tauri.localhost, que no lo es: ahí se usa el
 * camino antiguo, que todos los WebView siguen aceptando.
 */
export async function copiarAlPortapapeles(texto: string): Promise<boolean> {
  try {
    if (navigator.clipboard && window.isSecureContext) {
      await navigator.clipboard.writeText(texto)
      return true
    }
  } catch {
    // se intenta el camino antiguo
  }
  const area = document.createElement('textarea')
  area.value = texto
  area.setAttribute('readonly', '')
  area.style.position = 'fixed'
  area.style.opacity = '0'
  document.body.appendChild(area)
  area.select()
  try {
    return document.execCommand('copy')
  } catch {
    return false
  } finally {
    document.body.removeChild(area)
  }
}

/** Un botón «Copiar» que dice si funcionó, en vez de callar. */
export function BotonCopiar({ texto, etiqueta = 'Copiar' }: { texto: string; etiqueta?: string }) {
  const [resultado, setResultado] = useState<'copiado' | 'fallo' | null>(null)
  return (
    <button
      type="button"
      style={{ ...BOTON, ...BOTON_SECUNDARIO }}
      onClick={() => {
        void copiarAlPortapapeles(texto).then((ok) => {
          setResultado(ok ? 'copiado' : 'fallo')
          window.setTimeout(() => setResultado(null), 2500)
        })
      }}
    >
      {resultado === 'copiado' ? 'Copiado' : resultado === 'fallo' ? 'No se pudo copiar' : etiqueta}
    </button>
  )
}

/** Un enlace externo: se abre donde se puede y siempre se puede copiar. */
export function EnlaceExterno({ href, children }: { href: string; children: React.ReactNode }) {
  return (
    <span style={{ display: 'inline-flex', gap: 8, alignItems: 'center', flexWrap: 'wrap' }}>
      {sePuedenAbrirEnlaces() ? (
        <a href={href} target="_blank" rel="noopener noreferrer" style={{ color: 'var(--accent, #39a900)' }}>
          {children}
        </a>
      ) : (
        <span style={{ color: 'var(--text-secondary)' }}>{children}</span>
      )}
      <BotonCopiar texto={href} etiqueta="Copiar enlace" />
    </span>
  )
}

export const BOTON: React.CSSProperties = { padding: '4px 12px', fontSize: 11, cursor: 'pointer' }

export const BOTON_SECUNDARIO: React.CSSProperties = {
  background: 'transparent',
  border: '1px solid var(--border)',
  borderRadius: 4,
  color: 'var(--text-secondary)',
}
