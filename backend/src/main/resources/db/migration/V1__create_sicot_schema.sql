-- SICOT baseline schema for a new PostgreSQL database.
-- This migration contains structure only. Development users are created by
-- DataInitializer when the dev profile is active; no transactional demo data
-- is inserted or deleted by Flyway.

CREATE TABLE usuarios (
    id BIGSERIAL PRIMARY KEY,
    nombre VARCHAR(150) NOT NULL,
    email VARCHAR(150) NOT NULL UNIQUE,
    password VARCHAR(100) NOT NULL,
    rol VARCHAR(20) NOT NULL,
    activo BOOLEAN NOT NULL DEFAULT TRUE,
    fecha_creacion TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    fecha_actualizacion TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    telefono VARCHAR(20),
    CONSTRAINT ck_usuarios_rol CHECK (rol IN ('ADMINISTRADOR', 'GESTION', 'SUPERVISOR'))
);

CREATE TABLE contratos (
    id BIGSERIAL PRIMARY KEY,
    numero_contrato VARCHAR(50) NOT NULL UNIQUE,
    objeto TEXT NOT NULL,
    valor NUMERIC(18, 2) NOT NULL,
    fecha_inicio DATE,
    fecha_fin DATE,
    estado VARCHAR(20) NOT NULL DEFAULT 'BORRADOR',
    supervisor_id BIGINT REFERENCES usuarios (id),
    fecha_creacion TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    fecha_actualizacion TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    tipo_contrato VARCHAR(100),
    contratista VARCHAR(255),
    contratista_nit VARCHAR(30),
    representante_legal VARCHAR(255),
    lugar_ejecucion VARCHAR(255),
    numero_registro_presupuestal VARCHAR(50),
    fecha_registro_presupuestal DATE,
    centro_costo VARCHAR(100),
    CONSTRAINT ck_contratos_estado CHECK (estado IN ('BORRADOR', 'ACTIVO', 'SUSPENDIDO', 'FINALIZADO', 'CANCELADO')),
    CONSTRAINT ck_contratos_valor CHECK (valor >= 0),
    CONSTRAINT ck_contratos_fechas CHECK (fecha_fin IS NULL OR fecha_inicio IS NULL OR fecha_fin >= fecha_inicio)
);

CREATE INDEX idx_contratos_supervisor ON contratos (supervisor_id);
CREATE INDEX idx_contratos_estado ON contratos (estado);

CREATE TABLE etapas (
    id BIGSERIAL PRIMARY KEY,
    contrato_id BIGINT NOT NULL REFERENCES contratos (id) ON DELETE CASCADE,
    nombre VARCHAR(150) NOT NULL,
    numero INT NOT NULL,
    estado VARCHAR(20) NOT NULL DEFAULT 'PENDIENTE',
    porcentaje INT NOT NULL DEFAULT 0,
    CONSTRAINT ck_etapas_numero CHECK (numero > 0),
    CONSTRAINT ck_etapas_estado CHECK (estado IN ('PENDIENTE', 'EN_CURSO', 'COMPLETADA')),
    CONSTRAINT ck_etapas_porcentaje CHECK (porcentaje BETWEEN 0 AND 100),
    CONSTRAINT uq_etapas_contrato_numero UNIQUE (contrato_id, numero)
);

CREATE INDEX idx_etapas_contrato ON etapas (contrato_id);

CREATE TABLE subetapas (
    id BIGSERIAL PRIMARY KEY,
    etapa_id BIGINT NOT NULL REFERENCES etapas (id) ON DELETE CASCADE,
    codigo VARCHAR(20) NOT NULL,
    nombre VARCHAR(255) NOT NULL,
    descripcion TEXT,
    estado VARCHAR(20) NOT NULL DEFAULT 'PENDIENTE',
    responsable VARCHAR(150) NOT NULL,
    CONSTRAINT ck_subetapas_estado CHECK (estado IN ('PENDIENTE', 'EN_CURSO', 'COMPLETADA')),
    CONSTRAINT uq_subetapas_etapa_codigo UNIQUE (etapa_id, codigo)
);

CREATE INDEX idx_subetapas_etapa ON subetapas (etapa_id);

