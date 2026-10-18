// The path the app is served under, set at build time. Unset, the app runs at the root.
// Vite fills import.meta.env; the openui CLI bundles this file as CJS (where import.meta is
// empty), so fall back to "/" there — the generated artifacts never depend on the base path.
const BASE_URL = import.meta.env?.BASE_URL ?? "/";

export const BASE_PATH = BASE_URL.replace(/\/+$/, "");

export const withBase = (path: string) => `${BASE_PATH}${path}`;
