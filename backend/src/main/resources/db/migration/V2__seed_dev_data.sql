-- ═══════════════════════════════════════════════════════════════════════════
-- SICOT — V2: Datos de desarrollo (contratos de ejemplo)
-- Estructura de etapas/subetapas: GCCON-P-010 (Procedimiento Mínima Cuantía),
-- espejo 1:1 de STEPS_INITIAL del frontend (6 etapas, 28 subetapas).
-- Los USUARIOS se crean en ejecución (DataInitializer) porque sus contraseñas
-- se codifican con BCrypt desde la aplicación. El seeder asigna el supervisor
-- al contrato 1.
-- ═══════════════════════════════════════════════════════════════════════════

-- ── Contratos de ejemplo (solo desarrollo) ──────────────────────────────────
INSERT INTO contratos (numero_contrato, objeto, valor, fecha_inicio, fecha_fin, estado) VALUES
('CO1.PCCNTR.8151685', 'Suministro de materiales de formación profesional para el Centro Tecnológico del Mobiliario', 99067527.00, '2025-02-10', '2025-12-19', 'ACTIVO'),
('CO1.PCCNTR.8426076', 'Prestación de servicios de apoyo a la gestión para el mantenimiento de maquinaria industrial', 18942000.00, '2025-06-01', '2025-11-30', 'BORRADOR'),
('CO1.PCCNTR.7865124', 'Adquisición de equipos de cómputo para ambientes de formación', 46500000.00, '2024-03-15', '2025-03-14', 'SUSPENDIDO');

-- ── Etapas y subetapas del contrato 1 (GCCON-P-010 completo) ────────────────
INSERT INTO etapas (contrato_id, nombre, numero, estado, porcentaje) VALUES
(1, 'INICIO — Estudios y Suscripción', 1, 'COMPLETADA', 100),
(1, 'INICIO — Acta de Inicio (GCCON-F-018)', 2, 'COMPLETADA', 100),
(1, 'INSPECCIÓN — Monitoreo y Ejecución', 3, 'EN_CURSO', 50),
(1, 'RECEPCIÓN — Acta de Recibo a Satisfacción', 4, 'PENDIENTE', 0),
(1, 'CERTIFICACIÓN — ESUCON y Trámite de Pago', 5, 'PENDIENTE', 0),
(1, 'CIERRE — Liquidación y Archivo (GCCON-F-030)', 6, 'PENDIENTE', 0);

