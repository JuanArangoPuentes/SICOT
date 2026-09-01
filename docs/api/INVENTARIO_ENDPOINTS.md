# Inventario completo de endpoints SICOT — Rama `fix/consistencia-api`

Generado el 2026-08-28. 13 controladores, 40 endpoints.

| # | Controlador | Método | Ruta | Rol en `@PreAuthorize` | Control de acceso real | Éxito | Forma de respuesta |
|---|-------------|--------|------|------------------------|------------------------|-------|-------------------|
| 1 | AuthController | POST | /api/auth/login | (público) | público | 200 | `AuthResponse` |
| 2 | UsuarioController | GET | /api/usuarios | ADMINISTRADOR, GESTION (lectura) | @PreAuthorize en clase + método | 200 | `List<UsuarioResponse>` |
| 3 | UsuarioController | GET | /api/usuarios/{id} | ADMINISTRADOR | @PreAuthorize en clase | 200 | `UsuarioResponse` |
| 4 | UsuarioController | POST | /api/usuarios | ADMINISTRADOR | @PreAuthorize en clase | **201** | `UsuarioResponse` |
| 5 | UsuarioController | PUT | /api/usuarios/{id} | ADMINISTRADOR | @PreAuthorize en clase | 200 | `UsuarioResponse` |
| 6 | UsuarioController | PATCH | /api/usuarios/{id}/estado | ADMINISTRADOR | @PreAuthorize en clase | 200 | `UsuarioResponse` |
| 7 | UsuarioController | POST | /api/usuarios/{id}/enviar-credenciales | ADMINISTRADOR | @PreAuthorize en clase | 200 | `EnviarCredencialesResponse` |
| 8 | ContratoController | GET | /api/contratos | (autenticado) | Service: `SecurityUtils.verificarAccesoAlContrato` para filtros | 200 | `List<ContratoResponse>` |
| 9 | ContratoController | GET | /api/contratos/{id} | (autenticado) | Service: `SecurityUtils.verificarAccesoAlContrato` | 200 | `ContratoResponse` |
| 10 | ContratoController | POST | /api/contratos | GESTION, ADMINISTRADOR | @PreAuthorize | **201** | `ContratoResponse` |
| 11 | ContratoController | PUT | /api/contratos/{id} | GESTION, ADMINISTRADOR | @PreAuthorize + Service verifica acceso | 200 | `ContratoResponse` |
| 12 | ContratoController | PATCH | /api/contratos/{id}/supervisor | GESTION, ADMINISTRADOR | @PreAuthorize | 200 | `ContratoResponse` |
| 13 | ContratoController | PATCH | /api/contratos/{id}/estado | GESTION, ADMINISTRADOR | @PreAuthorize | 200 | `ContratoResponse` |
| 14 | EtapaController | GET | /api/contratos/{contratoId}/etapas | (ninguno) | **Service: `verificarAccesoAlContrato`** | 200 | `List<EtapaResponse>` |
| 15 | EtapaController | GET | /api/contratos/{contratoId}/etapas/{etapaId} | (ninguno) | **Service: `verificarAccesoAlContrato`** | 200 | `EtapaResponse` |
| 16 | SubetapaController | GET | /api/etapas/{etapaId}/subetapas | (ninguno) | **Service: `verificarAccesoAlContrato` vía etapa** | 200 | `List<SubetapaResponse>` |
| 17 | SubetapaController | PATCH | /api/subetapas/{id}/estado | SUPERVISOR, GESTION, ADMINISTRADOR | @PreAuthorize + Service verifica acceso | 200 | `SubetapaResponse` |
| 18 | DocumentoController | GET | /api/contratos/{contratoId}/documentos | (autenticado) | Service: `verificarAccesoAlContrato` | 200 | `List<DocumentoResponse>` |
| 19 | DocumentoController | POST | /api/contratos/{contratoId}/documentos | GESTION, ADMINISTRADOR | @PreAuthorize + regla de ruta + Service verifica acceso | **201** | `DocumentoResponse` |
| 20 | DocumentoController | GET | /api/contratos/{contratoId}/documentos/{id}/archivo | (autenticado) | Service: `verificarAccesoAlContrato` | 200 | `byte[]` (archivo) |
| 20b | DocumentoController | GET | /api/contratos/{contratoId}/documentos/{id}/verificacion | (autenticado) | Service: `verificarAccesoAlContrato` | 200 | `VerificacionIntegridadResponse` |
| 21 | DocumentoController | POST | /api/contratos/{contratoId}/documentos/generar | SUPERVISOR, ADMINISTRADOR | @PreAuthorize + Service verifica supervisor del contrato | 200 | `DocumentoResponse` |
| 22 | DocumentoController | POST | /api/contratos/{contratoId}/documentos/{id}/firmar | SUPERVISOR, ADMINISTRADOR | @PreAuthorize + Service verifica supervisor del contrato | 200 | `DocumentoResponse` |
| 23 | CopilotoController | POST | /api/contratos/{contratoId}/copiloto/chat | SUPERVISOR, ADMINISTRADOR | @PreAuthorize + Service verifica supervisor del contrato | 200 | `ChatResponse` |
| 24 | IAController | POST | /api/ia/extraer-contrato | GESTION, ADMINISTRADOR | @PreAuthorize | 200 | `ExtraccionContratoResponse` |
| 25 | FormatoDocumentalController | GET | /api/formatos | (autenticado) | @PreAuthorize en clase (ADMIN para POST/DELETE) | 200 | `List<FormatoDocumentalResponse>` |
| 26 | FormatoDocumentalController | POST | /api/formatos | ADMINISTRADOR | @PreAuthorize en método | 200 | `FormatoDocumentalResponse` |
| 27 | FormatoDocumentalController | GET | /api/formatos/{id}/archivo | (autenticado) | @PreAuthorize en clase | 200 | `byte[]` (archivo) |
| 28 | FormatoDocumentalController | DELETE | /api/formatos/{id} | ADMINISTRADOR | @PreAuthorize en método | **204** | `void` |
| 29 | FirmaElectronicaController | GET | /api/firmas | ADMINISTRADOR | @PreAuthorize en clase | 200 | `List<FirmaResponse>` |
| 30 | FirmaElectronicaController | GET | /api/firmas/mia | (autenticado) | @PreAuthorize("isAuthenticated()") | 200 | `MiFirmaResponse` |
| 31 | FirmaElectronicaController | POST | /api/firmas | ADMINISTRADOR | @PreAuthorize en clase | **201** | `FirmaResponse` |
| 32 | FirmaElectronicaController | PATCH | /api/firmas/{id}/estado | ADMINISTRADOR | @PreAuthorize en clase | 200 | `FirmaResponse` |
| 33 | AlertaController | GET | /api/contratos/{contratoId}/alertas | (autenticado) | **Service: `verificarAccesoAlContrato`** | 200 | `List<AlertaResponse>` |
| 34 | AlertaController | GET | /api/alertas | GESTION, ADMINISTRADOR | @PreAuthorize | 200 | `List<AlertaResponse>` |
| 35 | AlertaController | PATCH | /api/alertas/{id}/leida | SUPERVISOR, GESTION, ADMINISTRADOR | @PreAuthorize | 200 | `AlertaResponse` |
| 36 | RegistroController | GET | /api/contratos/{contratoId}/registros | (autenticado) | **Service: `verificarAccesoAlContrato`** | 200 | `List<RegistroResponse>` |
| 37 | RegistroController | GET | /api/registros | ADMINISTRADOR | @PreAuthorize | 200 | `List<RegistroResponse>` |
| 38 | ListaChequeoController | GET | /api/listas-chequeo | (autenticado) | (ninguno — catálogo de solo lectura) | 200 | `List<ListaChequeoResumen>` |
| 39 | ListaChequeoController | GET | /api/listas-chequeo/{codigo} | (autenticado) | (ninguno — catálogo de solo lectura) | 200 | `ListaChequeoDetalle` |

