# SICOT — FASE 2: AUDITORÍA COMPLETA DE DATOS

**Fecha de Auditoría**: 2026-08-12  
**Base de Datos**: PostgreSQL 18.4, BD `sicot`  
**Estado**: AUDITORÍA SOLAMENTE — SIN MODIFICACIONES  

---

## 1. CONEXIÓN VERIFICADA ✓

✅ **Base de datos**: `sicot` (PostgreSQL 18.4)  
✅ **Host**: localhost:5432  
✅ **Usuario**: `sicot`  
✅ **Credenciales**: Configuradas en `application.properties`  
✅ **Status**: Conexión exitosa  

---

## 2. RESUMEN DE DATOS ACTUALES

| Tabla | Cantidad |
|-------|----------|
| **usuarios** | 4 |
| **contratos** | 5 |
| **etapas** | 9 |
| **subetapas** | 32 |
| **documentos** | 4 |
| **alertas** | 5 |
| **registros** | 11 |
| **flyway_schema_history** | 2 migraciones aplicadas |

---

## 3. SEED ORIGINAL (CATEGORÍA A)

Definido en `V2__seed_dev_data.sql`, aplicado **2026-08-11 09:09:10**

### 3.1 USUARIOS (3)

| ID | Nombre | Email | Rol | Activo | Fecha | Status |
|----|----|----|----|----|----|-----|
| 1 | Administrador SICOT | administrador@soy.sena.edu.co | ADMINISTRADOR | ✓ | 2026-08-11 09:11:12 | SEED |
| 2 | Unidad de Gestión Contractual | gestion@soy.sena.edu.co | GESTION | ✓ | 2026-08-11 09:11:12 | SEED |
| 3 | Alex Fernando Zapata | supervisor@soy.sena.edu.co | SUPERVISOR | ✓ | 2026-08-11 09:11:12 | SEED |

**Nota**: Los usuarios del SEED se crean mediante `DataInitializer` de Java (no SQL), con contraseñas hasheadas con BCrypt.

### 3.2 CONTRATOS (3)

| ID | Número | Objeto | Valor | Estado | Supervisor | Fecha | Status |
|----|----|----|----|----|----|----|----|
| 1 | CO1.PCCNTR.8151685 | Suministro de materiales de formación profesional para el Centro Tecnológico del Mobiliario | $99,067,527 | ACTIVO | usuario_id=3 (Supervisor) | 2026-08-11 09:09:10 | SEED |
| 2 | CO1.PCCNTR.8426076 | Prestación de servicios de apoyo a la gestión para el mantenimiento de maquinaria industrial | $18,942,000 | BORRADOR | (sin supervisor) | 2026-08-11 09:09:10 | SEED |
| 3 | CO1.PCCNTR.7865124 | Adquisición de equipos de cómputo para ambientes de formación | $46,500,000 | SUSPENDIDO | (sin supervisor) | 2026-08-11 09:09:10 | SEED |

### 3.3 ETAPAS (9)

**Contrato 1** (6 etapas - GCCON-P-010 completo):
- Etapa 1: COMPLETADA (100%) — INICIO — Estudios y Suscripción
- Etapa 2: COMPLETADA (100%) — INICIO — Acta de Inicio (GCCON-F-018)
- Etapa 3: EN_CURSO (75%) — INSPECCIÓN — Monitoreo y Ejecución ⚠️ (MODIFICADA DURANTE PRUEBAS)
- Etapa 4: PENDIENTE (0%) — RECEPCIÓN — Acta de Recibo a Satisfacción
- Etapa 5: PENDIENTE (0%) — CERTIFICACIÓN — ESUCON y Trámite de Pago
- Etapa 6: PENDIENTE (0%) — CIERRE — Liquidación y Archivo (GCCON-F-030)

**Contrato 2** (2 etapas):
- Etapa 1: EN_CURSO (40%) — INICIO — Estudios y Suscripción
- Etapa 2: PENDIENTE (0%) — INICIO — Acta de Inicio (GCCON-F-018)

**Contrato 3** (1 etapa):
- Etapa 1: PENDIENTE (0%) — INICIO — Acta de Inicio (GCCON-F-018)

### 3.4 SUBETAPAS (32)

