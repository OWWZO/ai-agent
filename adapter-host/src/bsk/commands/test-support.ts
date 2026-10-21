/**
 * Test support for the bsk command layer: a recording {@link BskCaller}.
 * Not exported from `index.ts` — it exists only for `*.test.ts` files.
 */

import type { BskCallOptions } from '../client.js';
import type { Method } from '../methods.js';
import type { BskCaller } from './types.js';

export interface RecordedCall {
  method: Method;
  params: unknown;
  options?: BskCallOptions;
}

type Reply = unknown | Error | ((params: unknown) => unknown | Promise<unknown>);

export class RecordingCaller implements BskCaller {
  readonly calls: RecordedCall[] = [];
  readonly cancels: string[] = [];
  private readonly replies = new Map<string, Reply[]>();

  /** Queue a reply for the next call of `method` (queue repeats the last one). */
  reply(method: Method, value: Reply): this {
    const queue = this.replies.get(method) ?? [];
    queue.push(value);
    this.replies.set(method, queue);
    return this;
  }

  callsOf(method: Method): RecordedCall[] {
    return this.calls.filter((call) => call.method === method);
  }

  last(method: Method): RecordedCall {
    const calls = this.callsOf(method);
    if (calls.length === 0) throw new Error(`no recorded call for ${method}`);
    return calls[calls.length - 1];
  }

  async call<T = unknown>(
    method: Method,
    params?: unknown,
    options?: BskCallOptions,
  ): Promise<T> {
    this.calls.push({ method, params, options });
    const queue = this.replies.get(method);
    const reply = queue && queue.length > 1 ? queue.shift() : queue?.[0];
    if (reply instanceof Error) throw reply;
    const value = typeof reply === 'function' ? await (reply as (p: unknown) => unknown)(params) : reply;
    return value as T;
  }

  async cancel(rpcId: string): Promise<unknown> {
    this.cancels.push(rpcId);
    return { cancelled: true };
  }
}
