// Small helpers that deal with the operating system.
import { spawnSync } from 'node:child_process';

export function hasCommand(cmd) {
  return spawnSync(process.platform === 'win32' ? 'where' : 'which', [cmd], { stdio: 'ignore' }).status === 0;
}
