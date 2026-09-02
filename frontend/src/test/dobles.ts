// Dobles de prueba compartidos: sesiones y respuestas del backend.
//
// Se centralizan aquí para que las pruebas describan el ESCENARIO que ponen a
// prueba ("un supervisor sin contrato asignado") y no el detalle de armar un
// objeto de quince campos. Cuando el backend agregue un campo obligatorio, se
// arregla en este archivo y no en cada prueba.

import type {
  AlertaResponse,
  AuthResponse,
  ContratoResponse,
  DocumentoResponse,
} from "@/services/api/types"

export function sesionSupervisor(): AuthResponse {
  return {
    token: "token-de-prueba",
    usuarioId: 3,
    nombre: "Alex Fernando Zapata",
    email: "supervisor@soy.sena.edu.co",
    rol: "SUPERVISOR",
  }
}

export function sesionAdministrador(): AuthResponse {
  return {
    token: "token-de-prueba",
    usuarioId: 1,
    nombre: "Administrador SICOT",
    email: "administrador@soy.sena.edu.co",
    rol: "ADMINISTRADOR",
  }
}

export function sesionGestion(): AuthResponse {
  return {
    token: "token-de-prueba",
    usuarioId: 2,
    nombre: "Unidad de Gestión Contractual",
    email: "gestion@soy.sena.edu.co",
    rol: "GESTION",
  }
}

export function contrato(
  parcial: Partial<ContratoResponse> = {},
): ContratoResponse {
  return {
    id: 1,
    numeroContrato: "CTMA-2026-0184",
    objeto: "Suministro e instalación de mobiliario para las aulas",
    valor: 184_500_000,
    fechaInicio: "2026-03-02",
    fechaFin: "2026-09-30",
    estado: "ACTIVO",
    supervisorId: 3,
    supervisorNombre: "Alex Fernando Zapata",
    supervisorEmail: "supervisor@soy.sena.edu.co",
    tipoContrato: "Suministro de Bienes",
    contratista: "Maderas del Norte S.A.S.",
    contratistaNit: "900123456-7",
    representanteLegal: "María Restrepo",
    lugarEjecucion: "Centro Tecnológico del Mobiliario",
    numeroRegistroPresupuestal: "RP-2026-118",
    fechaRegistroPresupuestal: "2026-02-20",
    centroCosto: "CTMA-01",
    fechaCreacion: "2026-02-15T09:00:00Z",
    ...parcial,
  }
}

export function documento(
  parcial: Partial<DocumentoResponse> = {},
): DocumentoResponse {
  return {
    id: 1,
    contratoId: 1,
    subetapaId: null,
    nombre: "Acta de Inicio",
    tipo: "PDF",
    rutaArchivo: "",
    estado: "PENDIENTE",
    tamanioBytes: 12_345,
    generadoPorIa: false,
    firmaId: null,
    fechaFirma: null,
    firmaHashSha256: null,
    firmadoPorNombre: null,
    subidoPorNombre: "Unidad de Gestión Contractual",
    fechaSubida: "2026-03-05T10:00:00Z",
    ...parcial,
  }
}

export function alerta(parcial: Partial<AlertaResponse> = {}): AlertaResponse {
  return {
    id: 1,
    contratoId: 1,
    tipo: "CRONOGRAMA",
    prioridad: "ALTA",
    mensaje: "El paso en curso está atrasado.",
    leida: false,
    fechaCreacion: "2026-09-01T08:00:00Z",
    ...parcial,
  }
}
