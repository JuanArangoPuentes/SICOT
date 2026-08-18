# FASE 0 — INSPECCIÓN DE DATOS FICTICIOS Y ESTRUCTURA

**Fecha**: 2026-08-12  
**Estado**: SOLO INSPECCIÓN — NO SE HAN REALIZADO MODIFICACIONES  
**Próximo paso**: Requiere aprobación explícita del usuario para proceder a FASE 1  

---

## 1. DATOS FICTICIOS ENCONTRADOS

### 1.1 En `src/data/contractFlow.ts`

#### `CONTRACT` (líneas 11–18)
```typescript
export const CONTRACT = {
  id: '',
  object: '',
  value: '',
  startDate: '',
  endDate: '',
  supervisor: '',
  supervisorEmail: 'supervisor@soy.sena.edu.co',
}
```
- **Tipo**: Plantilla vacía — NO contiene datos demo.
- **Función**: Define la estructura de un contrato para TypeScript. Inicialmente vacío, se rellena desde backend.
- **¿Debe eliminarse?** NO — es un tipo de dato estructural.
- **Nota**: `supervisorEmail` hardcoded — puede considerarse para hacerlo dinámico desde backend.

#### `AI_GENERATED_DOCS` (línea 21)
```typescript
export const AI_GENERATED_DOCS = new Set(['2.7', '3.4', '4.3', '4.4', '6.2'])
```
- **Tipo**: Configuración de etapas dónde la IA genera documentos.
- **Función**: Define qué subetapas tienen documentos generados automáticamente (no son demo).
- **¿Debe eliminarse?** NO — es lógica de negocio estructural.

#### `STEPS_INITIAL` (líneas 24–87)
```typescript
export const STEPS_INITIAL: Step[] = [
  { id: 1, title: 'INICIO — Estudios y Suscripción', status: 'active', subSteps: [...] },
  { id: 2, title: 'INICIO — Acta de Inicio (GCCON-F-018)', status: 'pending', subSteps: [...] },
  { id: 3, title: 'INSPECCIÓN — Monitoreo y Ejecución', status: 'pending', subSteps: [...] },
  { id: 4, title: 'RECEPCIÓN — Acta de Recibo a Satisfacción', status: 'pending', subSteps: [...] },
  { id: 5, title: 'CERTIFICACIÓN — ESUCON y Trámite de Pago', status: 'pending', subSteps: [...] },
  { id: 6, title: 'CIERRE — Liquidación y Archivo (GCCON-F-030)', status: 'pending', subSteps: [...] },
]
```
- **Tipo**: Definición de las 6 etapas GCCON-P-010.
- **Función**: Estructura canónica del proceso de supervisión — NO son datos de un contrato específico.
- **Detalles**: Cada etapa tiene subetapas con `completed: false` — esto es correcto (estado inicial).
- **¿Debe eliminarse?** NO — es la configuración del workflow y debe conservarse exactamente igual.

#### `TUTORIAL` (líneas 90–102)
```typescript
export const TUTORIAL: Record<string, string> = {
  welcome: `Este es tu panel de supervisión. Cuando se te asigne un contrato...`,
  '3.1': `GCCON-P-010 · Etapa Inspección: Ve a bodega...`,
  '3.2': `Carga las fotos de bodega...`,
  '3.3': `Compara la cantidad y calidad recibida...`,
  '3.4': `Ya generé el Informe de Supervisión GCCON-F-031...`,
  step3done: `¡Excelente! Completaste el Paso 3...`,
}
```
- **Tipo**: Textos de tutorial/onboarding.
- **Función**: Mensajes que guían al supervisor en cada etapa (asesor de proceso, no datos de contrato).
- **¿Debe eliminarse?** NO — son instrucciones estructurales del flujo.

