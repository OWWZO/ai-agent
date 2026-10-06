// New-version notice: once a week a background child process reads package.json on GitHub and writes the result into state.json;
// on the next render, if a new version exists, print one "! Update hint" line for the Agent, which asks the user. Never updates automatically.
import { spawn } from 'node:child_process';
import { DAY, writeState } from './state.js';

export const UPDATE = Object.freeze({
  checkEveryDays: 7,
  hintEveryDays: 3,
  timeoutMs: 5000,
  url: 'https://raw.githubusercontent.com/QingYunA/answer-me-with-html/main/package.json',
});

const SEMVER = /^v?(\d+)\.(\d+)\.(\d+)$/;

export function parseVersion(v) {
  const m = String(v).trim().match(SEMVER);
  return m ? m.slice(1, 4).map(Number) : null;
}

// Whether a is newer than b. Returns false when either is not in x.y.z format.
export function newer(a, b) {
  const pa = parseVersion(a);
  const pb = parseVersion(b);
  if (!pa || !pb) return false;
  const i = pa.findIndex((n, k) => n !== pb[k]);
  return i !== -1 && pa[i] > pb[i];
}

// Give the update command for the install method. scriptPath is the path of the running am script.
export function updateCommand(scriptPath = '') {
  if (/[\\/]\.claude[\\/]plugins[\\/]/.test(scriptPath)) {
    return 'run claude plugin update answer-me-with-html@answer-me-with-html in a terminal (or click Update now on the Installed tab of /plugin), then /reload-plugins';
  }
  if (/[\\/]bin[\\/]am\.js$/.test(scriptPath)) {
    return 'run git pull && npm install in the answer-me-with-html repository';
  }
  return 'run npx skills update answer-me-with-html -y';
}

// update_check off, CI and AM_NO_UPDATE_CHECK each turn off both the background check and the update notice.
export const updateEnabled = (env, config) => !(config.update_check === false || env.CI || env.AM_NO_UPDATE_CHECK);

export function updateHint(state, current, scriptPath, now = Date.now()) {
  if (!newer(state.latestVersion, current)) return null;
  if (state.lastUpdateHint && now - state.lastUpdateHint < UPDATE.hintEveryDays * DAY) return null;
  return `! Update hint: Answer me with HTML ${state.latestVersion} is available (current ${current}). Ask the user whether to update: ${updateCommand(scriptPath)}.`;
}

export function shouldCheckUpdate(state, env, config, now = Date.now()) {
  if (!updateEnabled(env, config)) return false;
  return !state.lastUpdateCheck || now - state.lastUpdateCheck >= UPDATE.checkEveryDays * DAY;
}

export function spawnUpdateCheck(home, scriptPath) {
  writeState(home, { lastUpdateCheck: Date.now() });
  try {
    spawn(process.execPath, [scriptPath, '__update-check'], { detached: true, stdio: 'ignore', env: { ...process.env, AM_HOME: home } })
      .on('error', () => {})
      .unref();
  } catch {
    // A failed background check does not affect normal use.
  }
}

// Accept only x.y.z version numbers and ignore anything else, so arbitrary remote text never reaches the Agent's context.
export async function runUpdateCheck(home, fetchImpl = fetch) {
  try {
    const res = await fetchImpl(UPDATE.url, { signal: AbortSignal.timeout(UPDATE.timeoutMs) });
    if (!res.ok) return null;
    const { version } = await res.json();
    if (!parseVersion(version)) return null;
    writeState(home, { latestVersion: version.trim(), lastUpdateCheck: Date.now() });
    return version.trim();
  } catch {
    return null;
  }
}