**Total**: 32 subetapas

- **Contrato 1**: 28 subetapas (códigos 1.1–6.4, GCCON-P-010)
  - Etapa 1: 6 subetapas (1.1–1.6)
  - Etapa 2: 7 subetapas (2.1–2.7)
  - Etapa 3: 4 subetapas (3.1–3.4)
  - Etapa 4: 3 subetapas (4.1–4.3)
  - Etapa 5: 4 subetapas (5.1–5.4)
  - Etapa 6: 4 subetapas (6.1–6.4)

- **Contrato 2**: 4 subetapas
  - Etapa 7 (etapa 1 del contrato 2): 3 subetapas (1.1–1.3)
  - Etapa 9 (etapa 2 del contrato 2): 1 subetapa (2.1)

- **Contrato 3**: 0 subetapas (etapa sin descomposición)

### 3.5 DOCUMENTOS (4)

Todos asociados al **Contrato 1** (SEED):

| ID | Contrato | Subetapa | Nombre | Tipo | Estado | Fecha |
|----|----------|----------|--------|------|--------|-------|
| 1 | 1 | 2.7 | Acta de Inicio — GCCON-F-018 | PDF | APROBADO | 2026-08-11 09:09:10 |
| 2 | 1 | 2.13 | Estudios previos — GCCON-F-046 | PDF | APROBADO | 2026-08-11 09:09:10 |
| 3 | 1 | 3.15 | Registro fotográfico de entrega Lote 1 | IMAGEN | APROBADO | 2026-08-11 09:09:10 |
| 4 | 1 | NULL | Ficha técnica del bien | PDF | PENDIENTE | 2026-08-11 09:09:10 |

### 3.6 ALERTAS (5)

| ID | Contrato | Tipo | Prioridad | Mensaje | Leída | Fecha |
|----|----------|------|-----------|---------|-------|-------|
| 1 | 1 | VENCIMIENTO | ALTA | El contrato vence el 2025-12-19. Faltan menos de 30 días. | ✓ | 2026-08-11 09:09:10 |
| 2 | 1 | DOCUMENTO | MEDIA | Falta el Informe de Supervisión GCCON-F-031 (subetapa 3.4). | ✓ | 2026-08-11 09:09:10 |
| 3 | 1 | CRONOGRAMA | BAJA | La subetapa 3.3 (Comparación vs. ficha técnica) está en curso. | ✓ | 2026-08-11 09:09:10 |
| 4 | 1 | RECORDATORIO | MEDIA | Verificar la factura electrónica antes de la recepción (GIL-F-010). | ✓ | 2026-08-11 09:09:10 |
| 5 | 3 | FIRMA | ALTA | Pendiente de firma del Acta de Inicio tras la suspensión. | ✗ | 2026-08-11 09:09:10 |

### 3.7 REGISTROS DEL SEED (6 de 11)

| ID | Contrato | Usuario | Acción | Descripción | Fecha |
|----|----------|---------|--------|-------------|-------|
| 1 | 1 | - | CONTRATO_CREADO | Contrato creado a partir de la orden de compra SECOP II. | 2026-04-13 09:09:10 |
| 2 | 1 | - | SUPERVISOR_ASIGNADO | Designación formal de supervisor mediante comunicación interna. | 2026-04-23 09:09:10 |
| 3 | 1 | - | ETAPA_COMPLETADA | Etapa 1 "INICIO — Estudios y Suscripción" completada al 100%. | 2026-04-28 09:09:10 |
| 4 | 1 | - | DOCUMENTO_SUBIDO | Acta de Inicio GCCON-F-018 firmada y registrada. | 2026-05-08 09:09:10 |
| 5 | 1 | - | ETAPA_EN_CURSO | Etapa 3 "INSPECCIÓN" iniciada: verificación de entrega en bodega. | 2026-08-01 09:09:10 |
| 6 | 2 | - | CONTRATO_CREADO | Contrato creado en estado borrador para estructuración. | 2026-08-08 09:09:10 |

**Nota**: Las fechas del registro 5 son simuladas (`NOW() - INTERVAL '10 days'` y similar).

---

## 4. DATOS CREADOS DURANTE PRUEBAS (CATEGORÍA B)