#### `CHAT_RESPONSES` (líneas 105–115)
```typescript
export const CHAT_RESPONSES: Array<[string, string]> = [
  ['cómo crear un contrato', 'El rol de Gestión carga la ficha del contrato...'],
  ['qué documentos necesito', 'Para el Paso 3 activo (Inspección)...'],
  ['iniciar paso 1', 'Los Pasos 1 y 2 ya están completados...'],
  ...
]
```
- **Tipo**: Banco de respuestas del copiloto IA.
- **Función**: Base de conocimiento para el asistente (no son datos de un contrato específico).
- **Impacto**: Se utiliza en `SupervisorPanel.tsx` línea 192 y 201.
- **¿Debe eliminarse?** NO — son respuestas configurables del sistema.

#### `FORMAL_DOCS` (líneas 118–125)
```typescript
export const FORMAL_DOCS = [
  { subStepId: '2.7', name: 'Acta de Inicio', code: 'GCCON-F-018', ... },
  { subStepId: '3.4', name: 'Informe de Supervisión', code: 'GCCON-F-031', ... },
  ...
]
```
- **Tipo**: Catálogo de documentos formales del proceso.
- **Función**: Mapeo entre subetapas y documentos generados (es estructura del workflow).
- **¿Debe eliminarse?** NO — define la estructura de documentos del GCCON-P-010.

---

### 1.2 En `src/components/AdminPanel.tsx`

#### `DOCS_INICIAL` (línea 21)
```typescript
const DOCS_INICIAL: DocRow[] = []
```
- **Valor**: Array vacío.
- **Comentario en código**: "Catálogo de formatos oficiales — vacío por defecto (mock: no expuesto por la API)."
- **Función**: Estado inicial del catálogo de documentos en el panel Admin.
- **¿Debe eliminarse?** NO — es el estado inicial correcto. El catálogo se carga desde backend cuando esté disponible.

#### `FIRMAS_INICIAL` (línea 40)
```typescript
const FIRMAS_INICIAL: FirmaRow[] = []
```
- **Valor**: Array vacío.
- **Comentario en código**: "Firmas electrónicas emitidas — vacío por defecto (mock: módulo no expuesto por la API)"
- **Función**: Estado inicial del registro de firmas.
- **¿Debe eliminarse?** NO — es el estado inicial correcto.

#### `ACTIVIDAD` (línea 43)
```typescript
const ACTIVIDAD: { dia: string; creados: number; supervisados: number; cerrados: number }[] = []
```
- **Valor**: Array vacío.
- **Comentario en código**: "Actividad de los últimos 30 días — vacío hasta tener datos reales de uso (mock)"
- **Función**: Datos para el gráfico de actividad en el dashboard.
- **Ubicación de uso**: 
  - `AdminPanel.tsx` línea 163 (se renderiza con condición `ACTIVIDAD.length === 0`)
  - Muestra un estado vacío: "Aquí verás tus estadísticas cuando tengas contratos activos."
- **¿Debe eliminarse?** PARCIALMENTE:
  - El array vacío es correcto.
  - El estado vacío en el UI es correcto.
  - NO hay datos ficticios actualmente mostrados.

---

### 1.3 En `src/screens/SupervisorPanel.tsx`

#### Alertas derivadas y fallback (línea 254–258)
```typescript
const alertasDerivadas: LiveAlert[] = [
  ...(!gccon031Signed ? [{ id: 'a-031', severity: 'leve' as const, text: 'Inspección activa: Informe GCCON-F-031 pendiente de firma.' }] : []),
  ...(step3.status === 'completed' && !gil010Signed ? [{ id: 'a-gil', severity: 'critica' as const, text: 'Acta de Recibo GIL-F-010 pendiente — Paso 4 desbloqueado.' }] : []),
  ...
]
```
- **Tipo**: Alertas derivadas del estado local cuando no hay alertas del backend.
- **Función**: Proporciona feedback cuando `alertasApi.length === 0`.
- **Problema**: Aunque son "derivadas", cuando no hay contrato estos mensajes pueden mostrarse sin contexto.
- **Línea crítica**: `liveAlerts = (alertasReales.length > 0 ? alertasReales : alertasDerivadas)` — si el usuario SIN CONTRATO ve estas alertas, será confuso.
- **¿Debe eliminarse?** PARCIALMENTE — Mantener solo si hay un contrato cargado.

