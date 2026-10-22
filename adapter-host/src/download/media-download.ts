/**
 * Media download helper — shared logic for batch downloading images/videos.
 *
 * Used by: xiaohongshu/download, twitter/download, bilibili/download,
 * and future media adapters.
 *
 * Flow: MediaItem[] → DownloadProgressTracker → httpDownload/ytdlpDownload → results
 */

import * as fs from 'node:fs';
import * as path from 'node:path';
import { getErrorMessage } from '../errors.js';
import {
  httpDownload,
  ytdlpDownload,
  checkYtdlp,
  getTempDir,
  exportCookiesToNetscape,
  sanitizeFilename,
} from './index.js';
import type { BrowserCookie } from '../types.js';
import { DownloadProgressTracker, formatBytes } from './progress.js';

// ============================================================
// Types
// ============================================================

export interface MediaItem {
  type: 'image' | 'video' | 'video-tweet' | 'video-ytdlp';
  url: string;
  /** Optional custom filename (without directory) */
  filename?: string;
}

export interface MediaDownloadOptions {
  output: string;
  /** Subdirectory inside output */
  subdir?: string;
  /** Cookie string for HTTP downloads */
  cookies?: string;
  /** Raw browser cookies — auto-exported to Netscape for yt-dlp, auto-cleaned up */
  browserCookies?: BrowserCookie[];
  /** Timeout in ms (default: 30000 for images, 60000 for videos) */
  timeout?: number;
  /** File name prefix (default: 'download') */
  filenamePrefix?: string;
  /** Extra yt-dlp args */
  ytdlpExtraArgs?: string[];
  /** Whether to show progress (default: true) */
  verbose?: boolean;
}

export interface MediaDownloadResult {
  index: number;
  type: string;
  status: string;
  size: string;
}

/**
 * Artifact candidate reported to the host for download-type commands.
 *
 * Contract (consumed by Cute's CliArtifactCandidateParser):
 * - `relativePath` is relative to the CLI working directory (Agent workspace),
 *   uses forward slashes, and never contains `..` or an absolute path;
 * - `size` is the byte count on disk;
 * - `mime` is the best-effort content type.
 * Download URLs are NOT produced here: Cute's file service registers the
 * file and generates preview/download URLs, so the two systems never maintain
 * separate URL rules.
 */
export interface MediaArtifactCandidate {
  relativePath: string;
  size: number;
  mime: string;
}

/**
 * Rows plus artifact candidates in one payload.
 * `renderJson` prints the whole object, so the `artifacts` key reaches the
 * host's JSON output without touching the existing table columns.
 */
export interface MediaDownloadPayload {
  files: MediaDownloadResult[];
  artifacts?: MediaArtifactCandidate[];
}

// ============================================================
// Main API
// ============================================================

/**
 * Batch download media files with progress tracking.
 *
 * Handles:
 * - DownloadProgressTracker for terminal UX
 * - Automatic httpDownload vs ytdlpDownload routing via MediaItem.type
 * - Cookie export to Netscape format for yt-dlp (auto-cleanup)
 * - Directory creation
 * - Error handling with per-file results
 */
