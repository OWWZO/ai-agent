/** Tests for the standalone `bsk` Commander adapter. */

import { PassThrough } from 'node:stream';
import { afterEach, describe, expect, it, vi } from 'vitest';

import { BskRpcError } from './bsk/errors.js';
import { METHODS } from './bsk/methods.js';
import type { Method } from './bsk/methods.js';
import { buildBskCli, hoistRootOptions, runBskCli, SIGINT_GRACE_MS, type BskCliClient } from './bsk-cli.js';

const EXPECTED_COMMANDS = [
  'navigate',
  'navigate-back',
  'navigate-forward',
  'reload',
  'click',
  'hover',
  'wheel',
  'scroll-to',
  'focus',
  'blur',
  'fill',
  'press',
  'select',
  'upload',
  'download',
  'snapshot',
  'observe',
  'get-html',
  'evaluate',
  'screenshot',
  'console',
  'network',
  'cookies',
  'frames',
  'cdp',
  'wait-for-navigation',
  'wait-ms',
  'request-help',
  'session',
  'tab',
  'browsers',
  'emulate',
  'window',
] as const;

interface RecordedCall {
  method: string;
  params: unknown;
  options?: { timeoutMs?: number; signal?: AbortSignal };
}

class FakeClient implements BskCliClient {
  readonly calls: RecordedCall[] = [];
  readonly cancels: string[] = [];
  closeCount = 0;
  reply: unknown = { tab_id: 3, reached: 'load', url: 'https://example.test/' };
  onCall?: (method: string, params: unknown, options?: RecordedCall['options']) => Promise<unknown> | unknown;
  /** Emulate `BskClient`: aborting an in-flight call sends `cancel`. */
  emulateAbortCancel = false;
  private counter = 0;

  async call<T = unknown>(
    method: Method,
    params?: unknown,
    options?: { timeoutMs?: number; signal?: AbortSignal },
  ): Promise<T> {
    this.calls.push({ method, params, options });
    const id = `fake-${++this.counter}`;
    if (this.emulateAbortCancel && options?.signal) {
      const signal = options.signal;
      return new Promise<T>((_resolve, reject) => {
        const onAbort = (): void => {
          void this.cancel(id);
          reject(new BskRpcError({ code: 'cancelled', message: 'aborted' }, id));
        };
        if (signal.aborted) onAbort();
        else signal.addEventListener('abort', onAbort, { once: true });
      });
    }
    const value = this.onCall ? await this.onCall(method, params, options) : this.reply;
    return value as T;
  }

  async cancel(rpcId: string): Promise<{ cancelled: boolean }> {
    this.cancels.push(rpcId);
    return { cancelled: true };
  }

  async close(): Promise<void> {
    this.closeCount += 1;
  }
}

function findCommand(program: ReturnType<typeof buildBskCli>, name: string) {
  const command = program.commands.find((candidate) => candidate.name() === name);
  if (!command) throw new Error(`missing command ${name}`);
  return command;
}

async function parse(
  program: ReturnType<typeof buildBskCli>,
  argv: string[],
): Promise<void> {
  await program.parseAsync(argv, { from: 'user' });
}

function makeCapture() {
  const stdout: string[] = [];
  const stderr: string[] = [];
  const out = new PassThrough();
  const err = new PassThrough();
  out.on('data', (chunk: Buffer) => stdout.push(chunk.toString('utf8')));
  err.on('data', (chunk: Buffer) => stderr.push(chunk.toString('utf8')));
  return {
    stdout,
    stderr,
    out,
    err,
    text: () => stdout.join(''),
    errorText: () => stderr.join(''),
  };
}

