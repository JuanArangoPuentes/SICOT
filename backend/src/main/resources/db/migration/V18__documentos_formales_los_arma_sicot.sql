-- ═══════════════════════════════════════════════════════════════════════════
-- La descripción de las cinco subetapas con documento formal deja de decir que
-- el documento lo genera el Copiloto IA.
--
-- POR QUÉ
-- -------
-- Desde el 24-09-2026 (PR #98) los documentos formales salen de plantillas con
-- los datos del contrato; el modelo solo reescribe las observaciones del
-- supervisor, y lo que SICOT no sabe queda como «dato pendiente». La guía del
-- tutorial muestra esta descripción tal cual, y seguía diciéndole al supervisor
-- que el texto era del modelo y que él «solo lo firma»: justo lo contrario de
-- lo que se quiere que haga, que es revisarlo antes de firmar.
--
-- Solo se tocan las filas que conservan el texto original de la plantilla: si
-- alguien lo cambió a mano, se respeta.
-- ═══════════════════════════════════════════════════════════════════════════
UPDATE subetapas SET descripcion = 'SICOT arma el Acta de Inicio con los datos del contrato; el supervisor la revisa y la firma.'
 WHERE codigo = '2.7' AND descripcion = 'Acta de inicio generada por el Copiloto IA con los datos del contrato; el supervisor solo la firma.';
UPDATE subetapas SET descripcion = 'SICOT arma el Informe de Supervisión con los datos del contrato; el supervisor lo revisa y lo firma.'
 WHERE codigo = '3.4' AND descripcion = 'Informe de supervisión generado por el Copiloto IA; el supervisor solo lo firma.';
UPDATE subetapas SET descripcion = 'SICOT arma el Acta de Recibo a Satisfacción con los datos del contrato; el supervisor la revisa y la firma.'
 WHERE codigo = '4.3' AND descripcion = 'Acta de recibo generada por el Copiloto IA; el supervisor solo la firma.';
UPDATE subetapas SET descripcion = 'SICOT arma la Certificación de cumplimiento con los datos del contrato; el supervisor la revisa y la firma.'
 WHERE codigo = '5.3' AND descripcion = 'Certificación de cumplimiento generada por el Copiloto IA; el supervisor solo la firma.';
UPDATE subetapas SET descripcion = 'SICOT arma el Informe Final de Supervisión con los datos del contrato; el supervisor lo revisa y lo firma.'
 WHERE codigo = '6.3' AND descripcion = 'Informe final generado por el Copiloto IA; el supervisor solo lo firma.';
