// Tipos, tablas de traducción y ayudantes compartidos por el panel de
// Administración y sus modales.
//
// Viven aquí y no dentro de AdminPanel.tsx porque los cuatro modales extraídos
// también los necesitan: dejarlos en la pantalla obligaría a que los modales
// importaran desde ella, invirtiendo la dependencia (una pieza dependiendo de
// quien la usa).

import type { EstadoFormato, FirmaResponse, Rol, UsuarioResponse } from '@/services/api/types'
import type { ChipType } from '@/components/ui'
import { formatFecha } from '@/services/format'

export interface UserRow {
  id: string
  nombre: string
  correo: string
  telefono: string
  cargo: string
  rol: string
  activo: boolean
  centros: string
  /** Rol y teléfono tal como los devuelve la API — los exige PUT /api/usuarios/{id}. */
  rolApi: Rol
  telefonoApi: string | null
}

export interface FirmaRow { id: string; usuarioId: string; usuario: string; correo: string; firmaId: string; fecha: string; activa: boolean }

// Actividad de los últimos 30 días — sin endpoint de estadísticas agregadas
// todavía; se deja vacío a propósito (estado honesto) en vez de inventar
// números. El panel ya maneja el caso vacío mostrando un mensaje, no un mock.
export const ACTIVIDAD: { dia: string; creados: number; supervisados: number; cerrados: number }[] = []

export const ROL_LABEL: Record<Rol, string> = {
  ADMINISTRADOR: 'Administrador',
  GESTION: 'Gestor de Contratación',
  SUPERVISOR: 'Supervisor',
}

export const ROL_CARGO: Record<Rol, string> = {
  ADMINISTRADOR: 'Administrador del sistema',
  GESTION: 'Profesional de Gestión',
  SUPERVISOR: 'Instructor',
}

export const mapUser = (u: UsuarioResponse): UserRow => ({
  id: String(u.id),
  nombre: u.nombre,
  correo: u.email,
  telefono: u.telefono ?? '—',
  cargo: ROL_CARGO[u.rol],
  rol: ROL_LABEL[u.rol],
  activo: u.activo,
  centros: '—',
  rolApi: u.rol,
  telefonoApi: u.telefono,
})

export const mapFirma = (f: FirmaResponse): FirmaRow => ({
  id: String(f.id),
  usuarioId: String(f.usuarioId),
  usuario: f.usuarioNombre,
  correo: f.usuarioEmail,
  firmaId: f.firmaId,
  fecha: formatFecha(f.fechaAsignacion),
  activa: f.activa,
})

export const FORMATO_CHIP: Record<EstadoFormato, { label: string; type: ChipType }> = {
  VIGENTE: { label: 'Vigente', type: 'vigente' },
  OBSOLETO: { label: 'Obsoleto', type: 'inactive' },
}

// Contraseña inicial de una cuenta real. Se usa `crypto.getRandomValues`, no
// `Math.random()`: `Math.random()` no es criptográficamente seguro — su estado
// interno se puede reconstruir observando unas pocas salidas, así que quien vea
// una contraseña generada podría predecir las siguientes.
//
// El descarte de `byte >= limite` evita el sesgo de módulo: 256 no es múltiplo
// de 61 (el tamaño del alfabeto), así que un `% chars.length` directo haría más
// probables los primeros caracteres del alfabeto.
//
// El alfabeto omite a propósito los caracteres ambiguos (I, l, 1, O, 0) porque
// esta contraseña se dicta o se transcribe a mano al entregarla.
export const randomPassword = () => {
  const chars = 'ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789#$%&'
  const limite = 256 - (256 % chars.length)
  const salida: string[] = []
  const buffer = new Uint8Array(32)

  while (salida.length < 12) {
    crypto.getRandomValues(buffer)
    for (const byte of buffer) {
      if (salida.length === 12) break
      if (byte < limite) salida.push(chars[byte % chars.length])
    }
  }

  return salida.join('')
}