#### Chat inicial con fallback (línea 243)
```typescript
const [chatMsgs, setChatMsgs] = useState<ChatMsg[]>([{ role: 'ai', text: TUTORIAL.welcome }])
```
- **Tipo**: Mensaje inicial del copiloto.
- **Función**: Bienvenida al supervisor.
- **Valor de `TUTORIAL.welcome`**: "Este es tu panel de supervisión. Cuando se te asigne un contrato, aquí verás..."
- **Problema**: Es genérico y funciona bien incluso sin contrato.
- **¿Debe eliminarse?** NO — pero puede requirir ajuste si el supervisor entra SIN contrato.

---

### 1.4 En `src/screens/GestionPanel.tsx`

#### `CONTRACT_TYPES` (líneas 15–26)
```typescript
const CONTRACT_TYPES: Record<string, { etapas: string[]; documentos: string[] }> = {
  'Suministro de Bienes': { etapas: [...], documentos: [...] },
  Servicios: { etapas: [...], documentos: [...] },
  Obras: { etapas: [...], documentos: [...] },
  Arrendamiento: { etapas: [...], documentos: [...] },
}
```
- **Tipo**: Catálogo de tipos de contrato con etapas predefinidas.
- **Función**: Define la secuencia de etapas según tipo (estructura de workflow).
- **¿Debe eliminarse?** NO — es configuración del sistema, no datos demo.

#### `CENTROS_COSTO` (línea 49)
```typescript
const CENTROS_COSTO = ['920510 — CTMA Formación', '920511 — CTMA Ebanistería', '920512 — CTMA Tapicería']
```
- **Tipo**: Lista de centros de costo institucionales.
- **Función**: Opciones en el dropdown de centros.
- **¿Debe eliminarse?** POSIBLEMENTE — debería cargarse desde backend, pero actualmente es una lista fija.
- **Impacto**: Si hay más centros en el sistema, esta lista es incompleta.

#### `ESTADO_ROW` (línea 52–58)
```typescript
const ESTADO_ROW: Record<EstadoContrato, { label: string; type: ChipType }> = {
  ACTIVO: { label: 'Activo', type: 'vigente' },
  BORRADOR: { label: 'Borrador', type: 'pending' },
  ...
}
```
- **Tipo**: Mapeo de estados a etiquetas visuales.
- **Función**: Define cómo se muestran los estados en el UI.
- **¿Debe eliminarse?** NO — es configuración visual del sistema.

#### Nota en modal (línea 281)
```html
<p style={{ color: 'var(--text-muted)', fontSize: 11, marginTop: 12, textAlign: 'center' }}>
  Nota: en el prototipo la lectura se simula con datos de ejemplo precargados.
</p>
```
- **Tipo**: Texto aclaratorio en el UI.
- **Función**: Avisa al usuario que el sistema de lectura de PDF/DOCX es simulado.
- **¿Debe eliminarse?** SÍ — si se implementa lectura real; NO si sigue siendo simulada.

---

## 2. DATOS ESTRUCTURALES QUE DEBEN CONSERVARSE

| Elemento | Archivo | Razón |
|----------|---------|-------|
| `STEPS_INITIAL` | `contractFlow.ts` | Define GCCON-P-010 — estructura del workflow, no datos demo |
| `TUTORIAL` | `contractFlow.ts` | Base de conocimiento del sistema — textos instruccionales |
| `CHAT_RESPONSES` | `contractFlow.ts` | Banco de respuestas del copiloto IA — no es información ficticia |
| `FORMAL_DOCS` | `contractFlow.ts` | Catálogo de documentos formales — mapeo etapa↔doc |
| `AI_GENERATED_DOCS` | `contractFlow.ts` | Configuración de qué subetapas generan documentos IA |
| `CONTRACT_TYPES` | `GestionPanel.tsx` | Secuencias de etapas por tipo de contrato |
| `ESTADO_ROW` | `GestionPanel.tsx` | Mapeo visual de estados de contrato |
| Componentes visuales | Todo | Tarjetas, botones, layouts, estilos |
| Modales | Todo | Estructura del formulario de carga de contrato |
| Tabs y navegación | Todo | Estructura de navegación de los paneles |