### 4.1 USUARIOS DE PRUEBA (1)

| ID | Nombre | Email | Rol | Activo | Fecha de Creación | Status |
|----|--------|-------|-----|--------|-------------------|--------|
| 4 | Supervisor Prueba F10 | supervisor2@soy.sena.edu.co | SUPERVISOR | ✓ | **2026-08-11 13:53:59** | PRUEBA |

**Clasificación**: PRUEBA  
**Razón**: Creado después del seed (a las 13:53:59, vs seed a las 09:09:10)  
**Propósito aparente**: Verificación de múltiples supervisores  
**Contratos asignados**: Ninguno  
**Registros asociados**: 0  

---

### 4.2 CONTRATOS DE PRUEBA (2)

| ID | Número | Objeto | Valor | Estado | Supervisor | Fecha de Creación | Status |
|----|--------|--------|-------|--------|-----------|-------------------|--------|
| 4 | CO1.PCCNTR.PRUEBA.FASE9 | Prueba FASE 9 - contrato temporal | - | BORRADOR | (sin supervisor) | **2026-08-11 13:51:22** | PRUEBA |
| 5 | CO1.PCCNTR.PRUEBA.AUDITORIA | Prueba auditoria - asignacion de supervisor | - | BORRADOR | usuario_id=3 | **2026-08-11 14:02:34** | PRUEBA |

**Clasificación**: PRUEBAS  
**Razón**: Números de contrato contienen "PRUEBA"  
**Fechas**: Posteriores al seed (13:51:22 y 14:02:34)  
**Estado**: Ambos BORRADOR (no activos)  
**Etapas**: Ninguno tiene etapas  
**Subetapas**: Ninguno tiene subetapas  

---

### 4.3 REGISTROS DE PRUEBA (5 de 11)

| ID | Contrato | Usuario | Acción | Descripción | Fecha | Status |
|----|----------|---------|--------|-------------|-------|--------|
| 7 | 1 | 3 (Supervisor) | ETAPA_ACTUALIZADA | Etapa 3 (INSPECCIÓN) ahora está en EN_CURSO al 75%. | **2026-08-11 09:12:31** | PRUEBA |
| 8 | 4 | 2 (Gestión) | CONTRATO_CREADO | Contrato CO1.PCCNTR.PRUEBA.FASE9 creado en estado BORRADOR. | **2026-08-11 13:51:22** | PRUEBA |
| 9 | 1 | 3 (Supervisor) | ETAPA_ACTUALIZADA | Etapa 3 (INSPECCIÓN) ahora está en COMPLETADA al 100%. | **2026-08-11 14:02:14** | PRUEBA |
| 10 | 1 | 3 (Supervisor) | ETAPA_ACTUALIZADA | Etapa 3 (INSPECCIÓN) ahora está en EN_CURSO al 75%. | **2026-08-11 14:02:14** | PRUEBA |
| 11 | 5 | 2 (Gestión) | CONTRATO_CREADO | Contrato CO1.PCCNTR.PRUEBA.AUDITORIA creado en estado BORRADOR. | **2026-08-11 14:02:34** | PRUEBA |

**Clasificación**: PRUEBAS  
**Razón**: Creados después del seed, referencia a contratos de prueba o cambios de estado experimental  

---

## 5. DATOS AMBIGUOS (CATEGORÍA C)

**NINGUNO IDENTIFICADO**

Todos los datos se clasifican claramente como SEED o PRUEBAS basándose en:
1. Timestamp de creación
2. Nombres/números de contrato ("PRUEBA" explícito)
3. Asociaciones entre tablas

---

## 6. MIGRACIONES FLYWAY

| Rank | Version | Descripción | Script | Éxito | Instalado Por | Fecha |
|------|---------|-------------|--------|-------|---------------|-------|
| 1 | 1 | create initial schema | V1__create_initial_schema.sql | ✓ | sicot | 2026-08-11 09:09:10 |
| 2 | 2 | seed dev data | V2__seed_dev_data.sql | ✓ | sicot | 2026-08-11 09:09:10 |

✅ **Ambas migraciones aplicadas correctamente**  
✅ **Estructura y seed completados exitosamente**

---

## 7. MAPEO DE RELACIONES Y DEPENDENCIAS