describe('buildBskCli', () => {
  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('registers exactly the expected top-level command set', () => {
    const program = buildBskCli({ client: new FakeClient() });
    expect(program.commands.map((command) => command.name()).sort()).toEqual(
      [...EXPECTED_COMMANDS].sort(),
    );
    expect(findCommand(program, 'session').commands.map((command) => command.name()).sort()).toEqual([
      'list',
      'start',
      'stop',
    ]);
    expect(findCommand(program, 'tab').commands.map((command) => command.name()).sort()).toEqual([
      'borrow',
      'close',
      'create',
      'list',
      'return',
      'select',
    ]);
  });

  it('passes the exact command-layer result through in --json mode', async () => {
    const result = Object.freeze({
      tab_id: 3,
      url: 'https://example.test/',
      reached: 'load',
      nested: Object.freeze({ exact: true }),
    });
    const commandSpy = vi.fn(
      async (_client: unknown, _args: unknown): Promise<typeof result> => result,
    );
    const client = new FakeClient();
    const capture = makeCapture();
    const program = buildBskCli({
      client,
      commandLayer: { navigate: commandSpy },
      stdout: capture.out,
      stderr: capture.err,
    });

    await parse(program, [
      '--session',
      's1',
      '--tab-id',
      '3',
      '--json',
      'navigate',
      'https://example.test/',
    ]);

    expect(commandSpy).toHaveBeenCalledTimes(1);
    expect(commandSpy).toHaveBeenCalledWith(
      expect.objectContaining({
        call: expect.any(Function),
        cancel: expect.any(Function),
      }),
      {
        sessionId: 's1',
        url: 'https://example.test/',
        tabId: 3,
      },
    );
    expect(capture.text()).toBe(`${JSON.stringify(result, null, 2)}\n`);
    expect(JSON.parse(capture.text())).toEqual(result);
    expect(client.calls).toHaveLength(0);
  });

  it('without --json still reaches the command layer, not a local reimplementation', async () => {
    const result = { tab_id: 9, url: 'https://example.test/', reached: 'commit' };
    const commandSpy = vi.fn(
      async (_client: unknown, _args: unknown): Promise<typeof result> => result,
    );
    const capture = makeCapture();
    const program = buildBskCli({
      client: new FakeClient(),
      commandLayer: { navigate: commandSpy },
      stdout: capture.out,
      stderr: capture.err,
    });

    await parse(program, ['--session', 's9', 'navigate', 'https://example.test/path']);

    expect(commandSpy).toHaveBeenCalledTimes(1);
    expect((commandSpy.mock.calls[0] as unknown[])[1]).toEqual({
      sessionId: 's9',
      url: 'https://example.test/path',
      tabId: undefined,
    });
    // 输出带上实际使用的 session_id，让 agent 能核对操作的是哪个会话。
    expect(capture.text()).toBe('navigate ok session_id=s9 tab_id=9 url=https://example.test/ reached=commit\n');
  });

  it('dispatches through the shared command library for human output too', async () => {
    const client = new FakeClient();
    const capture = makeCapture();
    const program = buildBskCli({ client, stdout: capture.out, stderr: capture.err });

    await parse(program, ['--session', 's1', '--tab-id', '8', 'navigate', 'https://example.test/']);

    expect(client.calls).toEqual([
      expect.objectContaining({
        method: METHODS.toolNavigate,
        params: {
          session_id: 's1',
          url: 'https://example.test/',
          tab_id: 8,
          wait_until: 'load',
          timeout_ms: 30_000,
        },
      }),
    ]);
    expect(capture.text()).toBe('navigate ok session_id=s1 tab_id=3 url=https://example.test/ reached=load\n');
  });

  it('uses --relay / --browser to create a client only when the command executes', async () => {
    const client = new FakeClient();
    const factory = vi.fn(async () => client);
    const capture = makeCapture();
    const program = buildBskCli({ clientFactory: factory, stdout: capture.out, stderr: capture.err });

    expect(factory).not.toHaveBeenCalled();
    await parse(program, [
      '--relay',
      'wss://relay.example/ws',
      '--browser',
      'browser-1',
      'browsers',
    ]);

    expect(factory).toHaveBeenCalledWith({ url: 'wss://relay.example/ws', browserId: 'browser-1' });
    expect(client.calls[0]?.method).toBe(METHODS.browserList);
  });

  it.each(['upload', 'download'] as const)(
    'registers %s but clearly rejects it before opening the relay',
    async (name) => {
      const factory = vi.fn(async () => new FakeClient());
      const capture = makeCapture();
      const program = buildBskCli({ clientFactory: factory, stdout: capture.out, stderr: capture.err });

      await expect(parse(program, [name])).rejects.toThrow(/unsupported over remote relay/i);
      expect(factory).not.toHaveBeenCalled();
    },
  );

  it('wires session and tab subcommands to the command layer functions', async () => {
    const sessionList = vi.fn(async (_client: unknown) => ({ sessions: [] }));
    const tabList = vi.fn(async (_client: unknown, _args: unknown) => ({ tabs: [] }));
    const capture = makeCapture();
    const program = buildBskCli({
      client: new FakeClient(),
      commandLayer: { sessionList, tabList },
      stdout: capture.out,
      stderr: capture.err,
    });

    await parse(program, ['session', 'list']);
    await parse(program, ['--session', 's1', 'tab', 'list']);

    expect(sessionList).toHaveBeenCalledTimes(1);
    expect(tabList).toHaveBeenCalledTimes(1);
    expect((tabList.mock.calls[0] as unknown[])[1]).toEqual({ sessionId: 's1', scope: 'all' });
  });

  it('passes parsed command options through to the library call', async () => {
    const client = new FakeClient();
    const capture = makeCapture();
    const program = buildBskCli({ client, stdout: capture.out, stderr: capture.err });

    await parse(program, [
      '--session',
      's1',
      'click',
      '@e3',
      '--button',
      'right',
      '--click-count',
      '2',
      '--modifiers',
      'ctrl,shift',
    ]);

    expect(client.calls[0]).toMatchObject({
      method: METHODS.toolClick,
      params: {
        session_id: 's1',
        ref: '@e3',
        button: 'right',
        click_count: 2,
        modifiers: ['ctrl', 'shift'],
      },
    });
  });

  it('aborts an in-flight library call and exits inside the two-second budget', async () => {
    const client = new FakeClient();
    client.emulateAbortCancel = true;
    const capture = makeCapture();
    const program = buildBskCli({ client, stdout: capture.out, stderr: capture.err });

    const started = Date.now();
    const running = parse(program, ['--session', 's1', 'snapshot']);
    await vi.waitFor(() => expect(client.calls).toHaveLength(1));
    process.emit('SIGINT');

    await expect(running).rejects.toMatchObject({ code: 'cancelled' });
    expect(client.calls[0]?.options?.signal?.aborted).toBe(true);
    expect(Date.now() - started).toBeLessThan(2_000);
    expect(client.cancels).toEqual([expect.any(String)]);
    expect(SIGINT_GRACE_MS).toBeLessThan(2_000);
  });

  it('validates relay URLs before the client factory is invoked', async () => {
    const factory = vi.fn(async () => new FakeClient());
    const program = buildBskCli({ clientFactory: factory });

    await expect(parse(program, ['--relay', 'https://relay.example', 'browsers'])).rejects.toThrow(
      /must use ws:\/\/ or wss:\/\//,
    );
    expect(factory).not.toHaveBeenCalled();
  });
});