---

## Observaciones clave para la tarea 5 (pruebas IDOR)

1. **Controladores SIN `@PreAuthorize`** (protegidos en service):
   - `EtapaController` — líneas 15-37: 2 endpoints, acceso verificado en `EtapaService.listarPorContrato`/`obtenerEtapaDeContrato`
   - `ListaChequeoController` — líneas 18-42: 2 endpoints, catálogo de solo lectura, sin control por contrato
   - `RegistroController` — `listarPorContrato` protegido en service, `listarTodas` tiene `@PreAuthorize`

2. **AlertaController asimétrico**:
   - `listarPorContrato` (línea 25): **sin `@PreAuthorize`**, protegido en service
   - `listarTodas` (línea 31): **con `@PreAuthorize("hasAnyRole('GESTION', 'ADMINISTRADOR')`)**

3. **SubetapaController** — `@RequestMapping("/api")` con rutas sueltas:
   - `GET /api/etapas/{etapaId}/subetapas`
   - `PATCH /api/subetapas/{id}/estado`

4. **Códigos de éxito inconsistentes**:
   - **201 Created**: `UsuarioController.crear`, `ContratoController.crear`, `FirmaElectronicaController.crear` ✓
   - **200 OK** (deberían ser 201): `DocumentoController.subir`, `FormatoDocumentalController.subir`, `DocumentoController.generar`, `DocumentoController.firmar`, `CopilotoController.chat`, `IAController.extraerContrato`, `UsuarioController.enviarCredenciales`
   - **204 No Content**: `FormatoDocumentalController.eliminar` ✓

5. **Formas de respuesta**:
   - Listas: `List<T>` directo (todas)
   - Entidad única: `T` directo (todas)
   - Void: solo `FormatoDocumentalController.eliminar` → 204
   - Archivos: `byte[]` con headers `Content-Disposition`

6. **Errores**: Todos via `GlobalExceptionHandler` → `ErrorResponse {timestamp, status, error, message, path, fieldErrors}`. Códigos: 400, 401, 403, 404, 405, 409, 415, 500, 503.