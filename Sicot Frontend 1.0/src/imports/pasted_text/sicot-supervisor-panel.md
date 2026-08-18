PROYECTO: SICOT — Sistema Inteligente para la Gestión y Acompañamiento de Contratos (Centro Tecnológico del Mobiliario, SENA)

Construye un prototipo interactivo de alta fidelidad, con acabado profesional e institucional. Cada botón, pestaña, campo y tarjeta debe llevar a un estado real y funcional dentro del prototipo — nada puramente decorativo. El sistema tiene dos roles con dos interfaces distintas conectadas entre sí: Supervisor y Gestión.

1) SISTEMA DE DISEÑO

Fondo casi negro con una cuadrícula sutil en verde oscuro. Acento principal: verde esmeralda vibrante (tipo neón moderado, elegante, no saturado). Tarjetas con fondo azul-negro muy oscuro, bordes finos de 1px, esquinas redondeadas (8–12px), sombras suaves. Tipografía sans-serif limpia; badges en mayúsculas pequeñas con letter-spacing. Chips de contenido: violeta/púrpura para responsables (Supervisor, Unidad de Contratación, Ordenador del gasto), azul para documentos/artefactos (GIL-F-010, CDP, SECOP II), gris para el estado "Pendiente" y verde para "Completado".

2) LOGIN (compartido por ambos roles)

Tarjeta centrada con: logo SENA, "Centro Tecnológico del Mobiliario", "Sistema Inteligente para la Gestión y Acompañamiento de Contratos", badge "S SICOT", campo de correo (ícono sobre), campo de contraseña (ícono candado + mostrar/ocultar), botón verde "Iniciar sesión". Pie: "Sistema institucional del SENA" / "ACCESO RESTRINGIDO". Esquina inferior derecha: versión + botón de ayuda.

Cuentas de demostración precargadas:

supervisor@soy.sena.edu.co → rol Supervisor
gestion@soy.sena.edu.co → rol Gestión
(cualquier contraseña no vacía es válida en el prototipo)

3) ROL SUPERVISOR — Estado inicial (ya con contrato asignado, no vacío)

A diferencia de un sistema vacío, este prototipo debe arrancar ya con un contrato asignado, para demostrar el flujo completo desde el primer vistazo. Al iniciar sesión como Supervisor:

Pantalla de bienvenida (adaptada):
Ícono del Copiloto + etiqueta "COPILOTO IA SICOT". Tarjeta: "Bienvenido a SICOT. Tienes un contrato asignado — te acompañaré paso a paso en su ejecución." Botón verde "Ver mi contrato asignado" → lleva al Panel Principal ya poblado.

Panel Principal:

Barra superior: logo + badge "Panel Supervisor", botón "Conectar SECOP II" (atenuado, con tooltip: "La publicación en SECOP II corresponde a la Unidad de Contratación"), avatar con nombre y correo.
Pestañas: Panel Principal (activa) · Alertas · Documentos · Comunicación.
Tarjeta de notificación destacada, arriba del acordeón: "Contrato asignado — CO1.PCCNTR.8151794" con objeto (Suministro de materiales de formación — Lote 8 ADSO), valor ($39.552.042), fechas (11/08/2025 – 15/11/2025) y botón "Confirmar recepción" (mostrado ya en estado confirmado, con check verde).
Etapas del Contrato: las 6 etapas con sus subpasos exactos (detallados abajo). Pasos 1 y 2 aparecen marcados como completados (100%), ya que corresponden a la fase precontractual gestionada por Gestión/Unidad de Contratación. Paso 3 aparece como la etapa activa, coherente con que el contrato es de suministro de materiales.
Copiloto IA: mensaje proactivo: "Fuiste asignado al contrato CO1.PCCNTR.8151794. Vas a necesitar hacer esto primero: verificar la recepción física del material en bodega (Paso 3)." + botón "Iniciar Paso 3".

Las 6 etapas (validadas contra GCCON-M-002 y GCCON-P-010):