INSERT INTO subetapas (etapa_id, codigo, nombre, descripcion, estado, responsable) VALUES
(1, '1.1', 'Identificación de la necesidad institucional', 'Planteamiento de la necesidad de bienes y servicios (PAA).', 'COMPLETADA', 'Área requirente'),
(1, '1.2', 'Conformación de la Unidad de Contratación', 'Definición de las personas que conformarán la Unidad de Contratación.', 'COMPLETADA', 'Ordenador del gasto'),
(1, '1.3', 'Elaboración de estudios previos (GCCON-F-046)', 'Estudios previos, ficha técnica y análisis del sector económico.', 'COMPLETADA', 'Unidad de Contratación'),
(1, '1.4', 'Expedición del CDP y aprobación de garantías', 'Certificado de Disponibilidad Presupuestal y garantías exigibles.', 'COMPLETADA', 'Unidad de Contratación'),
(1, '1.5', 'Suscripción y publicación en SECOP II', 'Suscripción del contrato y publicación en la plataforma SECOP II.', 'COMPLETADA', 'Unidad de Contratación'),
(1, '1.6', 'Designación formal del supervisor', 'Comunicación interna de designación del supervisor del contrato.', 'COMPLETADA', 'Ordenador del gasto'),
(2, '2.1', 'Revisión del objeto y alcance del contrato', 'Revisión del objeto y alcance contractual por parte del supervisor.', 'COMPLETADA', 'Supervisor'),
(2, '2.2', 'Verificación de datos del contratista y NIT', 'Verificación de RUT y cámara de comercio del contratista.', 'COMPLETADA', 'Supervisor'),
(2, '2.3', 'Confirmación de fecha de inicio y duración', 'Confirmación de cronograma y plazo de ejecución.', 'COMPLETADA', 'Supervisor'),
(2, '2.4', 'Establecimiento de responsabilidades y puntos de control', 'Definición de la matriz de control del contrato.', 'COMPLETADA', 'Supervisor'),
(2, '2.5', 'Verificación de afiliaciones a seguridad social', 'Verificación de planillas PILA del contratista.', 'COMPLETADA', 'Supervisor'),
(2, '2.6', 'Registro de garantías vigentes', 'Registro de pólizas de cumplimiento vigentes.', 'COMPLETADA', 'Unidad de Contratación'),
(2, '2.7', 'Firma del Acta de Inicio (GCCON-F-018)', 'Acta de inicio firmada; documento generado por el copiloto.', 'COMPLETADA', 'Supervisor'),
(3, '3.1', 'Verificación física de la entrega en bodega', 'Verificación física de la entrega de materiales en bodega.', 'COMPLETADA', 'Supervisor'),
(3, '3.2', 'Carga de evidencia fotográfica georreferenciada', 'Evidencia fotográfica con georreferenciación activa.', 'COMPLETADA', 'Supervisor'),
(3, '3.3', 'Comparación cantidad/calidad vs. ficha técnica', 'Verificación de cantidades y calidad contra la ficha técnica.', 'EN_CURSO', 'Supervisor'),
(3, '3.4', 'Firma del Informe de Supervisión (GCCON-F-031)', 'Informe de supervisión generado por el copiloto y firmado.', 'PENDIENTE', 'Supervisor'),
(4, '4.1', 'Verificación de aportes a seguridad social (PILA)', 'Verificación de planillas PILA para recepción.', 'PENDIENTE', 'Supervisor'),
(4, '4.2', 'Verificación de factura electrónica DIAN (FEV)', 'Verificación de la factura electrónica de venta en DIAN.', 'PENDIENTE', 'Supervisor'),
(4, '4.3', 'Firma del Acta de Recibo a Satisfacción (GIL-F-010)', 'Acta de recibo generada por el copiloto y firmada.', 'PENDIENTE', 'Supervisor'),
(5, '5.1', 'Verificación de vigencia de garantías', 'Verificación de pólizas de vigencia del contrato.', 'PENDIENTE', 'Supervisor'),
(5, '5.2', 'Revisión de orden de pago y CRP', 'Revisión de la orden de pago y Certificado de Registro Presupuestal.', 'PENDIENTE', 'Supervisor'),
(5, '5.3', 'Firma del Certificado ESUCON y Oficio SCM', 'Certificado ESUCON y oficio SCM generados por el copiloto.', 'PENDIENTE', 'Supervisor'),
(5, '5.4', 'Registro de cumplimiento en GRF-F-089', 'Certificación de cumplimiento para trámite de pago.', 'PENDIENTE', 'Supervisor'),
(6, '6.1', 'Verificación de cumplimiento total del objeto contractual', 'Verificación del cumplimiento integral del objeto contractual.', 'PENDIENTE', 'Supervisor'),
(6, '6.2', 'Evaluación de modificaciones o adiciones (si aplica)', 'Evaluación de adiciones o prórrogas registradas en SECOP II.', 'PENDIENTE', 'Supervisor'),
(6, '6.3', 'Firma del Acta de Liquidación (GCCON-F-030)', 'Acta de liquidación generada por el copiloto y firmada.', 'PENDIENTE', 'Supervisor'),
(6, '6.4', 'Cierre y archivo del expediente digital en SIGEP', 'Cierre y archivo del expediente digital del contrato.', 'PENDIENTE', 'Unidad de Contratación');

-- ── Etapas parciales de los contratos 2 y 3 ─────────────────────────────────
INSERT INTO etapas (contrato_id, nombre, numero, estado, porcentaje) VALUES
(2, 'INICIO — Estudios y Suscripción', 1, 'EN_CURSO', 40),
(2, 'INICIO — Acta de Inicio (GCCON-F-018)', 2, 'PENDIENTE', 0),
(3, 'INICIO — Acta de Inicio (GCCON-F-018)', 2, 'PENDIENTE', 0);

