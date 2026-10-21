/**
 * Browser backend selection (spec 021, Phase 5).
 *
 * Two backends coexist this round:
 *   - `opencli` (default) — the vendored OpenCLI `Page` wrapped by OpenCliPage
 *   - `bsk`               — the ReactorPage / BskClient path
 *
 * Leaving `BROWSER_BACKEND` unset must behave exactly as before 021.
 */

export type BrowserBackend = 'opencli' | 'bsk';

const KNOWN_BACKENDS: readonly BrowserBackend[] = ['opencli', 'bsk'];

/**
 * Resolve the active backend from `BROWSER_BACKEND` (or an explicit value).
 * Defaults to `opencli`; throws on an unrecognised value so a typo never
 * silently falls back to the wrong backend.
 */
export function resolveBackend(raw: string | undefined = process.env.BROWSER_BACKEND): BrowserBackend {
  if (raw === undefined || raw === '') return 'opencli';
  if ((KNOWN_BACKENDS as readonly string[]).includes(raw)) return raw as BrowserBackend;
  throw new Error(
    `Unknown BROWSER_BACKEND "${raw}". Expected one of: ${KNOWN_BACKENDS.join(', ')}.`,
  );
}

export { OpenCliPage } from './opencli-page.js';
export { ReactorPage, NotImplementedError } from './reactor-page.js';
export type { BskSessionHandle } from './reactor-page.js';
export type { IPage } from './ipage.js';
