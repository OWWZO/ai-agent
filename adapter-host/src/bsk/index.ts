/**
 * Barrel for the bsk client + command layer.
 *
 * `src/page/bsk-browser-factory.ts` lazy-imports this module and only requires
 * `BskClient` + `resolveEndpoint`; everything else is convenience for library
 * callers (`src/bsk-cli.ts` included).
 */

export * from './client.js';
export * from './commands/index.js';
export * from './effects.js';
export * from './endpoint.js';
export * from './errors.js';
export * from './handshake.js';
export * from './methods.js';
export * from './protocol.js';
