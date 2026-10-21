import { describe, expect, it } from 'vitest';
import { resolveBackend } from './index.js';

describe('resolveBackend', () => {
  it('defaults to opencli when BROWSER_BACKEND is unset', () => {
    const previous = process.env.BROWSER_BACKEND;
    delete process.env.BROWSER_BACKEND;
    try {
      expect(resolveBackend()).toBe('opencli');
      expect(resolveBackend(undefined)).toBe('opencli');
    } finally {
      if (previous !== undefined) process.env.BROWSER_BACKEND = previous;
    }
  });

  it('accepts both known backends', () => {
    expect(resolveBackend('opencli')).toBe('opencli');
    expect(resolveBackend('bsk')).toBe('bsk');
  });

  it('throws on an unknown backend instead of silently falling back', () => {
    expect(() => resolveBackend('chromium')).toThrow(/Unknown BROWSER_BACKEND "chromium"/);
  });

  it('treats an empty env value as the default', () => {
    expect(resolveBackend('')).toBe('opencli');
  });
});