describe('bsk session handling', () => {
  it('hoists root options that the model wrote after the subcommand', () => {
    expect(hoistRootOptions(['click', '@e3', '--session', 's1', '--json'])).toEqual([
      '--session',
      's1',
      '--json',
      'click',
      '@e3',
    ]);
  });

  it('leaves --url alone because tab create owns that option', () => {
    expect(hoistRootOptions(['tab', 'create', '--url', 'https://example.test'])).toEqual([
      'tab',
      'create',
      '--url',
      'https://example.test',
    ]);
  });

  it('accepts a session written after the subcommand', async () => {
    const client = new FakeClient();
    const program = buildBskCli({ client });

    await parse(program, hoistRootOptions(['click', '@e3', '--session', 's1']));

    const call = client.calls.at(-1);
    expect(call?.method).toBe('tool.click');
    expect((call?.params as { session_id?: string }).session_id).toBe('s1');
  });

  it('does not recover from unrelated failures', async () => {
    const client = new FakeClient();
    client.onCall = async () => {
      throw new BskRpcError({ code: 'cdp_failed', message: 'no target' });
    };
    const program = buildBskCli({ client });

    await expect(parse(program, ['--session', 's1', 'click', '@e3'])).rejects.toMatchObject({
      code: 'cdp_failed',
    });
    expect(client.calls.map((call) => call.method)).toEqual(['tool.click']);
  });

  it('starts a session through the extension-level method and reports the session id', async () => {
    const client = new FakeClient();
    client.onCall = async () => ({ agent_window_id: 7, interaction: { mode: 'auto' } });
    const capture = makeCapture();
    const program = buildBskCli({ client, stdout: capture.out, stderr: capture.err });

    await parse(program, ['--session', 's1', '--json', 'session', 'start']);

    expect(client.calls.map((call) => call.method)).toEqual(['tool.session_start']);
    expect((client.calls[0]?.params as { session_id?: string }).session_id).toBe('s1');
    expect(JSON.parse(capture.text())).toMatchObject({ session_id: 's1', agent_window_id: 7 });
  });

  it('hoists --relay written after the subcommand', () => {
    expect(hoistRootOptions(['tab', 'list', '--relay', 'wss://relay.example/ws'])).toEqual([
      '--relay',
      'wss://relay.example/ws',
      'tab',
      'list',
    ]);
  });

  it('passes tab create --url to the subcommand instead of the root relay option', async () => {
    const client = new FakeClient();
    const program = buildBskCli({ client });

    await parse(program, ['--session', 's1', 'tab', 'create', '--url', 'https://example.test']);

    expect(client.calls.map((call) => call.method)).toEqual(['tool.tab_create']);
    expect(program.options.map((option) => option.long)).toContain('--relay');
    expect(program.options.map((option) => option.long)).not.toContain('--url');
    // 子命令自己也该有 --url，否则 --url 无处可去。
    const tabCreate = findCommand(program, 'tab').commands.find((c) => c.name() === 'create');
    expect(tabCreate?.options.map((option) => option.long)).toContain('--url');
    expect((client.calls[0]?.params as { url?: string }).url).toBe('https://example.test');
  });

  it('accepts a positional tab id for tab select and tab close', async () => {
    for (const [subcommand, method] of [
      ['select', 'tool.tab_select'],
      ['close', 'tool.tab_close'],
    ] as const) {
      const client = new FakeClient();
      const program = buildBskCli({ client });

      await parse(program, ['--session', 's1', 'tab', subcommand, '2038889396']);

      expect(client.calls.map((call) => call.method)).toEqual([method]);
      expect((client.calls[0]?.params as { tab_id?: number }).tab_id).toBe(2038889396);
    }
  });

  it('wires tab borrow and tab return to their methods', async () => {
    for (const [subcommand, method] of [
      ['borrow', 'tool.tab_borrow'],
      ['return', 'tool.tab_return'],
    ] as const) {
      const client = new FakeClient();
      const program = buildBskCli({ client });

      await parse(program, ['--session', 's1', 'tab', subcommand, '2038889954']);

      expect(client.calls.map((call) => call.method)).toEqual([method]);
      expect((client.calls[0]?.params as { tab_id?: number }).tab_id).toBe(2038889954);
    }
  });

  it('forwards --timeout to confirmation_timeout_ms and budgets the transport for it', async () => {
    const client = new FakeClient();
    const program = buildBskCli({ client });

    await parse(program, ['--session', 's1', 'tab', 'borrow', '7', '--timeout', '90s']);

    expect((client.calls[0]?.params as { confirmation_timeout_ms?: number }).confirmation_timeout_ms)
      .toBe(90_000);
    // 确认等待 + 移动标签的 15s + cancel 2s + 传输余量 5s。
    expect(client.calls[0]?.options?.timeoutMs).toBe(90_000 + 22_000);
  });

  it('warns that --no-confirm has no effect instead of silently ignoring it', async () => {
    const client = new FakeClient();
    const capture = makeCapture();
    const program = buildBskCli({ client, stdout: capture.out, stderr: capture.err });

    await parse(program, ['--session', 's1', 'tab', 'borrow', '7', '--no-confirm']);

    // 仍然照常发请求：这个开关是 legacy，扩展说了算。
    expect(client.calls.map((call) => call.method)).toEqual(['tool.tab_borrow']);
    expect(capture.errorText()).toContain('--no-confirm is deprecated');
  });

  it('rejects an unknown mouse button before any relay call', async () => {
    const client = new FakeClient();
    const program = buildBskCli({ client });

    await expect(parse(program, ['--session', 's1', 'click', '@e3', '--button', 'up'])).rejects.toThrow(
      /--button/,
    );
    expect(client.calls).toEqual([]);
  });

  it('treats an explicit start on an existing id as a no-op instead of restarting', async () => {
    const client = new FakeClient();
    client.onCall = async (method) => {
      if (method === 'tool.session_start') {
        // Java 侧按一次 run 注入固定 session id，重复 start 是常态。
        throw new BskRpcError({
          code: 'protocol_error',
          message: '[bh] session s1 already exists',
        });
      }
      return {};
    };
    const capture = makeCapture();
    const program = buildBskCli({ client, stdout: capture.out, stderr: capture.err });

    await parse(program, ['--session', 's1', '--json', 'session', 'start']);

    // 幂等：不 stop、不重建。session stop 会关掉用户正在看的 Agent Window，
    // 反复调用时会反复关开。
    expect(client.calls.map((call) => call.method)).toEqual(['tool.session_start']);
    expect(JSON.parse(capture.text())).toMatchObject({ session_id: 's1', reused: true });
  });

  it('generates a fresh session id when the agent did not pass --session', async () => {
    const client = new FakeClient();
    client.onCall = async () => ({ agent_window_id: 1 });
    const capture = makeCapture();

    // 模拟 Java 注入的默认会话：来自 env，不是命令行显式给的。
    const previous = process.env.OPENCLI_BSK_SESSION_ID;
    process.env.OPENCLI_BSK_SESSION_ID = 'java-injected';
    try {
      const program = buildBskCli({ client, stdout: capture.out, stderr: capture.err });
      await parse(program, ['--json', 'session', 'start']);
    } finally {
      if (previous === undefined) delete process.env.OPENCLI_BSK_SESSION_ID;
      else process.env.OPENCLI_BSK_SESSION_ID = previous;
    }

    // agent 没指定 → 新建一个，而不是挤进注入的那个会话。
    const sent = (client.calls[0]?.params as { session_id?: string }).session_id;
    expect(sent).toBeDefined();
    expect(sent).not.toBe('java-injected');
    expect(JSON.parse(capture.text()).session_id).toBe(sent);
  });

  it('creates the named session on first use, like OpenCLI leases', async () => {
    const client = new FakeClient();
    let attempts = 0;
    client.onCall = async (method) => {
      if (method === 'tool.click') {
        attempts += 1;
        // 第一次：会话还不存在。
        if (attempts === 1) throw new BskRpcError({ code: 'not_found', message: 'session 小红书 unknown' });
        return { ok: true };
      }
      return { agent_window_id: 7 };
    };
    const program = buildBskCli({ client });

    // agent 直接用名字操作，不需要先 session start。
    await parse(program, ['--session', '小红书', 'click', '@e3']);

    expect(client.calls.map((call) => call.method)).toEqual([
      'tool.click',
      'tool.session_start',
      'tool.click',
    ]);
    // 补建用的是 agent 给的那个名字，不是生成的新 id。
    expect((client.calls[1]?.params as { session_id?: string }).session_id).toBe('小红书');
  });

  it('requires --session on every tool command', async () => {
    const client = new FakeClient();
    const program = buildBskCli({ client });

    // 对齐 OpenCLI 的 <session> 位置参数：会话名是必需的身份。
    await expect(parse(program, ['click', '@e3'])).rejects.toThrow(/requires --session/);
    expect(client.calls).toEqual([]);
  });
});

