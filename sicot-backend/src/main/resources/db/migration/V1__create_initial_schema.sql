-- ═══════════════════════════════════════════════════════════════════════════
-- SICOT — V1: Esquema inicial (núcleo)
-- Sistema Inteligente para la Gestión y Acompañamiento de Contratos
-- Centro Tecnológico del Mobiliario (SENA - Regional Antioquia)
-- ═══════════════════════════════════════════════════════════════════════════

-- ── Usuarios ────────────────────────────────────────────────────────────────
CREATE TABLE usuarios (
    id                 BIGSERIAL PRIMARY KEY,
    nombre             VARCHAR(150)  NOT NULL,
    email              VARCHAR(150)  NOT NULL UNIQUE,
    password           VARCHAR(100)  NOT NULL,
    rol                VARCHAR(20)   NOT NULL,
    activo             BOOLEAN       NOT NULL DEFAULT TRUE,
    fecha_creacion     TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    fecha_actualizacion TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT ck_usuarios_rol CHECK (rol IN ('ADMINISTRADOR', 'GESTION', 'SUPERVISOR'))
);

-- ── Contratos ───────────────────────────────────────────────────────────────
CREATE TABLE contratos (
    id                  BIGSERIAL PRIMARY KEY,
    numero_contrato     VARCHAR(50)  NOT NULL UNIQUE,
    objeto              TEXT         NOT NULL,
    valor               NUMERIC(18,2) NOT NULL,
    fecha_inicio        DATE,
    fecha_fin           DATE,
    estado              VARCHAR(20)  NOT NULL DEFAULT 'BORRADOR',
    supervisor_id       BIGINT REFERENCES usuarios (id),
    fecha_creacion      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    fecha_actualizacion TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT ck_contratos_estado CHECK (estado IN ('BORRADOR', 'ACTIVO', 'SUSPENDIDO', 'FINALIZADO', 'CANCELADO'))
);

CREATE INDEX idx_contratos_supervisor ON contratos (supervisor_id);
CREATE INDEX idx_contratos_estado ON contratos (estado);

-- ── Etapas (pertenecen a un contrato) ───────────────────────────────────────
CREATE TABLE etapas (
    id            BIGSERIAL PRIMARY KEY,
    contrato_id   BIGINT NOT NULL REFERENCES contratos (id) ON DELETE CASCADE,
    nombre        VARCHAR(150) NOT NULL,
    numero        INT NOT NULL,
    estado        VARCHAR(20) NOT NULL DEFAULT 'PENDIENTE',
    porcentaje    INT NOT NULL DEFAULT 0,
    CONSTRAINT ck_etapas_estado CHECK (estado IN ('PENDIENTE', 'EN_CURSO', 'COMPLETADA')),
    CONSTRAINT ck_etapas_porcentaje CHECK (porcentaje BETWEEN 0 AND 100),
    CONSTRAINT uq_etapas_contrato_numero UNIQUE (contrato_id, numero)
);

CREATE INDEX idx_etapas_contrato ON etapas (contrato_id);

-- ── Subetapas (pertenecen a una etapa) ──────────────────────────────────────
CREATE TABLE subetapas (
    id           BIGSERIAL PRIMARY KEY,
    etapa_id     BIGINT NOT NULL REFERENCES etapas (id) ON DELETE CASCADE,
    codigo       VARCHAR(20) NOT NULL,
    nombre       VARCHAR(255) NOT NULL,
    descripcion  TEXT,
    estado       VARCHAR(20) NOT NULL DEFAULT 'PENDIENTE',
    responsable  VARCHAR(150) NOT NULL,
    CONSTRAINT ck_subetapas_estado CHECK (estado IN ('PENDIENTE', 'EN_CURSO', 'COMPLETADA')),
    CONSTRAINT uq_subetapas_etapa_codigo UNIQUE (etapa_id, codigo)
);

CREATE INDEX idx_subetapas_etapa ON subetapas (etapa_id);

-- ── Documentos (relacionados con contrato y opcionalmente con subetapa) ─────
CREATE TABLE documentos (
    id                  BIGSERIAL PRIMARY KEY,
    contrato_id         BIGINT NOT NULL REFERENCES contratos (id) ON DELETE CASCADE,
    subetapa_id         BIGINT REFERENCES subetapas (id) ON DELETE SET NULL,
    nombre              VARCHAR(255) NOT NULL,
    tipo                VARCHAR(20) NOT NULL,
    ruta_archivo        VARCHAR(500),
    estado              VARCHAR(20) NOT NULL DEFAULT 'PENDIENTE',
    fecha_subida        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    fecha_actualizacion TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT ck_documentos_tipo CHECK (tipo IN ('PDF', 'DOCX', 'XLSX', 'IMAGEN', 'OTRO')),
    CONSTRAINT ck_documentos_estado CHECK (estado IN ('PENDIENTE', 'APROBADO', 'RECHAZADO'))
);

CREATE INDEX idx_documentos_contrato ON documentos (contrato_id);
CREATE INDEX idx_documentos_subetapa ON documentos (subetapa_id);

-- ── Alertas ─────────────────────────────────────────────────────────────────
CREATE TABLE alertas (
    id             BIGSERIAL PRIMARY KEY,
    contrato_id    BIGINT REFERENCES contratos (id) ON DELETE CASCADE,
    tipo           VARCHAR(30) NOT NULL,
    prioridad      VARCHAR(10) NOT NULL,
    mensaje        TEXT NOT NULL,
    leida          BOOLEAN NOT NULL DEFAULT FALSE,
    fecha_creacion TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT ck_alertas_tipo CHECK (tipo IN ('VENCIMIENTO', 'DOCUMENTO', 'FACTURA', 'FIRMA', 'IA', 'SECOP', 'RECORDATORIO', 'SOLICITUD', 'RECHAZADO', 'CRONOGRAMA')),
    CONSTRAINT ck_alertas_prioridad CHECK (prioridad IN ('ALTA', 'MEDIA', 'BAJA'))
);

CREATE INDEX idx_alertas_contrato ON alertas (contrato_id);

-- ── Registros / Auditoría ───────────────────────────────────────────────────
CREATE TABLE registros (
    id          BIGSERIAL PRIMARY KEY,
    contrato_id BIGINT REFERENCES contratos (id) ON DELETE CASCADE,
    usuario_id  BIGINT REFERENCES usuarios (id) ON DELETE SET NULL,
    accion      VARCHAR(100) NOT NULL,
    descripcion TEXT,
    fecha       TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_registros_contrato ON registros (contrato_id);
CREATE INDEX idx_registros_usuario ON registros (usuario_id);
