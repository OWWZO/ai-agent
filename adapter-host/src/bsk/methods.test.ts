import { readFileSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

import { describe, expect, it } from 'vitest';

import { ALL_METHODS, METHODS, isMethod } from './methods.js';

const HERE = dirname(fileURLToPath(import.meta.url));
const METHOD_RS = resolve(HERE, '../../../BrowserSkill/crates/bsk-protocol/src/method.rs');

function wireNamesFromRust(): string[] {
  const source = readFileSync(METHOD_RS, 'utf8');
  const names: string[] = [];
  for (const match of source.matchAll(/#\[serde\(rename = "([^"]+)"\)\]/g)) {
    names.push(match[1]);
  }
  return names;
}

describe('methods', () => {
  it('transcribes every Rust serde rename verbatim and in order', () => {
    expect(ALL_METHODS).toEqual(wireNamesFromRust());
  });

  it('has no duplicates', () => {
    expect(new Set(ALL_METHODS).size).toBe(ALL_METHODS.length);
  });

  it('uses the corrected session start method', () => {
    expect(METHODS.toolSessionStart).toBe('tool.session_start');
    expect(METHODS.sessionStart).toBe('session.start');
  });

  it('does not invent tool.wait_for_element', () => {
    expect(isMethod('tool.wait_for_element')).toBe(false);
    expect(METHODS.toolWaitMs).toBe('tool.wait_ms');
    expect(METHODS.toolWaitForNavigation).toBe('tool.wait_for_navigation');
  });

  it('recognises known methods and rejects others', () => {
    expect(isMethod('tool.click')).toBe(true);
    expect(isMethod('cancel')).toBe(true);
    expect(isMethod('tool.nope')).toBe(false);
    expect(isMethod(42)).toBe(false);
  });
});
