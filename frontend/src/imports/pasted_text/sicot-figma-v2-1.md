PROYECTO: SICOT - Sistema Inteligente para la Gestión y Acompañamiento de Contratos
PLATAFORMA: Figma Make
VERSIÓN: 2.0 - Panel Supervisor Avanzado + Interfaz Administrador + Personalización

=== CONTEXTO INSTITUCIONAL ===
- Institución: SENA Regional Antioquia - Centro Tecnológico del Mobiliario (CTMA)
- Usuarios: Gestión de Contratación (admin@soy.sena.edu.co / sicot123) y Supervisores (supervisor@soy.sena.edu.co / sicot123)
- Documentos de referencia: GCCON-P-010, GCCON-M-001, GCCON-M-002
- Formatos oficiales: GCCON-F-018, GCCON-F-031, GRF-F-089, GIL-F-010, ESUCON
- Términos: Contratos, Procesos, Proveedores, Registros, Supervisión, Expediente Digital

=== ARQUITECTURA DE MÓDULOS ===
Mantener los 6 módulos existentes e integrar los nuevos componentes en cada uno:
- Módulo 0: Auth (mantener dos roles)
- Módulo 1: Gestión de Contratos (mantener + secuencia por tipo de contrato)
- Módulo 2: Panel Supervisor (ACTUALIZAR con barra progreso, alertas, registros)
- Módulo 3: Fábrica de Documentos (mantener)
- Módulo 4: Copiloto IA (mantener + avatar personalizable)
- Módulo 5: Expediente Digital (mantener)
- NUEVO - Módulo 6: Interfaz de Administrador

=== PARTE 1: PANEL PRINCIPAL DEL SUPERVISOR (MÓDULO 2) ===

