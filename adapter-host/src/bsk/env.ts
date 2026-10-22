/**
 * Environment variable names shared by the bsk endpoint resolver and the bsk
 * WebSocket client (spec 021, P0-3).
 *
 * `OPENCLI_RELAY_URL` / `OPENCLI_RELAY_SECRET` are the *legacy* Cute relay
 * variables: the relay uses the former as an **HTTP** base URL. The bsk
 * backend therefore has its own gateway variable (`OPENCLI_BSK_RELAY_URL`) and
 * only reuses the legacy URL when it points at a WebSocket gateway.
 */

/** Preferred bsk gateway variable (WebSocket URL). */
export const BSK_RELAY_URL_ENV = 'OPENCLI_BSK_RELAY_URL';

/** Legacy Cute relay variable (HTTP base URL; WebSocket only as fallback). */
export const LEGACY_RELAY_URL_ENV = 'OPENCLI_RELAY_URL';

/** Browser instance id used to route to one extension. */
export const RELAY_BROWSER_ID_ENV = 'OPENCLI_RELAY_BROWSER_ID';

/** Visitor (user) id sent as the `user_id` query parameter. */
export const BSK_VISITOR_ID_ENV = 'OPENCLI_BSK_VISITOR_ID';

/** Shared secret sent as the `X-Adapter-Secret` header. */
export const RELAY_SECRET_ENV = 'OPENCLI_RELAY_SECRET';

/** Header carrying the shared secret. */
export const ADAPTER_SECRET_HEADER = 'X-Adapter-Secret';