### Contrato 4 (PRUEBA.FASE9)

```
Contrato 4 (PRUEBA.FASE9)
├── Sin etapas
├── Sin subetapas
├── Sin documentos
├── Sin alertas
└── Registro 8: CONTRATO_CREADO (usuario_id=2, Gestión)
```

**Eliminación segura**: SÍ — Sin dependencias complejas

---

### Contrato 5 (PRUEBA.AUDITORIA)

```
Contrato 5 (PRUEBA.AUDITORIA)
├── Supervisor asignado: usuario_id=3 (Alex Fernando Zapata - SEED)
├── Sin etapas
├── Sin subetapas
├── Sin documentos
├── Sin alertas
└── Registro 11: CONTRATO_CREADO (usuario_id=2, Gestión)
```

**Eliminación segura**: SÍ — El supervisor es SEED, puede quedarse

---

### Usuario 4 (Supervisor Prueba F10)

```
Usuario 4 (PRUEBA F10)
├── Rol: SUPERVISOR
├── Contratos asignados: NINGUNO
├── Registros como usuario: NINGUNO (no creó ni modificó nada)
└── Auditoría: Usuario crear pero nunca usado
```

**Eliminación segura**: SÍ — Sin dependencias

---

### Registros de Prueba en Contrato 1 (SEED)

```
Registros 7, 9, 10 (Contrato 1 - SEED)
├── Contrato: SEED (no debe eliminarse)
├── Usuario: 3 (Alex Fernando Zapata - SEED, no debe eliminarse)
├── Acción: ETAPA_ACTUALIZADA (cambios de estado experimental)
├── Fecha: 2026-08-11 09:12:31 - 14:02:14
└── Impacto: Solo registros de auditoría, no afectan estructura
```

**Nota importante**: Estos registros muestran cambios de estado en Etapa 3 del contrato SEED (COMPLETADA → EN_CURSO → COMPLETADA → EN_CURSO). El estado actual de Etapa 3 es EN_CURSO al 75%.

**Decisión**: 
- Si queremos limpiar registros experimentales: ELIMINAR registros 7, 9, 10
- Si solo limpiar contratos de prueba: CONSERVAR (son auditoria de SEED)

---

## 8. RIESGOS DE ELIMINACIÓN

### ❌ CRÍTICO: NO ELIMINAR

1. **Usuario 1, 2, 3 (SEED)**: Necesarios para autenticación, asignación de contratos, auditoría
2. **Contrato 1 (SEED)**: Contiene todo el workflow completo de demostración; estructura de GCCON-P-010
3. **Contratos 2, 3 (SEED)**: Demuestran estados BORRADOR y SUSPENDIDO
4. **Todas las etapas y subetapas del SEED**: Backbone del sistema

### ⚠️ VERIFICAR PRIMERO

5. **Registros 7, 9, 10 (en Contrato 1)**: ¿Queremos historial de cambios experimentales o solo el estado final?
   - **Si se eliminan**: Se pierde historial experimental pero Contrato 1 mantiene su estado actual (EN_CURSO)
   - **Si se conservan**: Auditoría completa disponible

### ✅ SEGURO: ELIMINAR

6. **Usuario 4 (Supervisor Prueba F10)**: Sin dependencias, sin registros propios
7. **Contrato 4 (PRUEBA.FASE9)**: Sin estructura, sin referencias
8. **Contrato 5 (PRUEBA.AUDITORIA)**: Sin estructura
9. **Registros 8, 11**: Auditoría de creación de contratos de prueba

---

## 9. PLAN DE LIMPIEZA PROPUESTO

### Opción A: LIMPIEZA MÍNIMA (Recomendado)

**Objetivo**: Eliminar solo datos claramente de prueba sin afectar el SEED

#### Paso 1: Eliminar registros de prueba (registros 8, 11)
```sql
DELETE FROM registros WHERE id IN (8, 11);
```
**Impacto**: Elimina la auditoría de creación de contratos de prueba

#### Paso 2: Eliminar contratos de prueba (contratos 4, 5)
```sql
DELETE FROM contratos WHERE id IN (4, 5);
```
**Impacto**: Elimina contratos PRUEBA.FASE9 y PRUEBA.AUDITORIA
**Requisito previo**: Registros 8 y 11 deben estar eliminados (FK constraint)

