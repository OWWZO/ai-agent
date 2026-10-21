import { describe, expect, it } from 'vitest';

import { BskUsageError } from '../errors.js';
import { METHODS } from '../methods.js';
import { parseHelpTarget, requestHelp } from './human.js';
import { RecordingCaller } from './test-support.js';

describe('parseHelpTarget', () => {
  it('classifies refs and selectors', () => {
    expect(parseHelpTarget('@e3')).toEqual({ ref: '@e3' });
    expect(parseHelpTarget('e12')).toEqual({ ref: 'e12' });
    expect(parseHelpTarget('#login')).toEqual({ selector: '#login' });
    expect(parseHelpTarget('.btn')).toEqual({ selector: '.btn' });
    expect(parseHelpTarget('email')).toEqual({ selector: 'email' });
  });
});

describe('requestHelp', () => {
  it('defaults to a 5m budget and maps targets + criteria', async () => {
    const client = new RecordingCaller().reply(METHODS.toolRequestHelp, {
      outcome: 'continued',
      tab_id: 1,
    });
    const result = await requestHelp(client, {
      sessionId: 's1',
      prompt: 'solve the captcha',
      title: 'Human needed',
      tabId: 2,
      targets: ['@e4', '#captcha'],
      completionCriteria: '{"any":[{"url_contains":"/dashboard"}],"stable_for_ms":1000}',
    });
    expect(result.outcome).toBe('continued');
    expect(client.last(METHODS.toolRequestHelp).params).toEqual({
      session_id: 's1',
      tab_id: 2,
      prompt: 'solve the captcha',
      title: 'Human needed',
      targets: [{ ref: '@e4' }, { selector: '#captcha' }],
      completion_criteria: { any: [{ url_contains: '/dashboard' }], stable_for_ms: 1000 },
      timeout_ms: 300_000,
    });
    expect(client.last(METHODS.toolRequestHelp).options?.timeoutMs).toBe(315_000);
  });

  it('omits empty targets and absent criteria', async () => {
    const client = new RecordingCaller().reply(METHODS.toolRequestHelp, {});
    await requestHelp(client, { sessionId: 's1', prompt: 'hi', targets: [], timeoutMs: 1_000 });
    expect(client.last(METHODS.toolRequestHelp).params).toEqual({
      session_id: 's1',
      prompt: 'hi',
      timeout_ms: 1_000,
    });
  });

  it('rejects malformed criteria JSON', async () => {
    await expect(
      requestHelp(new RecordingCaller(), {
        sessionId: 's1',
        prompt: 'hi',
        completionCriteria: '{oops',
      }),
    ).rejects.toThrow(BskUsageError);
  });
});