export async function downloadMedia(
  items: MediaItem[],
  options: MediaDownloadOptions,
): Promise<MediaDownloadPayload> {
  const {
    output,
    subdir,
    cookies,
    browserCookies,
    timeout,
    filenamePrefix = 'download',
    ytdlpExtraArgs = [],
    verbose = true,
  } = options;

  if (!items || items.length === 0) {
    return { files: [{ index: 0, type: '-', status: 'failed', size: 'No media found' }] };
  }

  // Only report workspace-internal relative paths as candidates: the CLI cwd is
  // the Agent workspace, so `process.cwd()` is the correct base for the contract.
  const cwd = process.cwd();
  const candidates: MediaArtifactCandidate[] = [];

  // Create output directory
  const outputDir = subdir ? path.join(output, subdir) : output;
  fs.mkdirSync(outputDir, { recursive: true });

  // Pre-check yt-dlp availability (once, not per-item)
  const hasYtdlp = checkYtdlp();

  // Auto-export browser cookies to Netscape format for yt-dlp
  let cookiesFile: string | undefined;
  const needsYtdlp = items.some(m => m.type === 'video-tweet' || m.type === 'video-ytdlp');
  if (needsYtdlp && browserCookies && browserCookies.length > 0) {
    const tempDir = getTempDir();
    fs.mkdirSync(tempDir, { recursive: true });
    cookiesFile = path.join(tempDir, `media_cookies_${Date.now()}.txt`);
    exportCookiesToNetscape(browserCookies, cookiesFile);
  }

  const tracker = new DownloadProgressTracker(items.length, verbose);
  const results: MediaDownloadResult[] = [];

  try {
    for (let i = 0; i < items.length; i++) {
      const media = items[i];
      const isVideo = media.type !== 'image';
      const ext = isVideo ? 'mp4' : 'jpg';
      const filename = resolveMediaFilename(media.filename, filenamePrefix, i + 1, ext);
      const destPath = path.join(outputDir, filename);

      const progressBar = tracker.onFileStart(filename, i);

      try {
        let result: { success: boolean; size: number; error?: string };
        const useYtdlp = (media.type === 'video-tweet' || media.type === 'video-ytdlp') && hasYtdlp;

        if (useYtdlp) {
          result = await ytdlpDownload(media.url, destPath, {
            cookiesFile,
            extraArgs: ytdlpExtraArgs,
            onProgress: (percent) => {
              if (progressBar) progressBar.update(percent, 100);
            },
          });
        } else {
          // Direct HTTP download for images and direct video URLs
          const dlTimeout = timeout || (isVideo ? 60000 : 30000);
          result = await httpDownload(media.url, destPath, {
            cookies,
            timeout: dlTimeout,
            onProgress: (received, total) => {
              if (progressBar) progressBar.update(received, total);
            },
          });
        }

        if (progressBar) {
          progressBar.complete(result.success, result.success ? formatBytes(result.size) : undefined);
        }
        tracker.onFileComplete(result.success);

        if (result.success) {
          // Stat after the write so the candidate size matches what the host will register.
          let bytes = result.size;
          try {
            bytes = fs.statSync(destPath).size;
          } catch {
            // fall back to the downloader's reported byte count
          }
           const relativePath = toRelativePosixPath(cwd, destPath);
           if (relativePath) {
             candidates.push({
               relativePath,
               size: bytes,
               mime: isVideo ? 'video/mp4' : 'image/jpeg',
             });
           }
        }

        results.push({
          index: i + 1,
          type: media.type === 'video-tweet' || media.type === 'video-ytdlp' ? 'video' : media.type,
          status: result.success ? 'success' : 'failed',
          size: result.success ? formatBytes(result.size) : (result.error || 'unknown error'),
        });
      } catch (err) {
        const msg = getErrorMessage(err);
        if (progressBar) progressBar.fail(msg);
        tracker.onFileComplete(false);

        results.push({
          index: i + 1,
          type: media.type,
          status: 'failed',
          size: msg,
        });
      }
    }
  } finally {
    tracker.finish();

    // Auto-cleanup exported cookies file
    if (cookiesFile && fs.existsSync(cookiesFile)) {
      fs.unlinkSync(cookiesFile);
    }
  }

  return { files: results, artifacts: candidates };
}

/** Workspace-relative POSIX path; empty string when the file escapes the cwd. */
function toRelativePosixPath(cwd: string, absolutePath: string): string | null {
  const relative = path.relative(cwd, absolutePath).replace(/\\/g, '/');
  if (!relative || relative.startsWith('..') || path.isAbsolute(relative)) {
    return null;
  }
  return relative;
}

// ============================================================
// Artifact envelope
// ============================================================

/**
 * Wrap a media download payload so the host can read artifact candidates.
 *
 * `renderJson` prints the returned object as-is, while `renderTable` only reads
 * the declared `columns`, so the legacy table view stays unchanged. Commands
 * with extra columns append {@link buildArtifactEnvelope} as a trailing row.
 */
export function buildDownloadPayload(payload: MediaDownloadPayload): MediaDownloadPayload {
  return {
    files: payload.files ?? [],
    ...(payload.artifacts && payload.artifacts.length > 0
      ? { artifacts: payload.artifacts }
      : {}),
  };
}

/** Artifact-only row for commands that keep their own business columns. */
export function buildArtifactEnvelope(payload: MediaDownloadPayload): Record<string, unknown> {
  if (!payload.artifacts || payload.artifacts.length === 0) {
    return { artifacts: [] };
  }
  return { artifacts: payload.artifacts };
}

function resolveMediaFilename(filename: string | undefined, prefix: string, index: number, ext: string): string {
  const safePrefix = sanitizePathSegment(path.basename(path.win32.basename(prefix))) || 'download';
  const fallback = `${safePrefix}_${index}.${ext}`;
  if (!filename) return fallback;

  const basename = path.basename(path.win32.basename(filename));
  const safeName = sanitizePathSegment(basename);
  return safeName || fallback;
}

function sanitizePathSegment(value: string): string {
  const sanitized = sanitizeFilename(value);
  return sanitized && sanitized !== '.' && sanitized !== '..' ? sanitized : '';
}
