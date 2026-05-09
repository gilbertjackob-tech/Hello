import { existsSync, mkdirSync } from 'fs';
import path from 'path';

function resolveBrowserRoot() {
  const cwd = path.resolve(process.cwd());
  if (path.basename(cwd) === 'browser' && path.basename(path.dirname(cwd)) === 'apps') {
    return cwd;
  }

  const nestedBrowserRoot = path.resolve(cwd, 'apps', 'browser');
  if (existsSync(nestedBrowserRoot)) {
    return nestedBrowserRoot;
  }

  return cwd;
}

export const BROWSER_ROOT = resolveBrowserRoot();
export const MONOREPO_ROOT = path.resolve(BROWSER_ROOT, '..', '..');
export const HELLO_ROOT = path.resolve(MONOREPO_ROOT, 'apps', 'hello');
export const DATA_ROOT = path.resolve(MONOREPO_ROOT, 'data');
export const BROWSER_DATA_DIR = path.resolve(DATA_ROOT, 'browser');
export const HELLO_DATA_DIR = path.resolve(DATA_ROOT, 'hello');
export const HELLO_UPLOADS_DIR = path.resolve(HELLO_DATA_DIR, 'uploads');
export const HELLO_DB_PATH = path.resolve(HELLO_DATA_DIR, 'hello.db');

for (const directory of [DATA_ROOT, BROWSER_DATA_DIR, HELLO_DATA_DIR, HELLO_UPLOADS_DIR]) {
  if (!existsSync(directory)) {
    mkdirSync(directory, { recursive: true });
  }
}