Paso 1 — Solicitud de Necesidad y Estudios ✓ Completado
Identificación de la necesidad — Área requirente, Ficha de necesidad
Conformación de la Unidad de Contratación — Ordenador del gasto, Acto administrativo
Elaboración de estudios previos (GCCON-F-046) — Unidad de Contratación, GCCON-F-046
Revisión y aval de documentos — Ordenador del gasto, Memorando
Expedición del CDP — Unidad de Contratación, CDP
Paso 2 — Suscripción y Registro SECOP II ✓ Completado
Publicación de la invitación en SECOP II — Unidad de Contratación, SECOP II
Cierre y verificación de propuestas — Unidad de Contratación, Informe evaluación
Acto de adjudicación — Ordenador del gasto, Resolución adjudicación
Suscripción del contrato en SECOP II — Unidad de Contratación, Contrato SECOP II
Designación del supervisor — Ordenador del gasto, C.I. Supervisión
Aprobación de garantías (póliza) — Unidad de Contratación, Póliza de cumplimiento
Paso 3 — Recepción Física del Material ← Activo (0/4)
Verificación física de la entrega en bodega — Supervisor, Acta de visita
Carga de evidencia fotográfica georreferenciada — Supervisor, Fotos bodega
Comparación cantidad/calidad vs. ficha técnica — Supervisor, Lista de chequeo
Diligenciamiento del Acta de Recibo (GIL-F-010) — Supervisor, GIL-F-010
Paso 4 — Verificación Documental y Trámite de Pago (0/4)
Verificación de aportes a seguridad social (PILA) — Supervisor, Planilla PILA
Verificación de factura electrónica DIAN — Supervisor, FEV
Diligenciamiento del Informe de Supervisión (GCCON-F-031) — Supervisor, GCCON-F-031
Generación del Certificado ESUCON y Oficio SCM — Supervisor, ESUCON + SCM
Paso 5 — Seguimiento y Modificaciones (0/3)
Verificación de vigencia de garantías — Supervisor, Póliza vigencia
Evaluación de necesidad de adición o prórroga — Supervisor, Informe modificación
Publicación de modificación en SECOP II (si aplica) — Unidad de Contratación, SECOP II
Paso 6 — Liquidación y Cierre (0/3)
Verificación de cumplimiento total del objeto — Supervisor, Informe final
Elaboración del Acta de Liquidación (GCCON-F-030) — Supervisor, GCCON-F-030
Cierre y archivo del expediente digital — Unidad de Contratación, Expediente SIGEP

4) ROL GESTIÓN — Estado inicial (ya con una ficha cargada, no vacío)

Panel más técnico/denso, pensado para abogadas o asistentes de contratación.

Tabla de contratos (ya con una fila cargada de ejemplo): CO1.PCCNTR.8151794 | Suministro de materiales — Lote 8 ADSO | Alex Fernando Zapata Ríos | Estado: Asignado | $39.552.042 | 11/08/2025 – 15/11/2025.
Tarjeta "Ficha procesada" mostrando esos mismos datos extraídos, con ícono de verificado (check verde) indicando que ya fue leída y asignada automáticamente por el Copiloto IA.
Botón "+ Cargar nueva ficha": abre el flujo para simular una nueva asignación — subir documento → estado breve "Analizando documento..." → tarjeta de revisión con campos editables (empresa/objeto, valor, fechas, supervisor designado) → botón "Asignar contrato" → esto debe reflejarse en vivo como una nueva notificación en el Panel del Supervisor correspondiente.

Nota de alcance: por tratarse de un prototipo de Figma Make (sin backend real), la "lectura" de documentos se simula con la animación de análisis + datos de ejemplo prellenados, no un análisis real de archivos arbitrarios.

5) TUTORIAL INTERACTIVO — comportamiento y corrección de errores previos

El tutorial interactivo significa que el Copiloto IA le dice directamente al usuario, en lenguaje natural, qué hacer en cada momento — no es un recorrido genérico de tooltips sueltos. Debe integrarse dentro del panel fijo del Copiloto (columna derecha), indicando en cada pantalla qué botón presionar y qué información se necesita.

Corrección obligatoria de un error de la versión anterior: los controles de navegación del tutorial (Anterior, Siguiente, Finalizar, Omitir) deben permanecer siempre 100% visibles dentro del viewport, sin importar el tamaño de pantalla o el scroll — en la versión anterior el botón "Siguiente" quedaba fuera de la escala visible y era imposible continuar. Para evitarlo:

Ancla los controles de navegación del tutorial dentro del panel del Copiloto IA, que tiene posición fija garantizada, en lugar de usar tooltips flotantes independientes cerca de cada elemento.
Si además se usan indicadores tipo "spotlight" señalando un botón específico en pantalla, su posición debe recalcularse dinámicamente según el tamaño real de la ventana (con auto-flip si se acerca a un borde), nunca con coordenadas fijas.
Verifica en cada paso del tutorial que el texto y los botones de acción quepan completamente dentro del área visible antes de considerarlo terminado.

6) FUNCIONALIDAD COMPLETA

Todo elemento clicable debe cambiar de estado de forma real: expandir/colapsar etapas, mostrar confirmaciones, actualizar contadores de progreso, reflejar en el Supervisor lo que Gestión asigna. Nada debe quedar mudo al hacer clic.