1.1 BARRA DE PROGRESO DEL CONTRATO
   Ubicación: Encima de la tarjeta principal del contrato
   Tamaño: Ancho completo, alto 8px, bordes redondeados
   Segmentos: 5 etapas (Inicio → Entrega → Inspección → Recibo → Cierre)
   
   LÓGICA DE COLORES:
   - Verde (#2EE6A0): Etapa completada o al día
   - Amarillo (#FFD700): Pendientes no críticos (ej. faltan documentos por firmar)
   - Rojo (#EF4444): Urgentes/Críticos (ej. fecha de entrega vencida)
   - Gris (#CCCCCC): No iniciado
   
   Interactividad: Al pasar mouse, mostrar tooltip con descripción de etapa y % completado

1.2 SISTEMA DE ALERTAS
   Ubicación: Panel derecho superior, bajo avatar del supervisor
   Tipo 1 - ALERTAS LEVES (Amarillo):
      - Parpadeo leve (opacidad 0.7 → 1 en 1.5 segundos)
      - Color: #FFD700
      - Icono: ! (información)
      - Ejemplo: "Documentación pendiente: GIL-F-010"
   
   Tipo 2 - ALERTAS CRÍTICAS (Rojo):
      - Parpadeo rápido (opacidad 0.5 → 1 en 0.6 segundos)
      - Color: #EF4444
      - Icono: ⚠ (alerta)
      - Ejemplo: "Plazo de entrega vencido: 3 días"
   
   Máximo: 3 alertas visibles simultáneamente
   Descartar: Botón X en cada alerta, se oculta por 24h

1.3 CAMBIO: COMUNICACIÓN → REGISTROS
   Ubicación: Pestaña en panel superior (junto a Documentos, Tareas, Copiloto)
   Contenido: Historial de comunicaciones y firmas del contrato
   
   Estructura de cada registro:
   ┌─────────────────────────────────────────┐
   │ TIPO: [Correo Enviado / Firma / Notificación]
   │ DESTINATARIO: [Nombre - Cargo]
   │ FECHA/HORA: [DD/MM/YYYY HH:MM]
   │ ASUNTO: [Asunto del correo o acción]
   │ ESTADO: [Entregado / Leído / Firmado]
   └─────────────────────────────────────────┘
   
   Filtros: Por tipo, por destinatario, por fecha rango
   Exportar: Botón "Descargar Log Completo" → PDF

=== PARTE 2: INTERFAZ DE ADMINISTRADOR (NUEVO MÓDULO 6) ===

ACCESO:
- Credenciales: administrador@soy.sena.edu.co / sicot123
- Disponible solo tras autenticación exitosa en Module 0 (Auth)
- Opción: "Acceso Administrador" en menú principal (solo visible para este usuario)

2.1 DASHBOARD ADMINISTRADOR
   Secciones principales:
   
   A) GESTIÓN DE DOCUMENTOS
      Descripción: Centro de control para actualizar documentos y archivos oficiales del sistema
      
      Interfaz:
      - Tabla con columnas: Tipo | Versión Actual | Última Actualización | Estado | Acciones
      - Filas: GIL-F-010, GCCON-F-018, GCCON-F-031, GRF-F-089, ESUCON Cert, etc.
      - Estado visual:
         • Verde: Vigente y sin cambios pendientes
         • Amarillo: Cambios sugeridos (por IA)
         • Rojo: Conflictos detectados
      
      Acciones por documento:
      - Botón "Ver Versión Actual": Abre modal con preview del documento
      - Botón "Cargar Nueva Versión": 
         * Selector de archivo (PDF/DOCX)
         * Pre-análisis IA: "Analizando cambios..."
         * Mostrar resumen de cambios detectados
      - Botón "Ver Historial": Tabla con versiones anteriores y fecha cambio
      
      ANÁLISIS IA (Asistencia automática):
      Cuando se carga una nueva versión:
      ┌─────────────────────────────────────────┐
      │ 🤖 ANÁLISIS IA - CAMBIOS DETECTADOS    │
      ├─────────────────────────────────────────┤
      │ ✅ Campo "PROVEEDOR" → Formato OK      │
      │ ⚠️ Campo "FECHA" → Recomendación:      │
      │    "Usar formato DD/MM/YYYY para      │
      │     consistencia con expediente"      │
      │ 🔴 CONFLICTO DETECTADO:               │
      │    "Este campo no existe en GIL-F-031,│
      │     puede romper la consistencia"     │
      │    Recomendación: Revisar con legal   │
      ├─────────────────────────────────────────┤
      │ [Cargar Igualmente] [Revisar Cambios] │
      └─────────────────────────────────────────┘
   
   B) GESTIÓN DE USUARIOS
      Descripción: Crear, editar, desactivar supervisores y personal de gestión
      
      Interfaz:
      - Tabla: Nombre | Correo | Cargo | Rol | Estado | Acciones
      - Botón "+Nuevo Usuario"
      
      Flujo CREAR USUARIO:
      1. Modal "Crear Nuevo Supervisor/Gestor"
         Campos:
         - Nombre Completo (requerido)
         - Correo Institucional (validar dominio @soy.sena.edu.co)
         - Número de Teléfono (opcional)
         - Cargo (dropdown: Supervisor, Gestor de Contratación, Administrador)
         - Asignar Centros de Costo (multi-select, ej: 920510)
      
      2. Sistema de Contraseña:
         - Generar automática (contraseña fuerte 12 caracteres)
         - Mostrar contraseña temporal (solo una vez)
      
      3. Método de entrega (elegir uno):
         ☐ Enviar por Correo Institucional
         ☐ Enviar por WhatsApp/SMS
         ☐ Mostrar QR para descargar credenciales
      
      4. Confirmación:
         "Usuario creado exitosamente. Notificación enviada a [email]"
      
      Flujo EDITAR USUARIO:
      - Cambiar cargo, centros de costo, estado
      - "Resetear Contraseña" → Repite flujo de entrega
      
      Flujo DESACTIVAR:
      - Cambiar estado a "Inactivo" (no eliminar datos)
      - Opción de reactivar

   C) GESTIÓN DE FIRMAS ELECTRÓNICAS
      Descripción: Crear, asignar y gestionar firmas digitales del sistema
      
      Interfaz:
      - Tabla: Usuario | Firma ID | Fecha Creación | Estado | Acciones
      - Botón "+Generar Firma"
      
      Flujo CREAR FIRMA:
      1. Modal "Generar Firma Electrónica"
         Campos pre-rellenados (si usuario ya existe):
         - Nombre Completo: [campo]
         - Correo Institucional: [campo]
         - Número de Teléfono: [campo]
         - Cargo: [dropdown con opciones: Supervisor, Gestor, Administrador, Otro]
         - Datos Adicionales: Cédula/NIT (opcional)
      
      2. Generación (IA/Backend):
         - Crear firma única (hash + metadata)
         - Cifrar datos con certificado institucional
         - Generar archivo de firma (.p7s o similar)
      
      3. Envío:
         Método 1: "Descargar Firma"
         - Botón de descarga con instrucciones
         
         Método 2: "Enviar al Usuario"
         - Correo con archivo adjunto + instrucciones
         - SMS con enlace de descarga (si se proporcionó teléfono)
      
      4. Confirmación:
         "Firma electrónica generada: FIRMAXXXXXX"
         "Enviada a: [email] el [fecha/hora]"

2.2 PANEL DE CONTROL ADMINISTRATIVO
   Widgets de resumen:
   - Total de Contratos Activos: [XX]
   - Usuarios Activos: [XX]
   - Documentos Vigentes: [XX/12]
   - Alertas del Sistema: [X]
   
   Gráfico: Actividad últimos 30 días (contratos creados, supervisados, cerrados)

=== PARTE 3: SECUENCIA POR TIPO DE CONTRATO (MÓDULO 1) ===

3.1 FLUJO DE CARGA Y CONFIRMACIÓN
   Inicio: Usuario de Gestión inicia nuevo contrato (Módulo 1)
   
   Paso 1: Subir Contrato
   - Opción: Carga manual PDF o "Cargar asistido" (escanear metadatos)
   - Archivo acepta: PDF, DOCX
   - IA: Extrae automáticamente Proveedor, Monto, Fecha, Acto Administrativo
   
   Paso 2: DETECCIÓN AUTOMÁTICA DE TIPO (IA)
   ┌─────────────────────────────────────────────┐
   │ 🤖 ANÁLISIS DEL CONTRATO                    │
   │ Detectando tipo...                          │
   ├─────────────────────────────────────────────┤
   │ ✅ TIPO IDENTIFICADO: Suministro de Bienes │
   │                                             │
   │ CARACTERÍSTICAS:                            │
   │ • Proveedor: EQUIPARO SAS                  │
   │ • NIT: 890940618                           │
   │ • Monto: $XXX.XXX                          │
   │ • Acto Administrativo: CO1.PCCNTR.XXXX    │
   │ • Modalidad: Menor Cuantía (si aplica)    │
   │                                             │
   │ SECUENCIA RECOMENDADA:                     │
   │ 1️⃣ Acta de Inicio                          │
   │ 2️⃣ Inspección y Recepción de Bienes       │
   │ 3️⃣ Certificado ESUCON                     │
   │ 4️⃣ Cierre de Contrato                     │
   └─────────────────────────────────────────────┘
   
   Paso 3: CONFIRMACIÓN POR GESTIÓN
   - Tabla editable con datos extraídos
   - Usuario puede corregir si es necesario
   - Opciones:
      ☐ Tipo de Contrato (dropdown: Suministro, Servicios, Obra, Arrendamiento, etc.)
      ☐ Centro de Costo (de la lista del CTMA)
      ☐ Supervisor Asignado (dropdown de supervisores activos)
   
   Botones:
   - [✓ Confirmar y Cargar] → Contrato entra en el sistema
   - [✗ Rechazar] → Volver a cargar
   - [? Solicitar Revisión IA] → Envía al copiloto para análisis adicional

3.2 TIPOS DE CONTRATO (Secuencias diferenciadas)
   Cada tipo tiene pasos y documentos distintos:
   
   TIPO A: SUMINISTRO DE BIENES
   Etapas: Inicio → Inspección de Recepción → Recibo a Satisfacción → Cierre
   Documentos clave: GIL-F-010, ESUCON, Acta de Inicio
   
   TIPO B: SERVICIOS
   Etapas: Inicio → Ejecución → Inspección de Servicios → Cierre
   Documentos clave: Acta de Inicio, Informe de Supervisión, Certificación
   
   TIPO C: OBRAS
   Etapas: Inicio → Ejecución Parcial → Inspección Técnica → Cierre
   Documentos clave: Acta de Inicio, Actas de Avance, Certificación Final
   
   (Nota: Las secuencias detalladas se configuran en backend, esta vista es el selector)

=== PARTE 4: PERSONALIZACIÓN DE INTERFAZ (NUEVA SECCIÓN) ===

4.1 ACCESO A PERSONALIZACION
   Ubicación: Menú usuario (arriba derecha) → "Configuración" → "Personalización"
   
   Dos submodos:
   
   A) PRESETS (Configuraciones predeterminadas)
      - Clásico SENA (colores institucionales: azul/blanco)
      - Modo Oscuro (fondo oscuro, texto claro)
      - Alto Contraste (para accesibilidad)
      - Minimalista (sin animaciones)
   
   Cada preset aplica:
   - Esquema de colores
   - Tipografía
   - Tamaño de fuentes
   - Velocidad de animaciones
   
   B) PERSONALIZACIÓN MANUAL
      Permite controlar cada parámetro:
      
      SECCIÓN 1: COLORES
      - Color Primario: [Selector de color] (actual: #2EE6A0)
      - Color Secundario: [Selector de color]
      - Color de Fondo: [Selector de color]
      - Color de Texto: [Selector de color]
      - Color de Alerta Leve: [Selector de color] (amarillo)
      - Color de Alerta Crítica: [Selector de color] (rojo)
      
      SECCIÓN 2: TIPOGRAFÍA
      - Fuente Principal: [Dropdown: Inter, Roboto, Plus Jakarta Sans, etc.]
      - Tamaño Base: [Slider: 12px - 18px]
      - Peso de Fuente: [Radio: Normal, Semibold, Bold]
      
      SECCIÓN 3: ANIMACIONES
      - Velocidad de Transiciones: [Slider: Lenta(400ms) - Rápida(150ms)]
      - Habilitar Parpadeo en Alertas: [Toggle ON/OFF]
      - Habilitar Hover Effects: [Toggle ON/OFF]
      
      SECCIÓN 4: NOTIFICACIONES
      - Duración de Alertas: [Slider: 3s - 10s]
      - Posición: [Radio: Arriba Derecha, Arriba Centro, Abajo Derecha]
      - Sonido: [Toggle] + [Selector de sonido]
      
      Botones de acción:
      - [💾 Guardar Cambios]
      - [↶ Resetear a Predeterminado]
      - [👁 Vista Previa]

=== PARTE 5: PERSONALIZACIÓN DE IA (AVATAR INTERACTIVO) ===

5.1 CONFIGURACIÓN DEL AVATAR
   Ubicación: Menú usuario → "Configuración" → "Mi Copiloto IA"
   
   GALERÍA DE AVATARES (mínimo 6 opciones):
   1. "Asistente Profesional" - Figura minimalista, traje azul
   2. "Experto Legal" - Profesional con accesorios (lentes, portapapeles)
   3. "Bot Amigable" - Diseño futurista, redondeado, calidez
   4. "Gestor Eficiente" - Estilo corporativo moderno
   5. "Especialista SENA" - Con logo/branding institucional
   6. "Avatar Personalizado" - Permite subir imagen (limitación: 200x200px)
   
   Cada avatar tiene:
   - Nombre asignable por el usuario (ej: "Mi Asistente", "Diego")
   - Voz (si aplica): [Selector de acento español]
   - Tono de comunicación:
      ☐ Formal y directo
      ☐ Amable y detallado
      ☐ Conciso y técnico

5.2 AVATAR INTERACTIVO EN INTERFAZ (FEATURE AVANZADA)
   
   Modo 1: GHOST (Modo Desactivado - por defecto)
   - Avatar no visible en la interfaz cotidiana
   - Solo aparece cuando:
      * Usuario abre el panel "Copiloto" (Módulo 4)
      * Se dispara un tutorial
   
   Modo 2: FOLLOWER (Avatar Flotante)
   - Avatar pequeño (80x80px) sigue el cursor del usuario
   - Se posiciona en esquina inferior derecha cuando no hay interacción
   - Interactividad:
      * Click en avatar → Abre panel lateral de chat
      * Animación de entrada: Suave fade-in desde esquina
   
   Modo 3: GUIDE (Guía Paso a Paso - Tutorial Asistido)
   - Avatar aparece en tamaño mediano (120x120px)
   - Se posiciona junto al elemento que explica (ej: botón "Cargar Contrato")
   - Movimiento: Transición suave (300ms) hacia nuevo punto de enfoque
   - Cuadro de diálogo:
      * Posición: Bubble flotante junto al avatar
      * Texto: Instrucciones en imperativos cortos (máx 15 palabras)
      * Ej: "Haz clic aquí para subir el documento del contrato"
   - Controles:
      * [→ Siguiente paso] [← Anterior] [✕ Salir tutorial]
   
   ANIMACIONES DEL AVATAR:
   - Entrada: Fade-in + slide desde borde (200ms)
   - Movimiento: Bezier suave entre posiciones (300ms)
   - Idle: Micro-animación (parpadeo, pequeña onda de respiración)
   - Click: Pulso suave para feedback

   INTERACCIÓN EN VIVO:
   Cuando el avatar guía al usuario:
   - Usuario realiza acción → Avatar puede mostrar reacción (OK emoji, check)
   - Si usuario va fuera de paso → Avatar sugiere "Parece que te desviaste, aquí está el paso 3"

5.3 TUTORIAL INTERACTIVO (Integración con Avatar)
   
   Flujo:
   1. Primer acceso del usuario → Opción: "Iniciar tutorial"
   2. Avatar aparece en modo GUIDE
   3. Pasos predefinidos según rol:
      
      PARA SUPERVISORES:
      Paso 1: "Explora tu Panel Principal" → Señala zona de contrato
      Paso 2: "Aquí verás alertas y estado" → Señala zona de alertas
      Paso 3: "Gestiona registros de comunicación" → Señala pestaña "Registros"
      Paso 4: "El copiloto está siempre listo" → Señala botón copiloto
      [Completar Tutorial]
      
      PARA GESTIÓN:
      Paso 1: "Cargar nuevo contrato" → Señala botón principal
      Paso 2: "Sistema detecta tipo automáticamente" → Muestra ejemplo
      Paso 3: "Confirma datos antes de asignar" → Señala zona confirmación
      [Completar Tutorial]

=== PARTE 6: ELEMENTOS VISUALES GENERALES ===

6.1 DISEÑO COHERENTE
   Stack: React 19 + TypeScript + Vite + Tailwind CSS v4 + React Router v7
   
   Sistema de color base (mantener):
   - Acento primario: #2EE6A0 (neon verde)
   - Uso: Highlights de completado, spotlight tutoriales, barras de progreso, success states
   - RESTRICCIÓN: No usar como fondo grande (solo puntos de énfasis)
   
   Animaciones base:
   - Transiciones normales: 200-250ms
   - Parpadeo leve: 1.5s ciclo completo (0.7 → 1 opacidad)
   - Parpadeo rápido (alertas críticas): 0.6s ciclo completo (0.5 → 1 opacidad)
   - Skeleton loading: Shimmer estándar

6.2 COMPONENTES REUTILIZABLES
   Actualizar si es necesario:
   - AlertCard: Con tipografía de alerta leve/crítica
   - ProgressBar: Segmentada con 5 etapas
   - RegistroItem: Para lista de comunicaciones
   - DocumentUploader: Con pre-análisis IA
   - UserForm: Para crear supervisores
   - AvatarSelector: Galería y configuración

6.3 ESTRUCTURA RESPONSIVE
   - Breakpoints: 320px | 768px | 1024px | 1440px
   - Mobile-first approach
   - Modals en móvil: Full-screen o 95% viewport

=== PARTE 7: FLUJOS DE USUARIO (RESUMEN VISUAL) ===

7.1 SUPERVISOR - VISTA DIARIA
1. Login → Verificar alertas
2. Revisar barra de progreso de contratos
3. Leer registros de comunicación/firmas
4. Hacer clic en alerta → Ejecutar acción asistida por IA
5. Usar copiloto para generar documento/email

7.2 GESTIÓN - FLUJO DE CARGA
1. Login → "Nuevo Contrato"
2. Subir PDF → IA detecta tipo
3. Revisar datos extraídos
4. Confirmar supervisor y centro de costo
5. Contrato cargado → Se asigna al supervisor

7.3 ADMINISTRADOR - FLUJO DIARIO
1. Login (admin@soy.sena.edu.co)
2. Dashboard: Ver estado sistema, alertas
3. Gestión de documentos: Revisar versiones, cargar actualizaciones
4. Gestión de usuarios: Crear supervisores, resetear contraseñas
5. Firmas electrónicas: Generar nuevas, enviar usuarios

=== PARTE 8: INSTRUCCIONES TÉCNICAS PARA FIGMA MAKE ===

Crear frames en este orden:
1. Dashboard Supervisor (mejorado con barra + alertas)
2. Pestaña Registros (nueva)
3. Dashboard Administrador
4. Sección Gestión Documentos
5. Modal Crear Usuario
6. Modal Generar Firma
7. Personalización Interface (Presets + Manual)
8. Personalización Avatar (Galería + Configuración)
9. Tutorial Interactivo (3-4 pasos de ejemplo)
10. Flujo Carga Contrato (con detección tipo)

Usar variables de Figma para:
- Colores temáticos
- Tamaños de fuente
- Espaciado
- Animaciones (duración, easing)

Componentes interactivos:
- Prototipos de navegación entre vistas
- Simulación de parpadeo (usar overlay con opacidad variable)
- Estados hover/active en botones

=== NOTAS IMPORTANTES ===
- Mantener coherencia terminológica: Contratos, Supervisores, Gestión, Registros
- Prioridad accesibilidad: Alto contraste en alertas, tipografía legible
- No inventar datos: Usar estructuras reales del GIL-F-010 y manuales
- Énfasis en seguridad: Credenciales no visibles en mockups, indicar "(cifrado)"
- Guardar como: "SICOT-v2-Supervisor-Admin-Personalizacion-[FechaHoy]"

=== FIN DEL PROMPT ===