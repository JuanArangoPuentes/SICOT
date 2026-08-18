-- ═══════════════════════════════════════════════════════════════════════════
-- SICOT — V5: número de teléfono en usuarios
-- Nullable a nivel de base (los 3 usuarios seed no tienen uno real que
-- inventar) — la obligatoriedad para cuentas nuevas se aplica en la capa de
-- validación de la API (CrearUsuarioRequest/ActualizarUsuarioRequest).
-- ═══════════════════════════════════════════════════════════════════════════

ALTER TABLE usuarios ADD COLUMN telefono VARCHAR(20);
