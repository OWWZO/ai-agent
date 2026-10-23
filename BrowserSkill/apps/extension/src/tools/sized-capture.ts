// Temporary viewport sizing for one screenshot.
//
// Ported from the OpenCLI extension's `screenshot(width/height/fullPage)`
// path: pin `Emulation.setDeviceMetricsOverride`, capture, then restore. The
// restore step is what keeps this independent of `tool.emulate` — that tool
// owns a persistent profile per tab, so a sized screenshot must put back
// whatever was live rather than clearing the override.

import type { SessionContext } from "@/session-manager/manager";
import { liveEmulationOverrides } from "./emulate";
import type { CdpRunner } from "./shared";

/** CSS-pixel bounds for a requested capture size, mirroring window sizing. */
export const MIN_CAPTURE_DIMENSION = 1;
export const MAX_CAPTURE_DIMENSION = 30_000;

export interface CaptureSize {
  width: number;
  height: number;
}

/** Validate an optional width/height pair, matching `emulate`'s rules. */
export function validateCaptureSize(
  width: unknown,
  height: unknown,
): CaptureSize | undefined | { code: "invalid_params"; message: string } {
  if (width === undefined && height === undefined) return undefined;
  if (width === undefined || height === undefined) {
    return { code: "invalid_params", message: "width and height must be given together" };
  }
  for (const [name, value] of [
    ["width", width],
    ["height", height],
  ] as const) {
    if (
      typeof value !== "number" ||
      !Number.isSafeInteger(value) ||
      value < MIN_CAPTURE_DIMENSION ||
      value > MAX_CAPTURE_DIMENSION
    ) {
      return {
        code: "invalid_params",
        message: `${name} must be an integer in ${MIN_CAPTURE_DIMENSION}..=${MAX_CAPTURE_DIMENSION}`,
      };
    }
  }
  return { width: width as number, height: height as number };
}

/**
 * Apply `size` for the duration of `capture`, then restore the tab's previous
 * device profile. Restoring is best-effort: a capture that already produced an
 * image must not be reported as a failure because the cleanup raced a
 * navigation.
 */
export async function withTemporaryViewport<T>(
  cdp: CdpRunner,
  ctx: SessionContext,
  tabId: number,
  size: CaptureSize | undefined,
  capture: () => Promise<T>,
): Promise<T> {
  if (!size) return capture();
  const restore = liveEmulationOverrides(ctx, tabId, cdp);
  await cdp.send(tabId, "Emulation.setDeviceMetricsOverride", {
    width: size.width,
    height: size.height,
    deviceScaleFactor: 1,
    mobile: false,
  });
  try {
    return await capture();
  } finally {
    try {
      if (restore && restore.width !== undefined && restore.height !== undefined) {
        await cdp.send(tabId, "Emulation.setDeviceMetricsOverride", {
          width: restore.width,
          height: restore.height,
          deviceScaleFactor: restore.device_scale_factor ?? 0,
          mobile: restore.mobile ?? false,
        });
      } else {
        await cdp.send(tabId, "Emulation.clearDeviceMetricsOverride");
      }
    } catch {
      // The page may have navigated or detached mid-cleanup. The next
      // `tool.emulate` reapplies its profile explicitly.
    }
  }
}
