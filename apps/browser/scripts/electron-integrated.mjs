import { existsSync } from 'node:fs';
import path from 'node:path';
import { spawn } from 'node:child_process';

const isWindows = process.platform === 'win32';

function getElectronBinary() {
  const candidates = isWindows
    ? [
      path.join(process.cwd(), 'node_modules', 'electron', 'dist', 'electron.exe'),
      path.join(process.cwd(), '..', '..', 'node_modules', 'electron', 'dist', 'electron.exe'),
    ]
    : process.platform === 'darwin'
      ? [
        path.join(process.cwd(), 'node_modules', 'electron', 'dist', 'Electron.app', 'Contents', 'MacOS', 'Electron'),
        path.join(process.cwd(), '..', '..', 'node_modules', 'electron', 'dist', 'Electron.app', 'Contents', 'MacOS', 'Electron'),
      ]
      : [
        path.join(process.cwd(), 'node_modules', 'electron', 'dist', 'electron'),
        path.join(process.cwd(), '..', '..', 'node_modules', 'electron', 'dist', 'electron'),
      ];

  const binary = candidates.find((candidate) => existsSync(candidate));
  if (!binary) {
    throw new Error('Electron binary not found in node_modules/electron/dist');
  }

  return binary;
}

const electron = spawn(getElectronBinary(), ['.'], {
  stdio: 'inherit',
  env: {
    ...Object.fromEntries(Object.entries(process.env).filter(([key]) => key !== 'ELECTRON_RUN_AS_NODE')),
    NODE_ENV: 'production',
  },
});

electron.on('exit', (code) => {
  process.exit(code ?? 0);
});

process.on('SIGINT', () => {
  electron.kill();
  process.exit(0);
});

process.on('SIGTERM', () => {
  electron.kill();
  process.exit(0);
});