#### Paso 3: Eliminar usuario de prueba (usuario 4)
```sql
DELETE FROM usuarios WHERE id = 4;
```
**Impacto**: Elimina Supervisor Prueba F10
**Verificación**: Asegurar que ningún contrato lo referencia (ya verificado: SIN REFERENCIAS)

#### Resultado final:
- ✓ Base de datos limpia de pruebas
- ✓ SEED original intacto
- ✓ Estructura funcional completa
- ✓ Auditoría de cambios experimentales en Contrato 1 conservada (registros 7, 9, 10)

**Orden de ejecución**: 
1. DELETE FROM registros WHERE id IN (8, 11);
2. DELETE FROM contratos WHERE id IN (4, 5);
3. DELETE FROM usuarios WHERE id = 4;

---

### Opción B: LIMPIEZA PROFUNDA (Más agresiva)

**Objetivo**: Eliminar también registros de cambios experimentales

#### Pasos adicionales a Opción A:
```sql
DELETE FROM registros WHERE id IN (7, 9, 10);
```

**Impacto**: Elimina historial de cambios experimentales en Contrato 1
**Advertencia**: El estado actual de Etapa 3 (EN_CURSO, 75%) se mantiene, pero el historial de cómo llegó a ese estado se pierde

---

## 10. RECOMENDACIÓN FINAL

### ✅ APLICAR OPCIÓN A (LIMPIEZA MÍNIMA)

**Razones**:

1. **Seguridad**: Elimina datos de prueba sin tocar SEED
2. **Reversibilidad**: Si algo falla, solo 3 registros y 2 contratos afectados
3. **Auditoría**: Conserva el historial de cambios realizados durante desarrollo
4. **Estructura**: GCCON-P-010 completo intacto, demostración funcional preservada
5. **Autenticación**: Los 3 usuarios SEED permanecen para testing

### 🗑️ DATOS A ELIMINAR (6 filas totales)

**Registros** (2 filas):
- Registro 8: Creación de contrato PRUEBA.FASE9
- Registro 11: Creación de contrato PRUEBA.AUDITORIA

**Contratos** (2 filas):
- Contrato 4: CO1.PCCNTR.PRUEBA.FASE9
- Contrato 5: CO1.PCCNTR.PRUEBA.AUDITORIA

**Usuarios** (1 fila):
- Usuario 4: Supervisor Prueba F10 (supervisor2@soy.sena.edu.co)

**Total**: 5 filas de base de datos

### 📋 ESTADO FINAL ESPERADO

| Tabla | Antes | Después | Diferencia |
|-------|-------|---------|-----------|
| usuarios | 4 | 3 | -1 (Usuario PRUEBA) |
| contratos | 5 | 3 | -2 (Contratos PRUEBA) |
| etapas | 9 | 9 | 0 (Sin cambios) |
| subetapas | 32 | 32 | 0 (Sin cambios) |
| documentos | 4 | 4 | 0 (Sin cambios) |
| alertas | 5 | 5 | 0 (Sin cambios) |
| registros | 11 | 9 | -2 (Registros PRUEBA) |
| **Total filas** | **70** | **65** | **-5** |

---

## 11. EJECUCIÓN PENDIENTE

⏸️ **AUDITORÍA COMPLETADA**

⚠️ **NO SE HA EJECUTADO NINGÚN SQL DE ESCRITURA**

✅ Esperando aprobación explícita del usuario para proceder con eliminación.

---

## CONCLUSIÓN

La base de datos `sicot` contiene:
- **SEED FUNCIONAL**: 3 usuarios, 3 contratos, 9 etapas, 32 subetapas, 4 documentos, 5 alertas, 6 registros
- **DATOS DE PRUEBA**: 1 usuario, 2 contratos, 0 etapas, 0 documentos, 2 registros
- **CLASIFICACIÓN**: 100% de los datos clasificados exitosamente
- **RIESGOS**: Ninguno si se sigue Opción A
- **RECOMENDACIÓN**: Limpiar 5 filas (usuario, contratos y registros de prueba)

---

**Fin de la Auditoría**