describe('bsk browser primitives (extension-only methods)', () => {
  it('sends cookies scoped by --domain', async () => {
    const client = new FakeClient();
    const program = buildBskCli({ client });

    await parse(program, ['--session', 's1', 'cookies', '--domain', 'example.test']);

    expect(client.calls).toEqual([
      expect.objectContaining({
        method: 'tool.cookies',
        params: { session_id: 's1', domain: 'example.test' },
      }),
    ]);
  });

  it('sends frames with just the session', async () => {
    const client = new FakeClient();
    const program = buildBskCli({ client });

    await parse(program, ['--session', 's1', 'frames']);

    expect(client.calls).toEqual([
      expect.objectContaining({ method: 'tool.frames', params: { session_id: 's1' } }),
    ]);
  });

  it('parses --params as a JSON object for cdp', async () => {
    const client = new FakeClient();
    const program = buildBskCli({ client });

    await parse(program, [
      '--session',
      's1',
      'cdp',
      'Page.getLayoutMetrics',
      '--params',
      '{"a":1}',
    ]);

    expect(client.calls).toEqual([
      expect.objectContaining({
        method: 'tool.cdp',
        params: { session_id: 's1', method: 'Page.getLayoutMetrics', params: { a: 1 } },
      }),
    ]);
  });

  it('rejects --params that is not a JSON object', async () => {
    const client = new FakeClient();
    const program = buildBskCli({ client });

    await expect(
      parse(program, ['--session', 's1', 'cdp', 'DOM.getDocument', '--params', '[1,2]']),
    ).rejects.toThrow(/must be a JSON object/);
    expect(client.calls).toEqual([]);
  });

  it('recognises the extension-only methods as known', async () => {
    const { isMethod } = await import('./bsk/methods.js');
    expect(isMethod('tool.cookies')).toBe(true);
    expect(isMethod('tool.frames')).toBe(true);
    expect(isMethod('tool.cdp')).toBe(true);
  });
});

