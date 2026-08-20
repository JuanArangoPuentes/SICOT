// Prueba manual: arranca el servidor MCP como subproceso, lista las
// herramientas y llama un par de ellas contra el backend real. No forma
// parte del build (dist/); se corre con `npx tsx src/smoke-test.ts` o
// compilando aparte.

import { Client } from "@modelcontextprotocol/sdk/client/index.js";
import { StdioClientTransport } from "@modelcontextprotocol/sdk/client/stdio.js";

const transport = new StdioClientTransport({
  command: "node",
  args: ["dist/index.js"],
  env: {
    SICOT_API_URL: process.env.SICOT_API_URL ?? "http://localhost:8080",
    SICOT_EMAIL: process.env.SICOT_EMAIL ?? "",
    SICOT_PASSWORD: process.env.SICOT_PASSWORD ?? "",
  },
});

const client = new Client({ name: "sicot-mcp-smoke-test", version: "0.1.0" });
await client.connect(transport);

const tools = await client.listTools();
console.log(`Herramientas registradas: ${tools.tools.length}`);
for (const t of tools.tools) console.log(` - ${t.name}`);

const usuarios = await client.callTool({ name: "listar_usuarios", arguments: {} });
console.log("\nlistar_usuarios ->", JSON.stringify(usuarios).slice(0, 300));

const contratos = await client.callTool({ name: "listar_contratos", arguments: {} });
console.log("\nlistar_contratos ->", JSON.stringify(contratos).slice(0, 300));

const formatos = await client.callTool({ name: "listar_formatos", arguments: {} });
console.log("\nlistar_formatos ->", JSON.stringify(formatos).slice(0, 300));

await client.close();
process.exit(0);
