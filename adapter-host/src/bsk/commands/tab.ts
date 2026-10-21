/**
 * Tab management.
 * Mirrors `crates/bsk-cli/src/cli/tab.rs`.
 */

import { METHODS } from '../methods.js';
import type {
  TabBorrowParams,
  TabBorrowResult,
  TabCloseParams,
  TabCloseResult,
  TabCreateParams,
  TabCreateResult,
  TabListParams,
  TabListResult,
  TabReturnParams,
  TabReturnResult,
  TabSelectParams,
  TabSelectResult,
} from '../protocol.js';
import type { BskCaller, TabBorrowArgs, TabCreateArgs, TabIdArgs, TabListArgs } from './types.js';

const TOOL_TIMEOUT_MS = 35_000;
const DEFAULT_BORROW_CONFIRMATION_MS = 60_000;

export async function tabList(client: BskCaller, args: TabListArgs): Promise<TabListResult> {
  const params: TabListParams = {
    session_id: args.sessionId,
    scope: args.scope ?? 'all',
  };
  return client.call<TabListResult>(METHODS.toolTabList, params, { timeoutMs: TOOL_TIMEOUT_MS });
}

export async function tabCreate(client: BskCaller, args: TabCreateArgs): Promise<TabCreateResult> {
  const params: TabCreateParams = {
    session_id: args.sessionId,
    ...(args.url === undefined ? {} : { url: args.url }),
    // Rust's `--no-active` maps to an explicit `active: false`; the default
    // stays absent so the extension applies its own true default.
    ...(args.active === undefined ? {} : { active: args.active }),
    ...(args.index === undefined ? {} : { index: args.index }),
  };
  return client.call<TabCreateResult>(METHODS.toolTabCreate, params, { timeoutMs: TOOL_TIMEOUT_MS });
}

export async function tabClose(client: BskCaller, args: TabIdArgs): Promise<TabCloseResult> {
  const params: TabCloseParams = { session_id: args.sessionId, tab_id: args.tabId };
  return client.call<TabCloseResult>(METHODS.toolTabClose, params, { timeoutMs: TOOL_TIMEOUT_MS });
}

export async function tabSelect(client: BskCaller, args: TabIdArgs): Promise<TabSelectResult> {
  const params: TabSelectParams = { session_id: args.sessionId, tab_id: args.tabId };
  return client.call<TabSelectResult>(METHODS.toolTabSelect, params, { timeoutMs: TOOL_TIMEOUT_MS });
}

export async function tabBorrow(client: BskCaller, args: TabBorrowArgs): Promise<TabBorrowResult> {
  const params: TabBorrowParams = {
    session_id: args.sessionId,
    tab_id: args.tabId,
    ...(args.confirmationTimeoutMs === undefined
      ? {}
      : { confirmation_timeout_ms: args.confirmationTimeoutMs }),
    ...(args.confirm === undefined ? {} : { confirm: args.confirm }),
  };
  const confirmationMs = args.confirmationTimeoutMs ?? DEFAULT_BORROW_CONFIRMATION_MS;
  // Confirmation wait + cancel cleanup (2s) + transport slack, matching the
  // Rust CLI's budget for the borrow round trip.
  return client.call<TabBorrowResult>(METHODS.toolTabBorrow, params, {
    timeoutMs: confirmationMs + 15_000 + 2_000 + 5_000,
  });
}

export async function tabReturn(client: BskCaller, args: TabIdArgs): Promise<TabReturnResult> {
  const params: TabReturnParams = { session_id: args.sessionId, tab_id: args.tabId };
  return client.call<TabReturnResult>(METHODS.toolTabReturn, params, { timeoutMs: TOOL_TIMEOUT_MS });
}