describe('bsk failure rendering', () => {
  async function runWithFailure(error: unknown) {
    const client = new FakeClient();
    client.onCall = async () => {
      throw error;
    };
    const capture = makeCapture();
    const code = await runBskCli(
      ['node', 'bsk', '--session', 's1', 'click', '@e3'],
      { client, stdout: capture.out, stderr: capture.err },
    );
    return { code, text: capture.errorText() };
  }

  it('keeps the original first line and appends hint + retry', async () => {
    const { BskRpcError } = await import('./bsk/errors.js');
    const { text } = await runWithFailure(
      new BskRpcError({ code: 'cdp_failed', message: 'Detached while handling command' }),
    );

    const lines = text.trimEnd().split('\n');
    // Nothing that parsed the old single-line format may break.
    expect(lines[0]).toBe('Error: cdp_failed: Detached while handling command');
    expect(lines[1]).toMatch(/^Hint: .+/);
    expect(lines[2]).toBe('Retry: unsafe_to_retry');
  });

  it('marks a stale ref as retry-after-fix', async () => {
    const { BskRpcError } = await import('./bsk/errors.js');
    const { text } = await runWithFailure(
      new BskRpcError({ code: 'not_found', message: 'ref @e3 unknown for tab 4 in session s1' }),
    );

    expect(text).toContain('Retry: retry_after_fix');
    expect(text).toMatch(/observe again/);
  });

  it('marks an attach failure as safe to retry', async () => {
    const { BskRpcError } = await import('./bsk/errors.js');
    const { text } = await runWithFailure(
      new BskRpcError({ code: 'cdp_failed', message: 'attach failed' }),
    );

    expect(text).toContain('Retry: safe_to_retry');
  });

  it('always emits all three lines, even for an unexpected error', async () => {
    const { text } = await runWithFailure(new Error('boom'));

    const lines = text.trimEnd().split('\n');
    expect(lines).toHaveLength(3);
    expect(lines[0]).toBe('Error: boom');
    expect(lines[1]).toMatch(/^Hint: /);
    expect(lines[2]).toBe('Retry: fatal');
  });

  it('emits a structured envelope when --json is set', async () => {
    const { BskRpcError } = await import('./bsk/errors.js');
    const client = new FakeClient();
    client.onCall = async () => {
      throw new BskRpcError({ code: 'cdp_failed', message: 'Detached while handling command' });
    };
    const capture = makeCapture();

    await runBskCli(
      ['node', 'bsk', '--json', '--session', 's1', 'click', '@e3'],
      { client, stdout: capture.out, stderr: capture.err },
    );

    const parsed = JSON.parse(capture.errorText()) as { error: Record<string, unknown> };
    expect(parsed.error).toMatchObject({
      code: 'cdp_failed',
      message: 'Detached while handling command',
      retry: 'unsafe_to_retry',
    });
    expect(String(parsed.error.hint)).toMatch(/Inspect the page state/);
    // The human-readable three-line form must not leak into JSON mode.
    expect(capture.errorText()).not.toContain('Error:');
  });
});
