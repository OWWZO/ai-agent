import { describe, expect, it } from 'vitest';

import {
  METHOD_EFFECT,
  isMutating,
  methodEffect,
  requiresInterruptGate,
  requiresInterruptGateWithParams,
} from './effects.js';
import { METHODS } from './methods.js';

describe('METHOD_EFFECT', () => {
  it('matches the Rust judgment calls', () => {
    expect(methodEffect(METHODS.toolEvaluate)).toBe('BrowserMutation');
    expect(methodEffect(METHODS.toolObserve)).toBe('TransientInput');
    expect(methodEffect(METHODS.toolScreenshotFullPage)).toBe('TransientInput');
    expect(methodEffect(METHODS.toolScreenshot)).toBe('PassiveRead');
    expect(methodEffect(METHODS.toolSnapshot)).toBe('PassiveRead');
    expect(methodEffect(METHODS.toolWaitMs)).toBe('PassiveRead');
    expect(methodEffect(METHODS.toolWaitForNavigation)).toBe('PassiveRead');
    expect(methodEffect(METHODS.cancel)).toBe('ControlPlane');
    expect(methodEffect(METHODS.toolSessionStart)).toBe('ControlPlane');
    expect(methodEffect(METHODS.sessionStop)).toBe('ControlPlane');
    expect(methodEffect(METHODS.transferRead)).toBe('ControlPlane');
  });

  it('gates transient input and mutations only', () => {
    expect(requiresInterruptGate(METHODS.toolHover)).toBe(true);
    expect(requiresInterruptGate(METHODS.toolClick)).toBe(true);
    expect(requiresInterruptGate(METHODS.toolSnapshot)).toBe(false);
    expect(requiresInterruptGate(METHODS.sessionStop)).toBe(false);
    expect(requiresInterruptGate(METHODS.cancel)).toBe(false);
  });

  it('gates debug writes but not debug teardown', () => {
    for (const action of ['rule_add', 'rule_enable', 'replay']) {
      expect(
        requiresInterruptGateWithParams(METHODS.toolDebug, { action }),
        action,
      ).toBe(true);
    }
    for (const action of ['request', 'rules', 'export', 'stop', 'rule_disable', 'rule_remove']) {
      expect(
        requiresInterruptGateWithParams(METHODS.toolDebug, { action }),
        action,
      ).toBe(false);
    }
    expect(requiresInterruptGateWithParams(METHODS.toolDebug, undefined)).toBe(false);
  });

  it('classifies mutation helpers consistently with the Rust tests', () => {
    for (const method of [
      METHODS.toolTabCreate,
      METHODS.toolTabClose,
      METHODS.toolTabBorrow,
      METHODS.toolTabReturn,
      METHODS.toolTabSelect,
      METHODS.toolNavigate,
      METHODS.toolNavigateBack,
      METHODS.toolNavigateForward,
      METHODS.toolReload,
      METHODS.toolClick,
      METHODS.toolWheel,
      METHODS.toolScrollTo,
      METHODS.toolFocus,
      METHODS.toolBlur,
      METHODS.toolFill,
      METHODS.toolPress,
      METHODS.toolSelect,
      METHODS.toolEvaluate,
      METHODS.toolRecordStart,
      METHODS.toolWindowResize,
      METHODS.toolEmulate,
    ]) {
      expect(isMutating(method), method).toBe(true);
    }
    for (const method of [
      METHODS.toolTabList,
      METHODS.toolSnapshot,
      METHODS.toolHover,
      METHODS.toolObserve,
      METHODS.toolGetHtml,
      METHODS.toolScreenshot,
      METHODS.toolConsole,
      METHODS.toolNetwork,
      METHODS.toolWaitForNavigation,
      METHODS.toolWaitMs,
      METHODS.toolRecordStop,
      METHODS.toolRecordAwait,
      METHODS.sessionStart,
      METHODS.sessionStop,
      METHODS.toolSessionStart,
      METHODS.systemHandshake,
      METHODS.cancel,
    ]) {
      expect(isMutating(method), method).toBe(false);
    }
  });

  it('declares an entry for every method', () => {
    expect(Object.keys(METHOD_EFFECT)).toHaveLength(Object.keys(METHODS).length);
  });
});