INSERT INTO subetapas (etapa_id, codigo, nombre, descripcion, estado, responsable) VALUES
(7, '1.1', 'Identificación de la necesidad institucional', 'Planteamiento de la necesidad de bienes y servicios (PAA).', 'COMPLETADA', 'Área requirente'),
(7, '1.2', 'Conformación de la Unidad de Contratación', 'Definición de las personas que conformarán la Unidad de Contratación.', 'COMPLETADA', 'Ordenador del gasto'),
(7, '1.3', 'Elaboración de estudios previos (GCCON-F-046)', 'Estudios previos, ficha técnica y análisis del sector económico.', 'EN_CURSO', 'Unidad de Contratación'),
(9, '2.1', 'Revisión del objeto y alcance del contrato', 'Revisión del objeto y alcance contractual por parte del supervisor.', 'PENDIENTE', 'Supervisor');

-- ── Documentos de ejemplo (contrato 1) ──────────────────────────────────────
INSERT INTO documentos (contrato_id, subetapa_id, nombre, tipo, ruta_archivo, estado) VALUES
(1, 7,  'Acta de Inicio — GCCON-F-018',        'PDF',   '/evidencias/contrato-1/gccon-f-018-acta-inicio.pdf',   'APROBADO'),
(1, 13, 'Estudios previos — GCCON-F-046',       'PDF',   '/evidencias/contrato-1/gccon-f-046-estudios-previos.pdf', 'APROBADO'),
(1, 15, 'Registro fotográfico de entrega Lote 1', 'IMAGEN', '/evidencias/contrato-1/lote-1-bodega.jpg',           'APROBADO'),
(1, NULL, 'Ficha técnica del bien',             'PDF',   '/evidencias/contrato-1/ficha-tecnica.pdf',              'PENDIENTE');

-- ── Alertas de ejemplo ──────────────────────────────────────────────────────
INSERT INTO alertas (contrato_id, tipo, prioridad, mensaje, leida) VALUES
(1, 'VENCIMIENTO',  'ALTA', 'El contrato vence el 2025-12-19. Faltan menos de 30 días.', FALSE),
(1, 'DOCUMENTO',    'MEDIA', 'Falta el Informe de Supervisión GCCON-F-031 (subetapa 3.4).', FALSE),
(1, 'CRONOGRAMA',   'BAJA', 'La subetapa 3.3 (Comparación vs. ficha técnica) está en curso.', TRUE),
(1, 'RECORDATORIO', 'MEDIA', 'Verificar la factura electrónica antes de la recepción (GIL-F-010).', FALSE),
(3, 'FIRMA',        'ALTA', 'Pendiente de firma del Acta de Inicio tras la suspensión.', FALSE);

-- ── Registros de auditoría de ejemplo ───────────────────────────────────────
INSERT INTO registros (contrato_id, accion, descripcion, fecha) VALUES
(1, 'CONTRATO_CREADO', 'Contrato creado a partir de la orden de compra SECOP II.', NOW() - INTERVAL '120 days'),
(1, 'SUPERVISOR_ASIGNADO', 'Designación formal de supervisor mediante comunicación interna.', NOW() - INTERVAL '110 days'),
(1, 'ETAPA_COMPLETADA', 'Etapa 1 "INICIO — Estudios y Suscripción" completada al 100%.', NOW() - INTERVAL '105 days'),
(1, 'DOCUMENTO_SUBIDO', 'Acta de Inicio GCCON-F-018 firmada y registrada.', NOW() - INTERVAL '95 days'),
(1, 'ETAPA_EN_CURSO', 'Etapa 3 "INSPECCIÓN" iniciada: verificación de entrega en bodega.', NOW() - INTERVAL '10 days'),
(2, 'CONTRATO_CREADO', 'Contrato creado en estado borrador para estructuración.', NOW() - INTERVAL '3 days');