---

## 3. DATOS QUE VIENEN DEL BACKEND vs MOCKS

### 3.1 Datos del Backend (Autoridad única)

| Datos | Endpoint | Archivo | Implementado |
|-------|----------|---------|--------------|
| Contratos | `GET /api/contratos` | `contratoService.ts` | ✅ SÍ |
| Etapas/Subetapas | `GET /api/contratos/{id}/etapas` | `etapaService.ts` | ✅ SÍ |
| Alertas | `GET /api/contratos/{id}/alertas` | `alertaService.ts` | ✅ SÍ |
| Documentos | `GET /api/contratos/{id}/documentos` | `documentoService.ts` | ✅ SÍ |
| Registros | `GET /api/contratos/{id}/registros` | `registroService.ts` | ✅ SÍ |
| Usuarios | `GET /api/usuarios` | `usuarioService.ts` | ✅ SÍ |

**Todos estos endpoints YA están implementados en el frontend.**

### 3.2 Datos que aún usan Mocks/Derivados

| Datos | Estado | Ubicación | Problema |
|-------|--------|-----------|----------|
| Documentos formales (catálogo) | Mock | `AdminPanel.tsx` DOCS_INICIAL | Array vacío — está bien, debería cargarse desde backend en futuro |
| Firmas electrónicas | Mock | `AdminPanel.tsx` FIRMAS_INICIAL | Array vacío — está bien |
| Actividad de últimos 30 días | Derivado | `AdminPanel.tsx` ACTIVIDAD | Array vacío — no hay datos reales aún |
| Alertas derivadas | Fallback | `SupervisorPanel.tsx` línea 254 | Se muestran si no hay alertas del backend — riesgo SIN CONTRATO |
| Centros de costo | Hardcoded | `GestionPanel.tsx` línea 49 | Lista fija — debería ser dinámica desde backend |

---

## 4. ANÁLISIS POR PANEL

### 4.1 PANEL SUPERVISOR

#### ✅ Correcto actualmente:
- Etapas (`STEPS_INITIAL`) se reemplazan por datos reales desde `getEtapasContrato()` (línea 81).
- Alertas se cargan desde `getAlertasContrato()` (línea 98).
- Documentos se cargan desde `getDocumentosContrato()` (línea 110).
- Registros se cargan desde `getRegistrosContrato()` (App.tsx línea 81).

#### ❌ PROBLEMA CRÍTICO: Estado SIN CONTRATO
**Línea 289 en SupervisorPanel.tsx:**
```typescript
return (
  <div style={{ display: 'flex', flexDirection: 'column', height: '100vh', ... }}>
    {/* Top bar, Tabs, Content — TODO RENDERIZA INCLUSO SIN CONTRATO */}
```

**Qué ocurre si `contrato === null`:**
1. El renderizado continúa.
2. `steps` se mantiene en `STEPS_INITIAL` (6 etapas con `status: 'active'/'pending'`).
3. Las alertas derivadas se muestran:
   - "Inspección activa: Informe GCCON-F-031 pendiente de firma"
   - "Vencimiento próximo del contrato: Invalid Date"
4. La tarjeta de contrato muestra valores vacíos:
   - Número: `{contrato?.numeroContrato ?? ''}` → vacío
   - Objeto: `{contrato?.objeto ?? ''}` → vacío
   - Valor: `formatCOP(contrato?.valor)` → error potencial
   - Fechas: `formatFecha(contrato?.fechaFin)` → "Invalid Date"

**Línea 236 especialmente peligrosa:**
```typescript
const stages: Stage[] = [
  ...
  { key: 'cierre', label: 'Cierre', pct: pctOf([6]), ..., detail: `Acta de Liquidación GCCON-F-030 y archivo SIGEP. Vence ${formatFecha(contrato?.fechaFin)}.` },
]
```
Si `contrato` es null, `formatFecha(null)` produce "Invalid Date".

**Línea 251:**
```typescript
{ id: 'a-venc', severity: 'leve' as const, text: `Vencimiento próximo del contrato: ${formatFecha(contrato?.fechaFin)}.` },
```
Misma situación.