CREATE TABLE documentos (
    id BIGSERIAL PRIMARY KEY,
    contrato_id BIGINT NOT NULL REFERENCES contratos (id) ON DELETE CASCADE,
    subetapa_id BIGINT REFERENCES subetapas (id) ON DELETE SET NULL,
    nombre VARCHAR(255) NOT NULL,
    tipo VARCHAR(20) NOT NULL,
    ruta_archivo VARCHAR(500),
    estado VARCHAR(20) NOT NULL DEFAULT 'PENDIENTE',
    fecha_subida TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    fecha_actualizacion TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    content_type VARCHAR(100),
    contenido BYTEA,
    tamanio_bytes BIGINT,
    generado_por_ia BOOLEAN NOT NULL DEFAULT FALSE,
    firma_id VARCHAR(50),
    fecha_firma TIMESTAMPTZ,
    subido_por_id BIGINT REFERENCES usuarios (id) ON DELETE SET NULL,
    CONSTRAINT ck_documentos_tipo CHECK (tipo IN ('PDF', 'DOCX', 'XLSX', 'IMAGEN', 'OTRO')),
    CONSTRAINT ck_documentos_estado CHECK (estado IN ('PENDIENTE', 'APROBADO', 'RECHAZADO')),
    CONSTRAINT ck_documentos_tamanio CHECK (tamanio_bytes IS NULL OR tamanio_bytes >= 0)
);

CREATE INDEX idx_documentos_contrato ON documentos (contrato_id);
CREATE INDEX idx_documentos_subetapa ON documentos (subetapa_id);
CREATE INDEX idx_documentos_subido_por ON documentos (subido_por_id);

CREATE TABLE alertas (
    id BIGSERIAL PRIMARY KEY,
    contrato_id BIGINT REFERENCES contratos (id) ON DELETE CASCADE,
    tipo VARCHAR(30) NOT NULL,
    prioridad VARCHAR(10) NOT NULL,
    mensaje TEXT NOT NULL,
    leida BOOLEAN NOT NULL DEFAULT FALSE,
    fecha_creacion TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT ck_alertas_tipo CHECK (tipo IN ('VENCIMIENTO', 'DOCUMENTO', 'FACTURA', 'FIRMA', 'IA', 'SECOP', 'RECORDATORIO', 'SOLICITUD', 'RECHAZADO', 'CRONOGRAMA')),
    CONSTRAINT ck_alertas_prioridad CHECK (prioridad IN ('ALTA', 'MEDIA', 'BAJA'))
);

CREATE INDEX idx_alertas_contrato ON alertas (contrato_id);

CREATE TABLE registros (
    id BIGSERIAL PRIMARY KEY,
    contrato_id BIGINT REFERENCES contratos (id) ON DELETE CASCADE,
    usuario_id BIGINT REFERENCES usuarios (id) ON DELETE SET NULL,
    accion VARCHAR(100) NOT NULL,
    descripcion TEXT,
    fecha TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_registros_contrato ON registros (contrato_id);
CREATE INDEX idx_registros_usuario ON registros (usuario_id);

CREATE TABLE formatos_documentales (
    id BIGSERIAL PRIMARY KEY,
    codigo VARCHAR(50) NOT NULL UNIQUE,
    nombre VARCHAR(255) NOT NULL,
    version VARCHAR(20) NOT NULL DEFAULT 'v1',
    tipo_archivo VARCHAR(20) NOT NULL,
    nombre_archivo VARCHAR(255) NOT NULL,
    content_type VARCHAR(100) NOT NULL,
    contenido BYTEA NOT NULL,
    tamanio_bytes BIGINT NOT NULL,
    estado VARCHAR(20) NOT NULL DEFAULT 'VIGENTE',
    subido_por_id BIGINT REFERENCES usuarios (id) ON DELETE SET NULL,
    fecha_creacion TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    fecha_actualizacion TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT ck_formatos_tipo_archivo CHECK (tipo_archivo IN ('PDF', 'DOCX', 'XLSX', 'IMAGEN', 'OTRO')),
    CONSTRAINT ck_formatos_estado CHECK (estado IN ('VIGENTE', 'OBSOLETO')),
    CONSTRAINT ck_formatos_tamanio CHECK (tamanio_bytes >= 0)
);

CREATE INDEX idx_formatos_subido_por ON formatos_documentales (subido_por_id);

CREATE TABLE firmas_electronicas (
    id BIGSERIAL PRIMARY KEY,
    usuario_id BIGINT NOT NULL REFERENCES usuarios (id) ON DELETE CASCADE,
    firma_id VARCHAR(50) NOT NULL,
    activa BOOLEAN NOT NULL DEFAULT TRUE,
    asignado_por_id BIGINT REFERENCES usuarios (id) ON DELETE SET NULL,
    fecha_asignacion TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    fecha_actualizacion TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_firmas_electronicas_firma_id UNIQUE (firma_id)
);

CREATE INDEX idx_firmas_electronicas_usuario ON firmas_electronicas (usuario_id);
CREATE UNIQUE INDEX uq_firma_activa_por_usuario ON firmas_electronicas (usuario_id) WHERE activa = TRUE;