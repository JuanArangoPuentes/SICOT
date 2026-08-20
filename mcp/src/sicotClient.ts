// Cliente delgado contra la API REST real del backend. No reimplementa
// ninguna regla de negocio: cada función es un llamado 1:1 a un endpoint
// existente. La autenticación se hace una vez (o al recibir 401) con las
// credenciales configuradas por variables de entorno.

const API_URL = process.env.SICOT_API_URL ?? "http://localhost:8080";
const EMAIL = process.env.SICOT_EMAIL;
const PASSWORD = process.env.SICOT_PASSWORD;

if (!EMAIL || !PASSWORD) {
  throw new Error(
    "Faltan SICOT_EMAIL / SICOT_PASSWORD en el entorno. El servidor MCP actúa " +
      "como esa cuenta de SICOT, así que sus permisos (ADMINISTRADOR, GESTION o " +
      "SUPERVISOR) determinan qué herramientas funcionan.",
  );
}

let token: string | null = null;

async function login(): Promise<string> {
  const res = await fetch(`${API_URL}/api/auth/login`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ email: EMAIL, password: PASSWORD }),
  });
  if (!res.ok) {
    throw new Error(`No se pudo autenticar contra SICOT (${res.status}): ${await res.text()}`);
  }
  const data = (await res.json()) as { token: string; rol: string; nombre: string };
  token = data.token;
  return token;
}

export class SicotApiError extends Error {
  constructor(
    public status: number,
    message: string,
  ) {
    super(message);
    this.name = "SicotApiError";
  }
}

interface RequestOptions {
  method?: "GET" | "POST" | "PUT" | "PATCH" | "DELETE";
  query?: Record<string, string | number | boolean | undefined>;
  body?: unknown;
}

/** Llama a un endpoint de SICOT, autenticando primero si hace falta y reintentando una vez si el token expiró. */
export async function sicotFetch<T>(path: string, options: RequestOptions = {}, retried = false): Promise<T> {
  if (!token) await login();

  const url = new URL(path, API_URL);
  for (const [key, value] of Object.entries(options.query ?? {})) {
    if (value !== undefined) url.searchParams.set(key, String(value));
  }

  const res = await fetch(url, {
    method: options.method ?? "GET",
    headers: {
      Authorization: `Bearer ${token}`,
      ...(options.body ? { "Content-Type": "application/json" } : {}),
    },
    body: options.body ? JSON.stringify(options.body) : undefined,
  });

  if (res.status === 401 && !retried) {
    token = null;
    return sicotFetch<T>(path, options, true);
  }

  if (!res.ok) {
    const detail = await res.text();
    throw new SicotApiError(res.status, `SICOT respondió ${res.status} en ${path}: ${detail}`);
  }

  if (res.status === 204) return undefined as T;
  const text = await res.text();
  return (text ? JSON.parse(text) : undefined) as T;
}