#### ✅ SupervisorWelcome (pantalla previa)
```typescript
// SupervisorWelcome.tsx línea 35
<p style={{ ... }}>Tienes un contrato asignado — te acompañaré paso a paso en su ejecución.</p>
<div style={{ ... }}>{contrato?.numeroContrato ?? ''}</div>
```
- Si `contrato === null`, muestra:
  - "Tienes un contrato asignado..." (FALSO)
  - Número de contrato: vacío

**NECESITA REDISEÑO** para el estado SIN CONTRATO.

---

### 4.2 PANEL GESTIÓN

#### ✅ Correcto actualmente:
- Tabla de contratos se llena desde `getContratos()` (línea 82).
- Estado vacío si `contratos.length === 0`: ✅ ya existe (línea 233).
- Supervisores disponibles se cargan desde `getUsuarios()` (línea 119).

#### ✅ NO HAY DATOS FALSOS VISIBLES
- El formulario de carga es un wizard interactivo (idle → uploading → analyzing → detect → review → done).
- No hay contratos precargados en la tabla.
- Cuando no hay contratos, se muestra: "Aún no tienes contratos registrados".

#### ⚠ Nota de prototipo (línea 281):
```html
Nota: en el prototipo la lectura se simula con datos de ejemplo precargados.
```
- Esto es transparente al usuario.
- No hay datos ficticios actualmente visibles.

---

### 4.3 PANEL ADMINISTRADOR

#### ✅ Correcto actualmente:
- Usuarios se cargan desde `getUsuarios()` (línea 117).
- Contratos se cargan desde `getContratos()` (línea 122).
- Contadores se calculan correctamente:
  - `totalContratos = lista.length`
  - `contratosActivos = lista.filter(c => c.estado === 'ACTIVO').length`

#### ✅ Estado vacío correcto:
- Documentos: `DOCS_INICIAL = []` y se muestra correcto (tabla vacía).
- Firmas: `FIRMAS_INICIAL = []` y se muestra correcto (tabla vacía).
- Actividad: `ACTIVIDAD = []` y se muestra estado vacío: "Aquí verás tus estadísticas cuando tengas contratos activos."

#### ❌ Ningún dato ficticio visible actualmente.

---

## 5. PROBLEMAS IDENTIFICADOS

### 5.1 CRÍTICO: Supervisor SIN CONTRATO

**Descripción**:
- El supervisor es redirigido a `SupervisorWelcome` (App.tsx línea 121).
- `SupervisorWelcome` siempre dice "Tienes un contrato asignado" (línea 31).
- Si `contrato === null`, esto es FALSO y confuso.
- Al navegar a `SupervisorPanel`, el panel está TOTALMENTE FUNCIONAL pero muestra:
  - Etapas vacías/default
  - Alertas derivadas sin contexto
  - Fechas "Invalid Date"

**Impacto**: ALTO — Usuario sin contrato recibe información falsa y posiblemente errores.

### 5.2 ALTO: Alertas derivadas sin contrato

**Ubicación**: `SupervisorPanel.tsx` línea 254  
**Problema**: Las alertas derivadas se muestran incluso sin contrato.  
**Ejemplo**:
```
"Vencimiento próximo del contrato: Invalid Date"
"Inspección activa: Informe GCCON-F-031 pendiente de firma"
```

### 5.3 MEDIO: Centros de costo hardcoded

**Ubicación**: `GestionPanel.tsx` línea 49  
**Problema**: Si existen más centros en el sistema, la UI no los muestra.  
**Solución**: Cargar desde backend en futuro.

### 5.4 BAJO: Nota de prototipo

**Ubicación**: `GestionPanel.tsx` línea 281  
**Problema**: La nota "en el prototipo la lectura se simula..." está en el UI del usuario.  
**Solución**: Eliminar cuando se implemente lectura real; mantener ahora si el backend no la soporta aún.

---

## 6. COMPORTAMIENTO DEL SUPERVISOR CON/SIN CONTRATO

### ESTADO A: Supervisor CON Contrato

