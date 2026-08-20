// Servidor MCP de SICOT — envoltorio delgado sobre la API real del
// backend. Cada herramienta es un llamado 1:1 a un endpoint existente;
// ninguna regla de negocio se reimplementa aquí (la fuente de verdad sigue
// siendo el backend). Curado a un subconjunto de alto valor en vez de
// exponer las ~25 rutas mecánicamente — ver README.md para el criterio.

import { McpServer } from "@modelcontextprotocol/sdk/server/mcp.js";
import { StdioServerTransport } from "@modelcontextprotocol/sdk/server/stdio.js";
import { z } from "zod";
import { sicotFetch } from "./sicotClient.js";

const server = new McpServer({ name: "sicot", version: "0.1.0" });

const json = (data: unknown) => ({ content: [{ type: "text" as const, text: JSON.stringify(data, null, 2) }] });

const ESTADO_CONTRATO = ["BORRADOR", "ACTIVO", "SUSPENDIDO", "FINALIZADO", "CANCELADO"] as const;
const ESTADO_SUBETAPA = ["PENDIENTE", "EN_CURSO", "COMPLETADA"] as const;

// ─── Contratos ──────────────────────────────────────────────────────────────

server.registerTool(
  "listar_contratos",
  {
    description: "Lista contratos de SICOT, opcionalmente filtrados por supervisor o estado.",
    inputSchema: {
      supervisorId: z.number().int().optional().describe("Id del usuario supervisor"),
      estado: z.enum(ESTADO_CONTRATO).optional(),
    },
  },
  async ({ supervisorId, estado }) => json(await sicotFetch("/api/contratos", { query: { supervisorId, estado } })),
);

server.registerTool(
  "obtener_contrato",
  { description: "Obtiene el detalle de un contrato por id.", inputSchema: { id: z.number().int() } },
  async ({ id }) => json(await sicotFetch(`/api/contratos/${id}`)),
);

server.registerTool(
  "crear_contrato",
  {
    description:
      "Crea un contrato nuevo en estado BORRADOR. Requiere rol GESTION o ADMINISTRADOR en la cuenta configurada. " +
      "No inventes número de contrato, objeto ni valor — pídeselos al usuario si no los tienes.",
    inputSchema: {
      numeroContrato: z.string(),
      objeto: z.string(),
      valor: z.number().positive(),
      fechaInicio: z.string().date().nullable().optional().describe("YYYY-MM-DD"),
      fechaFin: z.string().date().nullable().optional().describe("YYYY-MM-DD"),
      supervisorId: z.number().int().nullable().optional(),
    },
  },
  async (args) => json(await sicotFetch("/api/contratos", { method: "POST", body: args })),
);

server.registerTool(
  "asignar_supervisor_contrato",
  { description: "Asigna un supervisor a un contrato existente.", inputSchema: { contratoId: z.number().int(), supervisorId: z.number().int() } },
  async ({ contratoId, supervisorId }) =>
    json(await sicotFetch(`/api/contratos/${contratoId}/supervisor`, { method: "PATCH", body: { supervisorId } })),
);

// ─── Etapas / subetapas ─────────────────────────────────────────────────────

server.registerTool(
  "listar_etapas_contrato",
  { description: "Lista las etapas y subetapas (GCCON-P-010) de un contrato.", inputSchema: { contratoId: z.number().int() } },
  async ({ contratoId }) => json(await sicotFetch(`/api/contratos/${contratoId}/etapas`)),
);

server.registerTool(
  "cambiar_estado_subetapa",
  {
    description: "Cambia el estado de una subetapa. El backend recalcula automáticamente el % y estado de la etapa.",
    inputSchema: { subetapaId: z.number().int(), estado: z.enum(ESTADO_SUBETAPA) },
  },
  async ({ subetapaId, estado }) =>
    json(await sicotFetch(`/api/subetapas/${subetapaId}/estado`, { method: "PATCH", body: { estado } })),
);

// ─── Alertas ─────────────────────────────────────────────────────────────────

server.registerTool(
  "listar_alertas",
  {
    description: "Lista alertas — de un contrato si se da contratoId, o todas si no.",
    inputSchema: { contratoId: z.number().int().optional() },
  },
  async ({ contratoId }) =>
    json(await sicotFetch(contratoId ? `/api/contratos/${contratoId}/alertas` : "/api/alertas")),
);

server.registerTool(
  "marcar_alerta_leida",
  { description: "Marca una alerta como leída.", inputSchema: { id: z.number().int() } },
  async ({ id }) => json(await sicotFetch(`/api/alertas/${id}/leida`, { method: "PATCH" })),
);

// ─── Formatos documentales ──────────────────────────────────────────────────

server.registerTool(
  "listar_formatos",
  { description: "Lista el catálogo de formatos documentales oficiales cargados por el Administrador.", inputSchema: {} },
  async () => json(await sicotFetch("/api/formatos")),
);

// ─── Usuarios ────────────────────────────────────────────────────────────────

server.registerTool(
  "listar_usuarios",
  { description: "Lista los usuarios de SICOT. Requiere rol ADMINISTRADOR o GESTION.", inputSchema: {} },
  async () => json(await sicotFetch("/api/usuarios")),
);

// ─── Registros / auditoría ──────────────────────────────────────────────────

server.registerTool(
  "listar_registros",
  {
    description: "Lista registros de auditoría — de un contrato si se da contratoId, o todos (solo ADMINISTRADOR) si no.",
    inputSchema: { contratoId: z.number().int().optional() },
  },
  async ({ contratoId }) =>
    json(await sicotFetch(contratoId ? `/api/contratos/${contratoId}/registros` : "/api/registros")),
);

const transport = new StdioServerTransport();
await server.connect(transport);
