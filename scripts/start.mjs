// One command for the whole app: builds what is missing, then runs the gateway (which also serves the web page).
//   npm start        -> http://localhost:3000   (PORT overrides)
// The games need a JDK 21 (javac + java). Without one the web page still loads, but starting a game reports
// that Java is missing.
import { spawn, spawnSync } from 'node:child_process';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const win = process.platform === 'win32';
const say = (m) => console.log(`[start] ${m}`);

function run(cmd, args, opts = {}) {
  return spawnSync(cmd, args, { cwd: ROOT, stdio: 'inherit', shell: win, ...opts });
}

// 1. web page
const dist = path.join(ROOT, 'web/dist/index.html');
if (!fs.existsSync(dist) || process.env.REBUILD_WEB === '1') {
  say('building the web page…');
  if (!fs.existsSync(path.join(ROOT, 'web/node_modules'))) run('npm', ['--prefix', 'web', 'install']);
  const r = run('npm', ['--prefix', 'web', 'run', 'build']);
  if (r.status !== 0) { console.error('[start] the web build failed, see the messages above'); process.exit(1); }
}

// 2. Java worker
const built = fs.existsSync(path.join(ROOT, 'worker/build/microemu')) || fs.existsSync(path.join(ROOT, 'worker/build/own'));
if (!built) {
  const jdk = spawnSync('javac', ['-version'], { stdio: 'pipe', shell: win });
  if (jdk.error || jdk.status !== 0) {
    say('WARNING: no JDK found (javac). The page will load, but games cannot start until JDK 21 is installed.');
  } else {
    say('compiling the Java worker (first run only)…');
    const hasBash = spawnSync('bash', ['-c', 'exit 0'], { stdio: 'pipe', shell: win }).status === 0; // build.sh uses bash
    const r = run(hasBash ? 'bash' : 'sh', ['worker/build.sh']);
    if (r.status !== 0) say('WARNING: the worker build failed; games will not start. See the messages above.');
  }
}

// 3. gateway
const port = process.env.PORT || '3000';
say(`starting on port ${port}`);
const gw = spawn(process.execPath, ['server.js'], {
  cwd: path.join(ROOT, 'gateway'),
  stdio: 'inherit',
  env: { ...process.env, PORT: port },
});
for (const sig of ['SIGINT', 'SIGTERM']) process.on(sig, () => gw.kill(sig));
gw.on('exit', (code) => process.exit(code ?? 0));