**Flujo actual:**
1. Login → `screenFor(auth) === 'supervisor-welcome'`
2. `App.tsx` línea 61–72: Carga contrato con `getContratos(session.usuarioId)`
3. Toma el contrato ACTIVO o el primero de la lista: `const activo = lista.find(...) ?? lista[0] ?? null`
4. Si `contrato !== null`:
   - `SupervisorWelcome` se muestra con número de contrato
   - Usuario click "Ver mi contrato" → `SupervisorPanel`
   - Panel carga datos reales del backend ✅

**Resultado**: ✅ Funciona correctamente.

### ESTADO B: Supervisor SIN Contrato

**Flujo actual:**
1. Login → `screenFor(auth) === 'supervisor-welcome'`
2. `App.tsx` línea 61–72: `getContratos(session.usuarioId)` retorna `[]`
3. `const activo = null`
4. Si `contrato === null`:
   - `SupervisorWelcome` SIGUE MOSTRÁNDOSE: "Tienes un contrato asignado"
   - Número de contrato: vacío
   - Usuario click "Ver mi contrato" → `SupervisorPanel`
   - Panel renderiza sin contrato:
     - Etapas default (`STEPS_INITIAL`)
     - Alertas derivadas sin contexto
     - Valores vacíos o "Invalid Date"

**Resultado**: ❌ Experiencia confusa, información falsa.

---

## 7. CAMBIOS PROPUESTOS (FASE 1)

### 7.1 SupervisorWelcome.tsx

**Cambio**: Mostrar estado "SIN CONTRATO" cuando `contrato === null`

```typescript
// Actual (líneas 26–36):
if (contrato) {
  return <Card>
    <p>Tienes un contrato asignado...</p>
    <div>{contrato.numeroContrato}</div>
    <button>Ver mi contrato asignado →</button>
  </Card>
} else {
  return <Card>
    <p>No tienes un contrato asignado.</p>
    <p>Actualmente no tienes un contrato asignado para seguimiento...</p>
    <p>Cuando Gestión te asigne uno, la información aparecerá aquí.</p>
  </Card>
}
```

**Conservar**: Paleta de colores, tarjeta, layout, tipografía, iconografía.

---

### 7.2 SupervisorPanel.tsx

**Cambio A**: Proteger el renderizado cuando `contrato === null`

```typescript
// Línea 289 — agregar guard:
if (!contrato) {
  return <EmptyContractState />
}
return (
  <div style={{ display: 'flex', ... }}>
    {/* Renderizado normal */}
  </div>
)
```

**Cambio B**: Eliminar alertas derivadas cuando no hay contrato

```typescript
// Línea 254 — modificar:
const alertasDerivadas: LiveAlert[] = contrato ? [
  ...(!gccon031Signed ? [...] : []),
  ...
] : []
```

**Cambio C**: Proteger valores de fecha

```typescript
// Línea 236 — ya está safe con `contrato?.fechaFin`
// Pero agregar fallback en formatFecha():
const detail = contrato 
  ? `Acta de Liquidación... Vence ${formatFecha(contrato.fechaFin)}.`
  : 'N/A'
```

---

### 7.3 Archivos SIN cambios

- `contractFlow.ts` — sin cambios ✅
- `GestionPanel.tsx` — sin cambios mayores (mantener nota de prototipo o remover) ✅
- `AdminPanel.tsx` — sin cambios ✅
- Componentes visuales — sin cambios ✅

---

## 8. RIESGOS

| Riesgo | Severidad | Mitigación |
|--------|-----------|-----------|
| Cambio en estructura de STEPS rompe componentes | ALTA | Validar todos los consumidores de `steps` |
| Eliminar alertas derivadas afecta supervisores con contrato | MEDIA | Mantener lógica, solo activar si `contrato !== null` |
| Estado vacío del supervisor debe mantener diseño | MEDIA | Usar componentes y estilos existentes — NO crear nuevos |
| formatFecha() con `null` produce "Invalid Date" | MEDIA | Validar entrada en formatFecha() |
| Supervisores asignados después del login no se actualizan | BAJA | Mantener polling o WebSocket (backend decision) |
| Tests E2E pueden fallar si expectedan datos demo | MEDIA | Crear tests parametrizados con datos reales y vacíos |

