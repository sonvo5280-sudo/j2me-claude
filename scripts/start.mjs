// One command for the whole app: builds what is missing, then runs the gateway (which also serves the web page).
//   npm start        -> http://localhost:3000   (PORT overrides)
// The games need a JDK 21 (javac + java). Without one the web page still loads, but starting a game reports
// that Java is missing.
import { spawn, spawnSync } from 'node:child_process';
import fs from 'node:fs';
import path from 'node:path';
import { Readable } from 'node:stream';
import { pipeline } from 'node:stream/promises';
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
function hasJdk21() {
  const jc = spawnSync('javac', ['-version'], { stdio: 'pipe', encoding: 'utf8', shell: win });
  const jv = spawnSync('java', ['-version'], { stdio: 'pipe', encoding: 'utf8', shell: win });
  if (jc.error || jc.status !== 0 || jv.error || jv.status !== 0) return false;
  const out = `${jc.stdout || ''} ${jc.stderr || ''}`;
  return /\b21(\.|\b)/.test(out);
}

function findLocalJdk(jdkDir) {
  if (!fs.existsSync(jdkDir)) return null;
  const exe = win ? 'javac.exe' : 'javac';
  if (fs.existsSync(path.join(jdkDir, 'bin', exe))) return jdkDir;
  for (const ent of fs.readdirSync(jdkDir, { withFileTypes: true })) {
    if (!ent.isDirectory()) continue;
    const direct = path.join(jdkDir, ent.name);
    if (fs.existsSync(path.join(direct, 'bin', exe))) return direct;
    const mac = path.join(direct, 'Contents', 'Home');
    if (fs.existsSync(path.join(mac, 'bin', exe))) return mac;
  }
  return null;
}

function useJdk(home) {
  process.env.JAVA_HOME = home;
  process.env.PATH = `${path.join(home, 'bin')}${path.delimiter}${process.env.PATH || ''}`;
}

async function ensureJdk() {
  if (process.env.J2ME_LOCAL_JDK !== '1' && hasJdk21()) return true;

  const jdkDir = path.join(ROOT, '.jdk');
  const existing = findLocalJdk(jdkDir);
  if (existing) {
    say(`using local JDK at ${existing}`);
    useJdk(existing);
    return true;
  }

  const osMap = { linux: 'linux', darwin: 'mac' };
  const archMap = { x64: 'x64', arm64: 'aarch64' };
  const osName = osMap[process.platform];
  const archName = archMap[process.arch];
  const url = process.env.JDK_URL
    || (osName && archName
      ? `https://api.adoptium.net/v3/binary/latest/21/ga/${osName}/${archName}/jdk/hotspot/normal/eclipse`
      : null);
  if (!url) {
    say(`WARNING: JDK download failed: unsupported platform ${process.platform}/${process.arch}`);
    return false;
  }

  const tgz = path.join(jdkDir, 'jdk.tar.gz');
  fs.mkdirSync(jdkDir, { recursive: true });
  try {
    say(`downloading Temurin JDK 21 from ${url}…`);
    const res = await fetch(url, { redirect: 'follow', signal: AbortSignal.timeout(10 * 60 * 1000) });
    if (!res.ok) throw new Error(`HTTP ${res.status} ${res.statusText}`);
    if (!res.body) throw new Error('empty response body');
    await pipeline(Readable.fromWeb(res.body), fs.createWriteStream(tgz));
    say('extracting JDK 21…');
    const ext = spawnSync('tar', ['-xzf', tgz, '-C', jdkDir], { stdio: 'pipe', encoding: 'utf8' });
    if (ext.error || ext.status !== 0) {
      throw new Error(ext.error?.message || ext.stderr?.trim() || `tar exited with ${ext.status}`);
    }
    const home = findLocalJdk(jdkDir);
    if (!home) throw new Error('bin/javac not found after extraction');
    useJdk(home);
    say(`JDK 21 ready at ${home}`);
    return true;
  } catch (err) {
    const cause = err?.cause?.message ? ` (${err.cause.message})` : '';
    say(`WARNING: JDK download failed: ${err?.message || err}${cause}`);
    return false;
  } finally {
    fs.rmSync(tgz, { force: true });
  }
}

const jdkReady = await ensureJdk();
const built = fs.existsSync(path.join(ROOT, 'worker/build/microemu')) || fs.existsSync(path.join(ROOT, 'worker/build/own'));
if (!built) {
  if (!jdkReady) {
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