---

## 9. RESUMEN DE DATOS FICTICIOS

### ❌ Datos FICTICIOS que deben eliminarse/ocultar:

**NINGUNO ENCONTRADO ACTUALMENTE VISIBLE**

- ✅ `STEPS_INITIAL` → NO es ficticio, es estructura.
- ✅ `TUTORIAL`, `CHAT_RESPONSES` → NO son ficticios, son contenido del sistema.
- ✅ `FORMAL_DOCS` → NO es ficticio, es catálogo.
- ✅ `DOCS_INICIAL`, `FIRMAS_INICIAL`, `ACTIVIDAD` → Arrays vacíos, no hay datos ficticios.
- ✅ Tabla de contratos → Cargada desde backend, estado vacío correcto.
- ✅ Tabla de usuarios → Cargada desde backend, estado vacío correcto.

### ⚠ Advertencias/Confusiones:

- `SupervisorWelcome` dice "Tienes contrato" cuando es `null` → NECESITA REDISEÑO VISUAL.
- Alertas derivadas se muestran sin contrato → NECESITA PROTECCIÓN.
- Valores de fecha pueden ser "Invalid Date" → NECESITA VALIDACIÓN.
- Centros de costo hardcoded → DEBERÍA ser dinámico en futuro.

---

## 10. PLAN DE IMPLEMENTACIÓN (FASE 1)

### Prioridad: CRÍTICO

1. **Crear componente `EmptyContractState`**
   - Archivo: `src/screens/SupervisorPanel.tsx` o nuevo componente
   - Contenido: Tarjeta con mensaje "No tienes contrato asignado"
   - Estilo: Usar variables CSS existentes

2. **Modificar `SupervisorWelcome.tsx`**
   - Agregar condicional: `if (contrato === null) { return <NoContractWelcome /> }`
   - Crear `<NoContractWelcome />` integrado con el diseño

3. **Proteger `SupervisorPanel.tsx`**
   - Línea 289: Agregar guard `if (!contrato) return <EmptyContractState />`
   - Línea 254: Proteger alertas derivadas con `contrato ?`
   - Línea 236, 251: Validar valores de fecha

### Prioridad: MEDIA

4. **Validar `formatFecha()`**
   - Archivo: `src/services/format.ts`
   - Agregar guard para `null` / `undefined`

5. **Remover nota de prototipo (GestionPanel.tsx línea 281)**
   - Si el backend implementa lectura real
   - Si sigue siendo prototipo, mantenerla

### Prioridad: BAJA (Futuro)

6. **Cargar centros de costo desde backend**
   - Crear endpoint `GET /api/centros-costo`
   - Reemplazar array hardcoded

---

## ARCHIVO DE SEGUIMIENTO

**Archivo preparado**: `FASE_0_INSPECCION.md`  
**Estado**: LISTO PARA REVISIÓN Y APROBACIÓN  
**Cambios realizados**: NINGUNO — FASE 0 SOLO INSPECCIÓN  
**Próximo paso**: Usuario aprueba y solicita FASE 1

---

## CONCLUSIÓN

**NO HAY DATOS FICTICIOS VISIBLES EN LA INTERFAZ**

Los datos que el usuario ve provienen del backend o son estructuras configuracionales del sistema (GCCON-P-010, tutorial, copiloto).

**PERO SÍ HAY UN PROBLEMA CRÍTICO**: 
El supervisor SIN CONTRATO asignado recibe mensajes confusos ("Tienes un contrato asignado") y estados vacíos con alertas sin contexto.

**SOLUCIÓN**:
1. Detectar cuando `contrato === null` en las pantallas de bienvenida y panel.
2. Mostrar un estado visual integrado que comunique claramente la situación.
3. No mostrar datos derivados (alertas, etapas, fechas) cuando no hay contrato.
4. Conservar el diseño exacto — solo cambiar contenido y estados visibles.

---

**Requiere aprobación explícita para proceder a FASE 1**
